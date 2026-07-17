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

package de.hallerweb.enterprise.prioritize.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.security.PermissionRecordService;
import de.hallerweb.enterprise.prioritize.service.security.RoleService;
import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.List;

/**
 * Master-detail CRUD screen for {@link Role}, including the C/R/U/D permission matrix that is the point
 * of this slice. The grid lists all roles; the side form edits a role's base fields (name, description)
 * and its optional {@link Department} scope, and — for an already persisted role — its
 * {@link PermissionRecord}s as a checkbox matrix.
 * <p>
 * Same master-detail conventions as {@link UserView}/{@link CompanyView} (a {@link SplitLayout} with a
 * draggable divider starting at 30/70, a placeholder while nothing is selected). Like {@link UserService}
 * and the backing {@link RoleService}/{@link PermissionRecordService}, this is an admin-only screen behind
 * the login, so there are no per-call authorization parameters or {@code AccessDeniedException} handling.
 * <p>
 * <b>Permission matrix.</b> Each row is one {@link PermissionRecord} targeting one object type at the
 * type level ({@code objectId = 0}, i.e. "all instances of this type"); the four Create/Read/Update/Delete
 * checkboxes map 1:1 to the record's flags. Editing is <em>immediate</em>: toggling a checkbox, adding a
 * target type or removing a row calls {@link RoleService#addPermissionToRole}/
 * {@link RoleService#removePermissionFromRole}/{@link PermissionRecordService#updatePermission} right away
 * and reloads the matrix from the persisted role. The matrix therefore requires a saved role — while
 * creating a new role it is disabled with a hint; save the base fields first, then select the role to
 * manage its permissions.
 * <p>
 * <b>Lazy-init guard</b> (see {@link CompanyView}): base-field edits go through a fresh, detached
 * {@link Role} bean carrying only name/description — never the entity returned by the finder — and the
 * department is passed to the service by id, so {@link RoleService#updateRole} never touches a detached
 * lazy relation. The role's own {@code permissions} and {@code department} are {@code EAGER}, so reading
 * them off a finder result (for the grid and when loading the matrix) is safe.
 *
 * @author peter haller
 */
@Route("roles")
@PageTitle("Roles | Prioritize")
@PermitAll
public class RoleView extends SplitLayout {

    private final transient RoleService roleService;
    private final transient PermissionRecordService permissionRecordService;
    private final transient DepartmentService departmentService;

    private final Grid<Role> grid = new Grid<>(Role.class, false);
    private final Binder<Role> binder = new Binder<>(Role.class);

    private final TextField name = new TextField("Name");
    private final TextField description = new TextField("Description");
    private final ComboBox<Department> department = new ComboBox<>("Department (optional scope)");

    private final Grid<PermissionRecord> permissionGrid = new Grid<>(PermissionRecord.class, false);
    private final ComboBox<TargetType> newTargetType = new ComboBox<>("Target type");
    private final Button addPermission = new Button("Add permission");
    private final Span matrixHint = new Span("Save the role first to manage its permissions.");
    private final VerticalLayout matrixSection = new VerticalLayout();

    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a role on the left, or create a new one.");

    // Fresh, detached bean carrying only the base fields — never the entity loaded by the finder.
    private Role formBean = new Role();
    private Long editingId;   // null while creating
    private boolean creating;

