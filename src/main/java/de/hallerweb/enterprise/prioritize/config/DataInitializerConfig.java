package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.service.InitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataInitializerConfig {

    private final InitializationService initializationService;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            initializationService.initData();
        };
    }
}