package de.hallerweb.enterprise.prioritize.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ApiError {
    private String message;
    private int status;
    private long timestamp;
}