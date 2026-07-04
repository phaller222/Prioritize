package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class TaskServiceTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserService userService;

    private PUser admin;
    private Project project;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        project = projectService.createProject(
                new ProjectData("Gemini", "Task project", 3, null, null, 50), admin);
    }

    private Task newTask() {
        return taskService.createTask(project.getId(),
                new TaskData("Design", "Design the thing", 2), admin);
    }

    @Test
    @DisplayName("createTask: Task landet auf dem Blackboard mit Status CREATED")
    void createTask_addsToBlackboardWithStatusCreated() {
        Task task = newTask();

        assertNotNull(task.getId());
        assertEquals(TaskStatus.CREATED, task.getTaskStatus());
        assertTrue(taskRepository.findByBlackboard_Id(project.getBlackboard().getId()).stream()
                .anyMatch(t -> t.getId().equals(task.getId())));
    }

    @Test
    @DisplayName("assignTask: setzt Assignee und hebt Status auf ASSIGNED")
    void assignTask_setsAssigneeAndPromotesStatus() {
        Task task = newTask();
        Task assigned = taskService.assignTask(task.getId(), admin.getId(), admin);

        assertNotNull(assigned.getAssignee());
        assertEquals(admin.getId(), assigned.getAssignee().getId());
        assertEquals(TaskStatus.ASSIGNED, assigned.getTaskStatus());
    }

    @Test
    @DisplayName("changeStatus: Übergang aus einem Terminalzustand (CLOSED) wirft IllegalStateException")
    void changeStatus_fromTerminal_throws() {
        Task task = newTask();
        taskService.changeStatus(task.getId(), TaskStatus.CLOSED, admin);

        assertThrows(IllegalStateException.class,
                () -> taskService.changeStatus(task.getId(), TaskStatus.OPEN, admin));
    }

    @Test
    @DisplayName("getTasksForProject: liefert die Tasks des Projekts")
    void getTasksForProject_returnsTasks() {
        Task task = newTask();
        assertTrue(taskService.getTasksForProject(project.getId(), admin).stream()
                .anyMatch(t -> t.getId().equals(task.getId())));
    }

    @Test
    @DisplayName("deleteTask: entfernt den Task vom Blackboard")
    void deleteTask_removesTask() {
        Task task = newTask();
        taskService.deleteTask(task.getId(), admin);
        assertFalse(taskRepository.existsById(task.getId()));
    }
}
