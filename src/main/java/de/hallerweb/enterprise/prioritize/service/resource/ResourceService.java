package de.hallerweb.enterprise.prioritize.service.resource;

import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceReservationRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceGroupRepository groupRepository;
    private final ResourceReservationRepository reservationRepository;
    private final AuthorizationService authService; // Dein zentraler Wächter

    // --- Resource Group Management ---

    public ResourceGroup createResourceGroup(String name, Department dept, PUser user) {
        // Hier prüfen wir, ob der User im Department überhaupt Gruppen anlegen darf
        // Falls das Department selbst ein PAuthorizedObject ist:
        if (!authService.hasPermission(user, dept, Action.CREATE)) {
            throw new AccessDeniedException("Keine Berechtigung, Gruppen in diesem Department anzulegen.");
        }

        ResourceGroup group = ResourceGroup.builder()
                .name(name)
                .department(dept)
                .build();
        return groupRepository.save(group);
    }


    // --- Resource Management ---

    public Resource createResource(Resource resource, int groupId, PUser user) {
        ResourceGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Zielgruppe nicht gefunden"));

        // Darf der User Ressourcen in dieser Gruppe erstellen?
        if (!authService.hasPermission(user, group, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung, Ressourcen zu dieser Gruppe hinzuzufügen.");
        }

        resource.setResourceGroup(group);
        resource.setDepartment(group.getDepartment());
        return resourceRepository.save(resource);
    }

    public Resource getResource(int resourceId, PUser user) {
        Resource res = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Ressource nicht gefunden"));

        if (!authService.hasPermission(user, res, Action.READ)) {
            throw new AccessDeniedException("Keine Leseberechtigung für diese Ressource.");
        }

        Instant now = Instant.now();
        long occupied = reservationRepository.findOverlappingReservations(resourceId, now, now.plusMillis(1))
                .size();
        res.setCurrentOccupiedSlots((int) occupied);
        return res;
    }

    // --- Reservierungs-Logik ---

    public ResourceReservation reserveResource(int resourceId, PUser user, Instant from, Instant until) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Ressource nicht gefunden"));

        // 1. Rechte prüfen
        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("Keine Berechtigung für diese Ressource.");
        }

        // 2. Überschneidungen für den Zeitraum finden
        List<ResourceReservation> overlaps = reservationRepository
                .findOverlappingReservations(resourceId, from, until);

        // 3. Freien Slot berechnen
        int assignedSlot = findFirstAvailableSlot(resource.getMaxSlots(), overlaps);
        if (assignedSlot == -1) {
            throw new IllegalStateException("Alle Slots (" + resource.getMaxSlots() + ") belegt.");
        }

        // 4. Reservierung inkl. TimeSpan speichern
        TimeSpan ts = TimeSpan.builder()
                .dateFrom(from)
                .dateUntil(until)
                .type(TimeSpan.TimeSpanType.RESOURCE_RESERVATION)
                .build();

        ResourceReservation reservation = ResourceReservation.builder()
                .resource(resource)
                .reservedBy(user)
                .timespan(ts)
                .slotNumber(assignedSlot)
                .build();

        return reservationRepository.save(reservation);
    }

    /**
     * Findet die erste freie Slot-Nummer zwischen 1 und maxSlots.
     *
     * @return freier Slot oder -1 wenn alles belegt
     */
    private int findFirstAvailableSlot(int maxSlots, List<ResourceReservation> overlaps) {
        Set<Integer> occupied = overlaps.stream()
                .map(ResourceReservation::getSlotNumber)
                .collect(Collectors.toSet());

        for (int i = 1; i <= maxSlots; i++) {
            if (!occupied.contains(i)) return i;
        }
        return -1;
    }


    @Transactional(readOnly = true)
    public boolean isResourceAvailable(int resourceId, Instant from, Instant until) {
        Resource resource = resourceRepository.findById(resourceId).orElseThrow();
        List<ResourceReservation> overlaps = reservationRepository.findOverlappingReservations(resourceId, from, until);
        return overlaps.size() < resource.getMaxSlots();
    }

    /**
     * Löscht eine Ressourcengruppe.
     * * @param groupId ID der zu löschenden Gruppe
     *
     * @param user Der ausführende Benutzer (für die Rechteprüfung)
     */
    public void deleteResourceGroup(int groupId, PUser user) {
        ResourceGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Ressourcengruppe nicht gefunden."));

        // 1. Schutz der Default-Gruppe (System-Invariant)
        if (ResourceGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalStateException("Die Default-Gruppe kann nicht gelöscht werden.");
        }

        // 2. Rechteprüfung
        if (!authService.hasPermission(user, group, Action.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Löschen dieser Gruppe.");
        }

        // 3. Löschen
        // Hinweis: Falls Ressourcen in der Gruppe sind, entscheidet das Cascade-Label im Model,
        // ob diese mitgelöscht werden oder das Löschen verhindert wird.
        groupRepository.delete(group);
        log.info("Ressourcengruppe '{}' (ID: {}) wurde von User '{}' gelöscht.",
                group.getName(), groupId, user.getUsername());
    }

    /**
     * Löscht eine einzelne Ressource und alle damit verbundenen Reservierungen.
     * * @param resourceId ID der Ressource
     *
     * @param user Der ausführende Benutzer
     */
    public void deleteResource(int resourceId, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Ressource nicht gefunden."));

        // 1. Rechteprüfung
        if (!authService.hasPermission(user, resource, Action.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Löschen dieser Ressource.");
        }

        // 2. Optional: Check auf aktive Reservierungen
        // Hier könntest du entscheiden, ob Löschen verboten ist, wenn noch jemand die Ressource nutzt.
        List<ResourceReservation> activeReservations = reservationRepository.findOverlappingReservations(
                resourceId, Instant.now(), Instant.now().plus(Duration.ofDays(365)));

        if (!activeReservations.isEmpty()) {
            log.warn("Löschen der Ressource {} trotz {} zukünftiger Reservierungen.", resourceId, activeReservations.size());
            // Entweder Exception werfen oder (wie hier geplant) durch Cascade mitlöschen.
        }

        // 3. Löschen
        resourceRepository.delete(resource);
    }

}