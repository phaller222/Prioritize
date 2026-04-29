package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializerConfig {

    private final UserService userService;
    private final DocumentGroupRepository groupRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            // 1. Admin User anlegen, falls noch keiner da ist
            if (userService.getAllUsers().isEmpty()) {
                PUser admin = new PUser();
                admin.setUsername("admin");
                admin.setPassword("p@ssword"); // Achte darauf, ob dein UserService das Passwort encodet!
                admin.setAdmin(true);
                userService.createUser(admin);
                log.info("Initialer Admin-User 'admin' wurde erstellt.");
            }

            // 2. Test-Gruppe anlegen, falls keine da ist
            if (groupRepository.findAll().isEmpty()) {
                DocumentGroup testGroup = new DocumentGroup();
                testGroup.setName("Test-Dokumentengruppe");
                groupRepository.save(testGroup);
                log.info("Initialer Dokumentengruppe 'Test-Dokumentengruppe' wurde erstellt.");
            }



        };
    }
}