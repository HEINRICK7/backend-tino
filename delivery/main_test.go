package main

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestSendOTPRejectsMissingInternalToken(t *testing.T) {
	cfg := &config{internalToken: "internal", client: http.DefaultClient}
	request := httptest.NewRequest(http.MethodPost, "/internal/v1/messages/otp", strings.NewReader(
		`{"destination":"+5586995922924","message":"Seu codigo TINO e 123456"}`))
	response := httptest.NewRecorder()

	cfg.sendOTP(response, request)

	if response.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
	}
}

func TestSendOTPForwardsNormalizedMessageToProvider(t *testing.T) {
	var receivedPath string
	var receivedKey string
	var receivedBody string
	provider := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		receivedPath = request.URL.Path
		receivedKey = request.Header.Get("apikey")
		body, _ := io.ReadAll(request.Body)
		receivedBody = string(body)
		writer.WriteHeader(http.StatusOK)
	}))
	defer provider.Close()

	cfg := &config{
		internalToken: "internal",
		providerURL:   provider.URL,
		providerKey:   "provider-key",
		instance:      "tino-pilot",
		sendPath:      "/message/sendText/{instance}",
		client:        provider.Client(),
	}
	request := httptest.NewRequest(http.MethodPost, "/internal/v1/messages/otp", strings.NewReader(
		`{"destination":"+5586995922924","message":"Seu codigo TINO e 123456"}`))
	request.Header.Set("X-Tino-Internal-Token", "internal")
	response := httptest.NewRecorder()

	cfg.sendOTP(response, request)

	if response.Code != http.StatusAccepted {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusAccepted)
	}
	if receivedPath != "/message/sendText/tino-pilot" {
		t.Fatalf("path = %q", receivedPath)
	}
	if receivedKey != "provider-key" {
		t.Fatalf("provider key = %q", receivedKey)
	}
	if !strings.Contains(receivedBody, `"number":"5586995922924"`) ||
		!strings.Contains(receivedBody, `"text":"Seu codigo TINO e 123456"`) {
		t.Fatalf("provider body = %q", receivedBody)
	}
}

func TestSendOTPMapsProviderFailureToRetryableResponse(t *testing.T) {
	provider := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusBadGateway)
	}))
	defer provider.Close()

	cfg := &config{
		internalToken: "internal",
		providerURL:   provider.URL,
		providerKey:   "provider-key",
		instance:      "tino-pilot",
		sendPath:      "/message/sendText/{instance}",
		client:        provider.Client(),
	}
	request := httptest.NewRequest(http.MethodPost, "/internal/v1/messages/otp", strings.NewReader(
		`{"destination":"+5586995922924","message":"Seu codigo TINO e 123456"}`))
	request.Header.Set("X-Tino-Internal-Token", "internal")
	response := httptest.NewRecorder()

	cfg.sendOTP(response, request)

	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusServiceUnavailable)
	}
}

func TestHealthReportsProviderConfiguration(t *testing.T) {
	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/readyz", nil)

	health(&config{})(response, request)

	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusServiceUnavailable)
	}
}
