package de.hallerweb.enterprise.prioritize.exception;

/**
 * Wird geworfen, wenn ein Steuerkommando nicht zugestellt werden kann, weil die Resource
 * über keinen erreichbaren Transport verfügt (z.B. MQTT-Resource ist offline und besitzt
 * keinen eigenen REST-Endpunkt).
 * <p>
 * Bewusst kein stiller Fehlschlag: Der Aufrufer erhält eine klare Fehlermeldung.
 * Wird im {@code GlobalExceptionHandler} auf HTTP 503 (Service Unavailable) gemappt.
 *
 * @author peter haller
 */
public class ResourceOfflineException extends RuntimeException {

    public ResourceOfflineException(String message) {
        super(message);
    }
}
