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

package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.repository.project.ProjectRepository;
import de.hallerweb.enterprise.prioritize.repository.security.PermissionRecordRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class ProjectServiceTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private PermissionRecordRepository permissionRecordRepository;

    private PUser admin;
    private PUser outsider;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        outsider = userService.createUser(PUser.builder()
                .username("project-outsider-" + System.nanoTime())
                .name("Outsider")
                .firstname("Olga")
                .email("olga@example.com")
                .password("plaintext123")
                .admin(false)
                .build());
    }

    private Project newProject() {
        return projectService.createProject(
                new ProjectData("Apollo", "Moon project", 5,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 100),
                admin);
    }

    @Test
    @DisplayName("createProject: legt Blackboard an und macht den Ersteller zu Manager und Mitglied")
    void createProject_setsUpBlackboardAndManagerMembership() {
        Project project = newProject();

        assertNotNull(project.getId());
        assertNotNull(project.getBlackboard(), "Blackboard should be created with the project");
        assertEquals(admin.getId(), project.getManager().getId());
        assertTrue(project.getMembers().stream().anyMatch(m -> m.getId().equals(admin.getId())),
                "Creator must be a member");

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertNotNull(reloaded.getBlackboard());
        assertEquals(project.getId(), reloaded.getBlackboard().getProject().getId(),
                "Blackboard back-reference must point to the project");
    }

    @Test
    @DisplayName("getMyProjects: liefert die vom Nutzer verwalteten Projekte")
    void getMyProjects_returnsManagedProject() {
        Project project = newProject();
        assertTrue(projectService.getMyProjects(admin).stream()
                .anyMatch(p -> p.getId().equals(project.getId())));
        assertTrue(projectService.getMyProjects(outsider).stream()
                .noneMatch(p -> p.getId().equals(project.getId())));
    }

    @Test
    @DisplayName("getProject: Nicht-Mitglied erhält AccessDeniedException")
    void getProject_asNonMember_throwsAccessDenied() {
        Project project = newProject();
        assertThrows(AccessDeniedException.class,
                () -> projectService.getProject(project.getId(), outsider));
    }

    @Test
    @DisplayName("addMember: gewährt dem Mitglied Lesezugriff")
    void addMember_grantsAccess() {
        Project project = newProject();
        projectService.addMember(project.getId(), outsider.getId(), admin);
        // Now the outsider is a member and may read the project.
        assertDoesNotThrow(() -> projectService.getProject(project.getId(), outsider));
    }

    @Test
    @DisplayName("addMember: Nicht-Manager darf keine Mitglieder verwalten")
    void addMember_asNonManager_throwsAccessDenied() {
        Project project = newProject();
        assertThrows(AccessDeniedException.class,
                () -> projectService.addMember(project.getId(), admin.getId(), outsider));
    }

    // --- Manager-Übergabe ---

    @Test
    @DisplayName("transferManager: übergibt an ein Mitglied, der bisherige Manager bleibt im Team")
    void transferManager_handsOverAndKeepsPreviousAsMember() {
        Project project = newProject();
        projectService.addMember(project.getId(), outsider.getId(), admin);

        projectService.transferManager(project.getId(), outsider.getId(), admin);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertEquals(outsider.getId(), reloaded.getManager().getId());
        assertTrue(reloaded.getMembers().stream().anyMatch(m -> m.getId().equals(admin.getId())),
                "der bisherige Manager bleibt Mitglied");
        // The new manager may now do what only a manager may do.
        assertDoesNotThrow(() -> projectService.updateProject(project.getId(),
                new ProjectData("Apollo", "handed over", 5, null, null, 100), outsider));
    }

    @Test
    @DisplayName("transferManager: erst nach der Übergabe ist der Alt-Manager entfernbar")
    void transferManager_previousManagerBecomesRemovable() {
        Project project = newProject();
        projectService.addMember(project.getId(), outsider.getId(), admin);
        // While admin is still the manager, the removal guard applies.
        assertThrows(IllegalStateException.class,
                () -> projectService.removeMember(project.getId(), admin.getId(), admin));

        projectService.transferManager(project.getId(), outsider.getId(), admin);
        projectService.removeMember(project.getId(), admin.getId(), outsider);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertTrue(reloaded.getMembers().stream().noneMatch(m -> m.getId().equals(admin.getId())));
    }

    @Test
    @DisplayName("transferManager: ein Nicht-Mitglied kann nicht Manager werden")
    void transferManager_rejectsNonMember() {
        Project project = newProject();
        assertThrows(IllegalArgumentException.class,
                () -> projectService.transferManager(project.getId(), outsider.getId(), admin));
    }

    @Test
    @DisplayName("transferManager: ein gewöhnliches Mitglied darf nicht übergeben")
    void transferManager_asOrdinaryMember_throwsAccessDenied() {
        Project project = newProject();
        projectService.addMember(project.getId(), outsider.getId(), admin);

        assertThrows(AccessDeniedException.class,
                () -> projectService.transferManager(project.getId(), outsider.getId(), outsider));
    }

    @Test
    @DisplayName("transferManager: der Admin darf auch übergeben, wenn er nicht Manager ist")
    void transferManager_allowedForAdminWhoIsNotManager() {
        Project project = newProject();
        projectService.addMember(project.getId(), outsider.getId(), admin);
        projectService.transferManager(project.getId(), outsider.getId(), admin);
        // admin is now an ordinary member — the rescue path for an orphaned project.

        assertDoesNotThrow(
                () -> projectService.transferManager(project.getId(), admin.getId(), admin));
        assertEquals(admin.getId(),
                projectRepository.findById(project.getId()).orElseThrow().getManager().getId());
    }

    @Test
    @DisplayName("addMember: der Admin darf Mitglieder verwalten, ohne Manager zu sein")
    void addMember_allowedForAdminWhoIsNotManager() {
        Project project = newProject();
        projectService.addMember(project.getId(), outsider.getId(), admin);
        projectService.transferManager(project.getId(), outsider.getId(), admin);

        PUser newcomer = userService.createUser(PUser.builder()
                .username("project-newcomer-" + System.nanoTime())
                .name("Newcomer")
                .firstname("Nina")
                .email("nina@example.com")
                .password("plaintext123")
                .admin(false)
                .build());

        assertDoesNotThrow(
                () -> projectService.addMember(project.getId(), newcomer.getId(), admin));
    }

    @Test
    @DisplayName("updateProject: ändert die Felder (nur Manager)")
    void updateProject_changesFields() {
        Project project = newProject();
        projectService.updateProject(project.getId(),
                new ProjectData("Apollo 2", "Updated", 9, null, null, 200), admin);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertEquals("Apollo 2", reloaded.getName());
        assertEquals(9, reloaded.getPriority());
        assertEquals(200, reloaded.getMaxManDays());
    }

    @Test
    @DisplayName("deleteProject: entfernt das Projekt (nur Manager)")
    void deleteProject_removesProject() {
        Project project = newProject();
        projectService.deleteProject(project.getId(), admin);
        assertFalse(projectRepository.existsById(project.getId()));
    }

    @Test
    @DisplayName("createProject: ohne Create-Permission verweigert (kein Admin)")
    void createProject_deniedWithoutCreatePermission() {
        ProjectData data = new ProjectData("Apollo", "Moon project", 5, null, null, 100);
        assertThrows(AccessDeniedException.class, () -> projectService.createProject(data, outsider));
    }

    @Test
    @DisplayName("createProject: mit typ-weiter Create-Permission erlaubt (kein Admin)")
    void createProject_allowedWithTypeLevelCreatePermission() {
        // A type-level (objectId 0) CREATE permission on Project, granted as a personal permission.
        PermissionRecord perm = permissionRecordRepository.save(PermissionRecord.builder()
                .absoluteObjectType(Project.class.getCanonicalName())
                .objectId(0L)
                .createPermission(true)
                .build());
        outsider.addPersonalPermission(perm);

        Project project = projectService.createProject(
                new ProjectData("Apollo", "Moon project", 5, null, null, 100), outsider);

        assertNotNull(project.getId());
        assertEquals(outsider.getId(), project.getManager().getId());
    }
}
