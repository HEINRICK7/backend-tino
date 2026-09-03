package main

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"
)

var brazilianPhone = regexp.MustCompile(`^\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})$`)

type config struct {
	internalToken string
	backendURL    string
	backendToken  string
	webhookSecret string
	webhookToken  string
	masterPhone   string
	providerURL   string
	providerKey   string
	instance      string
	sendPath      string
	client        *http.Client
}

type otpMessage struct {
	Recipient      string `json:"recipient"`
	Template       string `json:"template"`
	Code           string `json:"code"`
	ExpiresMinutes int    `json:"expires_minutes"`
	CorrelationID  string `json:"correlation_id"`
}

type confirmationEvent struct {
	CorrelationID     string `json:"correlation_id"`
	EventType         string `json:"event_type"`
	ProviderEventID   string `json:"provider_event_id"`
	ProviderMessageID string `json:"provider_message_id"`
	SenderPhone       string `json:"sender_phone"`
	OccurredAt        string `json:"occurred_at"`
}

type deliveryEvent struct {
	ProviderEventID   string `json:"provider_event_id"`
	ProviderMessageID string `json:"provider_message_id"`
	EventType         string `json:"event_type"`
	RecipientPhone    string `json:"recipient_phone"`
	OccurredAt        string `json:"occurred_at"`
}

type providerMessage struct {
	Number string `json:"number"`
	Text   string `json:"text"`
}

type providerButtonMessage struct {
	Number      string           `json:"number"`
	Title       string           `json:"title"`
	Description string           `json:"description"`
	Footer      string           `json:"footer"`
	Buttons     []providerButton `json:"buttons"`
}

type providerButton struct {
	Type        string `json:"type"`
	Title       string `json:"title"`
	DisplayText string `json:"displayText"`
	ID          string `json:"id"`
}

type evolutionWebhook struct {
	Event string           `json:"event"`
	Data  evolutionMessage `json:"data"`
}

type evolutionMessage struct {
	Key              evolutionMessageKey `json:"key"`
	Message          json.RawMessage     `json:"message"`
	MessageTimestamp json.RawMessage     `json:"messageTimestamp"`
	MessageType      string              `json:"messageType"`
	Update           struct {
		Status json.RawMessage `json:"status"`
	} `json:"update"`
}

type evolutionMessageKey struct {
	RemoteJID string `json:"remoteJid"`
	FromMe    bool   `json:"fromMe"`
	ID        string `json:"id"`
}

type evolutionInboundContent struct {
	Conversation        string `json:"conversation"`
	ExtendedTextMessage struct {
		Text string `json:"text"`
	} `json:"extendedTextMessage"`
	ButtonsResponseMessage struct {
		SelectedButtonID string `json:"selectedButtonId"`
	} `json:"buttonsResponseMessage"`
	TemplateButtonReplyMessage struct {
		SelectedID string `json:"selectedId"`
	} `json:"templateButtonReplyMessage"`
	ListResponseMessage struct {
		SingleSelectReply struct {
			SelectedRowID string `json:"selectedRowId"`
		} `json:"singleSelectReply"`
	} `json:"listResponseMessage"`
}

type result struct {
	Status            string `json:"status"`
	Provider          string `json:"provider"`
	ProviderMessageID string `json:"provider_message_id,omitempty"`
}

func main() {
	cfg := loadConfig()
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", health(cfg))
	mux.HandleFunc("/readyz", health(cfg))
	mux.HandleFunc("/internal/v1/messages/otp", cfg.sendOTP)
	mux.HandleFunc("/webhooks/whatsapp", cfg.receiveWebhook)

	server := &http.Server{
		Addr:              ":8080",
		Handler:           mux,
		ReadHeaderTimeout: 2 * time.Second,
		ReadTimeout:       5 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       30 * time.Second,
	}
	log.Printf("TINO delivery service started; provider_configured=%t", cfg.ready())
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal("delivery service stopped")
	}
}

func loadConfig() *config {
	return &config{
		internalToken: os.Getenv("TINO_INTERNAL_TOKEN"),
		backendURL:    valueOr(os.Getenv("TINO_OTP_BACKEND_URL"), "http://app:8080"),
		backendToken:  valueOr(os.Getenv("TINO_OTP_BACKEND_TOKEN"), os.Getenv("TINO_OTP_INTERNAL_TOKEN")),
		webhookSecret: os.Getenv("WA_EVOLUTION_WEBHOOK_SECRET"),
		webhookToken:  os.Getenv("WA_EVOLUTION_WEBHOOK_TOKEN"),
		masterPhone:   normalizeWhatsAppPhone(valueOr(os.Getenv("WA_EVOLUTION_MASTER_PHONE"), "+5586994209350")),
		providerURL:   strings.TrimRight(os.Getenv("WA_EVOLUTION_BASE_URL"), "/"),
		providerKey:   os.Getenv("WA_EVOLUTION_API_KEY"),
		instance:      os.Getenv("WA_EVOLUTION_INSTANCE"),
		sendPath:      valueOr(os.Getenv("WA_EVOLUTION_SEND_PATH"), "/message/sendButtons/{instance}"),
		client:        &http.Client{Timeout: 5 * time.Second},
	}
}

