package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.project.Blackboard;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.PActorRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Manages {@link Task tasks} on a project's {@link Blackboard}. Authorization is delegated to
 * {@link ProjectService}: any manager or member of the owning project may work on its tasks.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class TaskService {

    /** Statuses from which assigning a task promotes it to {@link TaskStatus#ASSIGNED}. */
    private static final Set<TaskStatus> PROMOTABLE_ON_ASSIGN =
            EnumSet.of(TaskStatus.CREATED, TaskStatus.ESTIMATED, TaskStatus.OPEN);

    /** Terminal statuses from which no further transition is allowed. */
    private static final Set<TaskStatus> TERMINAL =
            EnumSet.of(TaskStatus.CLOSED, TaskStatus.CANCELLED);

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final PActorRepository actorRepository;

    /**
     * Editable task fields, decoupling the service from HTTP DTOs.
     */
    public record TaskData(String name, String description, int priority) {
    }

    /**
     * Creates a task on the given project's blackboard with status {@link TaskStatus#CREATED}.
     *
     * @param projectId the owning project's id
     * @param data      the task's initial field values
     * @param user      the requesting user (must be manager or member)
     * @return the persisted task
     */
    public Task createTask(Long projectId, TaskData data, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireMemberOrManager(project, user);

        Blackboard blackboard = project.getBlackboard();
        Task task = Task.builder()
                .name(data.name())
                .description(data.description())
                .priority(data.priority())
                .taskStatus(TaskStatus.CREATED)
                .build();
        blackboard.addTask(task);
        Task saved = taskRepository.save(task);
        log.info("Task '{}' (id={}) created in project '{}' by '{}'.",
                saved.getName(), saved.getId(), project.getName(), user.getUsername());
        return saved;
    }

    @Transactional(readOnly = true)
    public Task getTask(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        return task;
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksForProject(Long projectId, PUser user) {
        Project project = projectService.getProject(projectId, user); // performs the access check
        return taskRepository.findByBlackboard_Id(project.getBlackboard().getId());
    }

    public Task updateTask(Long taskId, TaskData data, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        task.setName(data.name());
        task.setDescription(data.description());
        task.setPriority(data.priority());
        return task;
    }

    public void deleteTask(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        Blackboard blackboard = task.getBlackboard();
        if (blackboard != null) {
            blackboard.getTasks().remove(task); // keep the in-memory board consistent
        }
        task.setBlackboard(null);
        taskRepository.delete(task);
    }

    /**
     * Assigns a task to an actor (person or machine). If the task is still in an early status
     * it is promoted to {@link TaskStatus#ASSIGNED}.
     *
     * @param taskId  the task id
     * @param actorId the id of the actor to assign
     * @param user    the requesting user
     * @return the updated task
     */
    public Task assignTask(Long taskId, Long actorId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        PActor actor = actorRepository.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor not found"));
        task.setAssignee(actor);
        if (PROMOTABLE_ON_ASSIGN.contains(task.getTaskStatus())) {
            task.setTaskStatus(TaskStatus.ASSIGNED);
        }
        return task;
    }

    public Task unassignTask(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        task.setAssignee(null);
        return task;
    }

    /**
     * Changes a task's status. Transitions out of a terminal status
     * ({@link TaskStatus#CLOSED}/{@link TaskStatus#CANCELLED}) are rejected.
     *
     * @param taskId    the task id
     * @param newStatus the target status
     * @param user      the requesting user
     * @return the updated task
     * @throws IllegalStateException if the task is already in a terminal status
     */
    public Task changeStatus(Long taskId, TaskStatus newStatus, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        if (TERMINAL.contains(task.getTaskStatus())) {
            throw new IllegalStateException(
                    "Task is in terminal status " + task.getTaskStatus() + " and cannot change.");
        }
        task.setTaskStatus(newStatus);
        return task;
    }

    private Task findOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
    }

    private Project projectOf(Task task) {
        Blackboard blackboard = task.getBlackboard();
        Project project = blackboard != null ? blackboard.getProject() : null;
        if (project == null) {
            throw new IllegalStateException("Task is not attached to a project.");
        }
        return project;
    }
}
