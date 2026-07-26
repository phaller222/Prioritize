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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.process.ProcessDefinitionService;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;
import jakarta.annotation.security.PermitAll;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/**
 * Master-detail admin screen for the registered BPMN {@link ProcessDefinition}s: register one from a
 * document that carries a diagram, deploy it (activate), take it out of service (deactivate), and
 * remove it. This is the platform-wide half of the Flowable admin GUI; running instances live in the
 * membership-scoped {@link ProcessInstancesView}, mirroring the split between the two backing services.
 * <p>
 * Unlike the other admin views, definitions are <b>not</b> simply gated by being logged in: like
 * {@link ProcessDefinitionService} itself, they are governed by a <b>type-level</b> permission on
 * {@link ProcessDefinition} (Create to register, Read to see, Update to (de)activate, Delete to
 * remove), with admins allowed implicitly. The view reflects that up front — a button the user may not
 * use is not shown — but still relies on the service as the authority, mapping its exceptions to
 * notifications.
 * <p>
 * Registering picks a document through the same Department&nbsp;&rarr;&nbsp;Group&nbsp;&rarr;&nbsp;Document
 * cascade the {@link de.hallerweb.enterprise.prioritize.ui.document.DocumentsView} uses; the process key is
 * read out of the file by the service, never supplied here.
 *
 * @author peter haller
 */
@Route("process-definitions")
@PageTitle("Process Definitions | Prioritize")
@PermitAll
public class ProcessDefinitionsView extends SplitLayout {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Same target type the service authorizes against — a type-level permission ({@code objectId == 0}). */
    private static final String TYPE = ProcessDefinition.class.getCanonicalName();

    private final transient ProcessDefinitionService definitionService;
    private final transient DocumentService documentService;
    private final transient DepartmentService departmentService;
    private final transient AuthorizationService authService;
    private final transient CurrentUser currentUser;

    /** A document-group option for the register dialog — decoupled from the lazy-relation-carrying entity. */
    private record GroupOption(Long id, String name) implements Serializable {
    }

    private final Grid<ProcessDefinitionDTO> grid = new Grid<>(ProcessDefinitionDTO.class, false);
    private final Button register = new Button("Register from document");

    // --- detail pane ---
    private final Span key = new Span();
    private final Span name = new Span();
    private final Span state = new Span();
    private final Span source = new Span();
    private final Span deployment = new Span();
    private final Button activate = new Button("Activate");
    private final Button deactivate = new Button("Deactivate");
    private final Button remove = new Button("Remove");
    private final VerticalLayout detail = new VerticalLayout();
    private final Span placeholder = new Span("Select a process definition on the left, or register one.");
    private final Span noAccess =
            new Span("You do not have permission to view process definitions.");

    private final boolean canRead;
    private final boolean canRegister;
    private final boolean canUpdate;
    private final boolean canDelete;

    private ProcessDefinitionDTO selected;

    public ProcessDefinitionsView(ProcessDefinitionService definitionService, DocumentService documentService,
                                  DepartmentService departmentService, AuthorizationService authService,
                                  CurrentUser currentUser) {
        this.definitionService = definitionService;
        this.documentService = documentService;
        this.departmentService = departmentService;
        this.authService = authService;
        this.currentUser = currentUser;

        PUser user = currentUser.require();
        this.canRead = authService.hasPermission(user, TYPE, 0L, Action.READ);
        this.canRegister = authService.hasPermission(user, TYPE, 0L, Action.CREATE);
        this.canUpdate = authService.hasPermission(user, TYPE, 0L, Action.UPDATE);
        this.canDelete = authService.hasPermission(user, TYPE, 0L, Action.DELETE);

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildDetail());
        setSplitterPosition(30); // 30/70 (grid/detail), like the other admin views

