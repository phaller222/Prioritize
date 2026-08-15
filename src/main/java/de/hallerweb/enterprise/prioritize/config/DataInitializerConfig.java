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

package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.service.InitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@RequiredArgsConstructor
public class DataInitializerConfig {

    private final InitializationService initializationService;

    /**
     * The platform's own startup data. Ordered explicitly because anything that seeds on top of it
     * has to run afterwards, and an unordered runner sorts as lowest precedence — which would put a
     * later addition first without any visible reason.
     */
    @Bean
    @Order(0)
    public CommandLineRunner initData() {
        return args -> initializationService.initData();
    }
}