func valueOr(value, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func (c *config) ready() bool {
	return c.internalToken != "" && c.providerURL != "" && c.providerKey != "" && c.instance != ""
}

func (c *config) webhookReady() bool {
	return c.backendURL != "" && c.backendToken != "" && (c.webhookSecret != "" || c.webhookToken != "")
}

func health(c *config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		if !c.ready() || !c.webhookReady() {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusOK)
	}
}

func (c *config) sendOTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	if !c.authorized(r.Header.Get("X-Tino-Internal-Token")) {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	if !c.ready() {
		writeResult(w, http.StatusServiceUnavailable, result{Status: "RETRYABLE_FAILURE", Provider: "WA_EVOLUTION"})
		return
	}

	var message otpMessage
	decoder := json.NewDecoder(io.LimitReader(r.Body, 4096))
	if err := decoder.Decode(&message); err != nil || !validMessage(message, c.masterPhone) {
		w.WriteHeader(http.StatusBadRequest)
		return
	}

	status, providerMessageID := c.sendToProvider(r.Context(), message)
	switch status {
	case "ACCEPTED":
		writeResult(w, http.StatusAccepted, result{Status: status, Provider: "WA_EVOLUTION", ProviderMessageID: providerMessageID})
	case "RETRYABLE_FAILURE":
		writeResult(w, http.StatusServiceUnavailable, result{Status: status, Provider: "WA_EVOLUTION"})
	default:
		writeResult(w, http.StatusBadGateway, result{Status: "PERMANENT_FAILURE", Provider: "WA_EVOLUTION"})
	}
}

func (c *config) authorized(supplied string) bool {
	return supplied != "" && subtle.ConstantTimeCompare([]byte(c.internalToken), []byte(supplied)) == 1
}

func validMessage(message otpMessage, masterPhone string) bool {
	return brazilianPhone.MatchString(message.Recipient) && message.Recipient != masterPhone &&
		message.Template == "AUTH_OTP" && len(message.Code) == 6 && codePattern.MatchString(message.Code) &&
		message.ExpiresMinutes > 0 && message.ExpiresMinutes <= 15 &&
		safeCorrelationID(message.CorrelationID)
}

var codePattern = regexp.MustCompile(`^[0-9]{6}$`)

func safeCorrelationID(value string) bool {
	if value == "" || len(value) > 100 {
		return false
	}
	for _, character := range value {
		if (character < 'a' || character > 'z') && (character < 'A' || character > 'Z') &&
			(character < '0' || character > '9') && character != '-' && character != '_' && character != '.' {
			return false
		}
	}
	return true
}

func (c *config) receiveWebhook(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, 64*1024))
	if err != nil || !c.validWebhookAuthentication(r, body) {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	var event confirmationEvent
	var delivery deliveryEvent
	callbackPath := "/internal/v1/identity/otp/events"
	if normalized, ok := normalizeConfirmationEvent(body); ok {
		event = normalized
	} else if normalized, ok := normalizeDeliveryEvent(body); ok {
		delivery = normalized
		callbackPath = "/internal/v1/identity/otp/delivery-events"
	} else if err := json.Unmarshal(body, &event); err != nil || !validConfirmationEvent(event) {
		if err := json.Unmarshal(body, &delivery); err != nil || !validDeliveryEvent(delivery) {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		callbackPath = "/internal/v1/identity/otp/delivery-events"
	}
	if c.backendToken == "" || c.backendURL == "" {
		w.WriteHeader(http.StatusServiceUnavailable)
		return
	}
	var callback any = event
	if callbackPath != "/internal/v1/identity/otp/events" {
		callback = delivery
	}
	body, err = json.Marshal(callback)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		return
	}
	request, err := http.NewRequestWithContext(r.Context(), http.MethodPost,
		strings.TrimRight(c.backendURL, "/")+callbackPath, strings.NewReader(string(body)))
	if err != nil {
		w.WriteHeader(http.StatusBadGateway)
		return
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json")
	request.Header.Set("X-Tino-Internal-Token", c.backendToken)
	response, err := c.client.Do(request)
	if err != nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		return
	}
	defer response.Body.Close()
	_, _ = io.Copy(io.Discard, response.Body)
	if response.StatusCode >= 200 && response.StatusCode < 300 {
		writeResult(w, http.StatusOK, result{Status: "ACCEPTED", Provider: "TINO_BACKEND"})
		return
	}
	if response.StatusCode == 408 || response.StatusCode == 429 || response.StatusCode >= 500 {
		w.WriteHeader(http.StatusServiceUnavailable)
		return
	}
	w.WriteHeader(http.StatusBadGateway)
}

