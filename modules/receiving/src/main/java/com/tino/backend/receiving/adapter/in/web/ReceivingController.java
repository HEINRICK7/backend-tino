package com.tino.backend.receiving.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.usecase.GetNfeDocument;
import com.tino.backend.fiscal.application.usecase.ReprocessNfe;
import com.tino.backend.fiscal.application.usecase.RetrieveAndPersistNfe;
import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.GoodsReceiptItemResult;
import com.tino.backend.receiving.application.model.GoodsReceiptPreviewStatus;
import com.tino.backend.receiving.application.model.GoodsReceiptResult;
import com.tino.backend.receiving.application.model.GoodsReceiptStatus;
import com.tino.backend.receiving.application.model.PreviewItem;
import com.tino.backend.receiving.application.model.PreviewSnapshot;
import com.tino.backend.receiving.application.usecase.ConfirmGoodsReceipt;
import com.tino.backend.receiving.application.usecase.CreateGoodsReceiptPreview;
import com.tino.backend.receiving.application.usecase.GetGoodsReceipt;
import com.tino.backend.receiving.application.usecase.GetGoodsReceiptPreview;
import com.tino.backend.shared.kernel.BusinessId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
public final class ReceivingController {
    private final AuthenticatedUserResolver users;
    private final RetrieveAndPersistNfe retrieval;
    private final ReprocessNfe reprocess;
    private final GetNfeDocument fiscal;
    private final CreateGoodsReceiptPreview createPreview;
    private final GetGoodsReceiptPreview getPreview;
    private final ConfirmGoodsReceipt confirm;
    private final GetGoodsReceipt getReceipt;

    public ReceivingController(AuthenticatedUserResolver users, RetrieveAndPersistNfe retrieval,
            ReprocessNfe reprocess, GetNfeDocument fiscal, CreateGoodsReceiptPreview createPreview,
            GetGoodsReceiptPreview getPreview, ConfirmGoodsReceipt confirm, GetGoodsReceipt getReceipt) {
        this.users = users;
        this.retrieval = retrieval;
        this.reprocess = reprocess;
        this.fiscal = fiscal;
        this.createPreview = createPreview;
        this.getPreview = getPreview;
        this.confirm = confirm;
        this.getReceipt = getReceipt;
    }

    @Operation(summary = "Retrieve an NF-e and prepare its goods receipt preview")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fiscal retrieval result"),
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Idempotency or fiscal conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nfe-documents")
    public NfeResponse retrieve(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @Parameter(description = "Stable key for one logical retrieval", required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NfeRequest request) {
        requireKey(idempotencyKey);
        var user = users.resolve(requirePrincipal(principal));
        NfeAccessKey accessKey;
        try {
            accessKey = new NfeAccessKey(request.accessKey());
        } catch (IllegalArgumentException exception) {
            throw new ReceivingException(ReceivingErrorCode.INVALID_ACCESS_KEY, exception.getMessage(), false, 400);
        }
        var business = new BusinessId(businessId);
        var document = retrieval.execute(user.userId(), business, accessKey, idempotencyKey);
        var preview = document.retrievalStatus() == RetrievalStatus.SUCCESS
                && document.fiscalStatus() == FiscalStatus.AUTHORIZED
                ? createPreview.execute(user.userId(), business, document.id()) : null;
        return NfeResponse.from(document, preview);
    }

    @Operation(summary = "Get the persisted NF-e retrieval state")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fiscal document state"),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "NF-e not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nfe-documents/{documentId}")
    public NfeResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID documentId) {
        var user = users.resolve(requirePrincipal(principal));
        return NfeResponse.from(fiscal.execute(user.userId(), new BusinessId(businessId), documentId), null);
    }

