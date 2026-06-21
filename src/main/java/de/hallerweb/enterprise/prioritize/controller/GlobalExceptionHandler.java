package de.hallerweb.enterprise.prioritize.controller;

import de.hallerweb.enterprise.prioritize.dto.ApiError;
import de.hallerweb.enterprise.prioritize.exception.ResourceCommandFailedException;
import de.hallerweb.enterprise.prioritize.exception.ResourceOfflineException;
import de.hallerweb.enterprise.prioritize.exception.SlotNotReservedException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(ex.getMessage(), HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleForbidden(AccessDeniedException ex) {
        log.warn("Access denied for user '{}': {}",
            SecurityContextHolder.getContext().getAuthentication().getName(),
            ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("Zugriff verweigert: " + ex.getMessage(), HttpStatus.FORBIDDEN.value()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of(ex.getMessage(), HttpStatus.CONFLICT.value()));
    }

    @ExceptionHandler(SlotNotReservedException.class)
    public ResponseEntity<ApiError> handleSlotNotReserved(SlotNotReservedException ex) {
        log.warn("Slot nicht reserviert: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of(ex.getMessage(), HttpStatus.CONFLICT.value()));
    }

    @ExceptionHandler(ResourceOfflineException.class)
    public ResponseEntity<ApiError> handleResourceOffline(ResourceOfflineException ex) {
        log.warn("Resource offline: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiError.of(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @ExceptionHandler(ResourceCommandFailedException.class)
    public ResponseEntity<ApiError> handleCommandFailed(ResourceCommandFailedException ex) {
        log.warn("Command an Gerät fehlgeschlagen: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError.of(ex.getMessage(), HttpStatus.BAD_GATEWAY.value()));
    }

    //  vor Produktion einkommentieren!
    /*
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Ein interner Serverfehler ist aufgetreten.", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
    */
}