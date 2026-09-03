package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestSendOTPRejectsMissingInternalToken(t *testing.T) {
	cfg := &config{internalToken: "internal", client: http.DefaultClient}
	request := httptest.NewRequest(http.MethodPost, "/internal/v1/messages/otp", strings.NewReader(
		`{"recipient":"+5586995922924","template":"AUTH_OTP","code":"123456","expires_minutes":5,"correlation_id":"challenge-1"}`))
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
		_, _ = writer.Write([]byte(`{"key":{"id":"provider-message-1"}}`))
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
		`{"recipient":"+5586995922924","template":"AUTH_OTP","code":"123456","expires_minutes":5,"correlation_id":"challenge-1"}`))
	request.Body = io.NopCloser(strings.NewReader(
		`{"recipient":"+5586995922924","template":"AUTH_OTP","code":"123456","expires_minutes":5,"correlation_id":"challenge-1"}`))
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
		!strings.Contains(receivedBody, `"text":"Seu código TINO é 123456. Expira em 5 min."`) {
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
		`{"recipient":"+5586995922924","template":"AUTH_OTP","code":"123456","expires_minutes":5,"correlation_id":"challenge-1"}`))
	request.Body = io.NopCloser(strings.NewReader(
		`{"recipient":"+5586995922924","template":"AUTH_OTP","code":"123456","expires_minutes":5,"correlation_id":"challenge-1"}`))
	request.Header.Set("X-Tino-Internal-Token", "internal")
	response := httptest.NewRecorder()

	cfg.sendOTP(response, request)

	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusServiceUnavailable)
	}
}

func TestSendOTPUsesCorrelationBoundButtonWhenConfigured(t *testing.T) {
	var receivedBody string
	provider := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(request.Body)
		receivedBody = string(body)
		_, _ = writer.Write([]byte(`{"key":{"id":"provider-message-2"}}`))
	}))
	defer provider.Close()

	cfg := &config{
		internalToken: "internal",
		providerURL:   provider.URL,
		providerKey:   "provider-key",
		instance:      "tino",
		sendPath:      "/message/sendButtons/{instance}",
		client:        provider.Client(),
	}
	request := httptest.NewRequest(http.MethodPost, "/internal/v1/messages/otp", strings.NewReader(
		`{"recipient":"+5586995922924","template":"AUTH_OTP","code":"123456","expires_minutes":5,"correlation_id":"challenge-1"}`))
	request.Header.Set("X-Tino-Internal-Token", "internal")
	response := httptest.NewRecorder()

	cfg.sendOTP(response, request)

	if response.Code != http.StatusAccepted {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusAccepted)
	}
	if !strings.Contains(receivedBody, `"type":"reply"`) ||
		!strings.Contains(receivedBody, `"id":"TINO_AUTH_CONFIRM:challenge-1"`) ||
		!strings.Contains(receivedBody, `"number":"5586995922924"`) {
		t.Fatalf("provider button body = %q", receivedBody)
	}
}

func TestWebhookForwardsSignedConfirmationToBackend(t *testing.T) {
	var receivedBody string
	backend := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Header.Get("X-Tino-Internal-Token") != "backend-token" {
			t.Fatalf("backend token was not forwarded")
		}
		body, _ := io.ReadAll(request.Body)
		receivedBody = string(body)
		writer.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	cfg := &config{backendURL: backend.URL, backendToken: "backend-token", webhookSecret: "webhook-secret", client: backend.Client()}
	payload := `{"correlation_id":"challenge-1","event_type":"AUTH_CONFIRMED","provider_event_id":"event-1","provider_message_id":"message-1","sender_phone":"+5586995922924","occurred_at":"2026-09-03T12:00:00Z"}`
	mac := hmac.New(sha256.New, []byte(cfg.webhookSecret))
	_, _ = mac.Write([]byte(payload))
	request := httptest.NewRequest(http.MethodPost, "/webhooks/whatsapp", strings.NewReader(payload))
	request.Header.Set("X-Tino-Webhook-Signature", hex.EncodeToString(mac.Sum(nil)))
	response := httptest.NewRecorder()

	cfg.receiveWebhook(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if !strings.Contains(receivedBody, `"event_type":"AUTH_CONFIRMED"`) {
		t.Fatalf("backend body = %q", receivedBody)
	}
}

func TestWebhookRejectsUnsignedConfirmation(t *testing.T) {
	cfg := &config{webhookSecret: "webhook-secret", client: http.DefaultClient}
	request := httptest.NewRequest(http.MethodPost, "/webhooks/whatsapp", strings.NewReader(`{}`))
	response := httptest.NewRecorder()

	cfg.receiveWebhook(response, request)

	if response.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
	}
}

func TestWebhookNormalizesEvolutionButtonConfirmation(t *testing.T) {
	var receivedBody string
	backend := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(request.Body)
		receivedBody = string(body)
		writer.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	cfg := &config{backendURL: backend.URL, backendToken: "backend-token", webhookSecret: "webhook-secret", client: backend.Client()}
	payload := `{"event":"MESSAGES_UPSERT","instance":"tino","data":{"key":{"remoteJid":"5586995922924@s.whatsapp.net","fromMe":false,"id":"reply-1"},"message":{"buttonsResponseMessage":{"selectedButtonId":"TINO_AUTH_CONFIRM:challenge-1"}},"messageTimestamp":"1725364800","messageType":"buttonsResponseMessage"}}`
	mac := hmac.New(sha256.New, []byte(cfg.webhookSecret))
	_, _ = mac.Write([]byte(payload))
	request := httptest.NewRequest(http.MethodPost, "/webhooks/whatsapp", strings.NewReader(payload))
	request.Header.Set("X-Tino-Webhook-Signature", hex.EncodeToString(mac.Sum(nil)))
	response := httptest.NewRecorder()

	cfg.receiveWebhook(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	for _, expected := range []string{`"correlation_id":"challenge-1"`, `"event_type":"AUTH_CONFIRMED"`, `"provider_event_id":"reply-1"`, `"sender_phone":"+5586995922924"`} {
		if !strings.Contains(receivedBody, expected) {
			t.Fatalf("normalized body = %q, missing %s", receivedBody, expected)
		}
	}
}

func TestWebhookNormalizesEvolutionDeliveryReceipt(t *testing.T) {
	var receivedBody string
	backend := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/internal/v1/identity/otp/delivery-events" {
			t.Fatalf("callback path = %q", request.URL.Path)
		}
		body, _ := io.ReadAll(request.Body)
		receivedBody = string(body)
		writer.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	cfg := &config{backendURL: backend.URL, backendToken: "backend-token", webhookSecret: "webhook-secret", client: backend.Client()}
	payload := `{"event":"MESSAGES_UPDATE","data":{"key":{"remoteJid":"5586995922924@s.whatsapp.net","fromMe":true,"id":"sent-1"},"update":{"status":4},"messageTimestamp":1725364800}}`
	mac := hmac.New(sha256.New, []byte(cfg.webhookSecret))
	_, _ = mac.Write([]byte(payload))
	request := httptest.NewRequest(http.MethodPost, "/webhooks/whatsapp", strings.NewReader(payload))
	request.Header.Set("X-Tino-Webhook-Signature", hex.EncodeToString(mac.Sum(nil)))
	response := httptest.NewRecorder()

	cfg.receiveWebhook(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	for _, expected := range []string{`"event_type":"AUTH_DELIVERED"`, `"provider_message_id":"sent-1"`, `"recipient_phone":"+5586995922924"`} {
		if !strings.Contains(receivedBody, expected) {
			t.Fatalf("normalized delivery body = %q, missing %s", receivedBody, expected)
		}
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