    @Operation(summary = "Reprocess persisted fiscal raw data without calling SERPRO")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reprocessed fiscal document"),
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Idempotency conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nfe-documents/{documentId}/reprocess")
    public NfeResponse reprocess(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID documentId,
            @Parameter(description = "Stable key for one logical reprocess", required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        requireKey(idempotencyKey);
        var user = users.resolve(requirePrincipal(principal));
        return NfeResponse.from(reprocess.execute(user.userId(), new BusinessId(businessId), documentId, idempotencyKey), null);
    }

    @Operation(summary = "Get the human-review preview for an NF-e")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mobile goods receipt preview"),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Preview is not available", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nfe-documents/{documentId}/preview")
    public PreviewResponse preview(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID documentId) {
        var user = users.resolve(requirePrincipal(principal));
        var business = new BusinessId(businessId);
        var snapshot = getPreview.executeByDocument(user.userId(), business, documentId);
        return PreviewResponse.from(snapshot, fiscal.execute(user.userId(), business, documentId));
    }

    @Operation(summary = "Confirm a reviewed goods receipt exactly once")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Confirmed goods receipt result"),
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Review, conversion, fiscal or idempotency conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/goods-receipts/{previewId}/confirm")
    public ReceiptResponse confirm(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID previewId,
            @Valid @RequestBody ConfirmRequest request,
            @Parameter(description = "Stable key for one logical confirmation", required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        requireKey(idempotencyKey);
        var user = users.resolve(requirePrincipal(principal));
        var decisions = request.items().stream()
                .map(v -> new ConfirmGoodsReceipt.Decision(v.lineNumber(), ConfirmGoodsReceipt.Action.valueOf(v.action().name()), v.productId(),
                        v.conversionFactor(), v.baseUnit())).toList();
        return ReceiptResponse.from(confirm.execute(user.userId(), new BusinessId(businessId), previewId,
                request.previewVersion(), decisions));
    }

    @Operation(summary = "Get an authoritative confirmed goods receipt result")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Goods receipt result"),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Goods receipt not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/goods-receipts/{receiptId}")
    public ReceiptResponse getReceipt(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID receiptId) {
        var user = users.resolve(requirePrincipal(principal));
        return ReceiptResponse.from(getReceipt.execute(user.userId(), new BusinessId(businessId), receiptId));
    }

    private static AuthenticatedPrincipal requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                "authentication required", false, 401);
        return principal;
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                    "Idempotency-Key is required and must be at most 200 characters", false, 400);
        }
    }

    public record NfeRequest(
            @JsonProperty("access_key")
            @Schema(description = "44-character NF-e access key", example = "53160911510448000171550010000106771000187760", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String accessKey) {}

    public record ConfirmRequest(
            @JsonProperty("preview_version")
            @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED) long previewVersion,
            @NotEmpty @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DecisionRequest> items) {
        public ConfirmRequest { items = items == null ? List.of() : List.copyOf(items); }
    }

    public record DecisionRequest(
            @JsonProperty("line_number") int lineNumber,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionAction action,
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("conversion_factor") BigDecimal conversionFactor,
            @JsonProperty("base_unit") String baseUnit) {}

    public record NfeResponse(
            @JsonProperty("document_id") UUID id,
            @JsonProperty("access_key") String accessKey,
            @JsonProperty("retrieval_status") RetrievalStatus retrievalStatus,
            @JsonProperty("fiscal_status") FiscalStatus fiscalStatus,
            @JsonProperty("item_count") int itemCount,
            @JsonProperty("error_code") String errorCode,
            boolean retryable,
            PreviewResponse preview) {
        static NfeResponse from(NfeDocumentSnapshot document, PreviewSnapshot preview) {
            var error = errorCode(document);
            return new NfeResponse(document.id(), document.accessKey().value(), document.retrievalStatus(),
                    document.fiscalStatus(), document.document() == null ? 0 : document.document().items().size(),
                    error, document.retrievalStatus() == RetrievalStatus.FAILED
                            || document.retrievalStatus() == RetrievalStatus.OUTCOME_UNKNOWN,
                    preview == null ? null : PreviewResponse.from(preview, document));
        }

        private static String errorCode(NfeDocumentSnapshot document) {
            return switch (document.retrievalStatus()) {
                case NOT_FOUND -> "NFE_NOT_FOUND";
                case FAILED -> "RETRIEVAL_UNAVAILABLE";
                case OUTCOME_UNKNOWN -> "OUTCOME_UNKNOWN";
                default -> null;
            };
        }
    }

    public record PreviewResponse(
            @JsonProperty("preview_id") UUID previewId,
            @JsonProperty("document_id") UUID documentId,
            @JsonProperty("document_number") String documentNumber,
            String series,
            IssuerSummary issuer,
            @JsonProperty("retrieval_status") RetrievalStatus retrievalStatus,
            @JsonProperty("fiscal_status") FiscalStatus fiscalStatus,
            GoodsReceiptPreviewStatus status,
            long version,
            ReceiptSummary summary,
            List<ReceiptPreviewItem> items) {
        static PreviewResponse from(PreviewSnapshot preview, NfeDocumentSnapshot document) {
            var canonical = document.document();
            var source = canonical == null ? List.<com.tino.backend.fiscal.domain.model.CanonicalNfeItem>of() : canonical.items();
            var items = preview.items().stream().map(item -> {
                var fiscalItem = source.stream().filter(value -> value.lineNumber() == item.lineNumber()).findFirst().orElse(null);
                return ReceiptPreviewItem.from(item, fiscalItem);
            }).toList();
            var summary = new ReceiptSummary(
                    items.size(),
                    (int) items.stream().filter(v -> v.resolutionStatus() == ProductResolutionStatus.MATCHED).count(),
                    (int) items.stream().filter(v -> v.resolutionStatus() == ProductResolutionStatus.NEW_CANDIDATE).count(),
                    (int) items.stream().filter(v -> v.resolutionStatus() == ProductResolutionStatus.NEEDS_REVIEW).count());
            var issuer = canonical == null ? null : new IssuerSummary(canonical.issuer().legalName(), canonical.issuer().tradeName());
            return new PreviewResponse(preview.id(), preview.documentId(), canonical == null ? null : canonical.number(),
                    canonical == null ? null : canonical.series(), issuer, document.retrievalStatus(), document.fiscalStatus(),
                    preview.status(), preview.version(), summary, items);
        }
    }

    public record IssuerSummary(@JsonProperty("legal_name") String legalName, @JsonProperty("trade_name") String tradeName) {}
    public record ReceiptSummary(@JsonProperty("total_items") int totalItems, @JsonProperty("matched_items") int matchedItems,
            @JsonProperty("new_candidate_items") int newCandidateItems, @JsonProperty("review_required_items") int reviewRequiredItems) {}

    public record ReceiptPreviewItem(
            int lineNumber,
            String description,
            @JsonProperty("supplier_product_code") String supplierProductCode,
            String gtin,
            @JsonProperty("resolution_status") ProductResolutionStatus resolutionStatus,
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("candidate_name") String candidateName,
            @JsonProperty("purchase_unit") String purchaseUnit,
            @JsonProperty("purchase_quantity") BigDecimal purchaseQuantity,
            @JsonProperty("purchase_unit_cost") BigDecimal purchaseUnitCost,
            @JsonProperty("product_total") BigDecimal productTotal,
            @JsonProperty("base_unit") String baseUnit,
            @JsonProperty("conversion_factor") BigDecimal conversionFactor,
            @JsonProperty("stock_quantity") BigDecimal stockQuantity,
            @JsonProperty("requires_user_action") boolean requiresUserAction) {
        static ReceiptPreviewItem from(PreviewItem item, com.tino.backend.fiscal.domain.model.CanonicalNfeItem fiscalItem) {
            var factor = item.conversionFactor();
            return new ReceiptPreviewItem(item.lineNumber(), fiscalItem == null ? item.candidateName() : fiscalItem.description(),
                    fiscalItem == null ? null : fiscalItem.supplierProductCode(), fiscalItem == null ? null : fiscalItem.gtin(),
                    ProductResolutionStatus.valueOf(item.resolutionStatus().name()), item.productId(), item.candidateName(), item.purchaseUnit(), item.purchaseQuantity(),
                    item.unitCost(), fiscalItem == null ? null : fiscalItem.productTotal(), item.baseUnit(), factor,
                    factor == null ? null : item.purchaseQuantity().multiply(factor),
                    item.resolutionStatus() != com.tino.backend.catalog.application.model.ProductResolution.Status.MATCHED);
        }
    }

    public record ReceiptResponse(
            @JsonProperty("receipt_id") UUID receiptId,
            GoodsReceiptStatus status,
            @JsonProperty("item_count") int itemCount,
            List<ReceiptResultItem> items) {
        static ReceiptResponse from(GoodsReceiptResult result) {
            return new ReceiptResponse(result.receiptId(), result.status(), result.itemCount(),
                    result.items().stream().map(ReceiptResultItem::from).toList());
        }
    }

    public record ReceiptResultItem(
            @JsonProperty("line_number") int lineNumber,
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("base_unit") String baseUnit,
            @JsonProperty("quantity_added") BigDecimal quantityAdded,
            BigDecimal unitCost) {
        static ReceiptResultItem from(GoodsReceiptItemResult item) {
            return new ReceiptResultItem(item.lineNumber(), item.productId(), item.productName(), item.baseUnit(),
                    item.quantityAdded(), item.unitCost());
        }
    }

    public record ErrorResponse(
            @Schema(example = "PACKAGING_CONVERSION_REQUIRED") String code,
            @Schema(example = "packaging conversion is required") String message,
            boolean retryable,
            @JsonProperty("correlation_id") String correlationId) {}
}
