package com.tino.backend.receiving.application.model;

import java.util.List;
import java.util.UUID;

/** Authoritative remote result that Android can project into Room. */
public record GoodsReceiptResult(
        UUID receiptId,
        GoodsReceiptStatus status,
        int itemCount,
        List<GoodsReceiptItemResult> items) {
    public GoodsReceiptResult {
        items = List.copyOf(items);
    }
}
