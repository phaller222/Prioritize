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

package de.hallerweb.enterprise.prioritize.service.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Hangs the demo data set into startup without the platform having to know it exists.
 * <p>
 * <b>Why a separate class rather than letting {@link DemoDataService} implement the runner:</b>
 * {@code seed()} is {@code @Transactional}, and a runner calling its own {@code seed()} would be an
 * internal call — the proxy would not intercept it and the whole data set would be written without
 * a transaction. Going through the injected bean keeps the proxy in the path.
 * <p>
 * <b>Ordering:</b> the demo data builds on what {@code InitializationService} creates (the admin
 * account it acts as, above all), so it must run second. Both runners therefore carry an explicit
 * {@link Order}; an unordered bean sorts as lowest precedence, which would silently put this one
 * first.
 *
 * @author peter haller
 */
@Component
@Profile("demo")
@Order(DemoDataRunner.ORDER)
@RequiredArgsConstructor
public class DemoDataRunner implements CommandLineRunner {

    /** After the platform's own initialization — see the class comment. */
    public static final int ORDER = 100;

    private final DemoDataService demoDataService;

    @Override
    public void run(String... args) {
        demoDataService.seed();
    }
}
