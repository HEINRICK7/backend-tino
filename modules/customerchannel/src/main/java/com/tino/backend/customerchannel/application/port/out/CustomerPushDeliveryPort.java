package com.tino.backend.customerchannel.application.port.out;

public interface CustomerPushDeliveryPort {
    int send(CustomerChannelRepository.PushSubscriptionRecord subscription, String payload);
}
