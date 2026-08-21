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

package de.hallerweb.enterprise.prioritize.service.demo;

import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleRequest;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.repository.security.PermissionRecordRepository;
import de.hallerweb.enterprise.prioritize.service.InitializationService;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.NfcUnitData;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.scheduling.TaskScheduleService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Fills a fresh installation with a small electrical contracting business, so that the first screen
 * a visitor sees tells a story instead of showing "Main Department" and an empty list.
 * <p>
 * <b>Why this exists.</b> Everything Prioritize does is generic by design, and generic is exactly
 * what a tradesperson cannot recognise themselves in. The same software, filled with ladders that
 * are overdue for inspection and hours booked on a construction site, needs no explanation. It
 * serves two audiences: the demo on a laptop during a first conversation, and a {@code docker run}
 * that shows a populated business rather than an empty form.
 * <p>
 * <b>The overdue ladder inspection is the most important record in here.</b> A data set in which
 * everything is in order does not show what the tool is for.
 * <p>
 * <b>Only under {@code @Profile("demo")}</b>, started with
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=h2,demo} or {@code SPRING_PROFILES_ACTIVE}
 * in a container. Deliberately <em>not</em> a button in the admin GUI: sooner or later somebody
 * presses that in a real installation.
 * <p>
 * <b>Nothing in the platform refers to this package.</b> Prioritize is a neutral platform and a
 * trade business is a vertical on top of it; this data set therefore hangs itself into startup via
 * {@link DemoDataRunner} instead of being wired into the platform's own initialization. Deleting
 * the {@code demo} package, or moving it into a module of its own, needs no change anywhere else —
 * which is the point, because that decision may well be revisited.
 * <p>
 * <b>Every date is relative to today.</b> A fixed date would make the whole set worthless within
 * months, because everything in it would read as long overdue.
 * <p>
 * Idempotent: it recognises its own company and does nothing on a second start, so a restarting
 * container does not accumulate duplicates.
 *
 * @author peter haller
 */
@Service
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
public class DemoDataService {

    /** Also the marker that decides whether this data set has already been seeded. */
    private static final String COMPANY_NAME = "Elektro Musterbetrieb GmbH";

    /** Shared by every demo account. They exist to be logged into during a demo, nothing else. */
    private static final String DEMO_PASSWORD = "demo";

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final UserService userService;
    private final CompanyService companyService;
    private final DepartmentService departmentService;
    private final ResourceService resourceService;
    private final NfcUnitService nfcUnitService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final SkillService skillService;
    private final TaskScheduleService taskScheduleService;
    private final DocumentService documentService;
    private final PermissionRecordRepository permissionRepository;

    @Transactional
    public void seed() {
        if (companyService.findAll().stream().anyMatch(c -> COMPANY_NAME.equals(c.getName()))) {
            log.info("Demo data already present ('{}') — nothing seeded.", COMPANY_NAME);
            return;
        }
        PUser admin = userService.findUserByUsername("admin");

        Company company = createCompany(admin);
        Department werkstatt = createDepartment(company, "Werkstatt/Lager",
                "Lager, Betriebsmittel und Geräteprüfung", admin);
        Department montage = createDepartment(company, "Montage",
                "Baustellen und Installation", admin);
        createDepartment(company, "Büro", "Angebote, Abrechnung, Disposition", admin);

        Crew crew = createCrew(werkstatt, montage);
        createSkills(crew);

        Equipment equipment = createEquipment(werkstatt, admin);
        createReservations(equipment, crew, admin);
        createTelemetry(equipment, admin);

        Sites sites = createSites(crew, equipment, admin);
        createTrackerTags(equipment, sites, admin);
        createWorkSessions(sites, crew, admin);
        createInspectionSchedules(sites, admin);
        createInspectionRecords(werkstatt, admin);

        log.info("Demo data seeded: '{}' with {} crew members. Log in as 'meister' / '{}'.",
                COMPANY_NAME, 4, DEMO_PASSWORD);
    }

