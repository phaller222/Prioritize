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

package de.hallerweb.enterprise.prioritize.controller.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillRecordDTO;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillRecordRequest;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;

@Tag(name = "Users", description = "Manage users and their assigned skills.")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SkillService skillService;

    // ==========================================
    // USER CRUD
    // ==========================================

    @Operation(summary = "Get all users")
    @GetMapping
    public ResponseEntity<List<PUser>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Get user by id")
    @GetMapping("/{id}")
    public ResponseEntity<PUser> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Search by username: GET /api/v1/users?username=peter
     */
    @Operation(summary = "Search a user by username")
    @GetMapping(params = "username")
    public ResponseEntity<PUser> getByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findUserByUsername(username));
    }

    @Operation(summary = "Create a user",
            description = "Creates a local user record. The password field is write-ignored (@JsonIgnore) "
                    + "and is NOT set here by design: in production Keycloak owns credentials, and a local "
                    + "record created via REST is passwordless (it exists to carry app relationships). A "
                    + "login-able local user for the Basic-auth/dev mode is created via the admin GUI, which "
                    + "has a password field. See the user-provisioning notes in the project docs.")
    @PostMapping
    public ResponseEntity<PUser> create(@RequestBody PUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user));
    }

    /**
     * PUT: Full update – replaces all fields.
     * Note: password, roles and permissions are ignored.
     * Dedicated endpoints exist for those.
     */
    @Operation(summary = "Update a user (full replace)")
    @PutMapping("/{id}")
    public ResponseEntity<PUser> update(@PathVariable Long id, @RequestBody PUser user) {
        user.setId(id);
        return ResponseEntity.ok(userService.updateUser(user));
    }

    /**
     * PATCH: Partial update – only supplied fields are overwritten.
     * The password is encrypted if supplied.
     * Roles and the admin flag are not modifiable.
     */
    @Operation(summary = "Partially update a user")
    @PatchMapping("/{id}")
    public ResponseEntity<PUser> partialUpdate(
            @PathVariable Long id,
            @RequestBody PUser patch) {
        return ResponseEntity.ok(userService.partialUpdateUser(id, patch));
    }

    @Operation(summary = "Deactivate user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // SKILL RECORDS
    // ==========================================

    @Operation(summary = "Get skills for user")
    @GetMapping("/{userId}/skills")
    public ResponseEntity<List<SkillRecordDTO>> getSkillsForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(
                skillService.getSkillsForUser(userId).stream().map(SkillRecordDTO::from).toList());
    }

    @Operation(summary = "Assign skill to user")
    @PostMapping("/{userId}/skills")
    public ResponseEntity<SkillRecordDTO> assignSkillToUser(
            @PathVariable Long userId,
            @RequestBody SkillRecordRequest request) {
        SkillRecord assigned = skillService.assignSkillToUser(userId, request.toSkillRecord());
        return ResponseEntity.status(HttpStatus.CREATED).body(SkillRecordDTO.from(assigned));
    }
}