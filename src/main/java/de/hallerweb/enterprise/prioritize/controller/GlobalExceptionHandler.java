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

package de.hallerweb.enterprise.prioritize.controller;

import de.hallerweb.enterprise.prioritize.dto.ApiError;
import de.hallerweb.enterprise.prioritize.exception.ResourceCommandFailedException;
import de.hallerweb.enterprise.prioritize.exception.ResourceOfflineException;
import de.hallerweb.enterprise.prioritize.exception.SlotNotReservedException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
            .body(ApiError.of("Access denied: " + ex.getMessage(), HttpStatus.FORBIDDEN.value()));
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

    /**
     * A rejected constraint (a foreign key still pointing at the row being deleted, a unique violation)
     * is a conflict with the current data — not a permission problem. Without this mapping such a
     * failure escapes unhandled and Spring Security's translation filter turns it into a misleading
     * <b>403</b>, which is what made the "cannot delete a project that still has task schedules" case so
     * hard to read. The database message is not echoed back; it belongs in the log, not in the response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("The request conflicts with existing data (a related record still refers to it).",
                HttpStatus.CONFLICT.value()));
    }

    /**
     * A body that cannot be deserialized is a client mistake, so it must read as <b>400</b>. Without
     * this mapping Spring's default resolver handles it outside the advice, the error dispatch is
     * rejected by the security chain, and the caller sees a <b>403</b> instead — which has now bitten
     * twice in practice: omitting {@code maxManDays} when creating a project, and omitting
     * {@code active} when creating a user. Both are the same root cause, a missing JSON value for a
     * primitive field.
     * <p>
     * The parser's own message is not echoed back: it names internal classes and fields. It goes to
     * the log, where it is exactly what one needs while debugging a request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("The request body could not be read. Check for malformed JSON, or a "
                + "missing/null value for a field that cannot be empty.",
                HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * A lookup that must yield a single row found several. That is not the caller's fault and not a
     * permission problem — it is inconsistent stored data, so it answers <b>500</b> and is logged at
     * error level with the query's own message.
     * <p>
     * The realistic case is a database written before usernames became unique: two accounts share a
     * name, and every lookup by that name — including the login path — fails from then on. New
     * duplicates are rejected in {@code UserService}, but existing rows have to be cleaned up by hand,
     * and until then this at least says so instead of showing a 403.
     */
    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<ApiError> handleAmbiguousData(IncorrectResultSizeDataAccessException ex) {
        log.error("Ambiguous data — a lookup that must be unique returned several rows: {}",
            ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("Stored data is ambiguous: a lookup that must return a single record "
                + "found several. This needs an administrator to resolve.",
                HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @ExceptionHandler(SlotNotReservedException.class)
    public ResponseEntity<ApiError> handleSlotNotReserved(SlotNotReservedException ex) {
        log.warn("Slot not reserved: {}", ex.getMessage());
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
        log.warn("Command to device failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError.of(ex.getMessage(), HttpStatus.BAD_GATEWAY.value()));
    }

    //  vor Produktion einkommentieren!
    /*
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("An internal server error occurred.", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
    */
}