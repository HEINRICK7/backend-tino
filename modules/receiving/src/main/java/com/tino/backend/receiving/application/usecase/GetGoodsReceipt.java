package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.GoodsReceiptResult;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

/** Returns only the tenant-authorized mobile read model of a goods receipt. */
public final class GetGoodsReceipt {
    private final BusinessAuthorization authorization;
    private final ReceivingRepository receiving;

    public GetGoodsReceipt(BusinessAuthorization authorization, ReceivingRepository receiving) {
        this.authorization = Objects.requireNonNull(authorization);
        this.receiving = Objects.requireNonNull(receiving);
    }

    public GoodsReceiptResult execute(UUID userId, BusinessId businessId, UUID receiptId) {
        return authorization.execute(userId, businessId,
                authorized -> receiving.findReceipt(authorized, receiptId)
                        .orElseThrow(() -> new ReceivingException(ReceivingErrorCode.NFE_NOT_FOUND,
                                "goods receipt not found", false, 404)));
    }
}
