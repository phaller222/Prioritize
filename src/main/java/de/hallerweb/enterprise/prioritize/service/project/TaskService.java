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

import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.project.Blackboard;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.PActorRepository;
import de.hallerweb.enterprise.prioritize.repository.nfc.NfcUnitRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final NfcUnitRepository nfcUnitRepository;
    private final ResourceRepository resourceRepository;
    private final EntityManager entityManager;

    /**
     * Editable task fields, decoupling the service from HTTP DTOs.
     */
    public record TaskData(String name, String description, int priority) {
    }

    /**
     * Aggregated time-tracking total for a task. {@code totalSeconds} sums all completed spans plus
     * every clock currently running, each counted live up to now; {@code totalText} is that as an
     * ISO-8601 duration.
     * <p>
     * {@code trackingForMe} and {@code runningSince} describe the <em>requesting</em> user's own
     * clock ({@code runningSince} is {@code null} when they are not clocked in), while
     * {@code runningCount} is how many people are on this task right now. The field was renamed
     * from {@code tracking} when clocks became per-person: keeping the old name would have left
     * every client compiling happily against a changed meaning.
     */
    public record TrackingSummary(Long taskId, boolean trackingForMe, int runningCount,
                                  long totalSeconds, String totalText, Instant runningSince) {
    }

    /**
     * One tracked work session on a task: a single start-to-stop interval. For the currently open
     * session {@code until} is {@code null}, {@code running} is {@code true} and {@code seconds} is
     * counted live up to now. Hides the underlying {@code TimeSpan} from API consumers.
     * <p>
     * {@code id} addresses the session in the correction endpoints; it is {@code null} for a running
     * session that has never been persisted as a closed span. {@code correction} is {@code null} for
     * every session that was recorded normally and never touched.
     * <p>
     * {@code userId} and {@code username} name whose session it is. Since clocks became per person a
     * task carries the sessions of everybody who worked on it, and a list of intervals that does not
     * say whose they are cannot be shown to anyone or corrected by anyone — the correction endpoints
     * already decide by participation, so the caller has to be able to see it too. Both are
     * {@code null} for a session with no participant recorded, which only legacy rows can be.
     */
    public record WorkSession(Long id, Long userId, String username, Instant from, Instant until,
                              long seconds, boolean running, Correction correction) {
    }

    /** How a work session came to differ from what the clock actually recorded. */
    public enum CorrectionKind {
        /** A closed session's bounds were changed. */
        CORRECTED,
        /** A still-running session was stopped with an earlier timestamp than "now". */
        BACKDATED_STOP,
        /** The session was never clocked at all and was entered by hand afterwards. */
        MANUAL_ENTRY
    }

    /**
     * The audit trail of a hand-edited work session: who changed it, when, why, and the bounds as
     * they were before. {@code originalUntil} is {@code null} when the session was still running at
     * the time of the fix, {@code originalFrom} is {@code null} when there was nothing to preserve
     * because the session was entered by hand.
     * <p>
     * {@code correctedBy} is the username, so the audit trail reads without a second call;
     * {@code correctedById} addresses the same person the way the rest of the API does.
     */
    public record Correction(CorrectionKind kind, Long correctedById, String correctedBy,
                             Instant correctedAt, String reason,
                             Instant originalFrom, Instant originalUntil) {
    }

    /**
     * One booking of a piece of equipment to a task: a single clocked-in-to-clocked-out interval.
     * The machine counterpart of {@link WorkSession} — {@code resourceId}/{@code resourceName} take
     * the place of the user, because nobody owns a dryer's clock.
     */
    public record EquipmentSession(Long id, Long resourceId, String resourceName, Instant from,
                                   Instant until, long seconds, boolean running,
                                   Correction correction) {
    }

    /**
     * How long one piece of equipment has been booked to a task in total, running booking included
     * and counted live up to now.
     * <p>
     * Reported per resource and deliberately never summed across resources: a lift-day plus a
     * dryer-day is not two days of anything. Equipment totals are also kept apart from
     * {@link TrackingSummary} entirely — machine hours are not work hours, and a single number
     * covering both would be meaningless in every direction.
     */
    public record EquipmentUsageSummary(Long taskId, Long resourceId, String resourceName,
                                        long totalSeconds, String totalText, boolean running,
                                        Instant runningSince) {
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

    /**
     * Creates a task from a recurring schedule's template on {@code project}'s blackboard with status
     * {@link TaskStatus#CREATED}. This is a <b>trusted system path</b> invoked by the scheduler, not a
     * user: it performs <b>no membership check</b>, mirroring the user-less ingest paths (MQTT /
     * telemetry). It stays decoupled from the scheduling model — the caller unpacks the template.
     *
     * @param project     the owning project (its blackboard receives the task)
     * @param name        the generated task's name
     * @param description the generated task's description
     * @param priority    the generated task's priority
     * @return the persisted task
     */
    public Task createScheduledTask(Project project, String name, String description, int priority) {
        Blackboard blackboard = project.getBlackboard();
        Task task = Task.builder()
                .name(name)
                .description(description)
                .priority(priority)
                .taskStatus(TaskStatus.CREATED)
                .build();
        blackboard.addTask(task);
        Task saved = taskRepository.save(task);
        log.info("Scheduled task '{}' (id={}) created in project '{}'.",
                saved.getName(), saved.getId(), project.getName());
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
        // Detach any NFC tracker tags pointing at this task first, so the blackboard's
        // orphanRemoval below doesn't schedule the task deletion while an FK still references it.
        for (NfcUnit unit : nfcUnitRepository.findByTask_Id(taskId)) {
            unit.setTask(null);
        }
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
     * Assigns the task to one of its own project's goals, so it contributes to that goal's
     * progress. The goal must belong to the task's project.
     *
     * @param taskId the task id
     * @param goalId the id of a goal in the task's project
     * @param user   the requesting user (must be manager or member)
     * @return the updated task
     * @throws NoSuchElementException if the goal is not part of the task's project
     */
    public Task assignGoal(Long taskId, Long goalId, PUser user) {
        Task task = findOrThrow(taskId);
        Project project = projectOf(task);
        projectService.requireMemberOrManager(project, user);
        ProjectGoal goal = project.getGoals().stream()
                .filter(g -> goalId.equals(g.getId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Goal not found in this task's project"));
        task.setGoal(goal);
        return task;
    }

    public Task unassignGoal(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        task.setGoal(null);
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

    // --- Time tracking ---
    // A task accumulates closed TIME_TRACKER spans in its timeSpent list; the clocks currently
    // running are held in activeTimeSpans, one per person. Tracking is therefore per user, not per
    // task: one sticker on the site container serves the whole crew, and the second person to scan
    // starts their own clock instead of stopping the first one's. This works with or without NFC —
    // an NFC TIMETRACKER tag is just one trigger for toggleTracking (see NfcUnitService).
    // Authorization is the usual project membership: whoever may work on the task may clock time.

    /**
     * Starts time tracking on a task: opens a new {@link TimeSpan.TimeSpanType#TIME_TRACKER} span
     * with the current server time and promotes the task to {@link TaskStatus#STARTED}.
     *
     * @param taskId the task id
     * @param user   the requesting user (must be manager or member); recorded on the span
     * @return the updated task
     * @throws IllegalStateException if this user already has a clock running on this task
     */
    public Task startTracking(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        // Only this user's own clock blocks a start — colleagues may be tracking the same task.
        if (task.isTrackingFor(user)) {
            throw new IllegalStateException("Time tracking is already running for you on this task.");
        }
        TimeSpan span = TimeSpan.builder()
                .title(task.getName())
                .description(task.getDescription())
                .dateFrom(Instant.now())
                .type(TimeSpan.TimeSpanType.TIME_TRACKER)
                .build();
        span.getInvolvedUsers().add(user);
        task.getActiveTimeSpans().add(span);
        task.setTaskStatus(TaskStatus.STARTED);
        // Flush so the new span gets its id right away: the correction endpoints address a session
        // by id, and a caller reading the sessions in the same transaction must not see a null one.
        // Deliberately the EntityManager, not taskRepository.saveAndFlush — that merges, which would
        // persist a *copy* of the new span and leave the instance here without an id.
        entityManager.flush();
        log.info("Time tracking started on task '{}' (id={}) by '{}'.",
                task.getName(), taskId, user.getUsername());
        return task;
    }

    /**
     * Stops time tracking on a task: closes the running span with the current server time, moves it
     * into the task's {@link Task#getTimeSpent() timeSpent} history and sets the task to
     * {@link TaskStatus#STOPPED}.
     *
     * @param taskId the task id
     * @param user   the requesting user (must be manager or member)
     * @return the updated task
     * @throws IllegalStateException if this user has no clock running on this task
     */
    public Task stopTracking(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        TimeSpan span = task.activeTimeSpanFor(user);
        if (span == null) {
            throw new IllegalStateException("No time tracking is running for you on this task.");
        }
        span.setDateUntil(Instant.now());
        task.getTimeSpent().add(span);
        task.getActiveTimeSpans().remove(span);
        stopIfLastClock(task);
        entityManager.flush(); // see startTracking: the closed span needs its id
        log.info("Time tracking stopped on task '{}' (id={}) by '{}'.",
                task.getName(), taskId, user.getUsername());
        return task;
    }

    /**
     * A task only goes STOPPED once the last clock is out — while colleagues keep working it stays
     * STARTED. Otherwise the first person to knock off would mark the whole task stopped underneath
     * the rest of the crew.
     */
    private static void stopIfLastClock(Task task) {
        if (!task.isTracking()) {
            task.setTaskStatus(TaskStatus.STOPPED);
        }
    }

    /**
     * Toggles time tracking for the calling user: stops their clock if it runs, otherwise starts
     * one. Colleagues tracking the same task are untouched. This is the entry
     * point an NFC scan of a {@link de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType#TIMETRACKER}
     * tag maps to.
     *
     * @param taskId the task id
     * @param user   the requesting user (must be manager or member)
     * @return the updated task
     */
    public Task toggleTracking(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        return task.isTrackingFor(user) ? stopTracking(taskId, user) : startTracking(taskId, user);
    }

    /**
     * Returns the total time tracked on a task: the sum of all completed spans plus, if tracking is
     * currently running, the open span counted live up to now. Manager or member.
     *
     * @param taskId the task id
     * @param user   the requesting user
     * @return the aggregated tracking total
     */
    @Transactional(readOnly = true)
    public TrackingSummary getTrackingSummary(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        long seconds = 0;
        for (TimeSpan span : task.getTimeSpent()) {
            seconds += secondsBetween(span.getDateFrom(), span.getDateUntil());
        }
        // Every open clock counts live and they add up: two people clocked in for an hour are two
        // hours of work on this task. Counting only one of them was the bug that forced per-person
        // clocks in the first place.
        Instant now = Instant.now();
        for (TimeSpan span : task.getActiveTimeSpans()) {
            seconds += secondsBetween(span.getDateFrom(), now);
        }
        TimeSpan mine = task.activeTimeSpanFor(user);
        return new TrackingSummary(task.getId(), mine != null, task.getActiveTimeSpans().size(),
                seconds, Duration.ofSeconds(seconds).toString(),
                mine != null ? mine.getDateFrom() : null);
    }

    /**
     * Returns the individual work sessions tracked on a task: each completed span, plus the open
     * session (with {@code until = null}, counted live up to now) if tracking is running. Completed
     * sessions come first, the running one last. The aggregate total is {@link #getTrackingSummary}.
     * Manager or member.
     *
     * @param taskId the task id
     * @param user   the requesting user
     * @return the tracked work sessions, empty if nothing has been tracked yet
     */
    @Transactional(readOnly = true)
    public List<WorkSession> getWorkSessions(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        List<WorkSession> sessions = new ArrayList<>();
        for (TimeSpan span : task.getTimeSpent()) {
            sessions.add(toWorkSession(span, false));
        }
        for (TimeSpan active : task.getActiveTimeSpans()) {
            sessions.add(toWorkSession(active, true));
        }
        return sessions;
    }

    /** Maps a span to its outward view; a running span is counted live up to now. */
    private static WorkSession toWorkSession(TimeSpan span, boolean running) {
        long seconds = running
                ? secondsBetween(span.getDateFrom(), Instant.now())
                : secondsBetween(span.getDateFrom(), span.getDateUntil());
        PUser owner = ownerOf(span);
        return new WorkSession(span.getId(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getUsername(),
                span.getDateFrom(),
                running ? null : span.getDateUntil(), seconds, running, correctionOf(span));
    }

    /**
     * Whose session this is. A span records exactly one participant — {@link #startTracking} and
     * {@link #addWorkSession} each add a single user, which is what makes one clock per person work —
     * so the set has at most one element and the first is the owner.
     */
    private static PUser ownerOf(TimeSpan span) {
        return span.getInvolvedUsers().stream().findFirst().orElse(null);
    }

    /** The audit view of a span, or {@code null} if it was never hand-edited. */
    private static Correction correctionOf(TimeSpan span) {
        if (!span.isCorrected()) {
            return null;
        }
        CorrectionKind kind;
        if (span.getOriginalFrom() == null) {
            kind = CorrectionKind.MANUAL_ENTRY;
        } else if (span.getOriginalUntil() == null) {
            kind = CorrectionKind.BACKDATED_STOP;
        } else {
            kind = CorrectionKind.CORRECTED;
        }
        return new Correction(kind,
                span.getCorrectedBy() == null ? null : span.getCorrectedBy().getId(),
                span.getCorrectedBy() == null ? null : span.getCorrectedBy().getUsername(),
                span.getCorrectedAt(), span.getCorrectionReason(),
                span.getOriginalFrom(), span.getOriginalUntil());
    }

    // --- Correcting tracked time ---
    // Clocking in and out is honest but not infallible: someone forgets to clock out and the clock
    // runs all night, someone never clocks in at all, someone scans the wrong tag. Without a way to
    // fix that the records are worthless after a week. Every operation below therefore leaves an
    // audit trail on the span (see TimeSpan's correction fields) — a work session may be changed,
    // but never silently.
    //
    // Who may fix what: the project manager corrects any session in the project, everyone else only
    // the sessions they took part in.
    //
    // Known limit of keeping the trail on the span itself: only the bounds as originally recorded
    // plus the LAST change survive, and deleting a session takes its trail with it (the log line is
    // what remains). A full history would need a separate audit table; deliberately not built.

    /**
     * Corrects the bounds of a completed work session, keeping the originally recorded bounds and
     * recording who changed them and why. The task's status is left alone — a correction is
     * bookkeeping, not a state transition.
     *
     * @param taskId    the task id
     * @param sessionId the work session's id (from {@link WorkSession#id()})
     * @param from      the corrected start, must lie before {@code until}
     * @param until     the corrected end, must not lie in the future
     * @param reason    why the session is being changed, required
     * @param user      the requesting user (project manager, or a participant of this session)
     * @return the corrected session
     * @throws NoSuchElementException   if no such completed session exists on the task
     * @throws IllegalStateException    if the session is still running
     * @throws IllegalArgumentException if the reason is missing or the bounds are not plausible
     */
    public WorkSession updateWorkSession(Long taskId, Long sessionId, Instant from, Instant until,
                                         String reason, PUser user) {
        Task task = findOrThrow(taskId);
        TimeSpan span = findClosedSession(task, sessionId);
        requireCorrectionRights(task, span, user);
        requireReason(reason);
        requireValidBounds(from, until);

        rememberOriginalBounds(span);
        span.setDateFrom(from);
        span.setDateUntil(until);
        markCorrected(span, user, reason);
        log.info("Work session {} on task '{}' (id={}) corrected to {} - {} by '{}': {}",
                sessionId, task.getName(), taskId, from, until, user.getUsername(), reason);
        return toWorkSession(span, false);
    }

    /**
     * Books a work session that was never clocked at all — someone forgot to scan on arrival. The
     * session is marked {@link CorrectionKind#MANUAL_ENTRY}: there is no recorded state to preserve,
     * so it is visible as hand-entered for good.
     *
     * @param taskId    the task id
     * @param from      the session's start, must lie before {@code until}
     * @param until     the session's end, must not lie in the future
     * @param reason    why the session is being added, required
     * @param forUserId the user the time is booked for; {@code null} books it for the requesting
     *                  user. Booking someone else's time is reserved for the project manager.
     * @param user      the requesting user (must be manager or member)
     * @return the newly booked session
     * @throws IllegalArgumentException if the reason is missing or the bounds are not plausible
     */
    public WorkSession addWorkSession(Long taskId, Instant from, Instant until, String reason,
                                      Long forUserId, PUser user) {
        Task task = findOrThrow(taskId);
        Project project = projectOf(task);
        projectService.requireMemberOrManager(project, user);
        requireReason(reason);
        requireValidBounds(from, until);

        PUser worker = user;
        if (forUserId != null && !forUserId.equals(user.getId())) {
            projectService.requireManager(project, user);
            worker = findUserOrThrow(forUserId);
        }
        TimeSpan span = TimeSpan.builder()
                .title(task.getName())
                .description(task.getDescription())
                .dateFrom(from)
                .dateUntil(until)
                .type(TimeSpan.TimeSpanType.TIME_TRACKER)
                .build();
        span.getInvolvedUsers().add(worker);
        markCorrected(span, user, reason); // originalFrom stays null — nothing was ever recorded
        task.getTimeSpent().add(span);
        entityManager.flush(); // so the new span has its id for the response
        log.info("Work session {} - {} added by hand on task '{}' (id={}) for '{}' by '{}': {}",
                from, until, task.getName(), taskId, worker.getUsername(), user.getUsername(), reason);
        return toWorkSession(span, false);
    }

    /**
     * Removes a completed work session — a mis-booking, for instance a scan of the wrong tag.
     *
     * @param taskId    the task id
     * @param sessionId the work session's id
     * @param user      the requesting user (project manager, or a participant of this session)
     * @throws NoSuchElementException if no such completed session exists on the task
     * @throws IllegalStateException  if the session is still running
     */
    public void deleteWorkSession(Long taskId, Long sessionId, PUser user) {
        Task task = findOrThrow(taskId);
        TimeSpan span = findClosedSession(task, sessionId);
        requireCorrectionRights(task, span, user);
        // The audit trail lives on the span, so removing one takes its trail with it. Log it loudly:
        // this line is the only trace that the session ever existed.
        log.warn("Work session {} on task '{}' (id={}) ({} - {}) deleted by '{}'.",
                sessionId, task.getName(), taskId, span.getDateFrom(), span.getDateUntil(),
                user.getUsername());
        task.getTimeSpent().remove(span); // orphanRemoval deletes the row
    }

    /**
     * Stops a running session with an earlier timestamp than now — the "forgot to clock out last
     * night, noticed this morning" case. Unlike {@link #stopTracking}, this records who shortened the
     * session and why; the start is left untouched.
     *
     * @param taskId the task id
     * @param until        when the work actually ended; after the session's start, not in the future
     * @param reason       why the session is being closed retroactively, required
     * @param targetUserId whose clock to close; {@code null} means the caller's own. Closing
     *                     someone else's is a manager job (enforced via the correction rights).
     * @param user         the requesting user (project manager, or a participant of this session)
     * @return the updated task
     * @throws IllegalStateException    if that user has no clock running on this task
     * @throws IllegalArgumentException if the reason is missing or {@code until} is not plausible
     */
    public Task stopTrackingAt(Long taskId, Instant until, String reason, Long targetUserId, PUser user) {
        Task task = findOrThrow(taskId);
        // With one clock per person there is no longer "the" running span: say whose. Defaulting to
        // the caller keeps the common case simple, while a manager can still close the clock a crew
        // member left running overnight — which used to work only because there was ever just one.
        PUser owner = targetUserId != null ? findUserOrThrow(targetUserId) : user;
        TimeSpan span = task.activeTimeSpanFor(owner);
        if (span == null) {
            throw new IllegalStateException(targetUserId != null
                    ? "No time tracking is running for that user on this task."
                    : "No time tracking is running for you on this task.");
        }
        requireCorrectionRights(task, span, user);
        requireReason(reason);
        if (until == null) {
            throw new IllegalArgumentException("until is required.");
        }
        if (until.isAfter(Instant.now())) {
            throw new IllegalArgumentException("A work session cannot end in the future.");
        }
        if (until.isBefore(span.getDateFrom())) {
            throw new IllegalArgumentException("A work session cannot end before it started.");
        }

        rememberOriginalBounds(span); // originalUntil stays null — the session was still open
        span.setDateUntil(until);
        markCorrected(span, user, reason);
        task.getTimeSpent().add(span);
        task.getActiveTimeSpans().remove(span);
        stopIfLastClock(task);
        log.info("Time tracking on task '{}' (id={}) stopped retroactively at {} by '{}': {}",
                task.getName(), taskId, until, user.getUsername(), reason);
        return task;
    }

    // --- Equipment usage ---
    // The same clock, for machines instead of people: a sticker on the lift books the lift onto the
    // job it is working, so "how long was that device out" stops being a question for the memory of
    // whoever fetched it. Deliberately kept in its own pair of collections on the task rather than
    // as another TimeSpanType inside the work-time lists — machine hours and work hours must never
    // land in one sum, and separate storage makes the wrong number impossible instead of merely
    // discouraged. A task therefore also goes STOPPED while equipment keeps running: the crew went
    // home, the dryer did not.
    //
    // Authorization is project membership, as for work time. Booking a device onto a job is a
    // statement about the job, not a change to the device's master data, so it does not require
    // rights on the resource itself — the same reason an NFC scan works for whoever holds the phone.

    /**
     * Clocks a piece of equipment onto a task, opening an
     * {@link TimeSpan.TimeSpanType#EQUIPMENT_USAGE} span at the current server time.
     * <p>
     * Refuses if the device is already clocked in anywhere — including on another task. A device is
     * in one place at a time, so a second start means someone forgot to clock it out at the last
     * job; silently moving it would turn that mistake into plausible-looking data.
     *
     * @param taskId     the task id
     * @param resourceId the equipment to clock in
     * @param user       the requesting user (must be manager or member)
     * @return the updated task
     * @throws IllegalStateException if this device already has a clock running, here or elsewhere
     */
    public Task startEquipmentUsage(Long taskId, Long resourceId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        Resource resource = findResourceOrThrow(resourceId);
        requireEquipmentFree(task, resource);

        TimeSpan span = TimeSpan.builder()
                .title(resource.getName())
                .description(task.getName())
                .dateFrom(Instant.now())
                .type(TimeSpan.TimeSpanType.EQUIPMENT_USAGE)
                .build();
        span.getInvolvedResources().add(resource);
        task.getActiveEquipmentSpans().add(span);
        // Flush for the same reason as startTracking: the correction endpoints address a booking by
        // id, and a caller reading the bookings in the same transaction must not see a null one.
        entityManager.flush();
        log.info("Equipment '{}' (id={}) clocked in on task '{}' (id={}) by '{}'.",
                resource.getName(), resourceId, task.getName(), taskId, user.getUsername());
        return task;
    }

    /**
     * Clocks a piece of equipment off a task, closing its open booking at the current server time
     * and moving it into the task's history.
     *
     * @param taskId     the task id
     * @param resourceId the equipment to clock out
     * @param user       the requesting user (must be manager or member)
     * @return the updated task
     * @throws IllegalStateException if this device is not clocked in on this task
     */
    public Task stopEquipmentUsage(Long taskId, Long resourceId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        Resource resource = findResourceOrThrow(resourceId);
        TimeSpan span = requireRunningEquipment(task, resource);

        span.setDateUntil(Instant.now());
        task.getEquipmentUsage().add(span);
        task.getActiveEquipmentSpans().remove(span);
        entityManager.flush(); // see startEquipmentUsage: the closed booking needs its id
        log.info("Equipment '{}' (id={}) clocked out on task '{}' (id={}) by '{}'.",
                resource.getName(), resourceId, task.getName(), taskId, user.getUsername());
        return task;
    }

    /**
     * Toggles a piece of equipment on a task: clocks it out if it runs here, otherwise clocks it in.
     * This is the entry point an NFC scan of an
     * {@link de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType#EQUIPMENT} tag maps to
     * — one sticker on the device, scanned when it arrives and again when it leaves.
     *
     * @param taskId     the task id
     * @param resourceId the equipment to toggle
     * @param user       the requesting user (must be manager or member)
     * @return the updated task
     */
    public Task toggleEquipmentUsage(Long taskId, Long resourceId, PUser user) {
        Task task = findOrThrow(taskId);
        Resource resource = findResourceOrThrow(resourceId);
        return task.isEquipmentRunningFor(resource)
                ? stopEquipmentUsage(taskId, resourceId, user)
                : startEquipmentUsage(taskId, resourceId, user);
    }

    /**
     * Clocks a piece of equipment off with an earlier timestamp than now, recording who shortened
     * the booking and why. The device case of "forgot to clock out", which is the normal case rather
     * than the exception: nobody walks back to the lift at knock-off time to scan it.
     * <p>
     * Unlike a work session, this needs no ownership check beyond project membership — a device has
     * no session of its own to protect, and whoever notices the running clock is rarely the manager.
     * The audit trail still records who did it.
     *
     * @param taskId     the task id
     * @param resourceId the equipment to clock out
     * @param until      when the device actually stopped; after the booking's start, not in the future
     * @param reason     why the booking is being closed retroactively, required
     * @param user       the requesting user (must be manager or member)
     * @return the updated task
     * @throws IllegalStateException    if this device is not clocked in on this task
     * @throws IllegalArgumentException if the reason is missing or {@code until} is not plausible
     */
    public Task stopEquipmentUsageAt(Long taskId, Long resourceId, Instant until, String reason,
                                     PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        Resource resource = findResourceOrThrow(resourceId);
        TimeSpan span = requireRunningEquipment(task, resource);
        requireReason(reason);
        if (until == null) {
            throw new IllegalArgumentException("until is required.");
        }
        if (until.isAfter(Instant.now())) {
            throw new IllegalArgumentException("An equipment booking cannot end in the future.");
        }
        if (until.isBefore(span.getDateFrom())) {
            throw new IllegalArgumentException("An equipment booking cannot end before it started.");
        }

        rememberOriginalBounds(span); // originalUntil stays null — the booking was still open
        span.setDateUntil(until);
        markCorrected(span, user, reason);
        task.getEquipmentUsage().add(span);
        task.getActiveEquipmentSpans().remove(span);
        log.info("Equipment '{}' (id={}) on task '{}' (id={}) clocked out retroactively at {} by '{}': {}",
                resource.getName(), resourceId, task.getName(), taskId, until, user.getUsername(), reason);
        return task;
    }

    /**
     * Returns how long each piece of equipment has been booked to this task, one entry per resource
     * that was ever clocked in, running bookings counted live up to now. Manager or member.
     *
     * @param taskId the task id
     * @param user   the requesting user
     * @return one summary per resource, empty if no equipment was ever booked
     */
    @Transactional(readOnly = true)
    public List<EquipmentUsageSummary> getEquipmentUsage(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        Instant now = Instant.now();
        // LinkedHashMap: a device that was out three times appears once, and the order stays the
        // order in which the devices first turn up in the history.
        Map<Long, EquipmentUsageSummary> byResource = new LinkedHashMap<>();
        for (TimeSpan span : task.getEquipmentUsage()) {
            accumulate(byResource, task, span, secondsBetween(span.getDateFrom(), span.getDateUntil()),
                    null);
        }
        for (TimeSpan span : task.getActiveEquipmentSpans()) {
            accumulate(byResource, task, span, secondsBetween(span.getDateFrom(), now),
                    span.getDateFrom());
        }
        return new ArrayList<>(byResource.values());
    }

    /** Folds one booking into its resource's running total. */
    private static void accumulate(Map<Long, EquipmentUsageSummary> byResource, Task task,
                                   TimeSpan span, long seconds, Instant runningSince) {
        Resource resource = subjectOf(span);
        if (resource == null) {
            return; // a booking without a device says nothing; only broken data can produce one
        }
        EquipmentUsageSummary sofar = byResource.get(resource.getId());
        long total = (sofar == null ? 0 : sofar.totalSeconds()) + seconds;
        boolean running = runningSince != null || (sofar != null && sofar.running());
        Instant since = runningSince != null ? runningSince : (sofar == null ? null : sofar.runningSince());
        byResource.put(resource.getId(), new EquipmentUsageSummary(task.getId(), resource.getId(),
                resource.getName(), total, Duration.ofSeconds(total).toString(), running, since));
    }

    /**
     * Returns the individual equipment bookings on a task: each completed one, plus any open booking
     * (with {@code until = null}, counted live up to now). Completed bookings come first. The
     * per-device totals are {@link #getEquipmentUsage}. Manager or member.
     *
     * @param taskId the task id
     * @param user   the requesting user
     * @return the equipment bookings, empty if no equipment was ever booked
     */
    @Transactional(readOnly = true)
    public List<EquipmentSession> getEquipmentSessions(Long taskId, PUser user) {
        Task task = findOrThrow(taskId);
        projectService.requireMemberOrManager(projectOf(task), user);
        List<EquipmentSession> sessions = new ArrayList<>();
        for (TimeSpan span : task.getEquipmentUsage()) {
            sessions.add(toEquipmentSession(span, false));
        }
        for (TimeSpan span : task.getActiveEquipmentSpans()) {
            sessions.add(toEquipmentSession(span, true));
        }
        return sessions;
    }

    /** Maps a booking to its outward view; an open booking is counted live up to now. */
    private static EquipmentSession toEquipmentSession(TimeSpan span, boolean running) {
        long seconds = running
                ? secondsBetween(span.getDateFrom(), Instant.now())
                : secondsBetween(span.getDateFrom(), span.getDateUntil());
        Resource resource = subjectOf(span);
        return new EquipmentSession(span.getId(),
                resource == null ? null : resource.getId(),
                resource == null ? null : resource.getName(),
                span.getDateFrom(),
                running ? null : span.getDateUntil(), seconds, running, correctionOf(span));
    }

    /**
     * Which device a booking is about. An equipment span records exactly one resource — the
     * counterpart to {@code ownerOf} for work sessions — so the first is the subject.
     */
    private static Resource subjectOf(TimeSpan span) {
        return span.getInvolvedResources().stream().findFirst().orElse(null);
    }

    /**
     * Refuses to start a device that is already running. Names the other task when the clock is
     * elsewhere, because that is the actionable half of the message: someone has to go and close it.
     */
    private void requireEquipmentFree(Task task, Resource resource) {
        if (task.isEquipmentRunningFor(resource)) {
            throw new IllegalStateException(
                    "'" + resource.getName() + "' is already clocked in on this task.");
        }
        List<Task> elsewhere = taskRepository.findByEquipmentRunning(resource.getId());
        if (!elsewhere.isEmpty()) {
            Task other = elsewhere.getFirst();
            throw new IllegalStateException("'" + resource.getName() + "' is still clocked in on task '"
                    + other.getName() + "' (id=" + other.getId()
                    + "). Clock it out there first — a device cannot be in two places at once.");
        }
    }

    /** The device's open booking on this task, or a clear failure saying it has none. */
    private static TimeSpan requireRunningEquipment(Task task, Resource resource) {
        TimeSpan span = task.activeEquipmentSpanFor(resource);
        if (span == null) {
            throw new IllegalStateException(
                    "'" + resource.getName() + "' is not clocked in on this task.");
        }
        return span;
    }

    private Resource findResourceOrThrow(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));
    }

    /**
     * The project manager may correct any session in the project; everyone else only the sessions
     * they took part in.
     */
    private void requireCorrectionRights(Task task, TimeSpan span, PUser user) {
        Project project = projectOf(task);
        projectService.requireMemberOrManager(project, user);
        boolean ownSession = span.getInvolvedUsers().stream()
                .anyMatch(u -> u.getId().equals(user.getId()));
        if (!ownSession) {
            projectService.requireManager(project, user);
        }
    }

    /** Looks up a completed session on the task; the running one is not correctable in place. */
    private static TimeSpan findClosedSession(Task task, Long sessionId) {
        boolean stillRunning = task.getActiveTimeSpans().stream()
                .anyMatch(span -> span.getId() != null && span.getId().equals(sessionId));
        if (stillRunning) {
            throw new IllegalStateException(
                    "This work session is still running — stop it, retroactively if needed, before correcting it.");
        }
        return task.getTimeSpent().stream()
                .filter(span -> span.getId() != null && span.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Work session not found on this task."));
    }

    /** Preserves the bounds as first recorded; a second correction must not overwrite them. */
    private static void rememberOriginalBounds(TimeSpan span) {
        if (!span.isCorrected()) {
            span.setOriginalFrom(span.getDateFrom());
            span.setOriginalUntil(span.getDateUntil());
        }
    }

    /** Stamps who changed the span, when and why. */
    private static void markCorrected(TimeSpan span, PUser user, String reason) {
        span.setCorrectedBy(user);
        span.setCorrectedAt(Instant.now());
        span.setCorrectionReason(reason);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A correction needs a reason.");
        }
    }

    private static void requireValidBounds(Instant from, Instant until) {
        if (from == null || until == null) {
            throw new IllegalArgumentException("from and until are required.");
        }
        if (!from.isBefore(until)) {
            throw new IllegalArgumentException("A work session must start before it ends.");
        }
        if (until.isAfter(Instant.now())) {
            throw new IllegalArgumentException("A work session cannot end in the future.");
        }
    }

    private PUser findUserOrThrow(Long userId) {
        PActor actor = actorRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!(actor instanceof PUser puser)) {
            throw new IllegalArgumentException("Time can only be booked for a user.");
        }
        return puser;
    }

    /** Non-negative seconds between two instants; 0 if either bound is missing. */
    private static long secondsBetween(Instant from, Instant until) {
        if (from == null || until == null) {
            return 0;
        }
        return Math.max(Duration.between(from, until).getSeconds(), 0);
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