    // ==========================================
    // Company and departments
    // ==========================================

    private Company createCompany(PUser admin) {
        Company company = new Company();
        company.setName(COMPANY_NAME);
        company.setDescription("Elektroinstallation, Prüfungen und PV-Anlagen");
        company.setUrl("https://elektro-musterbetrieb.example");
        return companyService.createCompany(company, admin);
    }

    private Department createDepartment(Company company, String name, String description, PUser admin) {
        Department department = Department.builder()
                .name(name)
                .description(description)
                .build();
        return departmentService.saveDepartment(department, company.getId(), admin);
    }

    // ==========================================
    // People
    // ==========================================

    /** The four people the demo talks about. */
    private record Crew(PUser meister, PUser geselleKnx, PUser gesellePv, PUser azubi) {

        List<PUser> all() {
            return List.of(meister, geselleKnx, gesellePv, azubi);
        }
    }

    private Crew createCrew(Department werkstatt, Department montage) {
        PUser meister = createUser("meister", "Brandt", "Stefan", "Elektromeister", werkstatt);
        PUser geselleKnx = createUser("geselle1", "Kruse", "Tobias", "Elektrogeselle", montage);
        PUser gesellePv = createUser("geselle2", "Novak", "Marek", "Elektrogeselle", montage);
        PUser azubi = createUser("azubi", "Sommer", "Lena", "Auszubildende 2. Lehrjahr", montage);

        // The master craftsman runs the equipment; everyone else needs to see it (and their own
        // reservations) to make the walk-through work when logged in as somebody other than admin.
        grantResourceAccess(meister, true);
        grantResourceAccess(geselleKnx, false);
        grantResourceAccess(gesellePv, false);
        grantResourceAccess(azubi, false);

        return new Crew(meister, geselleKnx, gesellePv, azubi);
    }

    private PUser createUser(String username, String name, String firstname,
                             String occupation, Department department) {
        PUser user = new PUser();
        user.setUsername(username);
        user.setName(name);
        user.setFirstname(firstname);
        user.setOccupation(occupation);
        user.setDepartment(department);
        user.setPassword(DEMO_PASSWORD);
        user.setGender(PUser.Gender.OTHER);
        user.setAdmin(false);
        return userService.createUser(user);
    }

    /**
     * Grants access to every resource (object id 0 means "all instances", as in
     * {@link InitializationService}). Read for everybody so the equipment list is not empty after a
     * demo login; update on top for the master craftsman, who is the one managing the equipment.
     */
    private void grantResourceAccess(PUser user, boolean mayManage) {
        PermissionRecord resources = PermissionRecord.builder()
                .absoluteObjectType(Resource.class.getCanonicalName())
                .objectId(0L)
                .createPermission(false)
                .readPermission(true)
                .updatePermission(mayManage)
                .deletePermission(false)
                .build();
        PermissionRecord reservations = PermissionRecord.builder()
                .absoluteObjectType(ResourceReservation.class.getCanonicalName())
                .objectId(0L)
                .createPermission(true)
                .readPermission(true)
                .updatePermission(true)
                .deletePermission(mayManage)
                .build();
        permissionRepository.save(resources);
        permissionRepository.save(reservations);
        user.addPersonalPermission(resources);
        user.addPersonalPermission(reservations);
        userService.updateUser(user);
    }

    // ==========================================
    // Skills — "who is even allowed to do that inspection?"
    // ==========================================

