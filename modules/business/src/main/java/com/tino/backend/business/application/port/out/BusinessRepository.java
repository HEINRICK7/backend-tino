package com.tino.backend.business.application.port.out;

import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessDataSourceType;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Specific outbound operations required by M3; this is not a generic CRUD port. */
public interface BusinessRepository {
    /** Persists the Business and its first OWNER in one database transaction. */
    void createWithOwner(Business business, BusinessMembership owner);

    Optional<Business> findById(BusinessId businessId);

    List<Business> findByIds(Collection<BusinessId> businessIds);

    /** Changes the explicit source selected for a Business. */
    default void updateDataSource(BusinessId businessId, BusinessDataSourceType sourceType) {
        throw new UnsupportedOperationException("business data source update is not supported");
    }
}
