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

package de.hallerweb.enterprise.prioritize.service.resource;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.NameValueEntry;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class ResourceServiceTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ResourceGroupRepository resourceGroupRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserService userService;

    private PUser adminUser;
    private Department testDept;
    private ResourceGroup testGroup;
    private Resource testResource;

    @BeforeEach
    void setUp() {
        // Fetch admin user from the DB (created by the InitializationService)
        adminUser = userService.findUserByUsername("admin");

        // Fetch department from the DB (created by the InitializationService)
        testDept = departmentRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Kein Department gefunden - InitializationService nicht gelaufen?"));

        // Create own test group (not the default group)
        testGroup = resourceService.createResourceGroup("Test-Ressourcengruppe", testDept, adminUser);

        // Create a test resource
        Resource resource = Resource.builder()
                .name("Test-Ressource")
                .description("Eine Ressource für Tests")
                .maxSlots(2)
                .build();
        testResource = resourceService.createResource(resource, testGroup.getId(), adminUser);
    }

    // ==========================================
    // createResourceGroup
    // ==========================================

    @Test
    @DisplayName("createResourceGroup: Gruppe wird korrekt persistiert und dem Department zugeordnet")
    void createResourceGroup_ShouldPersistAndLinkToDepartment() {
        ResourceGroup group = resourceService.createResourceGroup("Neue-Test-Gruppe", testDept, adminUser);

        assertNotNull(group.getId());
        assertEquals("Neue-Test-Gruppe", group.getName());
        assertEquals(testDept.getId(), group.getDepartment().getId());
        assertTrue(resourceGroupRepository.existsById(group.getId()));
    }

    // ==========================================
    // createResource
    // ==========================================

    @Test
    @DisplayName("createResource: Ressource wird mit korrekten Defaults persistiert")
    void createResource_ShouldPersistWithDefaults() {
        Resource resource = Resource.builder()
                .name("Drucker-Test")
                .description("Netzwerkdrucker")
                .build();

        Resource created = resourceService.createResource(resource, testGroup.getId(), adminUser);

        assertNotNull(created.getId());
        assertEquals("Drucker-Test", created.getName());
        assertEquals(testGroup.getId(), created.getResourceGroup().getId());
        assertFalse(created.getAgent());
        assertFalse(created.getBusy());
        assertEquals(1, created.getMaxSlots());
        assertEquals(0, created.getCurrentOccupiedSlots());
    }

    @Test
    @DisplayName("createResource: Unbekannte Gruppen-ID wirft NoSuchElementException")
    void createResource_UnknownGroup_ShouldThrow() {
        Resource resource = Resource.builder().name("Ghost-Test").build();
        assertThrows(NoSuchElementException.class,
                () -> resourceService.createResource(resource, -999L, adminUser));
    }

    // ==========================================
    // getResource
    // ==========================================

    @Test
    @DisplayName("getResource: Existierende Ressource wird korrekt zurückgegeben")
    void getResource_ShouldReturnResource() {
        Resource found = resourceService.getResource(testResource.getId(), adminUser);
        assertNotNull(found);
        assertEquals("Test-Ressource", found.getName());
    }

    @Test
    @DisplayName("getResource: Unbekannte ID wirft NoSuchElementException")
    void getResource_UnknownId_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> resourceService.getResource(-999L, adminUser));
    }

    // ==========================================
    // getResourcesByGroupId
    // ==========================================

    @Test
    @DisplayName("getResourcesByGroupId: Gibt alle Ressourcen der Gruppe zurück")
    void getResourcesByGroupId_ShouldReturnResources() {
        Set<Resource> resources = resourceService.getResourcesByGroupId(testGroup.getId());
        assertTrue(resources.stream().anyMatch(r -> r.getId().equals(testResource.getId())));
    }

    @Test
    @DisplayName("getResourcesByGroupId: Unbekannte Gruppen-ID wirft EntityNotFoundException")
    void getResourcesByGroupId_UnknownGroup_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> resourceService.getResourcesByGroupId(-999L));
    }

    // ==========================================
    // reserveResource
    // ==========================================

    @Test
    @DisplayName("reserveResource: Reservierung wird korrekt angelegt")
    void reserveResource_ShouldCreateReservation() {
        Instant from = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant until = from.plus(2, ChronoUnit.HOURS);

        ResourceReservation reservation = resourceService.reserveResource(
                testResource.getId(), adminUser, from, until);

        assertNotNull(reservation.getId());
        assertEquals(testResource.getId(), reservation.getResource().getId());
        assertEquals(adminUser.getId(), reservation.getReservedBy().getId());
        assertEquals(1, reservation.getSlotNumber());
    }

    @Test
    @DisplayName("reserveResource: Alle Slots belegt wirft IllegalStateException")
    void reserveResource_AllSlotsFull_ShouldThrow() {
        Instant from = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant until = from.plus(2, ChronoUnit.HOURS);

        // maxSlots = 2, also zwei Reservierungen anlegen
        resourceService.reserveResource(testResource.getId(), adminUser, from, until);
        resourceService.reserveResource(testResource.getId(), adminUser, from, until);

        // Third reservation must fail
        assertThrows(IllegalStateException.class,
                () -> resourceService.reserveResource(testResource.getId(), adminUser, from, until));
    }

    // ==========================================
    // isResourceAvailable
    // ==========================================

    @Test
    @DisplayName("isResourceAvailable: Freie Ressource wird als verfügbar gemeldet")
    void isResourceAvailable_ShouldReturnTrue() {
        Instant from = Instant.now().plus(3, ChronoUnit.HOURS);
        Instant until = from.plus(1, ChronoUnit.HOURS);

        assertTrue(resourceService.isResourceAvailable(testResource.getId(), from, until));
    }

    @Test
    @DisplayName("isResourceAvailable: Voll reservierte Ressource wird als nicht verfügbar gemeldet")
    void isResourceAvailable_AllSlotsFull_ShouldReturnFalse() {
        Instant from = Instant.now().plus(5, ChronoUnit.HOURS);
        Instant until = from.plus(1, ChronoUnit.HOURS);

        // maxSlots = 2, beide belegen
        resourceService.reserveResource(testResource.getId(), adminUser, from, until);
        resourceService.reserveResource(testResource.getId(), adminUser, from, until);

        assertFalse(resourceService.isResourceAvailable(testResource.getId(), from, until));
    }

    // ==========================================
    // deleteResourceGroup
    // ==========================================

    @Test
    @DisplayName("deleteResourceGroup: Gruppe wird aus der DB entfernt")
    void deleteResourceGroup_ShouldRemoveFromDb() {
        ResourceGroup groupToDelete = resourceService.createResourceGroup("Zu-Löschende-Test-Gruppe", testDept, adminUser);
        Long id = groupToDelete.getId();

        resourceService.deleteResourceGroup(id, adminUser);

        assertThrows(EntityNotFoundException.class,
                () -> resourceService.getResourcesByGroupId(id));
    }

    @Test
    @DisplayName("deleteResourceGroup: Default-Gruppe kann nicht gelöscht werden")
    void deleteResourceGroup_DefaultGroup_ShouldThrow() {
        ResourceGroup defaultGroup = resourceGroupRepository
                .findByDepartment_Id(testDept.getId())
                .stream()
                .filter(g -> ResourceGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(g.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Default-Gruppe nicht gefunden"));

        assertThrows(IllegalStateException.class,
                () -> resourceService.deleteResourceGroup(defaultGroup.getId(), adminUser));
    }

    @Test
    @DisplayName("deleteResourceGroup: Unbekannte ID wirft NoSuchElementException")
    void deleteResourceGroup_UnknownId_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> resourceService.deleteResourceGroup(-999L, adminUser));
    }

    // ==========================================
    // deleteResource
    // ==========================================

    @Test
    @DisplayName("deleteResource: Ressource wird aus der DB entfernt")
    void deleteResource_ShouldRemoveFromDb() {
        Resource toDelete = Resource.builder().name("Zu-Löschende-Test-Ressource").build();
        toDelete = resourceService.createResource(toDelete, testGroup.getId(), adminUser);
        Long id = toDelete.getId();

        resourceService.deleteResource(id, adminUser);

        assertThrows(NoSuchElementException.class,
                () -> resourceService.getResource(id, adminUser));
    }

    @Test
    @DisplayName("deleteResource: Unbekannte ID wirft NoSuchElementException")
    void deleteResource_UnknownId_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> resourceService.deleteResource(-999L, adminUser));
    }

    // ==========================================
    // validateResourceInGroup
    // ==========================================

    @Test
    @DisplayName("validateResourceInGroup: Ressource in korrekter Gruppe wirft keine Exception")
    void validateResourceInGroup_ShouldNotThrow() {
        assertDoesNotThrow(() ->
                resourceService.validateResourceInGroup(testResource.getId(), testGroup.getId()));
    }

    @Test
    @DisplayName("validateResourceInGroup: Ressource in falscher Gruppe wirft IllegalArgumentException")
    void validateResourceInGroup_WrongGroup_ShouldThrow() {
        ResourceGroup otherGroup = resourceService.createResourceGroup("Andere-Test-Gruppe", testDept, adminUser);
        assertThrows(IllegalArgumentException.class,
                () -> resourceService.validateResourceInGroup(testResource.getId(), otherGroup.getId()));
    }

    @Test
    @DisplayName("validateResourceInGroup: Unbekannte Gruppen-ID wirft EntityNotFoundException")
    void validateResourceInGroup_UnknownGroup_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> resourceService.validateResourceInGroup(testResource.getId(), -999L));
    }

    @Test
    @DisplayName("validateResourceInGroup: Unbekannte Ressourcen-ID wirft EntityNotFoundException")
    void validateResourceInGroup_UnknownResource_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> resourceService.validateResourceInGroup(-999L, testGroup.getId()));
    }

    // ==========================================
    // MQTT TELEMETRY (VALUE)
    // ==========================================

    @Test
    @DisplayName("recordMqttValueByUuid: Neuer Datenpunkt wird mit dem ersten Wert angelegt")
    void recordMqttValue_createsEntry() {
        testResource.setMqttUUID("uuid-telemetry-1");
        resourceRepository.save(testResource);

        resourceService.recordMqttValueByUuid("uuid-telemetry-1", "temp", "21");

        Resource reloaded = resourceRepository.findByMqttUUID("uuid-telemetry-1").orElseThrow();
        NameValueEntry entry = reloaded.getMqttValues().stream()
                .filter(e -> "temp".equals(e.getMqttName())).findFirst().orElseThrow();
        assertEquals("21", entry.getMqttValues());
    }

    @Test
    @DisplayName("recordMqttValueByUuid: Weitere Werte werden an die Komma-Historie angehängt")
    void recordMqttValue_appendsHistory() {
        testResource.setMqttUUID("uuid-telemetry-2");
        resourceRepository.save(testResource);

        resourceService.recordMqttValueByUuid("uuid-telemetry-2", "temp", "21");
        resourceService.recordMqttValueByUuid("uuid-telemetry-2", "temp", "22");
        resourceService.recordMqttValueByUuid("uuid-telemetry-2", "temp", "23");

        Resource reloaded = resourceRepository.findByMqttUUID("uuid-telemetry-2").orElseThrow();
        NameValueEntry entry = reloaded.getMqttValues().stream()
                .filter(e -> "temp".equals(e.getMqttName())).findFirst().orElseThrow();
        assertEquals("21,22,23", entry.getMqttValues());
        assertEquals(1, reloaded.getMqttValues().size(), "Gleicher Datenpunkt, keine Dublette");
    }

    @Test
    @DisplayName("recordMqttValueByUuid: Unbekannte UUID wird fehlerfrei ignoriert")
    void recordMqttValue_unknownUuid_isIgnored() {
        assertDoesNotThrow(
                () -> resourceService.recordMqttValueByUuid("does-not-exist", "temp", "21"));
    }

    @Test
    @DisplayName("recordMqttValue(byId): Neuer Datenpunkt wird angelegt und Historie fortgeschrieben")
    void recordMqttValueById_createsAndAppends() {
        resourceService.recordMqttValue(testResource.getId(), "temp", "21", adminUser);
        resourceService.recordMqttValue(testResource.getId(), "temp", "22", adminUser);

        Resource reloaded = resourceRepository.findById(testResource.getId()).orElseThrow();
        NameValueEntry entry = reloaded.getMqttValues().stream()
                .filter(e -> "temp".equals(e.getMqttName())).findFirst().orElseThrow();
        assertEquals("21,22", entry.getMqttValues());
        assertEquals(1, reloaded.getMqttValues().size(), "Gleicher Datenpunkt, keine Dublette");
    }

    @Test
    @DisplayName("recordMqttValue(byId): Unbekannte Ressourcen-ID wirft NoSuchElementException")
    void recordMqttValueById_unknownId_throws() {
        assertThrows(NoSuchElementException.class,
                () -> resourceService.recordMqttValue(999999L, "temp", "21", adminUser));
    }
}