package de.hallerweb.enterprise.prioritize.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentSummaryDTO {
    private int id;
    private String name;
    private int currentVersion;
    private boolean locked;
    private String lockedBy;
}