    private void createSkills(Crew crew) {
        SkillCategory category = new SkillCategory();
        category.setName("Elektrotechnik");
        category.setDescription("Qualifikationen und Befähigungen im Elektrohandwerk");
        SkillCategory saved = skillService.createCategory(category);

        Skill inspections = createSkill(saved, "Befähigte Person für Prüfungen",
                "Darf ortsveränderliche Betriebsmittel und Leitern prüfen (DGUV Vorschrift 3)",
                "Prüfung,DGUV,Befähigung");
        Skill photovoltaics = createSkill(saved, "PV-Anlagen",
                "Montage und Inbetriebnahme von Photovoltaikanlagen", "PV,Photovoltaik,Wechselrichter");
        Skill knx = createSkill(saved, "KNX-Gebäudetechnik",
                "Planung und Programmierung von KNX-Installationen", "KNX,Bus,Gebäudetechnik");
        Skill heights = createSkill(saved, "Höhenarbeit",
                "Unterweisung für Hubarbeitsbühnen und Arbeiten auf Leitern", "Höhe,Hubarbeitsbühne,Leiter");

        // Only the master craftsman carries the inspection qualification — that is the point of
        // modelling skills for a trade business at all: the overdue ladder cannot just be handed to
        // anybody.
        assign(crew.meister(), inspections, 9);
        assign(crew.meister(), photovoltaics, 7);
        assign(crew.geselleKnx(), knx, 8);
        assign(crew.geselleKnx(), heights, 6);
        assign(crew.gesellePv(), photovoltaics, 8);
        assign(crew.gesellePv(), heights, 5);
        assign(crew.azubi(), knx, 3);
    }

    private Skill createSkill(SkillCategory category, String name, String description, String keywords) {
        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setKeywords(keywords);
        skill.setCategory(category);
        return skillService.createSkill(skill, userService.findUserByUsername("admin"));
    }

    private void assign(PUser user, Skill skill, int enthusiasm) {
        SkillRecord record = new SkillRecord();
        record.setSkill(skill);
        record.setEnthusiasm(enthusiasm);
        skillService.assignSkillToUser(user.getId(), record);
    }

    // ==========================================
    // Equipment
    // ==========================================

    /** The pieces of equipment the demo points at. */
    private record Equipment(Resource testDevice, Resource ladder, Resource coreDrill,
                             Resource insulationTester, Resource hammerDrill, Resource liftPlatform) {
    }

    private Equipment createEquipment(Department werkstatt, PUser admin) {
        ResourceGroup group = resourceService.createResourceGroup("Betriebsmittel", werkstatt, admin);

        Resource testDevice = createResource(group,
                "Installationsprüfgerät Benning IT 130",
                "Kalibrierung fällig am " + date(LocalDate.now().plusWeeks(3)), admin);
        Resource ladder = createResource(group,
                "Anlegeleiter dreiteilig 3x12 Sprossen",
                "Prüfung ÜBERFÄLLIG seit " + date(LocalDate.now().minusWeeks(2))
                        + " — bis zur Prüfung gesperrt", admin);
        Resource coreDrill = createResource(group,
                "Kernbohrmaschine",
                "Nassbohren bis 200 mm", admin);
        Resource insulationTester = createResource(group,
                "Isolationsmessgerät",
                "Im Lager, frei verfügbar", admin);
        Resource hammerDrill = createResource(group,
                "Akku-Bohrhammer",
                "Im Fahrzeug von Tobias Kruse", admin);

        Resource liftPlatform = createResource(group,
                "Hubarbeitsbühne (Mietgerät)",
                "Mietgerät, Rückgabe am " + date(LocalDate.now().plusDays(4)), admin);
        // The one piece of equipment that reports anything at all. A toolbox has no sensor, and
        // inflated IoT would distract from what the demo is actually about.
        // The one rented machine, so the one with a rate that actually costs money while it stands
        // around. A day rate, because that is how rental equipment is billed.
        liftPlatform.setCostRate(new BigDecimal("89.00"));
        liftPlatform.setCostCurrency("EUR");
        liftPlatform.setCostRateUnit(CostRateUnit.DAY);
        liftPlatform.setMqttResource(true);
        liftPlatform.setMqttUUID("demo-hubarbeitsbuehne");
        liftPlatform.setMqttOnline(true);

        return new Equipment(testDevice, ladder, coreDrill, insulationTester, hammerDrill, liftPlatform);
    }

