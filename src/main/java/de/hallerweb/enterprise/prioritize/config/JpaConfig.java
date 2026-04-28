package de.hallerweb.enterprise.prioritize.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing // Dies ist der "Hauptschalter"
public class JpaConfig {
}