func validConfirmationEvent(event confirmationEvent) bool {
	_, timestampError := time.Parse(time.RFC3339, event.OccurredAt)
	return safeCorrelationID(event.CorrelationID) && event.EventType == "AUTH_CONFIRMED" &&
		event.ProviderEventID != "" && len(event.ProviderEventID) <= 200 &&
		event.ProviderMessageID != "" && len(event.ProviderMessageID) <= 200 &&
		brazilianPhone.MatchString(event.SenderPhone) && timestampError == nil
}

func validDeliveryEvent(event deliveryEvent) bool {
	_, timestampError := time.Parse(time.RFC3339, event.OccurredAt)
	return event.ProviderEventID != "" && len(event.ProviderEventID) <= 200 &&
		event.ProviderMessageID != "" && len(event.ProviderMessageID) <= 200 &&
		(event.EventType == "AUTH_DELIVERED" || event.EventType == "AUTH_DELIVERY_FAILED") &&
		brazilianPhone.MatchString(event.RecipientPhone) && timestampError == nil
}

func (c *config) validWebhookSignature(signature string, payload []byte) bool {
	if signature == "" {
		return false
	}
	mac := hmac.New(sha256.New, []byte(c.webhookSecret))
	_, _ = mac.Write(payload)
	expected := hex.EncodeToString(mac.Sum(nil))
	signature = strings.TrimPrefix(signature, "sha256=")
	return subtle.ConstantTimeCompare([]byte(expected), []byte(signature)) == 1
}

func (c *config) validWebhookAuthentication(r *http.Request, payload []byte) bool {
	if c.webhookSecret != "" && c.validWebhookSignature(r.Header.Get("X-Tino-Webhook-Signature"), payload) {
		return true
	}
	provided := r.Header.Get("X-Tino-Webhook-Token")
	return c.webhookToken != "" && provided != "" &&
		subtle.ConstantTimeCompare([]byte(c.webhookToken), []byte(provided)) == 1
}

func normalizeConfirmationEvent(payload []byte) (confirmationEvent, bool) {
	var direct confirmationEvent
	if json.Unmarshal(payload, &direct) == nil && validConfirmationEvent(direct) {
		return direct, true
	}

	var webhook evolutionWebhook
	if json.Unmarshal(payload, &webhook) != nil || webhook.Data.Key.FromMe ||
		(webhook.Event != "" && !strings.EqualFold(webhook.Event, "MESSAGES_UPSERT") &&
			!strings.EqualFold(webhook.Event, "messages.upsert")) {
		return confirmationEvent{}, false
	}
	var content evolutionInboundContent
	if len(webhook.Data.Message) == 0 || json.Unmarshal(webhook.Data.Message, &content) != nil {
		return confirmationEvent{}, false
	}
	correlationID := firstNonBlank(
		content.ButtonsResponseMessage.SelectedButtonID,
		content.TemplateButtonReplyMessage.SelectedID,
		content.ListResponseMessage.SingleSelectReply.SelectedRowID,
	)
	const prefix = "TINO_AUTH_CONFIRM:"
	if !strings.HasPrefix(correlationID, prefix) {
		return confirmationEvent{}, false
	}
	correlationID = strings.TrimPrefix(correlationID, prefix)
	sender := normalizeWhatsAppPhone(webhook.Data.Key.RemoteJID)
	event := confirmationEvent{
		CorrelationID:     correlationID,
		EventType:         "AUTH_CONFIRMED",
		ProviderEventID:   webhook.Data.Key.ID,
		ProviderMessageID: webhook.Data.Key.ID,
		SenderPhone:       sender,
		OccurredAt:        evolutionOccurredAt(webhook.Data.MessageTimestamp),
	}
	return event, validConfirmationEvent(event)
}

