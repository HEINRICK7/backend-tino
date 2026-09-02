package com.tino.backend.receiving;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.fiscal.application.port.in.NfeReader;
import com.tino.backend.inventory.application.port.out.InventoryPort;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.receiving.application.usecase.ConfirmGoodsReceipt;
import com.tino.backend.receiving.application.usecase.CreateGoodsReceiptPreview;
import com.tino.backend.receiving.application.usecase.GetGoodsReceiptPreview;
import com.tino.backend.receiving.application.usecase.GetGoodsReceipt;
import com.tino.backend.receiving.application.port.out.PurchaseReceivingRepository;
import com.tino.backend.receiving.application.port.out.PurchaseDocumentProductLookup;
import com.tino.backend.receiving.application.port.out.PurchaseReceiptRepository;
import com.tino.backend.receiving.application.usecase.PurchaseDocumentMatcher;
import com.tino.backend.receiving.application.usecase.CreatePurchaseDocumentPreview;
import com.tino.backend.receiving.application.usecase.ConfirmPurchaseDocument;
import com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository;
import com.tino.backend.receiving.application.usecase.GetPurchaseHistory;
import com.tino.backend.receiving.application.usecase.GetPurchaseInsights;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReceivingConfiguration {
    @Bean CreateGoodsReceiptPreview createGoodsReceiptPreview(BusinessAuthorization a, NfeReader f, ProductCatalog c, ReceivingRepository r, Clock clock) { return new CreateGoodsReceiptPreview(a, f, c, r, clock); }
    @Bean GetGoodsReceiptPreview getGoodsReceiptPreview(BusinessAuthorization a, ReceivingRepository r) { return new GetGoodsReceiptPreview(a, r); }
    @Bean GetGoodsReceipt getGoodsReceipt(BusinessAuthorization a, ReceivingRepository r) { return new GetGoodsReceipt(a, r); }
    @Bean ConfirmGoodsReceipt confirmGoodsReceipt(BusinessAuthorization a, NfeReader f, ProductCatalog c, ReceivingRepository r, InventoryPort i, Clock clock) { return new ConfirmGoodsReceipt(a, f, c, r, i, clock); }
    @Bean PurchaseDocumentMatcher purchaseDocumentMatcher(PurchaseDocumentProductLookup catalog) { return new PurchaseDocumentMatcher(catalog); }
    @Bean CreatePurchaseDocumentPreview createPurchaseDocumentPreview(BusinessAuthorization a, PurchaseReceivingRepository r, PurchaseDocumentMatcher m, Clock clock) { return new CreatePurchaseDocumentPreview(a, r, m, clock); }
    @Bean ConfirmPurchaseDocument confirmPurchaseDocument(BusinessAuthorization a, PurchaseReceiptRepository r, ProductCatalog c, PurchaseDocumentProductLookup p, Clock clock) { return new ConfirmPurchaseDocument(a, r, c, p, clock); }
    @Bean GetPurchaseHistory getPurchaseHistory(BusinessAuthorization a, PurchaseHistoryRepository h, Clock clock) { return new GetPurchaseHistory(a, h, clock); }
    @Bean GetPurchaseInsights getPurchaseInsights(BusinessAuthorization a, PurchaseHistoryRepository h, Clock clock) { return new GetPurchaseInsights(a, h, clock); }
}
