package de.hallerweb.enterprise.prioritize.exception;

/**
 * Thrown when a control command could be delivered to a device,
 * but the device was unreachable or responded with an error
 * (connection refused, timeout, HTTP 4xx/5xx).
 * <p>
 * Mapped to HTTP 502 (Bad Gateway) in the GlobalExceptionHandler: the backend
 * itself works, but the downstream device did not accept the control command.
 */
public class ResourceCommandFailedException extends RuntimeException {

    public ResourceCommandFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}