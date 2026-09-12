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

package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.cost.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.QualificationLevel;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import de.hallerweb.enterprise.prioritize.service.skill.QualificationLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cost side of a task: that a rate stamped at booking time stays put when the price list moves,
 * and that labour is reported by qualification level rather than by person.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class TaskCostServiceTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private QualificationLevelService qualificationLevelService;
    @Autowired
    private DepartmentRepository departmentRepository;

    private PUser admin;
    private Project project;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        project = projectService.createProject(
                new ProjectData("Kostenprojekt", "Nachkalkulation", 3, null, null, 50), admin);
    }

    // ==========================================
    // 2c — der Satz wird beim Schließen gestempelt
    // ==========================================

    @Test
    @DisplayName("Gerätekosten: eine spätere Satzänderung schreibt eine abgeschlossene Buchung nicht um")
    void equipmentCost_laterRateChange_doesNotRewriteClosedBooking() {
        Task task = newTask();
        Resource lift = ratedResource("Hubarbeitsbühne", "10.00", CostRateUnit.HOUR);
        bookEquipment(task, lift, Duration.ofHours(3));

        // Preisliste zieht an, nachdem die Bühne zurück ist
        lift.setCostRate(new BigDecimal("99.00"));
        resourceService.partialUpdateResource(lift.getId(), lift, admin);

        TaskService.EquipmentCostReport report = taskService.getEquipmentCost(task.getId(), admin);
        assertEquals(1, report.lines().size());
        assertEquals(0, new BigDecimal("10.00").compareTo(report.lines().get(0).rate()));
        assertEquals(0, new BigDecimal("30.00").compareTo(report.lines().get(0).amount()));
    }

    @Test
    @DisplayName("Gerätekosten: zwei Buchungen zu verschiedenen Sätzen ergeben zwei Zeilen")
    void equipmentCost_twoRates_producesTwoLines() {
        Task task = newTask();
        Resource dryer = ratedResource("Bautrockner", "10.00", CostRateUnit.HOUR);
        bookEquipment(task, dryer, Duration.ofHours(2));

        dryer.setCostRate(new BigDecimal("20.00"));
        resourceService.partialUpdateResource(dryer.getId(), dryer, admin);
        bookEquipment(task, dryer, Duration.ofHours(2));

        TaskService.EquipmentCostReport report = taskService.getEquipmentCost(task.getId(), admin);
        assertEquals(2, report.lines().size(), "eine Zeile je Satz, nicht je Gerät");
        assertEquals(1, report.totals().size());
        // 2 h zu 10 + 2 h zu 20 = 60, nicht 4 h zu irgendeinem der beiden Sätze
        assertEquals(0, new BigDecimal("60.00").compareTo(report.totals().get(0).amount()));
    }

    @Test
    @DisplayName("Arbeitskosten: eine spätere Satzänderung an der Stufe ändert gebuchte Stunden nicht")
    void labourCost_laterRateChange_doesNotRewriteClosedSession() {
        Task task = newTask();
        QualificationLevel geselle = level("Geselle-" + System.nanoTime(), "60.00");
        PUser worker = memberWithLevel("geselle", geselle);
        bookWork(task, worker, Duration.ofHours(2));

        qualificationLevelService.updateQualificationLevel(geselle.getId(),
                QualificationLevel.builder().name(geselle.getName()).costRate(new BigDecimal("90.00"))
                        .costCurrency("EUR").costRateUnit(CostRateUnit.HOUR).build(), admin);

        TaskService.LabourCostReport report = taskService.getLabourCost(task.getId(), admin);
        assertEquals(1, report.lines().size());
        assertEquals(0, new BigDecimal("120.00").compareTo(report.lines().get(0).amount()));
    }

    // ==========================================
    // 2b — Arbeitskosten nach Stufe, nie nach Person
    // ==========================================

    @Test
    @DisplayName("Arbeitskosten: zwei Personen derselben Stufe stehen in EINER Zeile")
    void labourCost_twoPeopleSameLevel_areOneLine() {
        Task task = newTask();
        QualificationLevel geselle = level("Geselle-" + System.nanoTime(), "50.00");
        PUser first = memberWithLevel("geselle-a", geselle);
        PUser second = memberWithLevel("geselle-b", geselle);
        bookWork(task, first, Duration.ofHours(2));
        bookWork(task, second, Duration.ofHours(3));

        TaskService.LabourCostReport report = taskService.getLabourCost(task.getId(), admin);
        assertEquals(1, report.lines().size(), "gruppiert wird nach Stufe, nicht nach Person");
        TaskService.LabourCostLine line = report.lines().get(0);
        assertEquals(geselle.getName(), line.qualificationLevel());
        assertEquals(2, line.sessions());
        assertEquals(Duration.ofHours(5).toSeconds(), line.totalSeconds());
        assertEquals(0, new BigDecimal("250.00").compareTo(line.amount()));
    }

    @Test
    @DisplayName("Arbeitskosten: verschiedene Stufen ergeben getrennte Zeilen und eine Summe")
    void labourCost_differentLevels_areSeparateLinesWithOneTotal() {
        Task task = newTask();
        PUser meister = memberWithLevel("meister", level("Meister-" + System.nanoTime(), "80.00"));
        PUser azubi = memberWithLevel("azubi", level("Azubi-" + System.nanoTime(), "20.00"));
        bookWork(task, meister, Duration.ofHours(1));
        bookWork(task, azubi, Duration.ofHours(4));

        TaskService.LabourCostReport report = taskService.getLabourCost(task.getId(), admin);
        assertEquals(2, report.lines().size());
        assertEquals(1, report.totals().size());
        assertEquals(0, new BigDecimal("160.00").compareTo(report.totals().get(0).amount()));
        assertFalse(report.ratesMissing());
    }

    @Test
    @DisplayName("Arbeitskosten: Stunden ohne Qualifikationsstufe zählen als unbepreist, nicht als kostenlos")
    void labourCost_withoutLevel_isUnpricedNotFree() {
        Task task = newTask();
        PUser nobody = memberWithLevel("ohne-stufe", null);
        bookWork(task, nobody, Duration.ofHours(3));

        TaskService.LabourCostReport report = taskService.getLabourCost(task.getId(), admin);
        assertEquals(1, report.lines().size());
        assertNull(report.lines().get(0).amount(), "kein Betrag — 0 würde behaupten, es war umsonst");
        assertEquals(Duration.ofHours(3).toSeconds(), report.lines().get(0).totalSeconds());
        assertTrue(report.ratesMissing());
        assertTrue(report.totals().isEmpty());
    }

    @Test
    @DisplayName("Gesamtkosten: Geld wird über beide Blöcke summiert, Stunden nie")
    void taskCost_addsMoneyAcrossBlocks_butNeverHours() {
        Task task = newTask();
        Resource dryer = ratedResource("Trockner", "5.00", CostRateUnit.HOUR);
        bookEquipment(task, dryer, Duration.ofHours(10));   // 50.00
        PUser worker = memberWithLevel("geselle-c", level("Geselle-" + System.nanoTime(), "60.00"));
        bookWork(task, worker, Duration.ofHours(2));        // 120.00

        TaskService.TaskCostReport report = taskService.getTaskCost(task.getId(), admin);

        assertEquals(1, report.totals().size());
        assertEquals(0, new BigDecimal("170.00").compareTo(report.totals().get(0).amount()));
        assertFalse(report.ratesMissing());

        // Die Stunden bleiben getrennt — 10 Gerätestunden und 2 Arbeitsstunden sind keine 12
        assertEquals(Duration.ofHours(10).toSeconds(), report.equipment().lines().get(0).totalSeconds());
        assertEquals(Duration.ofHours(2).toSeconds(), report.labour().lines().get(0).totalSeconds());
    }

    @Test
    @DisplayName("Gesamtkosten: zwei Währungen werden nie zu einer Zahl vermischt")
    void taskCost_twoCurrencies_areNeverMerged() {
        Task task = newTask();
        Resource euroDevice = ratedResource("Euro-Gerät", "10.00", CostRateUnit.HOUR);
        bookEquipment(task, euroDevice, Duration.ofHours(1));
        Resource francDevice = newResource("Franken-Gerät");
        francDevice.setCostRate(new BigDecimal("10.00"));
        francDevice.setCostCurrency("CHF");
        francDevice.setCostRateUnit(CostRateUnit.HOUR);
        resourceService.partialUpdateResource(francDevice.getId(), francDevice, admin);
        bookEquipment(task, francDevice, Duration.ofHours(1));

        TaskService.TaskCostReport report = taskService.getTaskCost(task.getId(), admin);
        assertEquals(2, report.totals().size(), "je Währung eine Summe, keine Gesamtsumme");
    }

    @Test
    @DisplayName("Arbeitskosten sind dem Projektmanager vorbehalten — ein Mitglied bekommt AccessDenied")
    void labourCost_forPlainMember_isDenied() {
        Task task = newTask();
        PUser member = memberWithLevel("nur-mitglied", level("Stufe-" + System.nanoTime(), "40.00"));

        assertThrows(AccessDeniedException.class, () -> taskService.getLabourCost(task.getId(), member));
        assertThrows(AccessDeniedException.class, () -> taskService.getTaskCost(task.getId(), member));
        // ... die Gerätekosten dagegen darf ein Mitglied sehen
        assertEquals(0, taskService.getEquipmentCost(task.getId(), member).lines().size());
    }

    // ==========================================
    // Helfer
    // ==========================================

    private Task newTask() {
        return taskService.createTask(project.getId(),
                new TaskData("Baustelle", "Arbeit und Geräte", 2), admin);
    }

    private QualificationLevel level(String name, String hourlyRate) {
        return qualificationLevelService.createQualificationLevel(
                QualificationLevel.builder()
                        .name(name)
                        .costRate(new BigDecimal(hourlyRate))
                        .costCurrency("EUR")
                        .costRateUnit(CostRateUnit.HOUR)
                        .build(), admin);
    }

    /** A project member, optionally on a qualification level. */
    private PUser memberWithLevel(String prefix, QualificationLevel level) {
        PUser user = userService.createUser(PUser.builder()
                .username(prefix + "-" + System.nanoTime())
                .name("Test")
                .firstname("Person")
                .password("plaintext123")
                .admin(false)
                .build());
        projectService.addMember(project.getId(), user.getId(), admin);
        if (level != null) {
            user = qualificationLevelService.assignQualificationLevel(user.getId(), level.getId(), admin);
        }
        return user;
    }

    /**
     * Books a closed work session of the given length for that user. Clocking in and straight out
     * would measure milliseconds, so the start is moved back before stopping — the same trick the
     * equipment tests use.
     */
    private void bookWork(Task task, PUser worker, Duration length) {
        taskService.startTracking(task.getId(), worker);
        taskRepository.findById(task.getId()).orElseThrow()
                .activeTimeSpanFor(worker).setDateFrom(Instant.now().minus(length));
        taskService.stopTracking(task.getId(), worker);
    }

    private void bookEquipment(Task task, Resource device, Duration length) {
        taskService.startEquipmentUsage(task.getId(), device.getId(), admin);
        taskRepository.findById(task.getId()).orElseThrow()
                .activeEquipmentSpanFor(device).setDateFrom(Instant.now().minus(length));
        taskService.stopEquipmentUsage(task.getId(), device.getId(), admin);
    }

    private Resource ratedResource(String name, String rate, CostRateUnit unit) {
        Resource resource = newResource(name);
        resource.setCostRate(new BigDecimal(rate));
        resource.setCostCurrency("EUR");
        resource.setCostRateUnit(unit);
        return resourceService.partialUpdateResource(resource.getId(), resource, admin);
    }

    private Resource newResource(String name) {
        Department department = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Kein Department — InitializationService nicht gelaufen?"));
        ResourceGroup group = resourceService.createResourceGroup(
                "Geräte-" + System.nanoTime(), department, admin);
        Resource resource = resourceService.createResource(
                Resource.builder().name(name).description(name).maxSlots(1).build(),
                group.getId(), admin);
        return resource;
    }
}
