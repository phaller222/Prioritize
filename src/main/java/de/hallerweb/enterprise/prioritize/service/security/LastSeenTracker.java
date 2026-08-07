/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records when an account was last seen, on every successful authentication — throttled.
 * <p>
 * <b>Why not "last login":</b> this API is stateless. Under Basic auth (and equally under a bearer
 * token) there is no login and no session — every single request authenticates from scratch. Stamping
 * a timestamp per authentication would therefore mean <em>one database write per REST call</em>, and a
 * polling client would turn that into a steady write load for a field nobody reads in that request.
 * The field is consequently named {@code lastSeen} and answers "is this account still in use?", not
 * "when did this person log in?".
 * <p>
 * <b>The throttle</b> keeps an in-memory map of the last stamp per account and skips the write while
 * it is younger than {@code prioritize.last-seen.throttle} (default 5 minutes). So the guarantee is:
 * a stored {@code lastSeen} is at most that interval older than the account's true last activity, and
 * a burst of requests costs one write, not one per request. The map is process-local and lost on
 * restart — the first authentication after a start always writes, which is exactly the behaviour that
 * keeps a fresh instance honest. It is bounded by the number of distinct accounts that authenticate.
 * <p>
 * <b>Transaction:</b> the update runs in its own transaction via an explicit {@link TransactionTemplate}
 * rather than {@code @Transactional}. Two reasons, both concrete: a self-invoked annotated method would
 * bypass the proxy and silently run without a transaction at all, and annotating the listener itself
 * would open a transaction — and take a pooled connection — on <em>every</em> authentication, including
 * the throttled ones that write nothing. {@code REQUIRES_NEW} because the authentication path's own
 * transaction is {@code readOnly}. A failure here must never break authentication, so everything is
 * caught and logged.
 *
 * @author peter haller
 */
@Component
@Slf4j
public class LastSeenTracker {

    private final UserRepository userRepository;
    private final TransactionTemplate transactions;
    private final Duration throttle;

    /** Last stamp written per username, so the common case costs no database access at all. */
    private final Map<String, Instant> lastStamped = new ConcurrentHashMap<>();

    public LastSeenTracker(UserRepository userRepository,
                           PlatformTransactionManager transactionManager,
                           @Value("${prioritize.last-seen.throttle:PT5M}") Duration throttle) {
        this.userRepository = userRepository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.throttle = throttle;
    }

    /**
     * Stamps the authenticated account unless it was stamped within the throttle interval.
     *
     * @param event the successful authentication published by Spring Security
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        try {
            Authentication auth = event.getAuthentication();
            if (auth == null) {
                return;
            }
            String username = CurrentUserResolver.usernameOf(auth);
            if (username == null || username.isBlank()) {
                return;
            }

            Instant now = Instant.now();
            Instant previous = lastStamped.get(username);
            if (previous != null && Duration.between(previous, now).compareTo(throttle) < 0) {
                return;
            }
            // Claim the slot before writing: concurrent requests for the same account must not each
            // decide to write. Losing the race means skipping this stamp, which the throttle allows.
            if (previous == null
                    ? lastStamped.putIfAbsent(username, now) != null
                    : !lastStamped.replace(username, previous, now)) {
                return;
            }

            transactions.executeWithoutResult(status ->
                    userRepository.touchLastSeen(username, LocalDateTime.now()));
        } catch (Exception e) {
            // Never let bookkeeping break a successful authentication.
            log.warn("Could not record lastSeen: {}", e.toString());
        }
    }
}
