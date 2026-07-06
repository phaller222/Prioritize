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

package de.hallerweb.enterprise.prioritize.service.nfc;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.nfc.NfcUnitRepository;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.NfcUnitData;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class NfcUnitServiceTest {

    @Autowired
    private NfcUnitService nfcUnitService;
    @Autowired
    private NfcUnitRepository nfcUnitRepository;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private UserService userService;

    private PUser admin;
    private Resource resource;
    private Task task;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        Department dept = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No department found"));
        ResourceGroup group = resourceService.createResourceGroup("NFC-Test-Gruppe", dept, admin);
        resource = resourceService.createResource(
                Resource.builder().name("Stechuhr-Terminal").build(), group.getId(), admin);

        Project project = projectService.createProject(
                new ProjectData("NFC", "Tracking project", 3, null, null, 50), admin);
        task = taskService.createTask(project.getId(),
                new TaskData("Montage", "Assemble the thing", 2), admin);
    }

    private NfcUnit register(NfcUnitType type) {
        return nfcUnitService.registerNfcUnit(resource.getId(),
                new NfcUnitData(UUID.randomUUID().toString(), "Tag", "A tag", type, null), admin);
    }

    @Test
    @DisplayName("registerNfcUnit: Tag wird persistiert und der Resource zugeordnet")
    void registerNfcUnit_persistsAndLinksResource() {
        NfcUnit unit = register(NfcUnitType.TIMETRACKER);

        assertNotNull(unit.getId());
        assertEquals(NfcUnitType.TIMETRACKER, unit.getType());
        assertEquals(resource.getId(), unit.getResource().getId());
        assertTrue(nfcUnitService.getNfcUnitsForResource(resource.getId(), admin).stream()
                .anyMatch(u -> u.getId().equals(unit.getId())));
    }

    @Test
    @DisplayName("registerNfcUnit: doppelte UUID wirft IllegalArgumentException")
    void registerNfcUnit_duplicateUuid_throws() {
        NfcUnit unit = register(NfcUnitType.COUNTER);
        assertThrows(IllegalArgumentException.class, () -> nfcUnitService.registerNfcUnit(
                resource.getId(),
                new NfcUnitData(unit.getUuid(), "Dup", null, NfcUnitType.COUNTER, null), admin));
    }

    @Test
    @DisplayName("bindTask: bindet einen TIMETRACKER-Tag an einen Task")
    void bindTask_bindsTracker() {
        NfcUnit unit = register(NfcUnitType.TIMETRACKER);
        NfcUnit bound = nfcUnitService.bindTask(unit.getId(), task.getId(), admin);
        assertEquals(task.getId(), bound.getBoundTaskId());
    }

    @Test
    @DisplayName("bindTask: Nicht-TIMETRACKER-Tag kann nicht gebunden werden")
    void bindTask_nonTracker_throws() {
        NfcUnit unit = register(NfcUnitType.CHECKPOINT);
        assertThrows(IllegalStateException.class,
                () -> nfcUnitService.bindTask(unit.getId(), task.getId(), admin));
    }

    @Test
    @DisplayName("scan: TIMETRACKER-Tag toggelt die Zeiterfassung des gebundenen Tasks")
    void scan_timetracker_togglesTracking() {
        NfcUnit unit = register(NfcUnitType.TIMETRACKER);
        nfcUnitService.bindTask(unit.getId(), task.getId(), admin);

        ScanResult first = nfcUnitService.scan(unit.getUuid(), admin);
        assertEquals("TRACKING_STARTED", first.action());
        assertTrue(first.tracking());
        assertEquals(task.getId(), first.taskId());
        assertEquals(TaskStatus.STARTED, taskService.getTask(task.getId(), admin).getTaskStatus());

        ScanResult second = nfcUnitService.scan(unit.getUuid(), admin);
        assertEquals("TRACKING_STOPPED", second.action());
        assertFalse(second.tracking());
        assertEquals(1, taskService.getTask(task.getId(), admin).getTimeSpent().size());
    }

    @Test
    @DisplayName("scan: ungebundener TIMETRACKER-Tag wirft IllegalStateException")
    void scan_unboundTracker_throws() {
        NfcUnit unit = register(NfcUnitType.TIMETRACKER);
        assertThrows(IllegalStateException.class, () -> nfcUnitService.scan(unit.getUuid(), admin));
    }

    @Test
    @DisplayName("scan: COUNTER-Tag erhöht die Sequenznummer")
    void scan_counter_incrementsSequence() {
        NfcUnit unit = register(NfcUnitType.COUNTER);

        ScanResult r1 = nfcUnitService.scan(unit.getUuid(), admin);
        ScanResult r2 = nfcUnitService.scan(unit.getUuid(), admin);

        assertEquals("COUNTED", r1.action());
        assertEquals(1, r1.sequenceNumber());
        assertEquals(2, r2.sequenceNumber());
    }

    @Test
    @DisplayName("scan: unbekannte UUID wirft NoSuchElementException")
    void scan_unknownUuid_throws() {
        assertThrows(NoSuchElementException.class,
                () -> nfcUnitService.scan("does-not-exist", admin));
    }

    @Test
    @DisplayName("deleteNfcUnit: Tag wird entfernt")
    void deleteNfcUnit_removesTag() {
        NfcUnit unit = register(NfcUnitType.INFOPOINT);
        nfcUnitService.deleteNfcUnit(unit.getId(), admin);
        assertFalse(nfcUnitRepository.existsById(unit.getId()));
    }

    @Test
    @DisplayName("deleteTask: löst eine gebundene TIMETRACKER-Bindung, statt eine FK zu verwaisen")
    void deleteTask_detachesTrackerBinding() {
        NfcUnit unit = register(NfcUnitType.TIMETRACKER);
        nfcUnitService.bindTask(unit.getId(), task.getId(), admin);

        taskService.deleteTask(task.getId(), admin);

        NfcUnit reloaded = nfcUnitRepository.findById(unit.getId()).orElseThrow();
        assertNull(reloaded.getBoundTaskId());
    }
}
