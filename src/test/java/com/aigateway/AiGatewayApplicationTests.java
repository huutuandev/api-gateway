package com.aigateway;

import org.junit.jupiter.api.Test;

/**
 * Placeholder application test.
 *
 * Full @SpringBootTest context tests require:
 * - PostgreSQL running (not H2 — H2 does not support all PostgreSQL-specific DDL
 *   like CHECK constraints on enum columns used by AiRequest entity)
 * - Redis running
 *
 * Unit tests are in com.aigateway.service.* and com.aigateway.controller.*
 * and do NOT require a running application context.
 */
class AiGatewayApplicationTests {

    @Test
    void placeholder() {
        // Context load test requires full infrastructure (PostgreSQL + Redis).
        // Run manually against a live environment.
    }
}
