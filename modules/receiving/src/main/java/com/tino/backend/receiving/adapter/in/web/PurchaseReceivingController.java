package com.tino.backend.receiving.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchasePreviewSnapshot;
import com.tino.backend.receiving.application.model.PurchaseDocumentMatch;
import com.tino.backend.receiving.application.usecase.CreatePurchaseDocumentPreview;
import com.tino.backend.receiving.application.usecase.ConfirmPurchaseDocument;
import com.tino.backend.receiving.application.usecase.GetPurchaseHistory;
import com.tino.backend.receiving.application.usecase.GetPurchaseInsights;
import com.tino.backend.receiving.application.port.out.PurchaseReceiptRepository;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.shared.kernel.BusinessId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Versioned canonical PurchaseDocument input for Receiving preview. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/receiving/purchase-documents")
public final class PurchaseReceivingController {
    private final AuthenticatedUserResolver users;
    private final CreatePurchaseDocumentPreview createPreview;
    private final ConfirmPurchaseDocument confirmPurchase;
    private final GetPurchaseHistory getHistory;
    private final GetPurchaseInsights getInsights;

    public PurchaseReceivingController(AuthenticatedUserResolver users,
            CreatePurchaseDocumentPreview createPreview, ConfirmPurchaseDocument confirmPurchase,
            GetPurchaseHistory getHistory, GetPurchaseInsights getInsights) {
        this.users = users;
        this.createPreview = createPreview;
        this.confirmPurchase = confirmPurchase;
        this.getHistory = getHistory;
        this.getInsights = getInsights;
    }