        refresh();
        showDetail(false);
    }

    // ---- left side: register + definition grid --------------------------------------------------

    private VerticalLayout buildGridSide() {
        grid.addColumn(ProcessDefinitionDTO::processKey).setHeader("Process key").setAutoWidth(true).setSortable(true);
        grid.addColumn(ProcessDefinitionDTO::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(d -> stateBadge(d.state()))).setHeader("State").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                select(e.getValue());
            }
        });

        register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        register.setVisible(canRegister);
        register.addClickListener(e -> openRegisterDialog());

        VerticalLayout side = new VerticalLayout(register, noAccess, grid);
        side.setSizeFull();
        side.setPadding(false);
        side.setSpacing(true);
        noAccess.setVisible(false);
        noAccess.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return side;
    }

    /** A coloured badge for the definition's state, matching Lumo's badge theme. */
    private static Span stateBadge(ProcessDefinitionState state) {
        Span badge = new Span(state == null ? "—" : state.name());
        badge.getElement().getThemeList().add("badge");
        if (state == ProcessDefinitionState.ACTIVE) {
            badge.getElement().getThemeList().add("success");
        } else if (state == ProcessDefinitionState.SUSPENDED) {
            badge.getElement().getThemeList().add("contrast");
        } // DRAFT keeps the default (neutral) badge
        return badge;
    }

    // ---- right side: detail + actions -----------------------------------------------------------

    private VerticalLayout buildDetail() {
        activate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        activate.addClickListener(e -> activate());
        activate.setVisible(canUpdate);

        deactivate.addClickListener(e -> deactivate());
        deactivate.setVisible(canUpdate);

        remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
        remove.addClickListener(e -> confirmRemove());
        remove.setVisible(canDelete);

        HorizontalLayout actions = new HorizontalLayout(activate, deactivate, remove);

        detail.add(new H4("Process definition"), key, name, state, source, deployment, actions);
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

    private void select(ProcessDefinitionDTO dto) {
        this.selected = dto;
        key.setText("Process key: " + dto.processKey());
        name.setText("Name: " + (dto.name() != null ? dto.name() : "—"));
        state.getElement().removeAllChildren();
        state.add(new Span("State: "), stateBadge(dto.state()));
        source.setText("Source document: #" + (dto.documentInfoId() != null ? dto.documentInfoId() : "—"));
        deployment.setText(dto.deploymentId() == null
                ? "Not deployed yet."
                : "Deployment " + dto.deploymentId() + " · document v" + dto.deployedVersion()
                        + " · deployed " + TIMESTAMP.format(dto.deployedAt())
                        + (dto.deployedBy() != null ? " by " + dto.deployedBy() : ""));

        // Activate is valid from DRAFT (deploy) and SUSPENDED (resume); deactivate only from ACTIVE.
        activate.setEnabled(canUpdate && dto.state() != ProcessDefinitionState.ACTIVE);
        deactivate.setEnabled(canUpdate && dto.state() == ProcessDefinitionState.ACTIVE);
        remove.setEnabled(canDelete);
        showDetail(true);
    }

    private void activate() {
        if (selected == null) {
            return;
        }
        run(() -> definitionService.activate(selected.id(), currentUser.require()),
                "Definition activated.");
    }

    private void deactivate() {
        if (selected == null) {
            return;
        }
        run(() -> definitionService.deactivate(selected.id(), currentUser.require()),
                "Definition deactivated — running instances continue.");
    }

    /**
     * Removing a deployed definition tears down its engine deployment and is refused while instances run,
     * so it always goes through a confirmation. A draft is removed with a plain unregister; anything that
     * has been deployed needs {@code force} — the state decides which.
     */
    private void confirmRemove() {
        if (selected == null) {
            return;
        }
        boolean deployed = selected.state() != ProcessDefinitionState.DRAFT;

        Dialog dialog = new Dialog("Remove process definition");
        Span message = new Span(deployed
                ? "'" + selected.processKey() + "' has been deployed. Removing it also tears down its engine "
                        + "deployment; this is refused while instances are still running. Continue?"
                : "Remove the draft definition '" + selected.processKey() + "'?");
        Button confirm = new Button("Remove", e -> {
            dialog.close();
            run(() -> definitionService.unregister(selected.id(), deployed, currentUser.require()),
                    "Definition removed.");
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(message);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    // ---- register dialog ------------------------------------------------------------------------

    private void openRegisterDialog() {
        Dialog dialog = new Dialog("Register process definition from document");
        dialog.setWidth("420px");

        ComboBox<Department> department = new ComboBox<>("Department");
        department.setItems(departmentService.getAllDepartments());
        department.setItemLabelGenerator(Department::getName);
        department.setWidthFull();

        ComboBox<GroupOption> group = new ComboBox<>("Document group");
        group.setItemLabelGenerator(GroupOption::name);
        group.setWidthFull();
        group.setEnabled(false);

        ComboBox<DocumentSummaryDTO> document = new ComboBox<>("Document (BPMN)");
        document.setItemLabelGenerator(d -> d.getName() + " (v" + d.getCurrentVersion() + ")");
        document.setWidthFull();
        document.setEnabled(false);

        Button confirm = new Button("Register");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setEnabled(false);

        PUser user = currentUser.require();
        department.addValueChangeListener(e -> {
            group.clear();
            document.clear();
            document.setEnabled(false);
            confirm.setEnabled(false);
            if (e.getValue() == null) {
                group.setItems(List.of());
                group.setEnabled(false);
                return;
            }
            // Map to a record right away: a DocumentGroup's equals()/hashCode() touches its lazy department
            // and would throw inside the ComboBox key mapper (same guard as DocumentsView).
            group.setItems(documentService.getDocumentGroupsByDepartment(e.getValue().getId(), user)
                    .stream().map(g -> new GroupOption(g.getId(), g.getName())).toList());
            group.setEnabled(true);
        });
        group.addValueChangeListener(e -> {
            document.clear();
            confirm.setEnabled(false);
            if (e.getValue() == null) {
                document.setItems(List.of());
                document.setEnabled(false);
                return;
            }
            document.setItems(documentService.getDocumentsInGroupAsDTO(e.getValue().id(), user));
            document.setEnabled(true);
        });
        document.addValueChangeListener(e -> confirm.setEnabled(e.getValue() != null));

        confirm.addClickListener(e -> {
            DocumentSummaryDTO doc = document.getValue();
            if (doc == null) {
                return;
            }
            try {
                definitionService.register(doc.getId(), currentUser.require());
                dialog.close();
                notifySuccess("Definition registered as draft.");
                refresh();
            } catch (AccessDeniedException denied) {
                notifyError("You are not allowed to register process definitions.");
            } catch (RuntimeException ex) {
                // e.g. no usable BPMN in the document, or the process key is already registered
                notifyError(ex.getMessage());
            }
        });

        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(new VerticalLayout(department, group, document));
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    // ---- shared -------------------------------------------------------------------------------

    /** Runs a mutating service call, refreshing and re-selecting the affected row, mapping errors to toasts. */
    private void run(ServiceCall call, String successMessage) {
        try {
            call.execute();
            notifySuccess(successMessage);
            Long id = selected != null ? selected.id() : null;
            refresh();
            reselect(id);
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to do that.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
            refresh();
        }
    }

    private void refresh() {
        if (!canRead) {
            grid.setItems(List.of());
            noAccess.setVisible(true);
            return;
        }
        try {
            grid.setItems(definitionService.getAll(currentUser.require()));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
            noAccess.setVisible(true);
        }
    }

    /** Re-selects a row by id after a refresh, or clears the detail pane if it is gone (e.g. removed). */
    private void reselect(Long id) {
        if (id == null) {
            grid.deselectAll();
            showDetail(false);
            return;
        }
        grid.getListDataView().getItems()
                .filter(d -> id.equals(d.id()))
                .findFirst()
                .ifPresentOrElse(grid::select, () -> {
                    grid.deselectAll();
                    showDetail(false);
                });
    }

    private void showDetail(boolean visible) {
        detail.setVisible(visible);
        placeholder.setVisible(!visible && canRead);
    }

    private void notifySuccess(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_START);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.BOTTOM_START);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    @FunctionalInterface
    private interface ServiceCall {
        void execute();
    }
}
