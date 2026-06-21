package de.hallerweb.enterprise.prioritize.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * DTO for a summary of a document, contains ID, name, current version, lock and the user who released the lock.
 */
public class DocumentSummaryDTO {
    private Long id;
    private String name;
    private int currentVersion;
    private boolean locked;
    private String lockedBy;
}