    public RoleView(RoleService roleService, PermissionRecordService permissionRecordService,
                    DepartmentService departmentService) {
        this.roleService = roleService;
        this.permissionRecordService = permissionRecordService;
        this.departmentService = departmentService;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildEditor());
        setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        configureBinder();
        refresh();
        showEditor(false);
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(Role::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(Role::getDescription).setHeader("Description").setAutoWidth(true);
        // department and permissions are EAGER on Role, so reading them off the finder result is safe.
        grid.addColumn(r -> r.getDepartment() != null ? r.getDepartment().getName() : "—")
                .setHeader("Department").setAutoWidth(true);
        grid.addColumn(r -> r.getPermissions() != null ? r.getPermissions().size() : 0)
                .setHeader("Permissions").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        Button add = new Button("New role", e -> {
            grid.deselectAll();
            edit(new Role(), true);
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

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
        description.setWidthFull();
        department.setItems(departmentService.getAllDepartments());
        department.setItemLabelGenerator(Department::getName);
        department.setClearButtonVisible(true);
        department.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, description, department, buildMatrixSection(), actions);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("360px");
        editor.setPadding(true);
        // Bound the editor to the detail pane's height and let it scroll, so the action buttons stay
        // reachable when the form (base fields + permission matrix) is taller than the visible area.
        editor.setHeightFull();
        editor.getStyle().set("overflow-y", "auto");
        return editor;
    }

    private VerticalLayout buildMatrixSection() {
        permissionGrid.addColumn(this::labelFor).setHeader("Target type").setAutoWidth(true);
        permissionGrid.addComponentColumn(rec -> permissionCheckbox(rec,
                PermissionRecord::isCreatePermission, PermissionRecord::setCreatePermission)).setHeader("Create");
        permissionGrid.addComponentColumn(rec -> permissionCheckbox(rec,
                PermissionRecord::isReadPermission, PermissionRecord::setReadPermission)).setHeader("Read");
        permissionGrid.addComponentColumn(rec -> permissionCheckbox(rec,
                PermissionRecord::isUpdatePermission, PermissionRecord::setUpdatePermission)).setHeader("Update");
        permissionGrid.addComponentColumn(rec -> permissionCheckbox(rec,
                PermissionRecord::isDeletePermission, PermissionRecord::setDeletePermission)).setHeader("Delete");
        permissionGrid.addComponentColumn(rec -> {
            Button remove = new Button(VaadinIcon.TRASH.create(), e -> removePermission(rec));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON);
            return remove;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);
        permissionGrid.setAllRowsVisible(true);

        newTargetType.setItems(TargetType.values());
        newTargetType.setItemLabelGenerator(TargetType::getLabel);
        addPermission.addClickListener(e -> addPermission());
        HorizontalLayout addBar = new HorizontalLayout(newTargetType, addPermission);
        addBar.setDefaultVerticalComponentAlignment(HorizontalLayout.Alignment.END);

        matrixHint.getStyle().set("color", "var(--lumo-secondary-text-color)");

        matrixSection.add(new H4("Permissions"), matrixHint, permissionGrid, addBar);
        matrixSection.setPadding(false);
        matrixSection.setSpacing(true);
        return matrixSection;
    }

    private Checkbox permissionCheckbox(PermissionRecord rec, PermissionGetter getter, PermissionSetter setter) {
        Checkbox box = new Checkbox(getter.get(rec));
        box.addValueChangeListener(e -> {
            setter.set(rec, e.getValue());
            try {
                // updatePermission copies all flags + target from rec; department stays null (it is not
                // used by the authorization check for role-owned permissions — see AuthorizationService).
                permissionRecordService.updatePermission(rec.getId(), rec, null);
            } catch (RuntimeException ex) {
                notifyError("Could not update permission: " + ex.getMessage());
                loadMatrix(); // reload to reflect the actual persisted state
            }
        });
        return box;
    }

    private void configureBinder() {
        binder.forField(name)
                .asRequired("Name is required")
                .bind(Role::getName, Role::setName);
        binder.forField(description).bind(Role::getDescription, Role::setDescription);
    }

    private void edit(Role source, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : source.getId();

        // Copy only the base fields into a fresh bean; lazy users (and the collections we manage
        // separately) are left untouched.
        Role bean = new Role();
        if (!creating) {
            bean.setName(source.getName());
            bean.setDescription(source.getDescription());
        }
        this.formBean = bean;

        binder.readBean(bean);
        // department is EAGER on Role, so its id is available on the detached finder result; match it
        // against the freshly loaded ComboBox items by id.
        department.setValue(creating ? null : findDepartment(source.getDepartment()));
        delete.setVisible(!creating);

        loadMatrix();
        showEditor(true);
    }

    private void save() {
        try {
            binder.writeBean(formBean);
        } catch (ValidationException validation) {
            return; // field-level messages are already shown by the binder
        }
        Long departmentId = department.getValue() != null ? department.getValue().getId() : null;
        try {
            if (creating) {
                Role created = roleService.createRole(formBean, departmentId);
                notifySuccess("Role created");
                // Keep editing the new role so its permission matrix becomes available immediately.
                reset();
                grid.select(created);
            } else {
                roleService.updateRole(editingId, formBean, departmentId);
                notifySuccess("Role updated");
                reset();
            }
        } catch (RuntimeException ex) {
            notifyError("Could not save role: " + ex.getMessage());
        }
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        try {
            roleService.deleteRole(editingId);
            notifySuccess("Role deleted");
            reset();
        } catch (RuntimeException ex) {
            notifyError("Could not delete role: " + ex.getMessage());
        }
    }

    private void addPermission() {
        if (editingId == null) {
            return;
        }
        TargetType target = newTargetType.getValue();
        if (target == null) {
            notifyError("Pick a target type first.");
            return;
        }
        boolean alreadyPresent = permissionGrid.getListDataView().getItems()
                .anyMatch(rec -> target.getCanonical().equals(rec.getAbsoluteObjectType()));
        if (alreadyPresent) {
            notifyError("This target type is already in the matrix.");
            return;
        }
        // objectId 0 means "all instances of this type" (see AuthorizationService / InitializationService).
        PermissionRecord rec = PermissionRecord.builder()
                .absoluteObjectType(target.getCanonical())
                .objectId(0L)
                .createPermission(false)
                .readPermission(false)
                .updatePermission(false)
                .deletePermission(false)
                .build();
        try {
            roleService.addPermissionToRole(editingId, rec);
            newTargetType.clear();
            loadMatrix();
        } catch (RuntimeException ex) {
            notifyError("Could not add permission: " + ex.getMessage());
        }
    }

    private void removePermission(PermissionRecord rec) {
        if (editingId == null) {
            return;
        }
        try {
            roleService.removePermissionFromRole(editingId, rec.getId());
            loadMatrix();
        } catch (RuntimeException ex) {
            notifyError("Could not remove permission: " + ex.getMessage());
        }
    }

    /** Reloads the matrix from the persisted role; disables it (with a hint) while creating a new role. */
    private void loadMatrix() {
        boolean enabled = editingId != null;
        matrixHint.setVisible(!enabled);
        permissionGrid.setVisible(enabled);
        newTargetType.setEnabled(enabled);
        addPermission.setEnabled(enabled);
        if (!enabled) {
            permissionGrid.setItems(List.of());
            return;
        }
        // permissions are EAGER, so they are initialized within getRoleById's transaction and safe to read.
        List<PermissionRecord> permissions = roleService.getRoleById(editingId).getPermissions().stream().toList();
        permissionGrid.setItems(permissions);
    }

    private void cancel() {
        grid.deselectAll();
        reset();
    }

    private void reset() {
        editingId = null;
        creating = false;
        formBean = new Role();
        refresh();
        showEditor(false);
    }

    private void refresh() {
        grid.setItems(roleService.getAllRoles());
    }

    private void showEditor(boolean visible) {
        formFields.setVisible(visible);
        placeholder.setVisible(!visible);
    }

    private Department findDepartment(Department roleDepartment) {
        if (roleDepartment == null) {
            return null;
        }
        return departmentService.getAllDepartments().stream()
                .filter(d -> d.getId().equals(roleDepartment.getId()))
                .findFirst()
                .orElse(null);
    }

    private String labelFor(PermissionRecord rec) {
        return Arrays.stream(TargetType.values())
                .filter(t -> t.getCanonical().equals(rec.getAbsoluteObjectType()))
                .map(TargetType::getLabel)
                .findFirst()
                .orElse(rec.getObjectName()); // fall back to the derived simple name for unknown types
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
    private interface PermissionGetter {
        boolean get(PermissionRecord rec);
    }

    @FunctionalInterface
    private interface PermissionSetter {
        void set(PermissionRecord rec, boolean value);
    }

    /**
     * The curated set of authorizable domain types offered in the permission matrix. The stored
     * {@code absoluteObjectType} is the fully-qualified class name (matched verbatim by
     * {@link de.hallerweb.enterprise.prioritize.service.security.AuthorizationService}); the label is the
     * friendly name shown in the ComboBox and the matrix.
     */
    private enum TargetType {
        COMPANY("Company", "de.hallerweb.enterprise.prioritize.model.company.Company"),
        DEPARTMENT("Department", "de.hallerweb.enterprise.prioritize.model.company.Department"),
        RESOURCE("Resource", "de.hallerweb.enterprise.prioritize.model.resource.Resource"),
        RESOURCE_GROUP("Resource group", "de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup"),
        DOCUMENT("Document", "de.hallerweb.enterprise.prioritize.model.document.DocumentInfo"),
        DOCUMENT_GROUP("Document group", "de.hallerweb.enterprise.prioritize.model.document.DocumentGroup"),
        SKILL("Skill", "de.hallerweb.enterprise.prioritize.model.skill.Skill"),
        USER("User", "de.hallerweb.enterprise.prioritize.model.security.PUser");

        private final String label;
        private final String canonical;

        TargetType(String label, String canonical) {
            this.label = label;
            this.canonical = canonical;
        }

        String getLabel() {
            return label;
        }

        String getCanonical() {
            return canonical;
        }
    }
}
