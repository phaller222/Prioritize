package de.hallerweb.enterprise.prioritize.exception;

/**
 * Wird geworfen, wenn ein Steuerkommando an ein Gerät zwar zugestellt werden konnte,
 * das Gerät aber nicht erreichbar war oder mit einem Fehler geantwortet hat
 * (Connection refused, Timeout, HTTP 4xx/5xx).
 * <p>
 * Wird im GlobalExceptionHandler auf HTTP 502 (Bad Gateway) gemappt: Das Backend
 * selbst funktioniert, aber das nachgelagerte Gerät hat die Steuerung nicht angenommen.
 */
public class ResourceCommandFailedException extends RuntimeException {

    public ResourceCommandFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}