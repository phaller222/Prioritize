package de.hallerweb.enterprise.prioritize.exception;

/**
 * Thrown when a control command is to be sent to a resource,
 * but the executing user has no active reservation
 * (and thus no assigned slot) on that resource at the current time.
 * <p>
 * A command's slot is derived exclusively from the user's active
 * reservation; without a reservation there is no valid slot and therefore
 * no deliverable command. Deliberately not a silent failure.
 * <p>
 * Mapped to HTTP 409 (Conflict) in the {@code GlobalExceptionHandler}.
 *
 * @author peter haller
 */
public class SlotNotReservedException extends RuntimeException {

    public SlotNotReservedException(String message) {
        super(message);
    }
}