package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.security.PermissionRecordRepository;
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
    private final PermissionRecordRepository permissionRepository; // Neu hinzugefügt

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            PUser admin;
            // 1. Admin User anlegen, falls noch keiner da ist
            if (userService.getAllUsers().isEmpty()) {
                admin = new PUser();
                admin.setUsername("admin");
                admin.setPassword("p@ssword");
                admin.setAdmin(true);
                admin.setGender(PUser.Gender.OTHER);
                admin = userService.createUser(admin); // Speichern und Instanz halten
                log.info("Initialer Admin-User 'admin' wurde erstellt.");
            } else {
                admin = userService.getAllUsers().get(0);
            }

            // 2. Test-Gruppe anlegen, falls keine da ist
            if (groupRepository.findAll().isEmpty()) {
                DocumentGroup testGroup = new DocumentGroup();
                testGroup.setName("Test-Dokumentengruppe");
                testGroup = groupRepository.save(testGroup);
                log.info("Initialer Dokumentengruppe '{}' wurde erstellt.", testGroup.getName());

                // 3. Berechtigungen vergeben (Damit der AuthorizationService nicht blockiert)
                // Wir geben dem Admin Vollzugriff auf die neue Gruppe
                PermissionRecord adminGroupPermission = PermissionRecord.builder()
                        .absoluteObjectType(DocumentGroup.class.getCanonicalName())
                        .objectId(testGroup.getId())
                        .createPermission(true)
                        .readPermission(true)
                        .updatePermission(true)
                        .deletePermission(true)
                        .build();

                // Speichern des Records
                permissionRepository.save(adminGroupPermission);

                // Verknüpfung zum User (da PUser ein Set<PermissionRecord> personalPermissions hat)
                admin.addPersonalPermission(adminGroupPermission);
                userService.updateUser(admin);

                log.info("Berechtigungen für Admin auf '{}' wurden gesetzt.", testGroup.getName());
            }
        };
    }
}