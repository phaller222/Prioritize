package de.hallerweb.enterprise.prioritize.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

//        http
//                .csrf(csrf -> csrf.disable()) // Deaktivieren für einfache REST-Tests
//                .authorizeHttpRequests(auth -> auth
//                        .anyRequest().permitAll() // Erlaubt vorerst alles ohne Login
//                );
//        return http.build();
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll() // H2 darf ohne Login
                        .anyRequest().authenticated()                  // ZWINGT Bruno zum Login!
                )
                .httpBasic(org.springframework.security.config.Customizer.withDefaults()) // Aktiviert Basic Auth
                .headers(headers -> headers.frameOptions(f -> f.disable())); // Für H2 Konsole

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}