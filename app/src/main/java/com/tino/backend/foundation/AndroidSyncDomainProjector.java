package com.tino.backend.foundation;

import com.tino.backend.credit.application.exception.CreditConflictException;
import com.tino.backend.credit.application.exception.CreditCustomerNotFoundException;
import com.tino.backend.credit.application.exception.CreditInsufficientBalanceException;
import com.tino.backend.credit.application.usecase.AppendCreditEntry;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.application.exception.SyncEventRejectedException;
import com.tino.backend.sync.application.port.in.SyncEventProjector;
import com.tino.backend.sync.domain.model.SyncEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies the Android outbox to backend-owned customer and credit projections.
 * Event claiming remains the replay boundary; this adapter only runs for a
 * newly claimed event and participates in the same tenant transaction.
 */
@Component
public final class AndroidSyncDomainProjector implements SyncEventProjector {
    private static final String CUSTOMER_CREATED = "customer.created";
    private static final String CUSTOMER_UPDATED = "customer.updated";

    private final AppendCreditEntry appendCreditEntry;
    private final CustomerRepository customers;
    private final ObjectMapper mapper;

    public AndroidSyncDomainProjector(
            AppendCreditEntry appendCreditEntry,
            CustomerRepository customers,
            ObjectMapper mapper) {
        this.appendCreditEntry = appendCreditEntry;
        this.customers = customers;
        this.mapper = mapper;
    }

    @Override
    public void project(UUID authenticatedUserId, BusinessId businessId, SyncEvent event) {
        if (!isSupported(event.eventType())) return;

        var payload = parsePayload(event);
        try {
            switch (event.eventType()) {
                case CUSTOMER_CREATED -> createCustomer(businessId, event, payload);
                case CUSTOMER_UPDATED -> updateCustomer(businessId, event, payload);
                case "credit.receivable.created", "credit.sale.created" -> appendCredit(
                        authenticatedUserId, businessId, event, payload, CreditDirection.CREDIT, false);
                case "credit.receivable.reversed" -> appendCredit(
                        authenticatedUserId, businessId, event, payload, null, true);
                case "credit.payment.received", "credit.settled" -> appendCredit(
                        authenticatedUserId, businessId, event, payload, CreditDirection.DEBIT, false);
                case "credit.sale.reversed", "credit.settled.reversed" -> appendCredit(
                        authenticatedUserId, businessId, event, payload,
                        event.eventType().equals("credit.sale.reversed")
                                ? CreditDirection.DEBIT : CreditDirection.CREDIT,
                        false);
                case "credit.payment.reversed" -> appendCredit(
                        authenticatedUserId, businessId, event, payload, CreditDirection.CREDIT, false);
                case "credit.adjustment.created", "credit.adjustment.reversed" -> appendCredit(
                        authenticatedUserId, businessId, event, payload, null, true);
                default -> { }
            }
        } catch (CreditCustomerNotFoundException exception) {
            throw rejected("DOMAIN_CUSTOMER_NOT_FOUND", false, "customer is not available for credit projection");
        } catch (CreditInsufficientBalanceException exception) {
            throw rejected("DOMAIN_CREDIT_INSUFFICIENT_BALANCE", false,
                    "credit projection would make the balance negative");
        } catch (CreditConflictException exception) {
            throw rejected("DOMAIN_CREDIT_CONFLICT", false, "credit projection conflicts with an existing operation");
        } catch (JacksonException | IllegalArgumentException exception) {
            throw rejected("DOMAIN_EVENT_MALFORMED", false, "event payload cannot be projected");
        }
    }

    private void createCustomer(BusinessId businessId, SyncEvent event, JsonNode payload) {
        var customerId = customerId(event, payload);
        if (customers.find(businessId, customerId).isPresent()) return;
        var name = requiredText(payload, "name");
        var phone = optionalText(payload, "phone");
        var occurredAt = event.occurredAt();
        customers.insert(new Customer(
                customerId,
                businessId,
                name,
                null,
                phone,
                CustomerStatus.ACTIVE,
                occurredAt,
                occurredAt));
    }

