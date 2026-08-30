package com.tino.backend.receiving;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.fiscal.application.port.in.NfeReader;
import com.tino.backend.inventory.application.port.out.InventoryPort;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.receiving.application.usecase.ConfirmGoodsReceipt;
import com.tino.backend.receiving.application.usecase.CreateGoodsReceiptPreview;
import com.tino.backend.receiving.application.usecase.GetGoodsReceiptPreview;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReceivingConfiguration {
    @Bean CreateGoodsReceiptPreview createGoodsReceiptPreview(BusinessAuthorization a, NfeReader f, ProductCatalog c, ReceivingRepository r, Clock clock) { return new CreateGoodsReceiptPreview(a, f, c, r, clock); }
    @Bean GetGoodsReceiptPreview getGoodsReceiptPreview(BusinessAuthorization a, ReceivingRepository r) { return new GetGoodsReceiptPreview(a, r); }
    @Bean ConfirmGoodsReceipt confirmGoodsReceipt(BusinessAuthorization a, NfeReader f, ProductCatalog c, ReceivingRepository r, InventoryPort i, Clock clock) { return new ConfirmGoodsReceipt(a, f, c, r, i, clock); }
}
