package main

import (
    "context"
    "crypto/subtle"
    "encoding/json"
    "errors"
    "io"
    "log"
    "net/http"
    "os"
    "regexp"
    "strings"
    "time"
)

var brazilianPhone = regexp.MustCompile(`^\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})$`)

type config struct {
    internalToken string
    providerURL   string
    providerKey   string
    instance      string
    sendPath      string
    client        *http.Client
}

type otpMessage struct {
    Destination string `json:"destination"`
    Message     string `json:"message"`
}

type providerMessage struct {
    Number string `json:"number"`
    Text   string `json:"text"`
}

type result struct {
    Status   string `json:"status"`
    Provider string `json:"provider"`
}

func main() {
    cfg := loadConfig()
    mux := http.NewServeMux()
    mux.HandleFunc("/healthz", health(cfg))
    mux.HandleFunc("/readyz", health(cfg))
    mux.HandleFunc("/internal/v1/messages/otp", cfg.sendOTP)

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
        providerURL:   strings.TrimRight(os.Getenv("WA_EVOLUTION_BASE_URL"), "/"),
        providerKey:   os.Getenv("WA_EVOLUTION_API_KEY"),
        instance:      os.Getenv("WA_EVOLUTION_INSTANCE"),
        sendPath:      valueOr(os.Getenv("WA_EVOLUTION_SEND_PATH"), "/message/sendText/{instance}"),
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

func health(c *config) http.HandlerFunc {
    return func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodGet {
            w.WriteHeader(http.StatusMethodNotAllowed)
            return
        }
        if !c.ready() {
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
    if err := decoder.Decode(&message); err != nil || !validMessage(message) {
        w.WriteHeader(http.StatusBadRequest)
        return
    }

    status := c.sendToProvider(r.Context(), message)
    switch status {
    case "ACCEPTED":
        writeResult(w, http.StatusAccepted, result{Status: status, Provider: "WA_EVOLUTION"})
    case "RETRYABLE_FAILURE":
        writeResult(w, http.StatusServiceUnavailable, result{Status: status, Provider: "WA_EVOLUTION"})
    default:
        writeResult(w, http.StatusBadGateway, result{Status: "PERMANENT_FAILURE", Provider: "WA_EVOLUTION"})
    }
}

func (c *config) authorized(supplied string) bool {
    return supplied != "" && subtle.ConstantTimeCompare([]byte(c.internalToken), []byte(supplied)) == 1
}

func validMessage(message otpMessage) bool {
    return brazilianPhone.MatchString(message.Destination) && len(message.Message) >= 1 && len(message.Message) <= 200
}

func (c *config) sendToProvider(ctx context.Context, message otpMessage) string {
    path := strings.ReplaceAll(c.sendPath, "{instance}", c.instance)
    requestBody := providerMessage{Number: strings.TrimPrefix(message.Destination, "+"), Text: message.Message}
    body, err := json.Marshal(requestBody)
    if err != nil {
        return "PERMANENT_FAILURE"
    }
    request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.providerURL+path, strings.NewReader(string(body)))
    if err != nil {
        return "PERMANENT_FAILURE"
    }
    request.Header.Set("Content-Type", "application/json")
    request.Header.Set("Accept", "application/json")
    request.Header.Set("apikey", c.providerKey)
    response, err := c.client.Do(request)
    if err != nil {
        return "RETRYABLE_FAILURE"
    }
    defer response.Body.Close()
    _, _ = io.Copy(io.Discard, response.Body)
    if response.StatusCode >= 200 && response.StatusCode < 300 {
        return "ACCEPTED"
    }
    if response.StatusCode == 408 || response.StatusCode == 429 || response.StatusCode >= 500 {
        return "RETRYABLE_FAILURE"
    }
    return "PERMANENT_FAILURE"
}

func writeResult(w http.ResponseWriter, status int, value result) {
    w.Header().Set("Content-Type", "application/json")
    w.WriteHeader(status)
    _ = json.NewEncoder(w).Encode(value)
}
