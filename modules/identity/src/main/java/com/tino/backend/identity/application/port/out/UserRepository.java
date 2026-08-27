package com.tino.backend.identity.application.port.out;

import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import java.util.Optional;

/**
 * Minimal identity persistence port. It intentionally exposes no generic CRUD
 * operations and no persistence framework types.
 */
public interface UserRepository {
    Optional<User> findByExternalSubject(ExternalSubject externalSubject);

    User insert(User user) throws ExternalSubjectAlreadyExistsException;
}
