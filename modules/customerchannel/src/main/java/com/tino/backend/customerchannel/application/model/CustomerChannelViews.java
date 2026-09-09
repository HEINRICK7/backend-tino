package com.tino.backend.customerchannel.application.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.customerchannel.domain.model.CustomerChannelStatus;
import com.tino.backend.business.application.port.in.BusinessPixReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CustomerChannelViews {
    private CustomerChannelViews() {}

    public record InviteResponse(@JsonProperty("channelId") UUID channelId, String status,
            @JsonProperty("deliveryStatus") String deliveryStatus) {}
    public record ActivationResponse(String status) {}
    public record HomeResponse(Channel channel, Customer customer, Business business, Account account,
            Features features, Push push, Pix pix) {}
    public record Channel(String status) {}
    public record Customer(@JsonProperty("displayName") String displayName) {}
    public record Business(@JsonProperty("displayName") String displayName) {}
    public record Account(String status, Balance balance, long version,
            @JsonProperty("asOf") Instant asOf) {}
    public record Balance(long minor, String currency) {}
    public record Features(boolean push, boolean pix, boolean agreements) {}
    public record Push(int activeSubscriptions) {}
    public record Pix(boolean enabled, String key, @JsonProperty("copyPaste") String copyPaste) {}
    public record PushConfigResponse(boolean enabled, String vapidPublicKey) {}
    public record PushSubscriptionResponse(UUID id, String status) {}
    public record ActivityPage(List<Activity> items, @JsonProperty("nextCursor") String nextCursor,
            @JsonProperty("asOf") Instant asOf) {}
    public record Activity(UUID id, String type, String impact, Amount amount, String label,
            @JsonProperty("occurredAt") Instant occurredAt) {}
    public record Amount(long minor, String currency) {}

    public static HomeResponse home(CustomerChannelRepositoryHome home) {
        return home(home, false, 0, java.util.Optional.empty());
    }

    public static HomeResponse home(CustomerChannelRepositoryHome home, boolean pushEnabled,
            int activeSubscriptions) {
        return home(home, pushEnabled, activeSubscriptions, java.util.Optional.empty());
    }

    public static HomeResponse home(CustomerChannelRepositoryHome home, boolean pushEnabled,
            int activeSubscriptions, java.util.Optional<BusinessPixReader.PixView> pixConfiguration) {
        return new HomeResponse(new Channel(home.status().name()), new Customer(home.customerName()),
                new Business(home.businessName()), new Account(home.accountStatus(),
                new Balance(toMinor(home.balance()), home.currency()), home.version(), home.asOf()),
                new Features(pushEnabled, pixConfiguration.isPresent(), false), new Push(activeSubscriptions),
                pix(pixConfiguration));
    }

    public static HomeResponse home(com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository.HomeRecord home,
            boolean pushEnabled, int activeSubscriptions) {
        return home(home, pushEnabled, activeSubscriptions, java.util.Optional.empty());
    }

    public static HomeResponse home(com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository.HomeRecord home,
            boolean pushEnabled, int activeSubscriptions,
            java.util.Optional<BusinessPixReader.PixView> pixConfiguration) {
        return new HomeResponse(new Channel(home.channelStatus().name()), new Customer(home.customerName()),
                new Business(home.businessName()), new Account(home.accountStatus(),
                new Balance(toMinor(home.balance()), home.currency()), home.version(), home.asOf()),
                new Features(pushEnabled, pixConfiguration.isPresent(), false), new Push(activeSubscriptions),
                pix(pixConfiguration));
    }

    private static Pix pix(java.util.Optional<BusinessPixReader.PixView> value) {
        return value.map(configuration -> new Pix(
                        configuration.enabled(), configuration.key(), configuration.copyPaste()))
                .orElseGet(() -> new Pix(false, null, null));
    }

    public static Activity activity(com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository.ActivityRecord item) {
        return new Activity(item.id(), item.type(), item.impact(),
                new Amount(toMinor(item.amount()), item.currency()), item.label(), item.occurredAt());
    }

    private static long toMinor(BigDecimal value) {
        return value.movePointRight(2).longValueExact();
    }

    public interface CustomerChannelRepositoryHome {
        CustomerChannelStatus status();
        String customerName();
        String businessName();
        String accountStatus();
        BigDecimal balance();
        String currency();
        long version();
        Instant asOf();
    }
}
