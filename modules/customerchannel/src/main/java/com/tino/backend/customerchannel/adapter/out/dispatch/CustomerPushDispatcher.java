package com.tino.backend.customerchannel.adapter.out.dispatch;

import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerPushDeliveryPort;
import com.tino.backend.customerchannel.application.service.CustomerPushSettings;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends durable customer notifications outside the transaction that changed the ledger. */
@Component
public final class CustomerPushDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerPushDispatcher.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 8;
    private static final Duration LEASE = Duration.ofSeconds(30);

    private final CustomerChannelRepository channels;
    private final CustomerPushDeliveryPort delivery;
    private final CustomerPushSettings settings;
    private final TenantContextExecutor tenants;
    private final Clock clock;

    public CustomerPushDispatcher(CustomerChannelRepository channels,
            CustomerPushDeliveryPort delivery, CustomerPushSettings settings,
            TenantContextExecutor tenants, Clock clock) {
        this.channels = channels;
        this.delivery = delivery;
        this.settings = settings;
        this.tenants = tenants;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${tino.customer-channel.push.dispatch-interval-ms:5000}",
            initialDelayString = "${tino.customer-channel.push.dispatch-initial-delay-ms:10000}")
    public void dispatch() {
        if (!settings.enabled()) return;
        var now = clock.instant();
        for (var notification : channels.findDuePushNotifications(now, BATCH_SIZE)) {
            if (!channels.claimPushNotification(notification.id(), now, now.plus(LEASE))) continue;
            dispatch(notification);
        }
    }

    private void dispatch(CustomerChannelRepository.PushNotificationRecord notification) {
        List<CustomerChannelRepository.PushSubscriptionRecord> subscriptions;
        try {
            subscriptions = tenants.execute(notification.businessId(),
                    () -> channels.listActivePushSubscriptions(notification.businessId(), notification.customerId()));
        } catch (RuntimeException exception) {
            retry(notification, "subscription lookup failed");
            return;
        }

        if (subscriptions.isEmpty()) {
            if (notification.attempts() >= MAX_ATTEMPTS - 1) {
                channels.markPushNotificationDelivered(notification.id(), clock.instant());
                LOG.warn("customer push notification abandoned without an active subscription after attempts={} notificationId={}",
                        notification.attempts() + 1, notification.id());
            } else {
                retry(notification, "no active push subscriptions");
            }
            return;
        }

        var shouldRetry = false;
        for (var subscription : subscriptions) {
            try {
                var status = delivery.send(subscription, payload(notification));
                if (status >= 200 && status < 300) {
                    tenants.execute(notification.businessId(), () -> {
                        channels.recordPushDelivery(subscription.id(), clock.instant());
                        return null;
                    });
                } else if (status == 400 || status == 404 || status == 410) {
                    tenants.execute(notification.businessId(), () -> {
                        channels.revokePushSubscription(subscription.id(), clock.instant());
                        return null;
                    });
                } else {
                    shouldRetry = true;
                }
            } catch (RuntimeException exception) {
                shouldRetry = true;
                LOG.warn("customer push delivery failed notificationId={} subscriptionId={} reason={}",
                        notification.id(), subscription.id(), exception.getClass().getSimpleName());
            }
        }

        if (!shouldRetry || notification.attempts() >= MAX_ATTEMPTS - 1) {
            channels.markPushNotificationDelivered(notification.id(), clock.instant());
            if (shouldRetry) {
                LOG.warn("customer push notification abandoned after attempts={} notificationId={}",
                        notification.attempts() + 1, notification.id());
            }
            return;
        }
        retry(notification, "push provider returned a retryable failure");
    }

    private void retry(CustomerChannelRepository.PushNotificationRecord notification, String reason) {
        var exponent = Math.min(notification.attempts() + 1, 8);
        var delaySeconds = Math.min(300L, 1L << exponent);
        channels.retryPushNotification(notification.id(), clock.instant().plusSeconds(delaySeconds), reason);
    }

    private static String payload(CustomerChannelRepository.PushNotificationRecord notification) {
        var payment = "DEBIT".equals(notification.kind());
        var title = payment ? "Pagamento recebido" : "Caderneta atualizada";
        var body = payment
                ? "Seu pagamento foi confirmado. Toque para ver seu extrato."
                : "Sua caderneta foi atualizada. Toque para ver seu extrato.";
        return "{\"title\":\"" + title + "\",\"body\":\"" + body
                + "\",\"target\":\"/activity/" + notification.activityId()
                + "\",\"tag\":\"tino-activity-" + notification.activityId() + "\"}";
    }
}
