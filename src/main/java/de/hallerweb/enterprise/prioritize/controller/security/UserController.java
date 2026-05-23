package de.hallerweb.enterprise.prioritize.controller.security;

import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final SkillService skillService;

    // ==========================================
    // SKILL RECORDS - USER ZUORDNUNG
    // ==========================================

    @GetMapping("/{userId}/skills")
    public ResponseEntity<Set<SkillRecord>> getSkillsForUser(@PathVariable int userId) {
        return ResponseEntity.ok(skillService.getSkillsForUser(userId));
    }

    @PostMapping("/{userId}/skills")
    public ResponseEntity<SkillRecord> assignSkillToUser(
            @PathVariable int userId,
            @RequestBody SkillRecord record) {
        SkillRecord assignedRecord = skillService.assignSkillToUser(userId, record);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignedRecord);
    }
}