package com.studentorgs.portal.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;

import reactor.core.publisher.Mono;

/**
 * Rejects any request whose decoded path has an {@code internal} or {@code ..} segment, so
 * service-to-service {@code /internal/**} endpoints can never be reached from outside, even via
 * path tricks like {@code /api/users/../internal/...}.
 */
@Component
@Order(GatewayFilterOrder.INTERNAL_BLOCK)
public class InternalPathBlockingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (isBlocked(rawPath)) {
            return ProblemResponses.write(exchange, HttpStatus.FORBIDDEN,
                    "Internal endpoints are not reachable through the gateway");
        }
        return chain.filter(exchange);
    }

    static boolean isBlocked(String rawPath) {
        String decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return Arrays.stream(decoded.split("[/\\\\]"))
                .map(segment -> segment.split(";", 2)[0])
                .anyMatch(segment -> segment.equals("internal") || segment.equals(".."));
    }
}
