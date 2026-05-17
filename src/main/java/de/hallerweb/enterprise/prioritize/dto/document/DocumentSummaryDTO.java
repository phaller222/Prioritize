package de.hallerweb.enterprise.prioritize.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * DTO für eine Zusammenfassung eines Dokuments, enthält ID, Name, aktuelle Version, Sperre und Benutzer, der die Sperre aufgehoben hat.
 */
public class DocumentSummaryDTO {
    private int id;
    private String name;
    private int currentVersion;
    private boolean locked;
    private String lockedBy;
}