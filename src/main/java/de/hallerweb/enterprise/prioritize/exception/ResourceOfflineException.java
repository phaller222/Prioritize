package de.hallerweb.enterprise.prioritize.exception;

/**
 * Thrown when a control command cannot be delivered because the resource
 * has no reachable transport (e.g. the MQTT resource is offline and has
 * no REST endpoint of its own).
 * <p>
 * Deliberately not a silent failure: the caller receives a clear error message.
 * Mapped to HTTP 503 (Service Unavailable) in the {@code GlobalExceptionHandler}.
 *
 * @author peter haller
 */
public class ResourceOfflineException extends RuntimeException {

    public ResourceOfflineException(String message) {
        super(message);
    }
}
