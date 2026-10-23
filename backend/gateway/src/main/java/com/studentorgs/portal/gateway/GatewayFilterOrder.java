package com.studentorgs.portal.gateway;

import org.springframework.core.Ordered;

/** Order of the gateway's web filters: CORS first (so errors still carry CORS headers), then blocking, then auth. */
final class GatewayFilterOrder {

    static final int CORS = Ordered.HIGHEST_PRECEDENCE;
    static final int REQUEST_ID = Ordered.HIGHEST_PRECEDENCE + 5;
    static final int INTERNAL_BLOCK = Ordered.HIGHEST_PRECEDENCE + 10;
    static final int JWT = Ordered.HIGHEST_PRECEDENCE + 20;

    private GatewayFilterOrder() {
    }
}