func normalizeDeliveryEvent(payload []byte) (deliveryEvent, bool) {
	var direct deliveryEvent
	if json.Unmarshal(payload, &direct) == nil && validDeliveryEvent(direct) {
		return direct, true
	}
	var webhook evolutionWebhook
	if json.Unmarshal(payload, &webhook) != nil || !webhook.Data.Key.FromMe ||
		(webhook.Event != "" && !strings.EqualFold(webhook.Event, "MESSAGES_UPDATE") &&
			!strings.EqualFold(webhook.Event, "messages.update")) {
		return deliveryEvent{}, false
	}
	status := evolutionDeliveryStatus(webhook.Data.Update.Status)
	if webhook.Data.Key.ID == "" || status == 0 {
		return deliveryEvent{}, false
	}
	eventType := "AUTH_DELIVERY_FAILED"
	if status >= 4 {
		eventType = "AUTH_DELIVERED"
	}
	digest := sha256.Sum256(payload)
	event := deliveryEvent{
		ProviderEventID:   hex.EncodeToString(digest[:]),
		ProviderMessageID: webhook.Data.Key.ID,
		EventType:         eventType,
		RecipientPhone:    normalizeWhatsAppPhone(webhook.Data.Key.RemoteJID),
		OccurredAt:        evolutionOccurredAt(webhook.Data.MessageTimestamp),
	}
	return event, validDeliveryEvent(event)
}

func evolutionDeliveryStatus(raw json.RawMessage) int {
	var numeric int
	if json.Unmarshal(raw, &numeric) == nil {
		return numeric
	}
	var value string
	if json.Unmarshal(raw, &value) == nil {
		switch strings.ToUpper(value) {
		case "ERROR":
			return 1
		case "PENDING":
			return 2
		case "SERVER_ACK":
			return 3
		case "DELIVERY_ACK", "READ", "PLAYED":
			return 4
		}
	}
	return 0
}

func firstNonBlank(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func normalizeWhatsAppPhone(value string) string {
	value = strings.TrimSpace(strings.SplitN(value, "@", 2)[0])
	if colon := strings.IndexByte(value, ':'); colon >= 0 {
		value = value[:colon]
	}
	value = strings.TrimPrefix(value, "+")
	if strings.HasPrefix(value, "55") {
		return "+" + value
	}
	return ""
}

func evolutionOccurredAt(raw json.RawMessage) string {
	var numeric int64
	if json.Unmarshal(raw, &numeric) == nil && numeric > 0 {
		return time.Unix(numeric, 0).UTC().Format(time.RFC3339)
	}
	var textValue string
	if json.Unmarshal(raw, &textValue) == nil {
		if parsed, err := time.Parse(time.RFC3339, textValue); err == nil {
			return parsed.UTC().Format(time.RFC3339)
		}
		if seconds, err := strconv.ParseInt(textValue, 10, 64); err == nil && seconds > 0 {
			return time.Unix(seconds, 0).UTC().Format(time.RFC3339)
		}
	}
	return ""
}

func (c *config) sendToProvider(ctx context.Context, message otpMessage) (string, string) {
	path := strings.ReplaceAll(c.sendPath, "{instance}", c.instance)
	text := "Seu código TINO é " + message.Code + ". Expira em " + strconv.Itoa(message.ExpiresMinutes) + " min."
	var requestBody any = providerMessage{Number: strings.TrimPrefix(message.Recipient, "+"), Text: text}
	if strings.Contains(strings.ToLower(c.sendPath), "sendbuttons") {
		requestBody = providerButtonMessage{
			Number:      strings.TrimPrefix(message.Recipient, "+"),
			Title:       "Confirme seu acesso ao TINO",
			Description: text,
			Footer:      "O código continua disponível como fallback.",
			Buttons: []providerButton{{
				Type:        "reply",
				Title:       "Confirmar acesso",
				DisplayText: "Confirmar acesso",
				ID:          "TINO_AUTH_CONFIRM:" + message.CorrelationID,
			}},
		}
	}
	body, err := json.Marshal(requestBody)
	if err != nil {
		return "PERMANENT_FAILURE", ""
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.providerURL+path, strings.NewReader(string(body)))
	if err != nil {
		return "PERMANENT_FAILURE", ""
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json")
	request.Header.Set("apikey", c.providerKey)
	response, err := c.client.Do(request)
	if err != nil {
		return "RETRYABLE_FAILURE", ""
	}
	defer response.Body.Close()
	responseBody, _ := io.ReadAll(io.LimitReader(response.Body, 16*1024))
	if response.StatusCode >= 200 && response.StatusCode < 300 {
		var sent struct {
			Key struct {
				ID string `json:"id"`
			} `json:"key"`
		}
		if json.Unmarshal(responseBody, &sent) != nil || sent.Key.ID == "" {
			return "RETRYABLE_FAILURE", ""
		}
		return "ACCEPTED", sent.Key.ID
	}
	if response.StatusCode == 408 || response.StatusCode == 429 || response.StatusCode >= 500 {
		return "RETRYABLE_FAILURE", ""
	}
	return "PERMANENT_FAILURE", ""
}

func writeResult(w http.ResponseWriter, status int, value result) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
