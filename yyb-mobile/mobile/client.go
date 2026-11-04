package mobile

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"yyb_go/internal/protocol"
	"yyb_go/internal/qr"
	"yyb_go/internal/store"
)

// Client owns one in-memory protocol runtime. No account or session data is
// written to disk; Android only receives the QR image and one-time login code.
type Client struct {
	qr   *qr.Client
	pool *protocol.Pool
	db   *store.DB

	mu       sync.Mutex
	sessions map[string]*sessionState
	closed   bool
}

type sessionState struct {
	ctx    context.Context
	cancel context.CancelFunc
	qr     *qr.Session
}

type qrCreated struct {
	SessionID   string `json:"session_id"`
	ImageBase64 string `json:"image_base64"`
}

type qrPollResult struct {
	Status  string `json:"status"`
	ErrCode *int   `json:"errcode,omitempty"`
}

func NewClient() (*Client, error) {
	db, err := store.Open(":memory:")
	if err != nil {
		return nil, err
	}
	cfg := protocol.DefaultConfig()
	cfg.MaxLoginConcurrency = 1
	cfg.MaxShortlinkConcurrency = 2
	return &Client{
		qr:       qr.NewClient(8 * time.Second),
		pool:     protocol.NewPool(cfg, db),
		db:       db,
		sessions: map[string]*sessionState{},
	}, nil
}

// CreateQR returns JSON with session_id and a base64-encoded image supplied by
// WeChat. Re-rendering a guessed QR URL is intentionally not supported.
func (c *Client) CreateQR() (string, error) {
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		return "", errors.New("client is closed")
	}
	c.mu.Unlock()

	ctx, cancel := context.WithCancel(context.Background())
	img, err := c.qr.GetQRCodeImage(ctx)
	if err != nil {
		cancel()
		return "", err
	}
	state := &sessionState{ctx: ctx, cancel: cancel, qr: img.Session}
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		cancel()
		return "", errors.New("client is closed")
	}
	c.sessions[img.Session.ID] = state
	c.mu.Unlock()

	payload, err := json.Marshal(qrCreated{
		SessionID:   img.Session.ID,
		ImageBase64: base64.StdEncoding.EncodeToString(img.ImageBytes),
	})
	if err != nil {
		c.Cancel(img.Session.ID)
		return "", err
	}
	return string(payload), nil
}

// Poll performs one WeChat long poll. The caller must invoke it off Android's
// main thread and keep calling it while status is pending or scanned.
func (c *Client) Poll(sessionID string) (string, error) {
	state, err := c.session(sessionID)
	if err != nil {
		return "", err
	}
	result, err := c.qr.PollQRCode(state.ctx, state.qr)
	if err != nil {
		return "", err
	}
	if result.Status == "expired" || result.Status == "cancelled" || result.Status == "unknown" {
		c.Cancel(sessionID)
	}
	payload, err := json.Marshal(qrPollResult{Status: result.Status, ErrCode: result.ErrCode})
	return string(payload), err
}

// ConfirmAndGetCode exchanges the authorized QR session for a one-time code of
// the target mini program. The returned code must be consumed immediately.
func (c *Client) ConfirmAndGetCode(sessionID string, appID string) (string, error) {
	state, err := c.session(sessionID)
	if err != nil {
		return "", err
	}
	if appID == "" {
		return "", errors.New("appID is empty")
	}
	login, err := c.qr.GetLoginBuffer(state.ctx, state.qr)
	if err != nil {
		return "", err
	}
	account, err := c.db.UpsertAccount(
		state.ctx,
		login.Credentials.OpenID,
		login.LoginBuffer,
		nil,
		nil,
		nil,
		nil,
		nil,
		nil,
	)
	if err != nil {
		return "", err
	}
	defer func() { _ = c.db.DeleteAccount(context.Background(), account.ID) }()

	result, err := c.pool.GetCode(state.ctx, login.LoginBuffer, appID, account.ID, "", false)
	if err != nil {
		return "", err
	}
	code, _ := result["code"].(string)
	if code == "" {
		return "", errors.New("wx.login returned an empty code")
	}
	c.Cancel(sessionID)
	return code, nil
}

func (c *Client) session(id string) (*sessionState, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed {
		return nil, errors.New("client is closed")
	}
	state := c.sessions[id]
	if state == nil {
		return nil, errors.New("QR session not found")
	}
	return state, nil
}

func (c *Client) Cancel(sessionID string) {
	c.mu.Lock()
	state := c.sessions[sessionID]
	delete(c.sessions, sessionID)
	c.mu.Unlock()
	if state != nil {
		state.cancel()
	}
}

func (c *Client) Close() error {
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		return nil
	}
	c.closed = true
	sessions := c.sessions
	c.sessions = map[string]*sessionState{}
	c.mu.Unlock()
	for _, state := range sessions {
		state.cancel()
	}
	return c.db.Close()
}
