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

package de.hallerweb.enterprise.prioritize.ui.scheduling;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleDTO;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleRequest;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.scheduling.TaskScheduleService;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;
import jakarta.annotation.security.PermitAll;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/**
 * Master-detail admin screen for the recurring {@link de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule}s
 * of a project: a schedule fires on its cron cadence and drops a task from its template onto the project's
 * blackboard.
 * <p>
 * Unlike the other admin views this one is <b>not</b> company-wide: projects are deliberately membership-based
 * and there is no Projects admin view, so the screen starts from a "pick one of <em>my</em> projects" selector
 * fed by {@link ProjectService#getMyProjects(PUser)}. That keeps it membership-safe by construction — a project
 * the admin is not part of never appears, and no membership bypass had to be invented for the GUI.
 * <p>
 * Authorization mirrors the REST slice and stays in {@link TaskScheduleService}: every member may read a
 * project's schedules, only its manager may create, change or delete them. The view reflects that up front
 * (the editor turns read-only for a project the user merely participates in) but still relies on the service
 * as the authority, mapping its exceptions to notifications.
 *
 * @author peter haller
 */
@Route("task-schedules")
@PageTitle("Task Schedules | Prioritize")
@PermitAll
public class TaskSchedulesView extends SplitLayout {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final transient TaskScheduleService scheduleService;
    private final transient ProjectService projectService;
    private final transient CurrentUser currentUser;

    /**
     * One entry of the project selector. A flat view-local record rather than the {@link Project} entity: it
     * carries the {@code manager} flag the read-only handling needs, and keeps the entity out of the UI layer.
     */
    private record ProjectOption(Long id, String name, boolean managed) implements Serializable {
    }

    private final ComboBox<ProjectOption> project = new ComboBox<>("Project");
    private final Grid<TaskScheduleDTO> grid = new Grid<>(TaskScheduleDTO.class, false);
    private final Button add = new Button("New schedule");
    private final Span readOnlyHint =
            new Span("Read-only — only the project's manager may change its schedules.");

    // --- schedule editor ---
    private final TextField name = new TextField("Schedule name");
    private final TextField taskName = new TextField("Task name");
    private final TextArea taskDescription = new TextArea("Task description");
    private final IntegerField taskPriority = new IntegerField("Task priority");
    private final TextField cronExpression = new TextField("Cron expression");
    private final ComboBox<String> zoneId = new ComboBox<>("Time zone");
    private final Checkbox enabled = new Checkbox("Enabled");
    private final Span fireTimes = new Span();
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder =
            new Span("Select a project, then pick a schedule on the left or create a new one.");

    private Long editingId; // null while creating
    private boolean creating;

    public TaskSchedulesView(TaskScheduleService scheduleService, ProjectService projectService,
                             CurrentUser currentUser) {
        this.scheduleService = scheduleService;
        this.projectService = projectService;
        this.currentUser = currentUser;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildEditor());
        setSplitterPosition(30); // start at 30/70 (grid/form), like the other admin views

