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

package de.hallerweb.enterprise.prioritize.ui.process;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessInstanceDTO;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.process.ProcessDefinitionService;
import de.hallerweb.enterprise.prioritize.service.process.ProcessInstanceService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;
import jakarta.annotation.security.PermitAll;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;

/**
 * Master-detail admin screen for the BPMN process instances of a project: which processes are running or
 * have finished for it, start a new one from an active definition, and — as a last resort — cancel one.
 * This is the membership-scoped half of the Flowable admin GUI; registering and deploying the definitions
 * themselves lives in the platform-wide {@link ProcessDefinitionsView}, mirroring the two backing services.
 * <p>
 * Like {@link de.hallerweb.enterprise.prioritize.ui.scheduling.TaskSchedulesView} this view is <b>not</b>
 * company-wide: process instances are authorized by project membership, so the screen starts from a "pick
 * one of <em>my</em> projects" selector fed by {@link ProjectService#getMyProjects(PUser)} — membership-safe
 * by construction. Authorization mirrors the REST slice and stays in {@link ProcessInstanceService}: every
 * member may read and start, only the manager may cancel. The view reflects that (Cancel is shown only for a
 * project the user manages) but relies on the service as the authority, mapping its exceptions to notifications.
 * <p>
 * <b>Starting</b> lets the user pick one of the active definitions and, optionally, hand the instance a few
 * initial process variables as {@code key=value} lines — e.g. {@code awaitedResourceId=42} for the outbound
 * event correlation. The list of startable definitions comes from {@link ProcessDefinitionService#getAll},
 * which is Read-gated on the definition type; a member without that right sees no definitions to start, exactly
 * as they would over REST. Variables are for wiring order and correlation only — business logic stays in Java.
 *
 * @author peter haller
 */
@Route("process-instances")
@PageTitle("Process Instances | Prioritize")
@PermitAll
public class ProcessInstancesView extends SplitLayout {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final transient ProcessInstanceService instanceService;
    private final transient ProcessDefinitionService definitionService;
    private final transient ProjectService projectService;
    private final transient CurrentUser currentUser;

    /** One entry of the project selector, carrying the {@code managed} flag the Cancel gating needs. */
    private record ProjectOption(Long id, String name, boolean managed) implements Serializable {
    }

    private final ComboBox<ProjectOption> project = new ComboBox<>("Project");
    private final Grid<ProcessInstanceDTO> grid = new Grid<>(ProcessInstanceDTO.class, false);
    private final Button start = new Button("Start process");

    // --- detail pane ---
    private final Span definition = new Span();
    private final Span businessKey = new Span();
    private final Span belongsTo = new Span();
    private final Span status = new Span();
    private final Span started = new Span();
    private final Button cancel = new Button("Cancel instance");
    private final VerticalLayout detail = new VerticalLayout();
    private final Span placeholder =
            new Span("Select a project, then pick a process instance on the left or start a new one.");

    private ProcessInstanceDTO selected;

    public ProcessInstancesView(ProcessInstanceService instanceService,
                                ProcessDefinitionService definitionService,
                                ProjectService projectService, CurrentUser currentUser) {
        this.instanceService = instanceService;
        this.definitionService = definitionService;
        this.projectService = projectService;
        this.currentUser = currentUser;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildDetail());
        setSplitterPosition(30); // 30/70 (grid/detail), like the other admin views

