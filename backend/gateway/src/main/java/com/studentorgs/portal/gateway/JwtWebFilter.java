package com.studentorgs.portal.gateway;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.studentorgs.portal.common.security.InvalidTokenException;
import com.studentorgs.portal.common.security.JwtService;
import reactor.core.publisher.Mono;

/**
 * First line of defence: requests to non-public paths need a valid JWT. The token is forwarded
 * unchanged; each service validates it again and applies role rules.
 */
@Component
@Order(GatewayFilterOrder.JWT)
public class JwtWebFilter implements WebFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final List<PathPattern> publicPaths;

    public JwtWebFilter(JwtService jwtService, GatewayProperties properties) {
        this.jwtService = jwtService;
        this.publicPaths = properties.publicPaths().stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS || isPublic(exchange)) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return ProblemResponses.write(exchange, HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            jwtService.parse(header.substring(BEARER.length()).trim());
        } catch (InvalidTokenException e) {
            return ProblemResponses.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        return chain.filter(exchange);
    }

    private boolean isPublic(ServerWebExchange exchange) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        return publicPaths.stream().anyMatch(p -> p.matches(path));
    }
}
