package com.tino.backend.customerchannel.adapter.in.security;

import com.tino.backend.customerchannel.application.port.in.CustomerChannelPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class CustomerChannelAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = 1L;
    private final CustomerChannelPrincipal principal;

    public CustomerChannelAuthenticationToken(CustomerChannelPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_CHANNEL")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override public CustomerChannelPrincipal getPrincipal() { return principal; }
    @Override public Object getCredentials() { return "[PROTECTED]"; }
}
