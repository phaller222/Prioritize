package de.hallerweb.enterprise.prioritize.controller.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SkillService skillService;

    // ==========================================
    // USER CRUD
    // ==========================================

    @GetMapping
    public ResponseEntity<List<PUser>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PUser> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Suche nach Username: GET /api/v1/users?username=peter
     */
    @GetMapping(params = "username")
    public ResponseEntity<PUser> getByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findUserByUsername(username));
    }

    @PostMapping
    public ResponseEntity<PUser> create(@RequestBody PUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user));
    }

    /**
     * PUT: Vollständiges Update – ersetzt alle Felder.
     * Achtung: Passwort, Rollen und Berechtigungen werden ignoriert.
     * Dafür gibt es dedizierte Endpoints.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PUser> update(@PathVariable Long id, @RequestBody PUser user) {
        user.setId(id);
        return ResponseEntity.ok(userService.updateUser(user));
    }

    /**
     * PATCH: Teilupdate – nur mitgeschickte Felder werden überschrieben.
     * Passwort wird verschlüsselt falls mitgeschickt.
     * Rollen und admin-Flag sind nicht änderbar.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<PUser> partialUpdate(
            @PathVariable Long id,
            @RequestBody PUser patch) {
        return ResponseEntity.ok(userService.partialUpdateUser(id, patch));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // SKILL RECORDS
    // ==========================================

    @GetMapping("/{userId}/skills")
    public ResponseEntity<Set<SkillRecord>> getSkillsForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(skillService.getSkillsForUser(userId));
    }

    @PostMapping("/{userId}/skills")
    public ResponseEntity<SkillRecord> assignSkillToUser(
            @PathVariable Long userId,
            @RequestBody SkillRecord record) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(skillService.assignSkillToUser(userId, record));
    }
}