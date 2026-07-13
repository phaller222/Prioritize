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
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import jakarta.annotation.security.PermitAll;

/**
 * Master-detail CRUD screen for {@link PUser}. The grid lists all active users
 * ({@link UserService#getAllUsers()} already filters out deactivated ones), the side form creates a new
 * user or edits the selected one. There is NO hard delete: the "Deactivate" action soft-deletes via
 * {@link UserService#deactivateUser(Long)} (which refuses to deactivate an admin), after which the user
 * drops out of the active list.
 * <p>
 * Same conventions as {@link CompanyView}/{@link DepartmentView} (master-detail {@link SplitLayout} with a
 * draggable divider, placeholder when nothing is selected). Two view-specific choices driven by the service:
 * <ul>
 *   <li><b>Update</b> goes through {@link UserService#partialUpdateUser(Long, PUser)} — it loads the managed
 *   entity and copies only the supplied scalar fields (blank password = unchanged), so no detached entity is
 *   round-tripped and no lazy relation (roles/permissions/department) is touched. Username, the admin flag and
 *   roles are intentionally not editable here (roles/permissions are a later slice).</li>
 *   <li><b>Create</b> builds a fresh user via the Lombok builder with {@code active(true)} — a plain
 *   {@code new PUser()} would leave the {@code @Builder.Default} fields at their raw defaults
 *   ({@code active=false}, null collections), which would immediately hide the new user from the active list.</li>
 * </ul>
 * The password is required when creating and optional when editing (left blank keeps the current one).
 *
 * @author peter haller
 */
@Route("users")
@PageTitle("Users | Prioritize")
@PermitAll
public class UserView extends SplitLayout {

    private final transient UserService userService;

    private final Grid<PUser> grid = new Grid<>(PUser.class, false);
    private final Binder<PUser> binder = new Binder<>(PUser.class);

    private final TextField username = new TextField("Username");
    private final TextField firstname = new TextField("First name");
    private final TextField name = new TextField("Last name");
    private final TextField email = new TextField("Email");
    private final TextField occupation = new TextField("Occupation");
    private final ComboBox<PUser.Gender> gender = new ComboBox<>("Gender");
    private final PasswordField password = new PasswordField("Password");
    private final AddressForm addressForm = new AddressForm();

    private final Button save = new Button("Save");
    private final Button deactivate = new Button("Deactivate");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a user on the left, or create a new one.");

    // The form is bound to a fresh, detached bean carrying only the edited base fields — never the entity
    // loaded by getAllUsers(). The edited id is kept separately; null means we are creating a new user.
    private PUser formBean = new PUser();
    private Long editingId;
    private boolean creating;

    public UserView(UserService userService) {
        this.userService = userService;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildEditor());
        setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        configureBinder();
        refresh();
        showEditor(false);
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(PUser::getUsername).setHeader("Username").setAutoWidth(true).setSortable(true);
        grid.addColumn(PUser::getFirstname).setHeader("First name").setAutoWidth(true);
        grid.addColumn(PUser::getName).setHeader("Last name").setAutoWidth(true);
        grid.addColumn(PUser::getEmail).setHeader("Email").setAutoWidth(true);
        grid.addColumn(u -> u.isAdmin() ? "Admin" : "User").setHeader("Role").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        Button add = new Button("New user", e -> {
            grid.deselectAll();
            edit(new PUser(), true);
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
        deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deactivate.addClickListener(e -> deactivate());
        cancel.addClickListener(e -> cancel());

        username.setWidthFull();
        firstname.setWidthFull();
        name.setWidthFull();
        email.setWidthFull();
        occupation.setWidthFull();
        gender.setItems(PUser.Gender.values());
        gender.setWidthFull();
        password.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(save, deactivate, cancel);
        formFields.add(username, firstname, name, email, occupation, gender, password,
                new H4("Address"), addressForm, actions);
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
        binder.forField(username)
                .asRequired("Username is required")
                .bind(PUser::getUsername, PUser::setUsername);
        binder.forField(firstname).bind(PUser::getFirstname, PUser::setFirstname);
        binder.forField(name).bind(PUser::getName, PUser::setName);
        binder.forField(email).bind(PUser::getEmail, PUser::setEmail);
        binder.forField(occupation).bind(PUser::getOccupation, PUser::setOccupation);
        binder.forField(gender).bind(PUser::getGender, PUser::setGender);
        // Password is handled manually (required only on create), not through the binder.
    }

    private void edit(PUser source, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : source.getId();

        // Copy only the base fields into a fresh bean; lazy roles/permissions/department are left untouched.
        PUser bean = new PUser();
        if (!creating) {
            bean.setUsername(source.getUsername());
            bean.setFirstname(source.getFirstname());
            bean.setName(source.getName());
            bean.setEmail(source.getEmail());
            bean.setOccupation(source.getOccupation());
            bean.setGender(source.getGender());
        }
        this.formBean = bean;

        binder.readBean(bean);
        // The address is lazy and cannot be read off the detached grid entity; load a detached copy
        // through the service (see AddressForm / UserService#getAddress).
        addressForm.setAddress(creating ? null : userService.getAddress(source.getId()));
        password.clear();
        // Username is the identity and cannot be changed on an existing user (partialUpdate ignores it).
        username.setReadOnly(!creating);
        password.setHelperText(creating ? "Required for a new user" : "Leave blank to keep the current password");
        deactivate.setVisible(!creating);
        showEditor(true);
    }

    private void save() {
        try {
            binder.writeBean(formBean);
        } catch (ValidationException validation) {
            return; // field-level messages are already shown by the binder
        }
        try {
            if (creating) {
                if (password.getValue() == null || password.getValue().isBlank()) {
                    password.setInvalid(true);
                    password.setErrorMessage("Password is required for a new user");
                    return;
                }
                PUser toCreate = PUser.builder()
                        .username(formBean.getUsername())
                        .firstname(formBean.getFirstname())
                        .name(formBean.getName())
                        .email(formBean.getEmail())
                        .occupation(formBean.getOccupation())
                        .gender(formBean.getGender())
                        .password(password.getValue())
                        .address(addressForm.getAddressOrNull())
                        .active(true)
                        .build();
                userService.createUser(toCreate);
                notifySuccess("User created");
            } else {
                // partialUpdateUser copies only non-null fields; a blank password leaves it unchanged.
                PUser patch = new PUser();
                patch.setFirstname(formBean.getFirstname());
                patch.setName(formBean.getName());
                patch.setEmail(formBean.getEmail());
                patch.setOccupation(formBean.getOccupation());
                patch.setGender(formBean.getGender());
                if (!password.getValue().isBlank()) {
                    patch.setPassword(password.getValue());
                }
                patch.setAddress(addressForm.getAddressOrNull());
                userService.partialUpdateUser(editingId, patch);
                notifySuccess("User updated");
            }
            reset();
        } catch (RuntimeException ex) {
            notifyError("Could not save user: " + ex.getMessage());
        }
    }

    private void deactivate() {
        if (editingId == null || creating) {
            return;
        }
        try {
            userService.deactivateUser(editingId);
            notifySuccess("User deactivated");
            reset();
        } catch (IllegalArgumentException denied) {
            notifyError(denied.getMessage()); // e.g. admin users cannot be deactivated
        }
    }

    private void cancel() {
        grid.deselectAll();
        reset();
    }

    private void reset() {
        editingId = null;
        creating = false;
        formBean = new PUser();
        refresh();
        showEditor(false);
    }

    private void refresh() {
        grid.setItems(userService.getAllUsers());
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
