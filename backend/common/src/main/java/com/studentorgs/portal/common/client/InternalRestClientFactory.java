package com.studentorgs.portal.common.client;

import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.studentorgs.portal.common.security.InternalTokenFilter;
import com.studentorgs.portal.common.web.RequestIdFilter;

/**
 * Creates {@link RestClient}s for {@code /internal/**} calls to other services: sends
 * {@code X-Internal-Token}, forwards {@code X-Request-Id}, and uses short timeouts.
 */
public class InternalRestClientFactory {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient.Builder builder;
    private final String internalToken;

    public InternalRestClientFactory(RestClient.Builder builder, String internalToken) {
        this.builder = builder;
        this.internalToken = internalToken;
    }

    public RestClient create(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                    if (requestId != null) {
                        request.getHeaders().set(RequestIdFilter.HEADER, requestId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
