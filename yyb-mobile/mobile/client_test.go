package mobile

import "testing"

func TestClientRejectsUnknownSession(t *testing.T) {
	client, err := NewClient()
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	if _, err = client.Poll("missing"); err == nil {
		t.Fatal("expected an unknown-session error")
	}
	if _, err = client.ConfirmAndGetCode("missing", "wx4e8cbe4fb0eca54c"); err == nil {
		t.Fatal("expected an unknown-session error")
	}
}

func TestClientCancelAndCloseAreIdempotent(t *testing.T) {
	client, err := NewClient()
	if err != nil {
		t.Fatal(err)
	}
	client.Cancel("missing")
	client.Cancel("missing")
	if err = client.Close(); err != nil {
		t.Fatal(err)
	}
	if err = client.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err = client.CreateQR(); err == nil {
		t.Fatal("closed client accepted a new QR session")
	}
}