    private void updateCustomer(BusinessId businessId, SyncEvent event, JsonNode payload) {
        var customerId = customerId(event, payload);
        var current = customers.find(businessId, customerId)
                .orElseThrow(() -> rejected("DOMAIN_CUSTOMER_NOT_FOUND", false,
                        "customer is not available for update"));
        var updated = new Customer(
                current.id(),
                businessId,
                requiredText(payload, "name"),
                current.nickname(),
                optionalText(payload, "phone"),
                current.status(),
                current.createdAt(),
                event.occurredAt());
        customers.update(updated);
    }

    private void appendCredit(
            UUID authenticatedUserId,
            BusinessId businessId,
            SyncEvent event,
            JsonNode payload,
            CreditDirection fixedDirection,
            boolean signedAmount) {
        var amountCents = amountCents(payload);
        if (amountCents == 0 || amountCents == Long.MIN_VALUE) {
            throw rejected("DOMAIN_EVENT_MALFORMED", false, "credit amount must not be zero");
        }
        var direction = fixedDirection;
        if (signedAmount) direction = amountCents > 0 ? CreditDirection.CREDIT : CreditDirection.DEBIT;
        var absoluteCents = Math.abs(amountCents);
        appendCreditEntry.execute(
                authenticatedUserId,
                businessId,
                customerId(event, payload),
                direction,
                BigDecimal.valueOf(absoluteCents, 2),
                reason(event, payload),
                "SYNC_EVENT:" + event.eventId(),
                fingerprint(event));
    }

    private JsonNode parsePayload(SyncEvent event) {
        try {
            var payload = mapper.readTree(event.payloadJson());
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("payload must be an object");
            }
            return payload;
        } catch (JacksonException exception) {
            throw rejected("DOMAIN_EVENT_MALFORMED", false, "event payload cannot be parsed");
        }
    }

    private static boolean isSupported(String eventType) {
        return switch (eventType) {
            case CUSTOMER_CREATED, CUSTOMER_UPDATED,
                    "credit.receivable.created", "credit.sale.created",
                    "credit.receivable.reversed",
                    "credit.payment.received", "credit.settled",
                    "credit.sale.reversed", "credit.payment.reversed",
                    "credit.adjustment.created", "credit.adjustment.reversed",
                    "credit.settled.reversed" -> true;
            default -> false;
        };
    }

    private static UUID customerId(SyncEvent event, JsonNode payload) {
        var value = text(payload, "customer_id");
        if ((value == null || value.isBlank()) && event != null) value = event.aggregateId();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("customer_id is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("customer_id must be a UUID", exception);
        }
    }

    private static long amountCents(JsonNode payload) {
        var value = text(payload, "amount_cents");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("amount_cents is required");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("amount_cents must be an integer", exception);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        var value = optionalText(payload, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String optionalText(JsonNode payload, String field) {
        var value = text(payload, field);
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(JsonNode payload, String field) {
        var value = payload == null ? null : payload.get(field);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) return null;
        return value.isString() ? value.stringValue() : value.toString();
    }

    private static String reason(SyncEvent event, JsonNode payload) {
        var supplied = optionalText(payload, "reason");
        if (supplied != null && supplied.length() <= 64) return supplied;
        var normalized = event.eventType().replace('.', '_').toUpperCase(java.util.Locale.ROOT);
        return normalized.length() <= 59 ? "SYNC_" + normalized : "SYNC_CREDIT_EVENT";
    }

    private static String fingerprint(SyncEvent event) {
        try {
            var input = (event.eventType() + "\n" + event.schemaVersion() + "\n" + event.payloadJson())
                    .getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static SyncEventRejectedException rejected(String code, boolean retryable, String message) {
        return new SyncEventRejectedException(code, retryable, message);
    }
}
