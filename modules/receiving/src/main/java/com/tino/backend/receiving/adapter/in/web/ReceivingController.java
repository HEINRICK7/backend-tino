package com.tino.backend.receiving.adapter.in.web;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.usecase.GetNfeDocument;
import com.tino.backend.fiscal.application.usecase.RetrieveAndPersistNfe;
import com.tino.backend.fiscal.application.usecase.ReprocessNfe;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PreviewSnapshot;
import com.tino.backend.receiving.application.usecase.ConfirmGoodsReceipt;
import com.tino.backend.receiving.application.usecase.CreateGoodsReceiptPreview;
import com.tino.backend.receiving.application.usecase.GetGoodsReceiptPreview;
import com.tino.backend.shared.kernel.BusinessId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
public final class ReceivingController {
    private final AuthenticatedUserResolver users; private final RetrieveAndPersistNfe retrieval; private final ReprocessNfe reprocess; private final GetNfeDocument fiscal;
    private final CreateGoodsReceiptPreview createPreview; private final GetGoodsReceiptPreview getPreview; private final ConfirmGoodsReceipt confirm;
    public ReceivingController(AuthenticatedUserResolver users, RetrieveAndPersistNfe retrieval, ReprocessNfe reprocess, GetNfeDocument fiscal, CreateGoodsReceiptPreview createPreview, GetGoodsReceiptPreview getPreview, ConfirmGoodsReceipt confirm) { this.users = users; this.retrieval = retrieval; this.reprocess = reprocess; this.fiscal = fiscal; this.createPreview = createPreview; this.getPreview = getPreview; this.confirm = confirm; }
    @PostMapping("/nfe-documents")
    public NfeResponse retrieve(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID businessId, @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, @Valid @RequestBody NfeRequest request) {
        requireKey(idempotencyKey); var user = users.resolve(requirePrincipal(principal)); var business = new BusinessId(businessId); var document = retrieval.execute(user.userId(), business, new NfeAccessKey(request.accessKey()), idempotencyKey); var preview = document.retrievalStatus() == com.tino.backend.fiscal.domain.model.RetrievalStatus.SUCCESS ? createPreview.execute(user.userId(), business, document.id()) : null; return NfeResponse.from(document, preview);
    }
    @GetMapping("/nfe-documents/{documentId}")
    public NfeResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID businessId, @PathVariable UUID documentId) { var user = users.resolve(requirePrincipal(principal)); return NfeResponse.from(fiscal.execute(user.userId(), new BusinessId(businessId), documentId), null); }
    @PostMapping("/nfe-documents/{documentId}/reprocess")
    public NfeResponse reprocess(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID businessId,
            @PathVariable UUID documentId, @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) {
        requireKey(idempotencyKey); var user = users.resolve(requirePrincipal(principal));
        return NfeResponse.from(reprocess.execute(user.userId(), new BusinessId(businessId), documentId, idempotencyKey), null);
    }
    @GetMapping("/nfe-documents/{documentId}/preview")
    public PreviewResponse preview(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID businessId, @PathVariable UUID documentId) { var user = users.resolve(requirePrincipal(principal)); return PreviewResponse.from(getPreview.executeByDocument(user.userId(), new BusinessId(businessId), documentId)); }
    @PostMapping("/goods-receipts/{previewId}/confirm")
    public ReceiptResponse confirm(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID businessId, @PathVariable UUID previewId, @Valid @RequestBody ConfirmRequest request, @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) { requireKey(idempotencyKey); var user = users.resolve(requirePrincipal(principal)); var decisions = request.items().stream().map(v -> new ConfirmGoodsReceipt.Decision(v.lineNumber(), v.action(), v.productId(), v.conversionFactor(), v.baseUnit())).toList(); return new ReceiptResponse(confirm.execute(user.userId(), new BusinessId(businessId), previewId, request.previewVersion(), decisions)); }
    private static AuthenticatedPrincipal requirePrincipal(AuthenticatedPrincipal principal) { if (principal == null) throw new IllegalArgumentException("authentication required"); return principal; }
    private static void requireKey(String key) { if (key == null || key.isBlank() || key.length() > 200) throw new IllegalArgumentException("Idempotency-Key is required and must be at most 200 characters"); }
    public record NfeRequest(@NotBlank String accessKey) {}
    public record ConfirmRequest(long previewVersion, List<DecisionRequest> items) { public ConfirmRequest { items = items == null ? List.of() : List.copyOf(items); } }
    public record DecisionRequest(int lineNumber, ConfirmGoodsReceipt.Action action, UUID productId, BigDecimal conversionFactor, String baseUnit) {}
    public record ReceiptResponse(UUID receiptId) {}
    public record NfeResponse(UUID id, String accessKey, String retrievalStatus, String fiscalStatus, int itemCount, PreviewResponse preview) { static NfeResponse from(NfeDocumentSnapshot d, PreviewSnapshot p) { return new NfeResponse(d.id(), d.accessKey().value(), d.retrievalStatus().name(), d.fiscalStatus().name(), d.document() == null ? 0 : d.document().items().size(), p == null ? null : PreviewResponse.from(p)); } }
    public record PreviewResponse(UUID id, UUID documentId, String status, long version, List<PreviewItemResponse> items) { static PreviewResponse from(PreviewSnapshot p) { return new PreviewResponse(p.id(), p.documentId(), p.status(), p.version(), p.items().stream().map(v -> new PreviewItemResponse(v.lineNumber(), v.resolutionStatus().name(), v.productId(), v.candidateName(), v.purchaseUnit(), v.purchaseQuantity(), v.baseUnit(), v.conversionFactor(), v.unitCost())).toList()); } }
    public record PreviewItemResponse(int lineNumber, String resolutionStatus, UUID productId, String candidateName, String purchaseUnit, BigDecimal purchaseQuantity, String baseUnit, BigDecimal conversionFactor, BigDecimal unitCost) {}
}
