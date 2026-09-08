package com.tino.backend.customerchannel.adapter.in.security;

import com.tino.backend.customerchannel.application.port.in.CustomerChannelPrincipal;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.io.IOException;
import com.tino.backend.customerchannel.application.service.CustomerChannelToken;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class CustomerChannelAuthenticationFilter extends OncePerRequestFilter {
    private final CustomerChannelRepository channels;
    private final Clock clock;
    private final String cookieName;

    public CustomerChannelAuthenticationFilter(CustomerChannelRepository channels, Clock clock,
            @Value("${tino.customer-channel.session.cookie-name:tino_customer_session}") String cookieName) {
        this.channels = channels;
        this.clock = clock;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            readCookie(request).flatMap(token -> channels.findActiveSession(CustomerChannelToken.hash(token), Instant.now(clock)))
                    .ifPresent(session -> SecurityContextHolder.getContext().setAuthentication(
                            new CustomerChannelAuthenticationToken(new CustomerChannelPrincipal(
                                    session.id(), session.channelId(), session.businessId().value(), session.customerId()))));
        }
        filterChain.doFilter(request, response);
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

}