        loadProjects();
        applyPermissions(); // no project picked yet -> no "New schedule" button
        showEditor(false);
    }

    // ---- project selection --------------------------------------------------------------------

    private void loadProjects() {
        PUser user = currentUser.require();
        List<ProjectOption> options = new ArrayList<>();
        for (Project p : projectService.getMyProjects(user)) {
            options.add(new ProjectOption(p.getId(), p.getName(), isManagedBy(p, user)));
        }
        project.setItems(options);
        if (options.isEmpty()) {
            project.setHelperText("You are not a member of any project yet.");
        } else if (options.size() == 1) {
            project.setValue(options.get(0)); // nothing to choose — go straight to its schedules
        }
    }

    /** Both {@code manager} and {@code members} are eagerly mapped, so this is safe outside a transaction. */
    private static boolean isManagedBy(Project project, PUser user) {
        PActor manager = project.getManager();
        return manager != null && manager.getId().equals(user.getId());
    }

    private boolean mayEdit() {
        return project.getValue() != null && project.getValue().managed();
    }

    /**
     * Applies the selected project's permissions to the whole screen. Delete additionally depends on the
     * editor's mode — there is nothing to delete yet while a new schedule is being created.
     */
    private void applyPermissions() {
        boolean selected = project.getValue() != null;
        boolean editable = mayEdit();
        add.setVisible(selected && editable);
        readOnlyHint.setVisible(selected && !editable);
        save.setVisible(editable);
        delete.setVisible(editable && !creating && editingId != null);
        setFieldsReadOnly(!editable);
    }

    private void setFieldsReadOnly(boolean readOnly) {
        name.setReadOnly(readOnly);
        taskName.setReadOnly(readOnly);
        taskDescription.setReadOnly(readOnly);
        taskPriority.setReadOnly(readOnly);
        cronExpression.setReadOnly(readOnly);
        zoneId.setReadOnly(readOnly);
        enabled.setReadOnly(readOnly);
    }

    // ---- left side: project selector + schedule grid --------------------------------------------

    private VerticalLayout buildGridSide() {
        project.setItemLabelGenerator(ProjectOption::name);
        project.setWidthFull();
        project.addValueChangeListener(e -> {
            grid.deselectAll();
            showEditor(false);
            applyPermissions();
            refresh();
        });

        grid.addColumn(TaskScheduleDTO::name).setHeader("Schedule").setAutoWidth(true).setSortable(true);
        grid.addColumn(TaskScheduleDTO::taskName).setHeader("Task").setAutoWidth(true);
        grid.addColumn(TaskScheduleDTO::cronExpression).setHeader("Cron").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::enabledToggle)).setHeader("Enabled").setAutoWidth(true);
        grid.addColumn(s -> timestamp(s.nextFireAt())).setHeader("Next fire").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> {
            grid.deselectAll();
            edit(null, true);
        });

        readOnlyHint.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        readOnlyHint.setVisible(false);

        VerticalLayout side = new VerticalLayout(project, add, readOnlyHint, grid);
        side.setSizeFull();
        side.setPadding(false);
        side.setSpacing(true);
        return side;
    }

    /** Inline pause/resume — the one tuning action frequent enough to not deserve a trip through the editor. */
    private Checkbox enabledToggle(TaskScheduleDTO schedule) {
        Checkbox toggle = new Checkbox(schedule.enabled());
        toggle.setReadOnly(!mayEdit());
        toggle.addValueChangeListener(e -> {
            if (Boolean.valueOf(e.getValue()).equals(schedule.enabled())) {
                return; // programmatic / no-op
            }
            patch(schedule.id(),
                    new TaskScheduleRequest(null, null, null, null, null, null, e.getValue()),
                    e.getValue() ? "Schedule resumed" : "Schedule paused");
        });
        return toggle;
    }

    // ---- right side: editor ---------------------------------------------------------------------

    private VerticalLayout buildEditor() {
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> delete());
        cancel.addClickListener(e -> cancel());

        name.setWidthFull();
        name.setHelperText("Defaults to the task name when left empty.");
        taskName.setWidthFull();
        taskDescription.setWidthFull();
        cronExpression.setWidthFull();
        cronExpression.setHelperText("6 fields: second minute hour day-of-month month day-of-week "
                + "— e.g. '0 0 8 * * MON-FRI' for weekdays at 08:00.");

        // ~600 IANA ids; the ComboBox filters lazily. Empty means "use the server's zone".
        zoneId.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        zoneId.setClearButtonVisible(true);
        zoneId.setWidthFull();
        zoneId.setHelperText("Leave empty to evaluate the cron in the server's time zone.");

        fireTimes.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, taskName, taskDescription, taskPriority,
                cronExpression, zoneId, enabled, fireTimes, actions);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("320px");
        editor.setPadding(true);
        editor.setHeightFull();
        editor.getStyle().set("overflow-y", "auto");
        return editor;
    }

    private void edit(TaskScheduleDTO schedule, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : schedule.id();

        name.setValue(creating || schedule.name() == null ? "" : schedule.name());
        taskName.setValue(creating || schedule.taskName() == null ? "" : schedule.taskName());
        taskDescription.setValue(
                creating || schedule.taskDescription() == null ? "" : schedule.taskDescription());
        taskPriority.setValue(creating ? 0 : schedule.taskPriority());
        cronExpression.setValue(creating || schedule.cronExpression() == null ? "" : schedule.cronExpression());
        zoneId.setValue(creating || schedule.zoneId() == null || schedule.zoneId().isBlank()
                ? null : schedule.zoneId());
        enabled.setValue(creating || schedule.enabled());
        fireTimes.setText(creating ? ""
                : "Next fire: " + timestamp(schedule.nextFireAt())
                        + "   ·   Last fired: " + timestamp(schedule.lastFiredAt()));

        clearInvalid();
        showEditor(true); // applies the permissions, including the mode-dependent Delete button
    }

    private void save() {
        ProjectOption selected = project.getValue();
        if (selected == null) {
            return;
        }
        clearInvalid();
        boolean invalid = false;
        if (taskName.getValue() == null || taskName.getValue().isBlank()) {
            taskName.setInvalid(true);
            taskName.setErrorMessage("Task name is required");
            invalid = true;
        }
        if (cronExpression.getValue() == null || cronExpression.getValue().isBlank()) {
            cronExpression.setInvalid(true);
            cronExpression.setErrorMessage("Cron expression is required");
            invalid = true;
        }
        if (invalid) {
            return;
        }

        // An empty zone is sent as "" rather than null: null means "leave unchanged" in a PATCH, so it
        // could never be reset to the server zone once set.
        TaskScheduleRequest request = new TaskScheduleRequest(
                name.getValue() == null ? "" : name.getValue().trim(),
                taskName.getValue().trim(),
                taskDescription.getValue(),
                taskPriority.getValue() == null ? 0 : taskPriority.getValue(),
                cronExpression.getValue().trim(),
                zoneId.getValue() == null ? "" : zoneId.getValue(),
                enabled.getValue());

        try {
            if (creating) {
                scheduleService.createSchedule(selected.id(), request, currentUser.require());
                notifySuccess("Schedule created");
            } else {
                scheduleService.updateSchedule(editingId, request, currentUser.require());
                notifySuccess("Schedule updated");
            }
            showEditor(false);
            refresh();
        } catch (AccessDeniedException denied) {
            notifyError("Only the project's manager may change its schedules.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    private void delete() {
        if (creating || editingId == null) {
            return;
        }
        try {
            scheduleService.deleteSchedule(editingId, currentUser.require());
            notifySuccess("Schedule deleted — already generated tasks are kept.");
            showEditor(false);
            refresh();
        } catch (AccessDeniedException denied) {
            notifyError("Only the project's manager may delete its schedules.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    /** Applies a partial change from the grid (the inline toggle) and reports the outcome. */
    private void patch(Long scheduleId, TaskScheduleRequest request, String successMessage) {
        try {
            scheduleService.updateSchedule(scheduleId, request, currentUser.require());
            notifySuccess(successMessage);
        } catch (AccessDeniedException denied) {
            notifyError("Only the project's manager may change its schedules.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
        refresh(); // reload either way, so a rejected toggle snaps back to the persisted state
    }

    private void cancel() {
        grid.deselectAll();
        showEditor(false);
    }

    private void refresh() {
        ProjectOption selected = project.getValue();
        if (selected == null) {
            grid.setItems(List.of());
            return;
        }
        try {
            grid.setItems(scheduleService.getSchedules(selected.id(), currentUser.require()));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
            notifyError("You no longer have access to this project.");
        }
    }

    private void showEditor(boolean visible) {
        formFields.setVisible(visible);
        placeholder.setVisible(!visible);
        if (visible) {
            applyPermissions();
        }
    }

    private void clearInvalid() {
        taskName.setInvalid(false);
        cronExpression.setInvalid(false);
    }

    private static String timestamp(LocalDateTime value) {
        return value == null ? "—" : TIMESTAMP.format(value);
    }

    private void notifySuccess(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_START);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.BOTTOM_START);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
