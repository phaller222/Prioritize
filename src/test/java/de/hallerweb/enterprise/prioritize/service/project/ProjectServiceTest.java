package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.project.ProjectRepository;
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
}