        loadProjects();
        showDetail(false);
        applyProjectState();
    }

    // ---- project selection ----------------------------------------------------------------------

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
            project.setValue(options.get(0)); // nothing to choose — go straight to its instances
        }
    }

    /** {@code manager} is eagerly mapped on the finder result, so this is safe outside a transaction. */
    private static boolean isManagedBy(Project project, PUser user) {
        PActor manager = project.getManager();
        return manager != null && manager.getId().equals(user.getId());
    }

    private void applyProjectState() {
        start.setEnabled(project.getValue() != null);
    }

    // ---- left side: project selector + instance grid --------------------------------------------

    private VerticalLayout buildGridSide() {
        project.setItemLabelGenerator(ProjectOption::name);
        project.setWidthFull();
        project.addValueChangeListener(e -> {
            grid.deselectAll();
            showDetail(false);
            applyProjectState();
            refresh();
        });

        grid.addColumn(ProcessInstanceDTO::processKey).setHeader("Process").setAutoWidth(true).setSortable(true);
        grid.addColumn(ProcessInstanceDTO::businessKey).setHeader("For").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(i -> runningBadge(i.running()))).setHeader("State").setAutoWidth(true);
        grid.addColumn(i -> timestamp(i.startedAt())).setHeader("Started").setAutoWidth(true);
        grid.addColumn(ProcessInstanceDTO::startedBy).setHeader("By").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                select(e.getValue());
            }
        });

        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        start.addClickListener(e -> openStartDialog());

        VerticalLayout side = new VerticalLayout(project, start, grid);
        side.setSizeFull();
        side.setPadding(false);
        side.setSpacing(true);
        return side;
    }

    private static Span runningBadge(boolean running) {
        Span badge = new Span(running ? "RUNNING" : "FINISHED");
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(running ? "success" : "contrast");
        return badge;
    }

    // ---- right side: detail + cancel ------------------------------------------------------------

    private VerticalLayout buildDetail() {
        cancel.addThemeVariants(ButtonVariant.LUMO_ERROR);
        cancel.addClickListener(e -> confirmCancel());

        detail.add(new H4("Process instance"), definition, businessKey, belongsTo, status, started, cancel);
        detail.setPadding(true);
        detail.setMinWidth("320px");
        detail.setHeightFull();
        detail.getStyle().set("overflow-y", "auto");

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout side = new VerticalLayout(placeholder, detail);
        side.setSizeFull();
        side.setPadding(false);
        return side;
    }

    private void select(ProcessInstanceDTO dto) {
        this.selected = dto;
        definition.setText("Process: " + dto.processKey() + "   ·   instance " + dto.id());
        businessKey.setText("Business key: " + dto.businessKey());
        belongsTo.setText(dto.taskId() != null
                ? "Belongs to task #" + dto.taskId()
                : "Belongs to project #" + dto.projectId());
        status.getElement().removeAllChildren();
        status.add(new Span("State: "), runningBadge(dto.running()));
        started.setText("Started " + timestamp(dto.startedAt())
                + (dto.startedBy() != null ? " by " + dto.startedBy() : ""));

        // Cancel is manager-only and only meaningful while the instance is still running.
        cancel.setVisible(mayManage() && dto.running());
        showDetail(true);
    }

    private boolean mayManage() {
        return project.getValue() != null && project.getValue().managed();
    }

    // ---- start dialog ---------------------------------------------------------------------------

    private void openStartDialog() {
        ProjectOption selectedProject = project.getValue();
        if (selectedProject == null) {
            return;
        }

        Dialog dialog = new Dialog("Start a process for '" + selectedProject.name() + "'");
        dialog.setWidth("440px");

        ComboBox<ProcessDefinitionDTO> definitions = new ComboBox<>("Definition");
        definitions.setItemLabelGenerator(d -> d.processKey() + (d.name() != null ? " — " + d.name() : ""));
        definitions.setWidthFull();
        List<ProcessDefinitionDTO> active = activeDefinitions();
        definitions.setItems(active);
        if (active.isEmpty()) {
            definitions.setEnabled(false);
            definitions.setHelperText("No active definitions you may start. "
                    + "Activate one first, or ask for Read access on process definitions.");
        }

        TextArea variables = new TextArea("Initial variables (optional)");
        variables.setWidthFull();
        variables.setHelperText("One key=value per line, e.g. awaitedResourceId=42");

        Button confirm = new Button("Start");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setEnabled(false);
        definitions.addValueChangeListener(e -> confirm.setEnabled(e.getValue() != null));
        confirm.addClickListener(e -> {
            ProcessDefinitionDTO def = definitions.getValue();
            if (def == null) {
                return;
            }
            Map<String, Object> vars;
            try {
                vars = parseVariables(variables.getValue());
            } catch (IllegalArgumentException bad) {
                variables.setInvalid(true);
                variables.setErrorMessage(bad.getMessage());
                return;
            }
            try {
                instanceService.startForProject(selectedProject.id(), def.id(), vars, currentUser.require());
                dialog.close();
                notifySuccess("Process started.");
                refresh();
            } catch (AccessDeniedException denied) {
                notifyError("You are not allowed to start a process on this project.");
            } catch (RuntimeException ex) {
                // e.g. the definition is no longer active
                notifyError(ex.getMessage());
            }
        });

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.add(new VerticalLayout(definitions, variables));
        dialog.getFooter().add(cancelButton, confirm);
        dialog.open();
    }

    /** The active definitions the user may start, or an empty list if they cannot see definitions at all. */
    private List<ProcessDefinitionDTO> activeDefinitions() {
        try {
            return definitionService.getAll(currentUser.require()).stream()
                    .filter(d -> d.state() == ProcessDefinitionState.ACTIVE)
                    .toList();
        } catch (AccessDeniedException denied) {
            return List.of(); // no Read right on process definitions — nothing to offer
        }
    }

    /**
     * Parses the {@code key=value} lines of the variables field into process variables. Kept deliberately
     * simple: blank lines and {@code #} comments are ignored, a numeric value becomes a {@code Long} or
     * {@code Double} (so a resource id correlates as a number), everything else stays a trimmed String.
     *
     * @throws IllegalArgumentException if a non-blank line has no {@code =} or an empty key
     */
    private static Map<String, Object> parseVariables(String text) {
        Map<String, Object> vars = new HashMap<>();
        if (text == null || text.isBlank()) {
            return vars;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Each line must be 'key=value': " + line);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Empty key in: " + line);
            }
            vars.put(key, coerce(value));
        }
        return vars;
    }

    /** Coerces a value string to a Long or Double when it looks numeric, otherwise leaves it a String. */
    private static Object coerce(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException notLong) {
            // fall through
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException notDouble) {
            return value;
        }
    }

    // ---- cancel ---------------------------------------------------------------------------------

    private void confirmCancel() {
        if (selected == null || !selected.running()) {
            return;
        }
        Dialog dialog = new Dialog("Cancel process instance");
        dialog.add(new Span("Cancelling stops process '" + selected.processKey()
                + "' for good; tasks it created stay on the blackboard. The reason is kept in the engine history."));
        TextField reason = new TextField("Reason (optional)");
        reason.setWidthFull();
        dialog.add(new VerticalLayout(reason));

        Button confirm = new Button("Cancel instance", e -> {
            dialog.close();
            try {
                instanceService.cancel(selected.id(), reason.getValue(), currentUser.require());
                notifySuccess("Process instance cancelled.");
                refresh();
            } catch (AccessDeniedException denied) {
                notifyError("Only the project's manager may cancel its process instances.");
            } catch (RuntimeException ex) {
                notifyError(ex.getMessage());
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        Button keep = new Button("Keep running", e -> dialog.close());
        dialog.getFooter().add(keep, confirm);
        dialog.open();
    }

    // ---- shared ---------------------------------------------------------------------------------

    private void refresh() {
        ProjectOption selectedProject = project.getValue();
        if (selectedProject == null) {
            grid.setItems(List.of());
            return;
        }
        try {
            grid.setItems(instanceService.getForProject(selectedProject.id(), currentUser.require()));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
            notifyError("You no longer have access to this project.");
        }
        // The just-selected instance may be gone or have changed state — reset the detail pane.
        grid.deselectAll();
        showDetail(false);
    }

    private void showDetail(boolean visible) {
        detail.setVisible(visible);
        placeholder.setVisible(!visible);
    }

    private static String timestamp(Instant value) {
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
