package protocol

import (
	"context"
	"testing"
	"time"
)

func TestGetLonglinkTargetsFallsBackToOfficialHostname(t *testing.T) {
	dnsCache.Lock()
	dnsCache.entries = map[string]dnsCacheEntry{
		"0|Windows": {
			ExpiresAt: time.Now().Add(time.Minute),
			Parsed:    map[string]dnsDomain{},
		},
	}
	dnsCache.Unlock()
	t.Cleanup(clearDNSCache)

	targets, err := getLonglinkTargets(context.Background(), time.Second, time.Minute)
	if err != nil {
		t.Fatalf("getLonglinkTargets() error = %v", err)
	}
	if len(targets) != 1 || targets[0].IP != longlinkDomain || targets[0].Port != 443 {
		t.Fatalf("getLonglinkTargets() = %#v, want %s:443", targets, longlinkDomain)
	}
}

func TestGetLonglinkTargetsUsesHTTPDNSCandidates(t *testing.T) {
	dnsCache.Lock()
	dnsCache.entries = map[string]dnsCacheEntry{
		"0|Windows": {
			ExpiresAt: time.Now().Add(time.Minute),
			Parsed: map[string]dnsDomain{
				longlinkDomain: {
					IPs:       []string{"203.0.113.10"},
					Protocols: map[string][]int{protoMMTLS: []int{8080}},
				},
			},
		},
	}
	dnsCache.Unlock()
	t.Cleanup(clearDNSCache)

	targets, err := getLonglinkTargets(context.Background(), time.Second, time.Minute)
	if err != nil {
		t.Fatalf("getLonglinkTargets() error = %v", err)
	}
	if len(targets) != 1 || targets[0].IP != "203.0.113.10" || targets[0].Port != 8080 {
		t.Fatalf("getLonglinkTargets() = %#v", targets)
	}
}

func clearDNSCache() {
	dnsCache.Lock()
	dnsCache.entries = map[string]dnsCacheEntry{}
	dnsCache.Unlock()
}
