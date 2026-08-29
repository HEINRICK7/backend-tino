package com.tino.backend.sync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.AccessibleBusinessView;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.application.exception.SyncBusinessContextRequiredException;
import com.tino.backend.sync.application.model.SyncChange;
import com.tino.backend.sync.application.model.SyncChangePage;
import com.tino.backend.sync.application.port.out.SyncChangeRepository;
import com.tino.backend.sync.application.usecase.PullSyncChanges;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SyncPullUseCaseTest {
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-7000-8000-000000000301");
    private static final UUID BUSINESS_ID = UUID.fromString(
            "00000000-0000-7000-8000-00000000030a");

    @Test
    void returnsOnlyRepositoryPageForTheAuthorizedBusiness() {
        var repository = new RecordingRepository();
        var expected = new SyncChangePage(List.of(change("00000000-0000-7000-8000-00000000031a")), 7);
        repository.page = expected;

        var result = pull(repository).execute(USER_ID, BUSINESS_ID, 4, 10);

        assertThat(result).isEqualTo(expected);
        assertThat(repository.businessId).isEqualTo(BUSINESS_ID);
        assertThat(repository.cursor).isEqualTo(4);
        assertThat(repository.limit).isEqualTo(10);
    }

    @Test
    void validatesCursorAndLimitBounds() {
        var useCase = pull(new RecordingRepository());

        assertThatThrownBy(() -> useCase.execute(USER_ID, BUSINESS_ID, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(USER_ID, BUSINESS_ID, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(USER_ID, BUSINESS_ID, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipleBusinessesRequireExplicitBusinessContext() {
        var businesses = (BusinessContextReader) user -> List.of(
                business(BUSINESS_ID), business(UUID.fromString(
                        "00000000-0000-7000-8000-00000000030b")));
        var useCase = new PullSyncChanges(businesses, authorization(), new RecordingRepository());

        assertThatThrownBy(() -> useCase.execute(USER_ID, null, 0, 10))
                .isInstanceOf(SyncBusinessContextRequiredException.class);
    }

    private static PullSyncChanges pull(SyncChangeRepository repository) {
        return new PullSyncChanges(
                user -> List.of(business(BUSINESS_ID)), authorization(), repository);
    }

    private static BusinessAuthorization authorization() {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId businessId, Function<BusinessId, T> operation) {
                return operation.apply(businessId);
            }
        };
    }

    private static AccessibleBusinessView business(UUID id) {
        return new AccessibleBusinessView(id, "Test Business", "RETAIL", "ACTIVE", "OWNER");
    }

    private static SyncChange change(String eventId) {
        return new SyncChange(UUID.fromString(eventId), "store", "device", "aggregate",
                "known", 1, Instant.parse("2026-08-29T12:00:00Z"), "{\"value\":1}");
    }

    private static final class RecordingRepository implements SyncChangeRepository {
        private SyncChangePage page = new SyncChangePage(List.of(), 0);
        private UUID businessId;
        private long cursor;
        private int limit;

        @Override
        public SyncChangePage findAfter(BusinessId businessId, long cursor, int limit) {
            this.businessId = businessId.value();
            this.cursor = cursor;
            this.limit = limit;
            return page;
        }
    }
}