    private Resource createResource(ResourceGroup group, String name, String description, PUser admin) {
        Resource resource = Resource.builder()
                .name(name)
                .description(description)
                .stationary(false)
                .remote(false)
                .maxSlots(1)
                .build();
        return resourceService.createResource(resource, group.getId(), admin);
    }

    private void createReservations(Equipment equipment, Crew crew, PUser admin) {
        // The core drill is out on the Kita site until the end of the week …
        resourceService.reserveResource(equipment.coreDrill().getId(), crew.gesellePv(),
                startOfDay(LocalDate.now().minusDays(1)), endOfDay(nextFriday()));
        // … and the hammer drill has been in somebody's van for over a week, which is exactly the
        // "where is it?" the equipment list is supposed to answer.
        resourceService.reserveResource(equipment.hammerDrill().getId(), crew.geselleKnx(),
                startOfDay(LocalDate.now().minusDays(8)), endOfDay(LocalDate.now().plusDays(2)));
        resourceService.reserveResource(equipment.liftPlatform().getId(), crew.gesellePv(),
                startOfDay(LocalDate.now().minusDays(2)), endOfDay(LocalDate.now().plusDays(4)));
    }

    private void createTelemetry(Equipment equipment, PUser admin) {
        Resource lift = equipment.liftPlatform();
        resourceService.recordMqttValue(lift.getId(), "Betriebsstunden", "1284", admin);
        resourceService.recordMqttValue(lift.getId(), "Betriebsstunden", "1287", admin);
        resourceService.recordMqttValue(lift.getId(), "Betriebsstunden", "1291", admin);
    }

    // ==========================================
    // Sites, tasks and the stickers on them
    // ==========================================

    /** The construction sites, each with the task a sticker is bound to. */
    private record Sites(Project kita, Task kitaTask, Project pv, Task pvTask,
                         Project workshop, Task workshopTask) {
    }

    private Sites createSites(Crew crew, Equipment equipment, PUser admin) {
        Project kita = createProject("Baustelle Kita Sonnenschein",
                "Elektroinstallation Neubau Kindertagesstätte, 2 Gruppenräume und Küche",
                LocalDate.now().minusWeeks(2), LocalDate.now().plusWeeks(4), 120, crew, admin);
        Task kitaTask = createTask(kita, "Rohinstallation Erdgeschoss",
                "Leitungen ziehen, Dosen setzen, Zählerschrank vorbereiten", admin);
        createTask(kita, "Zählerschrank setzen", "Anschluss und Beschriftung", admin);

        Project pv = createProject("PV-Anlage Halle Nord",
                "42 Module auf dem Hallendach, Wechselrichter im Technikraum",
                LocalDate.now().minusWeeks(1), LocalDate.now().plusWeeks(6), 80, crew, admin);
        Task pvTask = createTask(pv, "Module montieren", "Unterkonstruktion und Modulmontage", admin);
        createTask(pv, "Wechselrichter anschließen", "Anschluss, Inbetriebnahme, Netzanmeldung", admin);

        Project workshop = createProject("Werkstatt und Innendienst",
                "Gerätepflege, Prüfungen und alles, was nicht auf einer Baustelle passiert",
                LocalDate.now().minusMonths(6), null, 200, crew, admin);
        Task workshopTask = createTask(workshop, "Geräteprüfung und Wartung",
                "Wiederkehrende Prüfungen der Betriebsmittel", admin);

        projectService.addResource(kita.getId(), equipment.coreDrill().getId(), admin);
        projectService.addResource(pv.getId(), equipment.liftPlatform().getId(), admin);
        projectService.addResource(workshop.getId(), equipment.testDevice().getId(), admin);

        return new Sites(kita, kitaTask, pv, pvTask, workshop, workshopTask);
    }

