package com.tino.backend.customerchannel.adapter.out.dispatch;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerPushDeliveryPort;
import com.tino.backend.customerchannel.application.service.CustomerPushSettings;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CustomerPushDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final UUID NOTIFICATION_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID ACTIVITY_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-000000000103");
    private static final BusinessId BUSINESS_ID = new BusinessId(
            UUID.fromString("00000000-0000-7000-8000-000000000104"));

    @Test
    void keepsNotificationRetryableWhenCustomerHasNotAuthorizedPushYet() {
        var channels = org.mockito.Mockito.mock(CustomerChannelRepository.class);
        var delivery = org.mockito.Mockito.mock(CustomerPushDeliveryPort.class);
        var notification = notification(0);
        when(channels.findDuePushNotifications(NOW, 20)).thenReturn(List.of(notification));
        when(channels.claimPushNotification(NOTIFICATION_ID, NOW, NOW.plusSeconds(30))).thenReturn(true);
        when(channels.listActivePushSubscriptions(BUSINESS_ID, CUSTOMER_ID)).thenReturn(List.of());

        dispatcher(channels, delivery).dispatch();

        verify(channels).retryPushNotification(
                NOTIFICATION_ID, NOW.plusSeconds(2), "no active push subscriptions");
        verify(channels, never()).markPushNotificationDelivered(eq(NOTIFICATION_ID), eq(NOW));
    }

    @Test
    void abandonsNotificationAfterRetryBudgetWithoutActiveSubscription() {
        var channels = org.mockito.Mockito.mock(CustomerChannelRepository.class);
        var delivery = org.mockito.Mockito.mock(CustomerPushDeliveryPort.class);
        var notification = notification(7);
        when(channels.findDuePushNotifications(NOW, 20)).thenReturn(List.of(notification));
        when(channels.claimPushNotification(NOTIFICATION_ID, NOW, NOW.plusSeconds(30))).thenReturn(true);
        when(channels.listActivePushSubscriptions(BUSINESS_ID, CUSTOMER_ID)).thenReturn(List.of());

        dispatcher(channels, delivery).dispatch();

        verify(channels).markPushNotificationDelivered(NOTIFICATION_ID, NOW);
        verify(channels, never()).retryPushNotification(
                NOTIFICATION_ID, NOW.plusSeconds(256), "no active push subscriptions");
    }

    private static CustomerPushDispatcher dispatcher(CustomerChannelRepository channels,
            CustomerPushDeliveryPort delivery) {
        CustomerPushSettings settings = new CustomerPushSettings(
                true, "public-key", "private-key", "mailto:suporte@tino.otimizanegocio.com",
                Duration.ofSeconds(5), Duration.ZERO, Duration.ofSeconds(5));
        TenantContextExecutor tenants = new TenantContextExecutor() {
            @Override
            public <T> T execute(BusinessId businessId, Supplier<T> operation) {
                return operation.get();
            }
        };
        return new CustomerPushDispatcher(channels, delivery, settings, tenants,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CustomerChannelRepository.PushNotificationRecord notification(int attempts) {
        return new CustomerChannelRepository.PushNotificationRecord(
                NOTIFICATION_ID, BUSINESS_ID, CUSTOMER_ID, ACTIVITY_ID,
                "DEBIT", NOW, attempts);
    }
}
