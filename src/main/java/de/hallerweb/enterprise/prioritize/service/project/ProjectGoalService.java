package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoalProperty;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.project.ProjectGoalRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Manages the {@link ProjectGoal goals} of a project and derives progress from them. Goals are
 * project-scoped: every operation goes through the owning project so that the membership-based
 * authorization of {@link ProjectService} applies (manager may modify, members may read).
 * <p>
 * Progress is never stored. A goal's completion is the share of its <em>counting</em> tasks
 * (all tasks assigned to the goal except {@link TaskStatus#CANCELLED} ones) that have reached a
 * done status ({@link TaskStatus#FINISHED} or {@link TaskStatus#CLOSED}). A goal without counting
 * tasks — and a project without any such goal — has no defined progress ({@code null}).
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class ProjectGoalService {

    /** Task statuses that count as "done" when computing goal completion. */
    private static final Set<TaskStatus> DONE = EnumSet.of(TaskStatus.FINISHED, TaskStatus.CLOSED);

    /** Task statuses ignored entirely — neither pending nor achieved. */
    private static final Set<TaskStatus> IGNORED = EnumSet.of(TaskStatus.CANCELLED);

    private final ProjectGoalRepository goalRepository;
    private final ProjectService projectService;
    private final TaskRepository taskRepository;

    /**
     * Editable goal fields, decoupling the service from HTTP DTOs. The properties travel with the
     * goal (created/replaced together with it).
     */
    public record GoalData(String name, String description, List<ProjectGoalProperty> properties) {
    }

    /** Completion of a single goal; {@code percentage} is {@code null} without counting tasks. */
    public record GoalProgress(Long goalId, String name, Integer percentage) {
    }

    /** Overall project progress; {@code overallPercentage} is {@code null} when undefined. */
    public record ProjectProgress(Integer overallPercentage, List<GoalProgress> goals) {
    }

    /**
     * Creates a goal on the given project. Manager only.
     *
     * @param projectId the owning project's id
     * @param data      the goal's initial fields (with optional properties)
     * @param user      the requesting user
     * @return the persisted goal
     */
    public ProjectGoal createGoal(Long projectId, GoalData data, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireManager(project, user);

        ProjectGoal goal = ProjectGoal.builder()
                .name(data.name())
                .description(data.description())
                .build();
        if (data.properties() != null) {
            goal.getProperties().addAll(data.properties());
        }
        project.getGoals().add(goal);
        ProjectGoal saved = goalRepository.save(goal);
        log.info("Goal '{}' (id={}) created in project '{}' by '{}'.",
                saved.getName(), saved.getId(), project.getName(), user.getUsername());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProjectGoal> getGoals(Long projectId, PUser user) {
        Project project = projectService.getProject(projectId, user); // performs the access check
        return new ArrayList<>(project.getGoals());
    }

    @Transactional(readOnly = true)
    public ProjectGoal getGoal(Long projectId, Long goalId, PUser user) {
        Project project = projectService.getProject(projectId, user); // performs the access check
        return findGoalOrThrow(project, goalId);
    }

    /**
     * Updates a goal's fields and replaces its properties. Manager only.
     */
    public ProjectGoal updateGoal(Long projectId, Long goalId, GoalData data, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireManager(project, user);
        ProjectGoal goal = findGoalOrThrow(project, goalId);
        goal.setName(data.name());
        goal.setDescription(data.description());
        goal.getProperties().clear(); // orphanRemoval deletes the previous properties
        if (data.properties() != null) {
            goal.getProperties().addAll(data.properties());
        }
        return goal;
    }

    /**
     * Deletes a goal. Any tasks pointing at it are detached first (their goal is cleared), so a
     * deletion never orphans a foreign key. Manager only.
     */
    public void deleteGoal(Long projectId, Long goalId, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireManager(project, user);
        ProjectGoal goal = findGoalOrThrow(project, goalId);

        for (Task task : taskRepository.findByGoal_Id(goalId)) {
            task.setGoal(null);
        }
        project.getGoals().remove(goal); // orphanRemoval deletes the goal (and its properties)
        log.info("Goal '{}' (id={}) deleted from project '{}' by '{}'.",
                goal.getName(), goalId, project.getName(), user.getUsername());
    }

    /**
     * Computes the progress of a project from its goals and their tasks. Manager or member.
     *
     * @param projectId the project id
     * @param user      the requesting user
     * @return per-goal and overall completion; percentages are {@code null} where undefined
     */
    @Transactional(readOnly = true)
    public ProjectProgress computeProgress(Long projectId, PUser user) {
        Project project = projectService.getProject(projectId, user); // performs the access check
        List<Task> tasks = taskRepository.findByBlackboard_Id(project.getBlackboard().getId());

        List<GoalProgress> goalProgresses = new ArrayList<>();
        List<Integer> defined = new ArrayList<>();
        for (ProjectGoal goal : project.getGoals()) {
            List<Task> counting = tasks.stream()
                    .filter(t -> t.getGoal() != null && goal.getId().equals(t.getGoal().getId()))
                    .filter(t -> !IGNORED.contains(t.getTaskStatus()))
                    .toList();
            Integer percentage = null;
            if (!counting.isEmpty()) {
                long done = counting.stream().filter(t -> DONE.contains(t.getTaskStatus())).count();
                percentage = (int) Math.round(done * 100.0 / counting.size());
                defined.add(percentage);
            }
            goalProgresses.add(new GoalProgress(goal.getId(), goal.getName(), percentage));
        }
        Integer overall = defined.isEmpty() ? null
                : (int) Math.round(defined.stream().mapToInt(Integer::intValue).average().orElse(0));
        return new ProjectProgress(overall, goalProgresses);
    }

    private ProjectGoal findGoalOrThrow(Project project, Long goalId) {
        return project.getGoals().stream()
                .filter(g -> goalId.equals(g.getId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Goal not found in this project"));
    }
}
