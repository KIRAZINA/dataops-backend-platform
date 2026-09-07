package com.dataops.platform.streaming.outbox;

/**
 * Lifecycle state of an {@code OutboxEvent} as it travels through the
 * transactional outbox + polling relay.
 *
 * <p>Transitions:
 * <pre>
 *   new save       -&gt; PENDING
 *   relay success  -&gt; PUBLISHED
 *   relay failure (retries &lt; MAX)  -&gt; PENDING (retries incremented)
 *   relay failure (retries &gt;= MAX) -&gt; FAILED
 *   cleanup task   -&gt; deleted from DB (terminal state for the row, not the enum)
 * </pre>
 *
 * <p>{@code FAILED} is terminal in the sense that the relay will no longer retry.
 * Re-driving a FAILED event back to PENDING is an operator action, deliberately
 * out of scope here (see Phase 7 §5 "Out of Scope").
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
