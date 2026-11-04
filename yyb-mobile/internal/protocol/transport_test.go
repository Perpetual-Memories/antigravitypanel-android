package protocol

import (
	"bufio"
	"encoding/base64"
	"errors"
	"net"
	"strings"
	"testing"
)

func TestParseTCPProxyCredentials(t *testing.T) {
	proxy, err := parseTCPProxy("http-connect://user:pass@127.0.0.1:8080")
	if err != nil {
		t.Fatalf("parseTCPProxy() error = %v", err)
	}
	if proxy.Username != "user" || proxy.Password != "pass" {
		t.Fatalf("proxy credentials = %q/%q", proxy.Username, proxy.Password)
	}
}

func TestHTTPConnectSendsBasicAuthentication(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	done := make(chan error, 1)
	go func() {
		reader := bufio.NewReader(server)
		var request strings.Builder
		for {
			line, err := reader.ReadString('\n')
			if err != nil {
				done <- err
				return
			}
			request.WriteString(line)
			if line == "\r\n" {
				break
			}
		}
		want := "Proxy-Authorization: Basic " + base64.StdEncoding.EncodeToString([]byte("user:pass"))
		if !strings.Contains(request.String(), want) {
			done <- errors.New("missing proxy authorization")
			return
		}
		_, err := server.Write([]byte("HTTP/1.1 200 Connection established\r\n\r\n"))
		done <- err
	}()
	proxy := &tcpProxy{Username: "user", Password: "pass"}
	if err := httpConnect(client, proxy, "example.com", 443); err != nil {
		t.Fatalf("httpConnect() error = %v", err)
	}
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}
