package com.tino.backend.receiving.application.model;

import java.util.List;
import java.util.UUID;

public record PreviewSnapshot(UUID id, UUID documentId, GoodsReceiptPreviewStatus status, long version, List<PreviewItem> items) {
    public PreviewSnapshot { items = List.copyOf(items); }
}
