package de.hallerweb.enterprise.prioritize.model.project;

/**
 * Lifecycle status of a {@link de.hallerweb.enterprise.prioritize.model.project.Task}.
 * {@code CLOSED} and {@code CANCELLED} are terminal states.
 */
public enum TaskStatus {
    CREATED,
    ESTIMATED,
    OPEN,
    ASSIGNED,
    STARTED,
    STOPPED,
    FINISHED,
    CANCELLED,
    CLOSED
}
