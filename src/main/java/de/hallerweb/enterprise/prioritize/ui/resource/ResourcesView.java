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

package de.hallerweb.enterprise.prioritize.ui.resource;
import de.hallerweb.enterprise.prioritize.ui.telemetry.TelemetryRulesPanel;
import de.hallerweb.enterprise.prioritize.ui.group.GroupsView;
import de.hallerweb.enterprise.prioritize.ui.document.DocumentsView;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceReservationDTO;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.telemetry.TelemetryRuleService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Admin resources screen for the content layer beneath a {@link Department}: the resources of one of its
 * {@link de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup}s, with a green/grey online dot for
 * MQTT devices and an occupancy overview of the reservation slots. Two selectors pick the context — a
 * department ({@link DepartmentService#getAllDepartments()}) and one of its resource groups
 * ({@link ResourceService#getResourceGroupsByDepartment}).
 * <p>
 * The master grid is <b>lazy</b>: it is fed by {@link ResourceService#getResourcesInGroup} +
 * {@link ResourceService#countResourcesInGroup} through a paged data provider, so a large group never loads
 * its whole resource set into server memory (Resources is the unbounded view the paging plan targets). Rows are
 * a {@link ResourceSummaryDTO}, never the {@code Resource} entity: {@code Resource}'s all-fields
 * {@code equals}/{@code hashCode} would drag its lazy relations into the grid's key mapper and throw a
 * {@code LazyInitializationException} (same trap handled in {@link GroupsView}/{@link DocumentsView}).
 * <p>
 * <b>Live status</b> is polling-based: while attached the view sets a ~5&nbsp;s {@code pollInterval} and
 * refreshes the grid on each poll, so the online dots reflect the latest {@code mqttOnline}/{@code mqttLastPing}
 * without a manual reload. The status itself is maintained elsewhere (MQTT STATUS →
 * {@link ResourceService#setMqttResourceStatusByUuid}); this view only reads it.
 * <p>
 * <b>Reservations are display + cancel only</b> (admin-lite, mirroring {@link DocumentsView}): the detail pane
 * shows the selected resource's reservations and lets the admin release one. Creating a reservation stays a
 * user/client concern over REST.
 *
 * @author peter haller
 */
@Route("resources")
@PageTitle("Resources | Prioritize")
@PermitAll
public class ResourcesView extends VerticalLayout {

    /** How often the grid is refreshed to pick up online-status changes. */
    private static final int POLL_INTERVAL_MS = 5000;

    /** A resource-group option for the selector — decoupled from the lazy-relation-carrying entity. */
    private record GroupOption(Long id, String name) {
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault());

    private final transient ResourceService resourceService;
    private final transient DepartmentService departmentService;
    private final transient CurrentUser currentUser;

    private final ComboBox<Department> departmentSelect = new ComboBox<>("Department");
    private final ComboBox<GroupOption> groupSelect = new ComboBox<>("Resource group");
    private final Grid<ResourceSummaryDTO> grid = new Grid<>(ResourceSummaryDTO.class, false);
    private final Button add = new Button("New resource");

    // --- editor ---
    private final TextField name = new TextField("Name");
    private final TextField description = new TextField("Description");
    private final TextField ip = new TextField("IP");
    private final IntegerField port = new IntegerField("Port");
    private final IntegerField maxSlots = new IntegerField("Max slots");
    private final Checkbox stationary = new Checkbox("Stationary");
    private final Checkbox remote = new Checkbox("Remote");
    private final Checkbox mqttResource = new Checkbox("MQTT resource");
    private final TextField mqttUUID = new TextField("MQTT UUID");
    private final TextField mqttSendTopic = new TextField("MQTT send topic");
    private final TextField mqttReceiveTopic = new TextField("MQTT receive topic");
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a resource on the left, or create a new one.");

    // --- reservations sub-panel ---
    private final Grid<ResourceReservationDTO> reservationGrid = new Grid<>(ResourceReservationDTO.class, false);
    private final H4 reservationTitle = new H4("Reservations");

    // --- telemetry-rules sub-panel (full CRUD on the selected resource's monitoring rules) ---
    private final TelemetryRulesPanel rulesPanel;

    private Long selectedGroupId;
    private Long editingId;   // null while creating
    private boolean creating;

    public ResourcesView(ResourceService resourceService, DepartmentService departmentService,
                         TelemetryRuleService telemetryRuleService, CurrentUser currentUser) {
        this.resourceService = resourceService;
        this.departmentService = departmentService;
        this.currentUser = currentUser;
        this.rulesPanel = new TelemetryRulesPanel(telemetryRuleService, currentUser);

        setSizeFull();
        setPadding(false);
        add(buildSelectors(), buildSplit());
        configureGrid();
        showEditor(false);
        updateAddEnabled();
    }

    // --- Live status polling: refresh the grid while the view is on screen. ---

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().setPollInterval(POLL_INTERVAL_MS);
        attachEvent.getUI().addPollListener(e -> {
            grid.getDataProvider().refreshAll();
            rulesPanel.refresh(); // keep the selected resource's rule states (OK/ALARM) live too
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        detachEvent.getUI().setPollInterval(-1);
        super.onDetach(detachEvent);
    }

    private HorizontalLayout buildSelectors() {
        departmentSelect.setItems(departmentService.getAllDepartments());
        departmentSelect.setItemLabelGenerator(Department::getName);
        departmentSelect.setWidth("280px");
        departmentSelect.addValueChangeListener(e -> onDepartmentChange(e.getValue()));

        groupSelect.setItemLabelGenerator(GroupOption::name);
        groupSelect.setWidth("280px");
        groupSelect.setEnabled(false);
        groupSelect.addValueChangeListener(e -> {
            selectedGroupId = e.getValue() != null ? e.getValue().id() : null;
            reset();
        });

        HorizontalLayout bar = new HorizontalLayout(departmentSelect, groupSelect);
        bar.setPadding(true);
        return bar;
    }

    private void onDepartmentChange(Department department) {
        selectedGroupId = null;
        if (department == null) {
            groupSelect.clear();
            groupSelect.setItems(List.of());
            groupSelect.setEnabled(false);
        } else {
            try {
                var options = resourceService.getResourceGroupsByDepartment(department.getId(), currentUser.require())
                        .stream().map(g -> new GroupOption(g.getId(), g.getName())).toList();
                groupSelect.clear();
                groupSelect.setItems(options);
                groupSelect.setEnabled(true);
            } catch (AccessDeniedException denied) {
                groupSelect.clear();
                groupSelect.setItems(List.of());
                groupSelect.setEnabled(false);
                notifyError("You are not allowed to view this department's resource groups.");
            }
        }
        reset();
    }

    private SplitLayout buildSplit() {
        SplitLayout split = new SplitLayout(buildGridSide(), buildEditor());
        split.setSizeFull();
        split.setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        return split;
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(new ComponentRenderer<>(this::statusDot)).setHeader("").setWidth("48px").setFlexGrow(0);
        grid.addColumn(ResourceSummaryDTO::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(ResourceSummaryDTO::getDescription).setHeader("Description").setAutoWidth(true);
        grid.addColumn(r -> r.getOccupiedSlots() + "/" + r.getMaxSlots()).setHeader("Slots").setAutoWidth(true);
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

        VerticalLayout side = new VerticalLayout(add, grid);
        side.setSizeFull();
        side.setPadding(false);
        side.setSpacing(true);
        return side;
    }

    /**
     * Green when an MQTT resource is online, grey when it is offline; a neutral dash for non-MQTT resources
     * (they have no reported online state). The tooltip carries the last ping.
     */
    private Span statusDot(ResourceSummaryDTO r) {
        if (!r.isMqttResource()) {
            Span dash = new Span("—");
            dash.getStyle().set("color", "#9e9e9e");
            return dash;
        }
        Span dot = new Span();
        // Explicit colors (not Lumo custom props): the --lumo-* variables do not resolve inside the
        // grid cell's light DOM, so var(...) would fall back to transparent and the dot stay invisible.
        // These greens/greys read well in both the light and dark Lumo themes.
        String color = r.isMqttOnline() ? "#4caf50" : "#bdbdbd";
        dot.getStyle()
                .set("display", "inline-block")
                .set("width", "12px")
                .set("height", "12px")
                .set("border-radius", "50%")
                .set("background-color", color)
                .set("border", "1px solid rgba(0,0,0,0.25)");
        String ping = r.getMqttLastPing() != null ? TS.format(r.getMqttLastPing()) : "never";
        dot.getElement().setAttribute("title", (r.isMqttOnline() ? "Online" : "Offline") + " — last ping: " + ping);
        return dot;
    }

    private void configureGrid() {
        // Lazy paged data provider: only the visible page of the group's resources is fetched (see class doc).
        grid.setItems(
                query -> {
                    if (selectedGroupId == null) {
                        return Stream.empty();
                    }
                    return resourceService.getResourcesInGroup(
                            selectedGroupId, currentUser.require(),
                            VaadinSpringDataHelpers.toSpringPageRequest(query)).stream();
                },
                query -> {
                    if (selectedGroupId == null) {
                        return 0;
                    }
                    return (int) resourceService.countResourcesInGroup(selectedGroupId, currentUser.require());
                });
    }

    private VerticalLayout buildEditor() {
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> delete());
        cancel.addClickListener(e -> cancel());

        name.setWidthFull();
        description.setWidthFull();
        mqttUUID.setWidthFull();
        mqttSendTopic.setWidthFull();
        mqttReceiveTopic.setWidthFull();

        HorizontalLayout net = new HorizontalLayout(ip, port, maxSlots);
        HorizontalLayout flags = new HorizontalLayout(stationary, remote, mqttResource);
        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);

        configureReservationGrid();

        formFields.add(name, description, net, flags, mqttUUID, mqttSendTopic, mqttReceiveTopic, actions,
                reservationTitle, reservationGrid, rulesPanel);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("320px");
        editor.setPadding(true);
        // Bounded height + scroll so Save/Delete (and the reservations panel) stay reachable when the form grows.
        editor.setHeightFull();
        editor.getStyle().set("overflow-y", "auto");
        return editor;
    }

    private void configureReservationGrid() {
        reservationGrid.addColumn(r -> r.getFrom() != null ? TS.format(r.getFrom()) : "—")
                .setHeader("From").setAutoWidth(true);
        reservationGrid.addColumn(r -> r.getUntil() != null ? TS.format(r.getUntil()) : "—")
                .setHeader("Until").setAutoWidth(true);
        reservationGrid.addColumn(ResourceReservationDTO::getSlotNumber).setHeader("Slot").setAutoWidth(true);
        reservationGrid.addColumn(ResourceReservationDTO::getReservedBy).setHeader("Reserved by").setAutoWidth(true);
        reservationGrid.addColumn(new ComponentRenderer<>(this::cancelReservationButton)).setHeader("").setAutoWidth(true);
        reservationGrid.setAllRowsVisible(true);
    }

    private Button cancelReservationButton(ResourceReservationDTO reservation) {
        Button b = new Button("Cancel");
        b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        b.addClickListener(e -> cancelReservation(reservation));
        return b;
    }

    private void cancelReservation(ResourceReservationDTO reservation) {
        try {
            resourceService.cancelReservation(reservation.getId(), currentUser.require());
            notifySuccess("Reservation cancelled");
            loadReservations();
            grid.getDataProvider().refreshAll(); // slot occupancy changed
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to cancel this reservation.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    private void edit(ResourceSummaryDTO row, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : row.getId();
        name.setInvalid(false);
        if (creating) {
            name.clear();
            description.clear();
            ip.clear();
            port.setValue(80);
            maxSlots.setValue(1);
            stationary.setValue(true);
            remote.setValue(false);
            mqttResource.setValue(false);
            mqttUUID.clear();
            mqttSendTopic.clear();
            mqttReceiveTopic.clear();
        } else {
            name.setValue(nullToEmpty(row.getName()));
            description.setValue(nullToEmpty(row.getDescription()));
            maxSlots.setValue(row.getMaxSlots());
            mqttResource.setValue(row.isMqttResource());
            // ip/port/uuid/topics are not part of the summary DTO — left as-is; edit via the dedicated fields
            // only overwrites what the admin actually changes (partialUpdate skips nulls, but the fields here
            // are non-null once shown, so on update we only touch the scalar base + MQTT fields). Keep them
            // blank on edit-load to avoid persisting stale blanks; the admin re-enters what they want changed.
            ip.clear();
            port.clear();
            mqttUUID.clear();
            mqttSendTopic.clear();
            mqttReceiveTopic.clear();
            stationary.setValue(false);
            remote.setValue(false);
        }
        delete.setVisible(!creating);
        boolean showRes = !creating;
        reservationTitle.setVisible(showRes);
        reservationGrid.setVisible(showRes);
        if (showRes) {
            loadReservations();
        }
        // Rules need a persisted resource; bind on edit, clear while creating (the panel hides itself when null).
        rulesPanel.setResource(creating ? null : editingId);
        showEditor(true);
    }

    private void loadReservations() {
        if (editingId == null) {
            reservationGrid.setItems(List.of());
            return;
        }
        try {
            reservationGrid.setItems(resourceService.getReservationsForResourceDTO(editingId, currentUser.require()));
        } catch (AccessDeniedException denied) {
            reservationGrid.setItems(List.of());
        }
    }

    private void save() {
        if (creating && selectedGroupId == null) {
            return;
        }
        String newName = name.getValue() != null ? name.getValue().trim() : "";
        if (newName.isEmpty()) {
            name.setInvalid(true);
            name.setErrorMessage("Name is required");
            return;
        }
        PUser user = currentUser.require();
        try {
            if (creating) {
                resourceService.createResource(buildResourceBean(), selectedGroupId, user);
                notifySuccess("Resource created");
            } else {
                resourceService.partialUpdateResource(editingId, buildResourceBean(), user);
                notifySuccess("Resource updated");
            }
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to save this resource.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    /**
     * A fresh {@link Resource} bean carrying only the edited scalar + MQTT fields. It is never the detached grid
     * row, so no lazy relation is touched; on update {@code partialUpdateResource} copies the non-null fields
     * onto the managed entity and leaves department/group/reservations/skills alone (see its Javadoc).
     */
    private Resource buildResourceBean() {
        return Resource.builder()
                .name(name.getValue().trim())
                .description(emptyToNull(description.getValue()))
                .ip(emptyToNull(ip.getValue()))
                .port(port.getValue())
                .maxSlots(maxSlots.getValue())
                .stationary(stationary.getValue())
                .remote(remote.getValue())
                .mqttResource(mqttResource.getValue())
                .mqttUUID(emptyToNull(mqttUUID.getValue()))
                .mqttDataSendTopic(emptyToNull(mqttSendTopic.getValue()))
                .mqttDataReceiveTopic(emptyToNull(mqttReceiveTopic.getValue()))
                .build();
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        PUser user = currentUser.require();
        try {
            resourceService.deleteResource(editingId, user);
            notifySuccess("Resource deleted");
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this resource.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    private void cancel() {
        grid.deselectAll();
        reset();
    }

    private void reset() {
        editingId = null;
        creating = false;
        grid.getDataProvider().refreshAll();
        showEditor(false);
        updateAddEnabled();
    }

    private void updateAddEnabled() {
        add.setEnabled(selectedGroupId != null);
    }

    private void showEditor(boolean visible) {
        formFields.setVisible(visible);
        placeholder.setVisible(!visible);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
