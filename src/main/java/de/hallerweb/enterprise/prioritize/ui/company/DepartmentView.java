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

package de.hallerweb.enterprise.prioritize.ui.company;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;
import de.hallerweb.enterprise.prioritize.ui.common.AddressForm;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
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
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * Master-detail CRUD screen for {@link Department}. Unlike {@link CompanyView}, departments are always
 * scoped to a {@link Company}: a company {@link ComboBox} at the top selects the context, and the grid
 * then lists that company's departments ({@link DepartmentService#getDepartmentsByCompany}). The side
 * form creates, edits or deletes the selected department.
 * <p>
 * Same conventions as {@link CompanyView}: the view calls the {@code @Service} layer directly, passes
 * the logged-in {@link PUser} (resolved via {@link CurrentUser}), and surfaces denied operations as an
 * error notification. Only the base fields (name, description) are edited; the address is a later slice.
 * <p>
 * Lazy-init guard (see {@link CompanyView}): the form is bound to a fresh, detached {@link Department}
 * bean carrying only the base fields — never to the entity returned by the finder. A detached entity's
 * lazy {@code address}/{@code company} proxies would otherwise be dragged into the service outside a
 * session. Because the fresh bean has a {@code null} address, {@link DepartmentService#updateDepartment}
 * leaves the stored address untouched. The edited id and the selected company id are kept separately.
 *
 * @author peter haller
 */
@Route("departments")
@PageTitle("Departments | Prioritize")
@PermitAll
public class DepartmentView extends VerticalLayout {

    private final transient DepartmentService departmentService;
    private final transient CompanyService companyService;
    private final transient CurrentUser currentUser;

    private final ComboBox<Company> companySelect = new ComboBox<>("Company");
    private final Grid<Department> grid = new Grid<>(Department.class, false);
    private final Binder<Department> binder = new Binder<>(Department.class);

    private final TextField name = new TextField("Name");
    private final TextField description = new TextField("Description");
    private final AddressForm addressForm = new AddressForm();

    private final Button add = new Button("New department");
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a department on the left, or create a new one.");

    // Fresh, detached bean carrying only the base fields — never the entity loaded by the finder.
    private Department formBean = new Department();
    private Long editingId;      // null while creating
    private Long selectedCompanyId;
    private boolean creating;

    public DepartmentView(DepartmentService departmentService, CompanyService companyService, CurrentUser currentUser) {
        this.departmentService = departmentService;
        this.companyService = companyService;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(false);
        add(buildCompanySelect(), buildSplit());
        configureBinder();
        showEditor(false);
        updateAddEnabled();
    }

    private HorizontalLayout buildCompanySelect() {
        companySelect.setItems(companyService.findAll());
        companySelect.setItemLabelGenerator(Company::getName);
        companySelect.setWidth("320px");
        companySelect.addValueChangeListener(e -> {
            selectedCompanyId = e.getValue() != null ? e.getValue().getId() : null;
            reset();
        });
        HorizontalLayout bar = new HorizontalLayout(companySelect);
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
        grid.addColumn(Department::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(Department::getDescription).setHeader("Description").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> {
            grid.deselectAll();
            edit(new Department(), true);
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
        description.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, description, new H4("Address"), addressForm, actions);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("320px");
        editor.setPadding(true);
        // Bound the editor to the detail pane's height and let it scroll, so the action buttons stay
        // reachable when the form (base fields + address sub-form) is taller than the visible area.
        editor.setHeightFull();
        editor.getStyle().set("overflow-y", "auto");
        return editor;
    }

    private void configureBinder() {
        binder.forField(name)
                .asRequired("Name is required")
                .bind(Department::getName, Department::setName);
        binder.forField(description).bind(Department::getDescription, Department::setDescription);
    }

    private void edit(Department source, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : source.getId();

        // Copy only the base fields into a fresh bean; the lazy address/company proxies are left untouched.
        Department bean = new Department();
        if (!creating) {
            bean.setName(source.getName());
            bean.setDescription(source.getDescription());
        }
        this.formBean = bean;

        binder.readBean(bean);
        // The address is lazy and cannot be read off the detached grid entity; load a detached copy
        // through the service (see AddressForm / DepartmentService#getAddress).
        addressForm.setAddress(creating ? null : departmentService.getAddress(source.getId(), currentUser.require()));
        delete.setVisible(!creating);
        showEditor(true);
    }

    private void save() {
        if (selectedCompanyId == null) {
            return; // cannot create/update without a company context
        }
        try {
            binder.writeBean(formBean);
        } catch (ValidationException validation) {
            return; // field-level messages are already shown by the binder
        }
        // A non-null address makes the service update it in place (or attach it on create); a fully
        // blank address stays null, so the stored address is left unchanged.
        formBean.setAddress(addressForm.getAddressOrNull());
        PUser user = currentUser.require();
        try {
            if (creating) {
                departmentService.saveDepartment(formBean, selectedCompanyId, user);
                notifySuccess("Department created");
            } else {
                // formBean carries no address, so updateDepartment leaves the existing one unchanged.
                departmentService.updateDepartment(editingId, formBean, user);
                notifySuccess("Department updated");
            }
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to save this department.");
        }
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        PUser user = currentUser.require();
        try {
            departmentService.deleteDepartment(editingId, user);
            notifySuccess("Department deleted");
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this department.");
        }
    }

    private void cancel() {
        grid.deselectAll();
        reset();
    }

    private void reset() {
        editingId = null;
        creating = false;
        formBean = new Department();
        refresh();
        showEditor(false);
        updateAddEnabled();
    }

    private void refresh() {
        if (selectedCompanyId == null) {
            grid.setItems(List.of());
            return;
        }
        try {
            grid.setItems(departmentService.getDepartmentsByCompany(selectedCompanyId, currentUser.require()));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
            notifyError("You are not allowed to view this company's departments.");
        }
    }

    private void updateAddEnabled() {
        add.setEnabled(selectedCompanyId != null);
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
