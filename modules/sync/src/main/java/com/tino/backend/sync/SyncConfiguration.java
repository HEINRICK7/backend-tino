package com.tino.backend.sync;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.sync.application.port.in.SyncEventHandler;
import com.tino.backend.sync.application.port.out.SyncEventRepository;
import com.tino.backend.sync.application.port.out.SyncChangeRepository;
import com.tino.backend.sync.application.usecase.ProcessSyncEvents;
import com.tino.backend.sync.application.usecase.PullSyncChanges;
import com.tino.backend.sync.application.usecase.SyncEventHandlerRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for Sync Push. Domain event handlers are registered independently. */
@Configuration(proxyBeanMethods = false)
public class SyncConfiguration {
    @Bean
    SyncEventHandlerRegistry syncEventHandlerRegistry(List<SyncEventHandler> handlers) {
        return new SyncEventHandlerRegistry(handlers);
    }

    @Bean
    ProcessSyncEvents processSyncEvents(
            BusinessContextReader businesses,
            BusinessAuthorization businessAuthorization,
            DeviceInstallationContextReader devices,
            SyncEventRepository events,
            SyncEventHandlerRegistry handlers,
            UuidGenerator ids,
            Clock clock) {
        return new ProcessSyncEvents(
                businesses, businessAuthorization, devices, events, handlers, ids, clock);
    }

    @Bean
    PullSyncChanges pullSyncChanges(
            BusinessContextReader businesses,
            BusinessAuthorization businessAuthorization,
            SyncChangeRepository changes) {
        return new PullSyncChanges(businesses, businessAuthorization, changes);
    }
}
