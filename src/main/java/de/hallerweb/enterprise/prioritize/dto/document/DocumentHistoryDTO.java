package de.hallerweb.enterprise.prioritize.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
/**
 * DTO for the history of a document, contains version, file name, modification date, modifying operation and comment.
 */
public class DocumentHistoryDTO {
    private int version;
    private String filename;
    private String modifiedBy;
    private String comment;
    private LocalDateTime modifiedAt;
}