    @Operation(summary = "Create a non-operational Receiving preview from a canonical purchase document")
    @PostMapping("/preview")
    public PurchasePreviewResponse preview(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseDocumentRequest request) {
        if (principal == null) {
            throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                    "authentication required", false, 401);
        }
        var user = users.resolve(principal);
        var snapshot = createPreview.execute(user.userId(), new BusinessId(businessId),
                request.toDomain(), idempotencyKey);
        return PurchasePreviewResponse.from(snapshot);
    }

    @Operation(summary = "Confirm a PurchaseDocument preview exactly once")
    @PostMapping("/{previewId}/confirm")
    public PurchaseReceiptResponse confirm(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID previewId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseConfirmationRequest request) {
        if (principal == null) {
            throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                    "authentication required", false, 401);
        }
        var user = users.resolve(principal);
        try {
            var result = confirmPurchase.execute(user.userId(), new BusinessId(businessId), previewId,
                    request.previewVersion(), request.items().stream().map(PurchaseDecisionRequest::toDecision).toList(),
                    idempotencyKey);
            return PurchaseReceiptResponse.from(result);
        } catch (IllegalArgumentException exception) {
            throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                    "invalid purchase confirmation", false, 400);
        }
    }

    @Operation(summary = "List confirmed NFC-e purchase history for a calendar period")
    @GetMapping("/purchase-history")
    public PurchaseHistoryResponse history(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "MONTH") String period) {
        if (principal == null) throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                "authentication required", false, 401);
        var user = users.resolve(principal);
        var result = getHistory.list(user.userId(), new BusinessId(businessId), period);
        return PurchaseHistoryResponse.from(result);
    }

    @Operation(summary = "Read one confirmed NFC-e purchase with its reconstructed facts")
    @GetMapping("/purchase-history/{receiptId}")
    public PurchaseHistoryDetailResponse historyDetail(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID receiptId) {
        if (principal == null) throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                "authentication required", false, 401);
        var user = users.resolve(principal);
        return PurchaseHistoryDetailResponse.from(getHistory.detail(user.userId(), new BusinessId(businessId), receiptId));
    }

    @Operation(summary = "Produce evidence-backed purchase insights")
    @GetMapping("/purchase-history-insights")
    public PurchaseInsightsResponse insights(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "MONTH") String period) {
        if (principal == null) throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                "authentication required", false, 401);
        var user = users.resolve(principal);
        var result = getInsights.execute(user.userId(), new BusinessId(businessId), period);
        return new PurchaseInsightsResponse(result.period(), result.insights().stream()
                .map(value -> new PurchaseInsightResponse(value.type(), value.message(), value.evidenceIds())).toList());
    }

    public record PurchaseDocumentRequest(
            @NotBlank String source,
            @JsonProperty("document_type") @NotBlank String documentType,
            @JsonProperty("access_key") @NotBlank String accessKey,
            @JsonProperty("issued_at") OffsetDateTime issuedAt,
            IssuerRequest issuer,
            @NotEmpty List<@Valid ItemRequest> items,
            BigDecimal total) {
        public PurchaseDocumentRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }

        PurchaseDocument toDomain() {
            try {
                var parsedSource = PurchaseDocument.Source.valueOf(source);
                var parsedType = PurchaseDocument.DocumentType.valueOf(documentType);
                var parsedIssuer = issuer == null ? null : new PurchaseDocument.Issuer(issuer.name(), issuer.taxId());
                return new PurchaseDocument(parsedSource, parsedType, accessKey, issuedAt, parsedIssuer,
                        items.stream().map(ItemRequest::toDomain).toList(), total);
            } catch (IllegalArgumentException exception) {
                throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                        "unsupported PurchaseDocument source or document_type", false, 400);
            }
        }
    }

    public record IssuerRequest(String name, @JsonProperty("tax_id") String taxId) {}

    public record ItemRequest(
            @JsonProperty("line_number") int lineNumber,
            @JsonProperty("external_code") String externalCode,
            String gtin,
            @JsonProperty("description") String description,
            BigDecimal quantity,
            String unit,
            @JsonProperty("unit_price") BigDecimal unitPrice,
            @JsonProperty("total_price") BigDecimal totalPrice) {
        PurchaseDocument.Item toDomain() {
            return new PurchaseDocument.Item(lineNumber, externalCode, gtin, description,
                    quantity, unit, unitPrice, totalPrice);
        }
    }

    public record PurchasePreviewResponse(
            @JsonProperty("preview_id") UUID previewId,
            @JsonProperty("document_id") UUID documentId,
            String status,
            long version,
            String source,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("access_key") String accessKey,
            @JsonProperty("issued_at") OffsetDateTime issuedAt,
            IssuerResponse issuer,
            List<PreviewItemResponse> items,
            BigDecimal total,
            Summary summary,
            List<Object> actions) {
        static PurchasePreviewResponse from(PurchasePreviewSnapshot snapshot) {
            var document = snapshot.document();
            var matches = snapshot.matches().stream().collect(java.util.stream.Collectors.toMap(
                    PurchaseDocumentMatch::lineNumber, value -> value));
            return new PurchasePreviewResponse(snapshot.previewId(), snapshot.documentId(), snapshot.status(),
                    snapshot.version(), document.source().name(), document.documentType().name(), document.accessKey(),
                    document.issuedAt(), new IssuerResponse(document.issuer().name(), document.issuer().taxId()),
                    document.items().stream().map(item -> PreviewItemResponse.from(item, matches.get(item.lineNumber()))).toList(), document.total(),
                    new Summary(snapshot.totalItems(), snapshot.count(PurchaseDocumentMatch.Status.EXACT_MATCH)
                            + snapshot.count(PurchaseDocumentMatch.Status.HIGH_CONFIDENCE_MATCH),
                            snapshot.count(PurchaseDocumentMatch.Status.NEW_PRODUCT),
                            snapshot.count(PurchaseDocumentMatch.Status.REVIEW_REQUIRED), document.total()), List.of());
        }
    }

    public record IssuerResponse(String name, @JsonProperty("tax_id") String taxId) {}

    public record PurchaseConfirmationRequest(
            @JsonProperty("preview_version") long previewVersion,
            @NotEmpty List<@Valid PurchaseDecisionRequest> items) {
        public PurchaseConfirmationRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record PurchaseDecisionRequest(
            @JsonProperty("line_number") int lineNumber,
            String action,
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("conversion_factor") BigDecimal conversionFactor,
            @JsonProperty("base_unit") String baseUnit) {
        ConfirmPurchaseDocument.Decision toDecision() {
            if (action == null) throw new IllegalArgumentException("action is required");
            return new ConfirmPurchaseDocument.Decision(lineNumber,
                    ConfirmPurchaseDocument.Action.valueOf(action), productId, conversionFactor, baseUnit);
        }
    }

    public record PreviewItemResponse(
            @JsonProperty("line_number") int lineNumber,
            @JsonProperty("external_code") String externalCode,
            String gtin,
            String description,
            BigDecimal quantity,
            String unit,
            @JsonProperty("unit_price") BigDecimal unitPrice,
            @JsonProperty("total_price") BigDecimal totalPrice,
            @JsonProperty("match_status") String matchStatus,
            @JsonProperty("matched_product_id") UUID matchedProductId,
            @JsonProperty("candidate_name") String candidateName,
            @JsonProperty("base_unit") String baseUnit,
            @JsonProperty("match_confidence") BigDecimal matchConfidence,
            @JsonProperty("requires_user_action") boolean requiresUserAction) {
        static PreviewItemResponse from(PurchaseDocument.Item item, PurchaseDocumentMatch match) {
            return new PreviewItemResponse(item.lineNumber(), item.externalCode(), item.gtin(), item.rawDescription(),
                    item.quantity(), item.unit(), item.unitPrice(), item.totalPrice(), match.status().name(),
                    match.productId(), match.candidateName(), match.baseUnit(), match.confidence(),
                    match.requiresUserAction());
        }
    }

    public record Summary(
            int items,
            long matched,
            @JsonProperty("new_products") long newProducts,
            @JsonProperty("needs_review") long needsReview,
            @JsonProperty("purchase_total") BigDecimal purchaseTotal) {}

    public record PurchaseReceiptResponse(
            @JsonProperty("receipt_id") UUID receiptId,
            String status,
            @JsonProperty("item_count") int itemCount,
            List<PurchaseReceiptItemResponse> items) {
        static PurchaseReceiptResponse from(PurchaseReceiptRepository.PurchaseReceiptResult result) {
            return new PurchaseReceiptResponse(result.receiptId(), result.status(), result.itemCount(),
                    result.items().stream().map(item -> new PurchaseReceiptItemResponse(item.lineNumber(),
                            item.productId(), item.matchStatus(), item.stockQuantity(), item.unitCost())).toList());
        }
    }

    public record PurchaseReceiptItemResponse(
            @JsonProperty("line_number") int lineNumber,
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("match_status") String matchStatus,
            @JsonProperty("stock_quantity") BigDecimal stockQuantity,
            @JsonProperty("unit_cost") BigDecimal unitCost) {}

    public record PurchaseHistoryResponse(
            String period, OffsetDateTime from, OffsetDateTime to, List<PurchaseHistoryEntryResponse> purchases,
            @JsonProperty("purchase_count") int purchaseCount, @JsonProperty("item_count") int itemCount,
            @JsonProperty("new_product_count") int newProductCount, BigDecimal total) {
        static PurchaseHistoryResponse from(GetPurchaseHistory.HistoryResult result) {
            return new PurchaseHistoryResponse(result.period(), result.from().atOffset(java.time.ZoneOffset.UTC),
                    result.to().atOffset(java.time.ZoneOffset.UTC), result.entries().stream()
                            .map(PurchaseHistoryEntryResponse::from).toList(), result.entries().size(),
                    result.itemCount(), result.newProductCount(), result.total());
        }
    }

    public record PurchaseHistoryEntryResponse(
            @JsonProperty("receipt_id") UUID receiptId, @JsonProperty("confirmed_at") OffsetDateTime confirmedAt,
            @JsonProperty("issuer_name") String issuerName, BigDecimal total,
            @JsonProperty("item_count") int itemCount, @JsonProperty("new_product_count") int newProductCount,
            @JsonProperty("stock_quantity") BigDecimal stockQuantity) {
        static PurchaseHistoryEntryResponse from(com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository.PurchaseHistoryEntry value) {
            return new PurchaseHistoryEntryResponse(value.receiptId(), value.confirmedAt().atOffset(java.time.ZoneOffset.UTC),
                    value.issuerName(), value.total(), value.itemCount(), value.newProductCount(), value.stockQuantity());
        }
    }

    public record PurchaseHistoryDetailResponse(
            @JsonProperty("receipt_id") UUID receiptId, @JsonProperty("confirmed_at") OffsetDateTime confirmedAt,
            @JsonProperty("issuer_name") String issuerName, @JsonProperty("issuer_tax_id") String issuerTaxId,
            @JsonProperty("access_key") String accessKey, BigDecimal total, List<PurchaseHistoryItemResponse> items) {
        static PurchaseHistoryDetailResponse from(com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository.PurchaseHistoryDetail value) {
            return new PurchaseHistoryDetailResponse(value.receiptId(), value.confirmedAt().atOffset(java.time.ZoneOffset.UTC),
                    value.issuerName(), value.issuerTaxId(), value.accessKey(), value.total(), value.items().stream()
                            .map(item -> new PurchaseHistoryItemResponse(item.lineNumber(), item.productId(), item.description(),
                                    item.quantity(), item.unit(), item.unitPrice(), item.stockQuantity(), item.matchStatus())).toList());
        }
    }

    public record PurchaseHistoryItemResponse(
            @JsonProperty("line_number") int lineNumber, @JsonProperty("product_id") UUID productId,
            String description, BigDecimal quantity, String unit, @JsonProperty("unit_price") BigDecimal unitPrice,
            @JsonProperty("stock_quantity") BigDecimal stockQuantity, @JsonProperty("match_status") String matchStatus) {}

    public record PurchaseInsightsResponse(String period, List<PurchaseInsightResponse> insights) {}

    public record PurchaseInsightResponse(String type, String message,
            @JsonProperty("evidence_ids") List<UUID> evidenceIds) {}
}