    private Project createProject(String name, String description, LocalDate begin, LocalDate due,
                                  int maxManDays, Crew crew, PUser admin) {
        Project project = projectService.createProject(
                new ProjectData(name, description, 3, begin, due, maxManDays), admin);
        crew.all().forEach(member -> projectService.addMember(project.getId(), member.getId(), admin));
        return project;
    }

    private Task createTask(Project project, String name, String description, PUser admin) {
        return taskService.createTask(project.getId(), new TaskData(name, description, 2), admin);
    }

    /**
     * The stickers that start and stop the clock. One tag is one task, so the tasks they point at
     * are deliberately coarse — "Rohinstallation Erdgeschoss", not every single socket.
     */
    private void createTrackerTags(Equipment equipment, Sites sites, PUser admin) {
        registerTracker(equipment.coreDrill(), "demo-tag-kita", "Aufkleber Baustellencontainer Kita",
                sites.kitaTask(), admin);
        registerTracker(equipment.liftPlatform(), "demo-tag-pv", "Aufkleber PV-Halle",
                sites.pvTask(), admin);
        registerTracker(equipment.insulationTester(), "demo-tag-lager", "Aufkleber Lagertor",
                sites.workshopTask(), admin);

        // A tag that identifies a piece of equipment rather than starting a clock — the ladder is
        // the one everybody should be able to check the inspection state of.
        nfcUnitService.registerNfcUnit(equipment.ladder().getId(),
                new NfcUnitData("demo-tag-leiter", "Aufkleber Anlegeleiter",
                        "Prüfplakette", NfcUnitType.CHECKPOINT, null), admin);
    }

    private void registerTracker(Resource carrier, String uuid, String name, Task task, PUser admin) {
        var unit = nfcUnitService.registerNfcUnit(carrier.getId(),
                new NfcUnitData(uuid, name, "Zeiterfassung für " + task.getName(),
                        NfcUnitType.TIMETRACKER, null), admin);
        nfcUnitService.bindTask(unit.getId(), task.getId(), admin);
    }

    // ==========================================
    // Recorded hours
    // ==========================================

    /**
     * Two days of hours already booked, so "hours on this site" shows something the moment the demo
     * starts instead of after a day of waiting. Booked per person — the manager may enter time for
     * others — which also means the demo shows separate hours for the master, the journeymen and the
     * apprentice without the parallel-clock work being done yet.
     * <p>
     * Lunch needs no special handling anywhere: it is simply the gap between two sessions.
     */
    private void createWorkSessions(Sites sites, Crew crew, PUser admin) {
        for (int daysAgo = 2; daysAgo >= 1; daysAgo--) {
            LocalDate day = workdayBefore(daysAgo);

            book(sites.kitaTask(), crew.geselleKnx(), day, 7, 0, 12, 0, admin);
            book(sites.kitaTask(), crew.geselleKnx(), day, 12, 30, 16, 15, admin);
            book(sites.kitaTask(), crew.azubi(), day, 7, 0, 12, 0, admin);
            book(sites.kitaTask(), crew.azubi(), day, 12, 30, 16, 15, admin);
            book(sites.pvTask(), crew.gesellePv(), day, 8, 0, 12, 0, admin);
            book(sites.pvTask(), crew.gesellePv(), day, 12, 30, 17, 0, admin);
            book(sites.workshopTask(), crew.meister(), day, 9, 0, 11, 30, admin);
        }
    }

    private void book(Task task, PUser worker, LocalDate day,
                      int fromHour, int fromMinute, int toHour, int toMinute, PUser admin) {
        taskService.addWorkSession(task.getId(),
                at(day, fromHour, fromMinute), at(day, toHour, toMinute),
                "Stunden aus dem Bautagebuch nachgetragen", worker.getId(), admin);
    }

    // ==========================================
    // Recurring inspections and the proof of them
    // ==========================================

