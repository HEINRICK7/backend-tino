package com.tino.backend.customerchannel.adapter.out.provider;

import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerPushDeliveryPort;
import com.tino.backend.customerchannel.application.service.CustomerPushSettings;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.charset.StandardCharsets;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.security.Security;

@Component
public final class WebPushCustomerDeliveryAdapter implements CustomerPushDeliveryPort {
    private static final int TTL_SECONDS = 300;

    private final CustomerPushSettings settings;

    public WebPushCustomerDeliveryAdapter(CustomerPushSettings settings) {
        this.settings = settings;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public int send(CustomerChannelRepository.PushSubscriptionRecord subscription, String payload) {
        try {
            var push = new PushService(settings.vapidPublicKey(), settings.vapidPrivateKey(),
                    settings.vapidSubject());
            var notification = new Notification(subscription.endpoint(), subscription.p256dhKey(),
                    subscription.authKey(), payload.getBytes(StandardCharsets.UTF_8), TTL_SECONDS);
            var future = push.sendAsync(notification, Encoding.AES128GCM);
            HttpResponse response;
            try {
                response = future.get(settings.dispatchTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw new IllegalStateException("push provider timed out", exception);
            }
            return response.getStatusLine().getStatusCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("push delivery interrupted", exception);
        } catch (ExecutionException | java.io.IOException | java.security.GeneralSecurityException
                | org.jose4j.lang.JoseException exception) {
            throw new IllegalStateException("push provider request failed", exception);
        }
    }
}
