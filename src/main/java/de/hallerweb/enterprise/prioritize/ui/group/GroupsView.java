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

package de.hallerweb.enterprise.prioritize.ui.group;
import de.hallerweb.enterprise.prioritize.ui.company.DepartmentView;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * Master-detail CRUD screen for the group layer directly beneath a {@link Department}:
 * {@link ResourceGroup}s and {@link DocumentGroup}s. Two selectors at the top pick the context — a
 * department (all departments, {@link DepartmentService#getAllDepartments()}) and a group type
 * (resource vs. document) — and the grid then lists that department's groups of the chosen type. The
 * side form creates a new group or renames/deletes the selected one; the group's only editable field is
 * its name.
 * <p>
 * Conventions follow {@link DepartmentView}: a {@link SplitLayout} with a draggable divider starting at
 * 30/70, the {@code @Service} layer called directly with the logged-in {@link PUser} (resolved via
 * {@link CurrentUser}), and denied or invalid operations surfaced as an error notification (e.g. the
 * built-in "Default" group cannot be renamed or deleted — the services reject it).
 * <p>
 * The grid is bound to a small {@link GroupRow} record (id + name), never to the entities returned by the
 * finders: {@link DocumentGroup} uses an all-fields {@code equals}/{@code hashCode} that would drag its
 * lazy {@code department} into the grid's key mapper and throw a {@code LazyInitializationException}. The
 * record sidesteps that and unifies both group types behind one grid and form.
 *
 * @author peter haller
 */
@Route("groups")
@PageTitle("Groups | Prioritize")
@PermitAll
public class GroupsView extends VerticalLayout {

    /** A group row for the grid — decoupled from the JPA entities (see class doc). */
    private record GroupRow(Long id, String name) {
    }

    /** The two group kinds this screen manages, each labelled for the type selector. */
    private enum GroupType {
        RESOURCE("Resource groups"),
        DOCUMENT("Document groups");

        private final String label;

        GroupType(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }
    }

    private final transient ResourceService resourceService;
    private final transient DocumentService documentService;
    private final transient DepartmentService departmentService;
    private final transient CurrentUser currentUser;

    private final ComboBox<Department> departmentSelect = new ComboBox<>("Department");
    private final ComboBox<GroupType> typeSelect = new ComboBox<>("Group type");
    private final Grid<GroupRow> grid = new Grid<>(GroupRow.class, false);

    private final TextField name = new TextField("Name");
    private final Button add = new Button("New group");
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a group on the left, or create a new one.");

    private Department selectedDepartment;
    private GroupType selectedType;
    private Long editingId;   // null while creating
    private boolean creating;

    public GroupsView(ResourceService resourceService, DocumentService documentService,
                      DepartmentService departmentService, CurrentUser currentUser) {
        this.resourceService = resourceService;
        this.documentService = documentService;
        this.departmentService = departmentService;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(false);
        add(buildSelectors(), buildSplit());
        showEditor(false);
        updateAddEnabled();
    }

    private HorizontalLayout buildSelectors() {
        departmentSelect.setItems(departmentService.getAllDepartments());
        departmentSelect.setItemLabelGenerator(Department::getName);
        departmentSelect.setWidth("280px");
        departmentSelect.addValueChangeListener(e -> {
            selectedDepartment = e.getValue();
            reset();
        });

        typeSelect.setItems(GroupType.values());
        typeSelect.setItemLabelGenerator(GroupType::getLabel);
        typeSelect.setWidth("220px");
        typeSelect.addValueChangeListener(e -> {
            selectedType = e.getValue();
            reset();
        });

        HorizontalLayout bar = new HorizontalLayout(departmentSelect, typeSelect);
        bar.setPadding(true);
        return bar;
    }

    private SplitLayout buildSplit() {
        SplitLayout split = new SplitLayout(buildGridSide(), buildEditor());
        split.setSizeFull();
        split.setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        return split;
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(GroupRow::name).setHeader("Name").setAutoWidth(true).setSortable(true);
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

    private VerticalLayout buildEditor() {
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> save());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> delete());
        cancel.addClickListener(e -> cancel());

        name.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, actions);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("300px");
        editor.setPadding(true);
        return editor;
    }

    private void edit(GroupRow row, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : row.id();
        name.setInvalid(false);
        name.setValue(creating ? "" : row.name());
        delete.setVisible(!creating);
        showEditor(true);
    }

    private void save() {
        if (selectedDepartment == null || selectedType == null) {
            return; // cannot create/rename without a context
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
                createGroup(newName, user);
                notifySuccess("Group created");
            } else {
                renameGroup(editingId, newName, user);
                notifySuccess("Group renamed");
            }
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to save this group.");
        } catch (RuntimeException ex) {
            // e.g. IllegalStateException (default group protected) or a duplicate-name constraint
            notifyError(ex.getMessage());
        }
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        PUser user = currentUser.require();
        try {
            deleteGroup(editingId, user);
            notifySuccess("Group deleted");
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this group.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage()); // e.g. the default group cannot be deleted
        }
    }

    private void createGroup(String groupName, PUser user) {
        if (selectedType == GroupType.RESOURCE) {
            resourceService.createResourceGroup(groupName, selectedDepartment, user);
        } else {
            documentService.createDocumentGroup(groupName, selectedDepartment, user);
        }
    }

    private void renameGroup(Long groupId, String newName, PUser user) {
        if (selectedType == GroupType.RESOURCE) {
            resourceService.renameResourceGroup(groupId, newName, user);
        } else {
            documentService.renameDocumentGroup(groupId, newName, user);
        }
    }

    private void deleteGroup(Long groupId, PUser user) {
        if (selectedType == GroupType.RESOURCE) {
            resourceService.deleteResourceGroup(groupId, user);
        } else {
            documentService.deleteDocumentGroup(groupId, user);
        }
    }

    private void cancel() {
        grid.deselectAll();
        reset();
    }

    private void reset() {
        editingId = null;
        creating = false;
        refresh();
        showEditor(false);
        updateAddEnabled();
    }

    private void refresh() {
        if (selectedDepartment == null || selectedType == null) {
            grid.setItems(List.of());
            return;
        }
        PUser user = currentUser.require();
        try {
            grid.setItems(loadGroups(selectedDepartment.getId(), user));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
            notifyError("You are not allowed to view this department's groups.");
        }
    }

    private List<GroupRow> loadGroups(Long departmentId, PUser user) {
        if (selectedType == GroupType.RESOURCE) {
            return resourceService.getResourceGroupsByDepartment(departmentId, user).stream()
                    .map(g -> new GroupRow(g.getId(), g.getName()))
                    .toList();
        }
        return documentService.getDocumentGroupsByDepartment(departmentId, user).stream()
                .map(g -> new GroupRow(g.getId(), g.getName()))
                .toList();
    }

    private void updateAddEnabled() {
        add.setEnabled(selectedDepartment != null && selectedType != null);
    }

    private void showEditor(boolean visible) {
        formFields.setVisible(visible);
        placeholder.setVisible(!visible);
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
