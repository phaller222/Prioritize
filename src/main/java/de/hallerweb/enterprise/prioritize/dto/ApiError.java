package de.hallerweb.enterprise.prioritize.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record ApiError(
    String message,
    int status,

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp
) {
    // Factory-Methode für bequeme Erstellung
    public static ApiError of(String message, int status) {
        return new ApiError(message, status, Instant.now());
    }
}