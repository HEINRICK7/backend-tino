package com.tino.backend.receiving.application.model;

import com.tino.backend.catalog.application.model.ProductResolution;
import java.math.BigDecimal;
import java.util.UUID;

public record PreviewItem(int lineNumber, ProductResolution.Status resolutionStatus, UUID productId,
        String candidateName, String purchaseUnit, BigDecimal purchaseQuantity, String baseUnit,
        BigDecimal conversionFactor, BigDecimal unitCost) {}