    /**
     * The recurring inspections a trade business actually has to keep track of. This is the first
     * time the scheduling feature has a reason that means something to somebody: the appointment
     * does not come from a calendar, it comes from a regulation.
     */
    private void createInspectionSchedules(Sites sites, PUser admin) {
        Long workshop = sites.workshop().getId();
        taskScheduleService.createSchedule(workshop, new TaskScheduleRequest(
                "Jährliche Geräteprüfung (DGUV V3)",
                "Ortsveränderliche Betriebsmittel prüfen",
                "Alle Geräte im Lager und in den Fahrzeugen prüfen und protokollieren",
                1, "0 0 7 15 1 *", ZONE.getId(), true), admin);
        taskScheduleService.createSchedule(workshop, new TaskScheduleRequest(
                "Leiterprüfung",
                "Leitern und Tritte prüfen",
                "Sichtprüfung und Protokoll für alle Leitern und Tritte",
                1, "0 0 7 1 3 *", ZONE.getId(), true), admin);
        taskScheduleService.createSchedule(workshop, new TaskScheduleRequest(
                "Kalibrierung Messgeräte",
                "Messgeräte kalibrieren lassen",
                "Installationsprüfgerät und Isolationsmessgerät zur Kalibrierung geben",
                2, "0 0 7 1 9 *", ZONE.getId(), true), admin);
    }

    /**
     * The answer to "the customer is asking for the inspection certificate": the same document in
     * two versions, so the history is visible rather than asserted.
     */
    private void createInspectionRecords(Department werkstatt, PUser admin) {
        DocumentGroup group = documentService.createDocumentGroup("Prüfnachweise", werkstatt, admin);
        DocumentInfo protocol = documentService.createDocument(
                "Pruefprotokoll_Betriebsmittel.txt", group.getId(), admin,
                ("Prüfprotokoll ortsveränderlicher Betriebsmittel\n"
                        + "Prüfdatum: " + date(LocalDate.now().minusMonths(11)) + "\n"
                        + "Prüfer: Stefan Brandt (befähigte Person)\n"
                        + "Ergebnis: alle geprüften Geräte in Ordnung.\n").getBytes(StandardCharsets.UTF_8),
                "text/plain");
        // A second version has to travel the same road a person would: check out, then check in.
        // addNewVersion on its own refuses ("document must be checked out first") — the lock is the
        // model's way of saying that two people cannot write version 2 at the same time.
        documentService.checkOut(protocol.getId(), admin);
        documentService.checkIn(protocol.getId(),
                ("Prüfprotokoll ortsveränderlicher Betriebsmittel\n"
                        + "Prüfdatum: " + date(LocalDate.now().minusWeeks(6)) + "\n"
                        + "Prüfer: Stefan Brandt (befähigte Person)\n"
                        + "Ergebnis: alle geprüften Geräte in Ordnung.\n"
                        + "Anmerkung: Anlegeleiter dreiteilig nicht vorgelegt — Prüfung offen.\n")
                        .getBytes(StandardCharsets.UTF_8),
                "text/plain", "Nachprüfung, Leiter fehlte", admin);
    }

    // ==========================================
    // Dates — everything relative to today, see the class comment
    // ==========================================

    /** Skips back over weekends, so booked hours never land on a Sunday. */
    private LocalDate workdayBefore(int days) {
        LocalDate day = LocalDate.now().minusDays(days);
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.minusDays(1);
        }
        return day;
    }

    private LocalDate nextFriday() {
        LocalDate day = LocalDate.now();
        while (day.getDayOfWeek().getValue() != 5) {
            day = day.plusDays(1);
        }
        return day;
    }

    private Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(ZONE).toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    private Instant endOfDay(LocalDate day) {
        return LocalDateTime.of(day, LocalTime.of(23, 59)).atZone(ZONE).toInstant();
    }

    private String date(LocalDate day) {
        return day.toString();
    }
}
