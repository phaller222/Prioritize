package de.hallerweb.enterprise.prioritize.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class DocumentHistoryDTO {
    private int version;
    private String filename;
    private String modifiedBy;
    private String comment;
    private LocalDateTime modifiedAt;
}