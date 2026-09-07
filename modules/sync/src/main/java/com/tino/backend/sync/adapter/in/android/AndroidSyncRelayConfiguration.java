package com.tino.backend.sync.adapter.in.android;

import com.tino.backend.sync.application.port.in.SyncEventHandler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit v1 Android event registry for the cloud relay contract. */
@Configuration(proxyBeanMethods = false)
public class AndroidSyncRelayConfiguration {
    static final List<String> SUPPORTED_EVENT_TYPES = List.of(
            "product.created",
            "product.created.from_fiscal_document",
            "product.price.changed",
            "product.price.reversed",
            "stock.received",
            "sale.created",
            "sale.reversed",
            "direct.receipt.created",
            "customer.created",
            "customer.updated",
            "supplier.created",
            "supplier.product.mapping.created",
            "payment.agreement.created",
            "credit.sale.created",
            "credit.receivable.created",
            "credit.payment.received",
            "credit.payment.reversed",
            "credit.adjustment.created",
            "credit.entry.disputed",
            "credit.settled",
            "purchase.created",
            "purchase.ordered",
            "purchase.received",
            "inventory.purchase.received",
            "fiscal.import.committed",
            "order.created",
            "order.status_changed");

    @Bean SyncEventHandler productCreatedRelay() { return relay("product.created"); }
    @Bean SyncEventHandler productCreatedFromFiscalRelay() { return relay("product.created.from_fiscal_document"); }
    @Bean SyncEventHandler productPriceChangedRelay() { return relay("product.price.changed"); }
    @Bean SyncEventHandler productPriceReversedRelay() { return relay("product.price.reversed"); }
    @Bean SyncEventHandler stockReceivedRelay() { return relay("stock.received"); }
    @Bean SyncEventHandler saleCreatedRelay() { return relay("sale.created"); }
    @Bean SyncEventHandler saleReversedRelay() { return relay("sale.reversed"); }
    @Bean SyncEventHandler directReceiptCreatedRelay() { return relay("direct.receipt.created"); }
    @Bean SyncEventHandler customerCreatedRelay() { return relay("customer.created"); }
    @Bean SyncEventHandler customerUpdatedRelay() { return relay("customer.updated"); }
    @Bean SyncEventHandler supplierCreatedRelay() { return relay("supplier.created"); }
    @Bean SyncEventHandler supplierProductMappingCreatedRelay() { return relay("supplier.product.mapping.created"); }
    @Bean SyncEventHandler paymentAgreementCreatedRelay() { return relay("payment.agreement.created"); }
    @Bean SyncEventHandler creditSaleCreatedRelay() { return relay("credit.sale.created"); }
    @Bean SyncEventHandler creditReceivableCreatedRelay() { return relay("credit.receivable.created"); }
    @Bean SyncEventHandler creditPaymentReceivedRelay() { return relay("credit.payment.received"); }
    @Bean SyncEventHandler creditPaymentReversedRelay() { return relay("credit.payment.reversed"); }
    @Bean SyncEventHandler creditAdjustmentCreatedRelay() { return relay("credit.adjustment.created"); }
    @Bean SyncEventHandler creditEntryDisputedRelay() { return relay("credit.entry.disputed"); }
    @Bean SyncEventHandler creditSettledRelay() { return relay("credit.settled"); }
    @Bean SyncEventHandler purchaseCreatedRelay() { return relay("purchase.created"); }
    @Bean SyncEventHandler purchaseOrderedRelay() { return relay("purchase.ordered"); }
    @Bean SyncEventHandler purchaseReceivedRelay() { return relay("purchase.received"); }
    @Bean SyncEventHandler inventoryPurchaseReceivedRelay() { return relay("inventory.purchase.received"); }
    @Bean SyncEventHandler fiscalImportCommittedRelay() { return relay("fiscal.import.committed"); }
    @Bean SyncEventHandler orderCreatedRelay() { return relay("order.created"); }
    @Bean SyncEventHandler orderStatusChangedRelay() { return relay("order.status_changed"); }

    private static SyncEventHandler relay(String eventType) {
        return new AndroidSyncRelayHandler(eventType);
    }
}
