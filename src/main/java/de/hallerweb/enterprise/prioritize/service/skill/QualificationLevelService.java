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

package de.hallerweb.enterprise.prioritize.service.skill;

import de.hallerweb.enterprise.prioritize.model.cost.CostRateRules;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.QualificationLevel;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.QualificationLevelRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The qualification levels of the installation and what an hour of each is costed at.
 * <p>
 * Every method is permission-checked, reads included. A level's cost rate is the basis of every labour
 * figure the platform reports, and publishing it to anyone who can log in would hand out the wage
 * structure of the business — see {@link QualificationLevel} for why the rate hangs on the level in the
 * first place.
 *
 * @author peter haller
 */
@Service
@Transactional
@RequiredArgsConstructor
public class QualificationLevelService {

    private final QualificationLevelRepository qualificationLevelRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authService;

    /** All levels, ordered by name. Requires READ on qualification levels. */
    @Transactional(readOnly = true)
    public List<QualificationLevel> getAllQualificationLevels(PUser user) {
        requirePermission(user, Action.READ, "read qualification levels");
        return qualificationLevelRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    /** One level by id. Requires READ on qualification levels. */
    @Transactional(readOnly = true)
    public QualificationLevel getQualificationLevelById(Long levelId, PUser user) {
        requirePermission(user, Action.READ, "read qualification levels");
        return findOrThrow(levelId);
    }

    /**
     * Creates a level. The name is mandatory and unique regardless of case — two levels called
     * "Geselle" and "geselle" would split the same people into two groups and quietly halve every
     * figure reported for either.
     */
    public QualificationLevel createQualificationLevel(QualificationLevel level, PUser user) {
        requirePermission(user, Action.CREATE, "create a qualification level");

        level.setName(requireName(level.getName()));
        if (qualificationLevelRepository.existsByNameIgnoreCase(level.getName())) {
            throw new IllegalArgumentException("A qualification level named '" + level.getName() + "' already exists.");
        }
        applyCostRate(level);
        return qualificationLevelRepository.save(level);
    }

    /**
     * Replaces a level's data. This is a PUT: the request is the new state, so cost fields left out
     * clear the rate. That is the only way to stop costing a level — there is no patch semantics here
     * in which {@code null} could mean "unchanged".
     */
    public QualificationLevel updateQualificationLevel(Long levelId, QualificationLevel update, PUser user) {
        QualificationLevel existing = findOrThrow(levelId);
        if (!authService.hasPermission(user, existing, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to update this qualification level.");
        }

        String name = requireName(update.getName());
        Optional<QualificationLevel> clash = qualificationLevelRepository.findByNameIgnoreCase(name);
        if (clash.isPresent() && !clash.get().getId().equals(levelId)) {
            throw new IllegalArgumentException("A qualification level named '" + name + "' already exists.");
        }

        existing.setName(name);
        existing.setDescription(update.getDescription());
        existing.setCostRate(update.getCostRate());
        existing.setCostCurrency(update.getCostCurrency());
        existing.setCostRateUnit(update.getCostRateUnit());
        applyCostRate(existing);

        return qualificationLevelRepository.save(existing);
    }

    /**
     * Deletes a level, but only while nobody is assigned to it. Detaching people automatically would
     * silently drop the qualification of everyone who held it, and the loss would only show up later as
     * unpriced hours in a cost report — by which time nobody remembers what those people were.
     */
    public void deleteQualificationLevel(Long levelId, PUser user) {
        QualificationLevel existing = findOrThrow(levelId);
        if (!authService.hasPermission(user, existing, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete this qualification level.");
        }

        long assigned = userRepository.countByQualificationLevel(existing);
        if (assigned > 0) {
            throw new IllegalStateException("Qualification level '" + existing.getName() + "' is still assigned to "
                    + assigned + " user(s) — reassign them before deleting it.");
        }
        qualificationLevelRepository.delete(existing);
    }

    /**
     * Puts a user on a qualification level, or takes them off it when {@code levelId} is {@code null}.
     * <p>
     * Deliberately its own endpoint rather than a field on the user update: the assignment is what
     * decides how that person's hours are costed, and it is guarded by UPDATE on the level, not by
     * whoever happens to be allowed to edit a user's phone number.
     */
    public PUser assignQualificationLevel(Long userId, Long levelId, PUser currentUser) {
        PUser target = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + userId + " not found"));

        if (levelId == null) {
            requirePermission(currentUser, Action.UPDATE, "change a qualification level assignment");
            target.setQualificationLevel(null);
            return userRepository.save(target);
        }

        QualificationLevel level = findOrThrow(levelId);
        if (!authService.hasPermission(currentUser, level, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to change a qualification level assignment.");
        }
        target.setQualificationLevel(level);
        return userRepository.save(target);
    }

    // ==========================================
    // INTERNALS
    // ==========================================

    private QualificationLevel findOrThrow(Long levelId) {
        return qualificationLevelRepository.findById(levelId)
                .orElseThrow(() -> new EntityNotFoundException("Qualification level with id " + levelId + " not found"));
    }

    /**
     * Type-level permission check for the cases where there is no instance yet (create) or the action is
     * not about one particular level (list, read), the same way {@code ProjectService} gates creating a
     * project.
     */
    private void requirePermission(PUser user, Action action, String what) {
        if (!authService.hasPermission(user, QualificationLevel.class.getCanonicalName(), 0L, action)) {
            throw new AccessDeniedException("No permission to " + what + ".");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A qualification level needs a name.");
        }
        return name.trim();
    }

    /** Validates the three cost fields together and stores the currency back canonically. */
    private static void applyCostRate(QualificationLevel level) {
        String currency = CostRateRules.requireConsistent(
                level.getCostRate(), level.getCostCurrency(), level.getCostRateUnit());
        if (currency != null) {
            level.setCostCurrency(currency);
        }
    }
}
