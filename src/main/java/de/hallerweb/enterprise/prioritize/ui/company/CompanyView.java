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
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

/**
 * First real administration view: a master-detail CRUD screen for {@link Company}. The grid lists all
 * companies (the global platform-admin view — {@link CompanyService#findAll()} deliberately returns
 * every company; per-tenant filtering is a later multi-tenancy concern), the side form creates, edits
 * or deletes the selected one.
 * <p>
 * Per project convention the view calls the {@link CompanyService} {@code @Service} layer directly and
 * passes the logged-in {@link PUser} (resolved via {@link CurrentUser}); the actual authorization stays
 * in the service. Denied operations surface as an error notification rather than an uncaught exception.
 * Only base fields are edited here; the address and departments are separate, later slices.
 * <p>
 * Grid and detail form sit in a {@link SplitLayout} so the user can drag the divider to resize either
 * side; the divider starts at 50%. When nothing is selected the detail pane shows a placeholder rather
 * than collapsing, so the divider stays put.
 *
 * @author peter haller
 */
@Route("companies")
@PageTitle("Companies | Prioritize")
@PermitAll
public class CompanyView extends SplitLayout {

    private final transient CompanyService companyService;
    private final transient CurrentUser currentUser;

    private final Grid<Company> grid = new Grid<>(Company.class, false);
    private final Binder<Company> binder = new Binder<>(Company.class);

    private final TextField name = new TextField("Name");
    private final TextField description = new TextField("Description");
    private final TextField url = new TextField("URL");
    private final TextField vatNumber = new TextField("VAT number");
    private final TextField taxId = new TextField("Tax ID");
    private final AddressForm addressForm = new AddressForm();

    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a company on the left, or create a new one.");

    // The form is bound to a fresh, detached bean carrying only the base fields — never to the
    // entity loaded by findAll(). That entity's lazy mainAddress proxy would otherwise be dragged
    // into updateCompany() outside a session (LazyInitializationException). The id of the edited
    // company is kept separately; null means we are creating a new one.
    private Company formBean = new Company();
    private Long editingId;
    private boolean creating;

    public CompanyView(CompanyService companyService, CurrentUser currentUser) {
        this.companyService = companyService;
        this.currentUser = currentUser;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildEditor());
        setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        configureBinder();
        refresh();
        showEditor(false);
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(Company::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(Company::getDescription).setHeader("Description").setAutoWidth(true);
        grid.addColumn(Company::getUrl).setHeader("URL").setAutoWidth(true);
        grid.addColumn(Company::getVatNumber).setHeader("VAT").setAutoWidth(true);
        grid.addColumn(Company::getTaxId).setHeader("Tax ID").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        Button add = new Button("New company", e -> {
            grid.deselectAll();
            edit(new Company(), true);
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
        url.setWidthFull();
        vatNumber.setWidthFull();
        taxId.setWidthFull();

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, description, url, vatNumber, taxId, new H4("Address"), addressForm, actions);
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
                .bind(Company::getName, Company::setName);
        binder.forField(description).bind(Company::getDescription, Company::setDescription);
        binder.forField(url).bind(Company::getUrl, Company::setUrl);
        binder.forField(vatNumber).bind(Company::getVatNumber, Company::setVatNumber);
        binder.forField(taxId).bind(Company::getTaxId, Company::setTaxId);
    }

    private void edit(Company source, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : source.getId();

        // Copy only the base fields into a fresh bean; the lazy mainAddress proxy is left untouched.
        Company bean = new Company();
        if (!creating) {
            bean.setName(source.getName());
            bean.setDescription(source.getDescription());
            bean.setUrl(source.getUrl());
            bean.setVatNumber(source.getVatNumber());
            bean.setTaxId(source.getTaxId());
        }
        this.formBean = bean;

        binder.readBean(bean);
        // The address is lazy and cannot be read off the detached grid entity; load a detached copy
        // through the service (see AddressForm / CompanyService#getMainAddress).
        addressForm.setAddress(creating ? null : companyService.getMainAddress(source.getId(), currentUser.require()));
        delete.setVisible(!creating);
        showEditor(true);
    }

    private void save() {
        try {
            binder.writeBean(formBean);
        } catch (ValidationException validation) {
            return; // field-level messages are already shown by the binder
        }
        // A non-null address makes the service update it in place (or attach it on create); a fully
        // blank address stays null, so the stored address is left unchanged.
        formBean.setMainAddress(addressForm.getAddressOrNull());
        PUser user = currentUser.require();
        try {
            if (creating) {
                companyService.createCompany(formBean, user);
                notifySuccess("Company created");
            } else {
                // formBean carries no address, so updateCompany leaves the existing one unchanged.
                companyService.updateCompany(editingId, formBean, user);
                notifySuccess("Company updated");
            }
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to save this company.");
        }
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        PUser user = currentUser.require();
        try {
            companyService.deleteCompany(editingId, user);
            notifySuccess("Company deleted");
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this company.");
        }
    }

    private void cancel() {
        grid.deselectAll();
        reset();
    }

    private void reset() {
        editingId = null;
        creating = false;
        formBean = new Company();
        refresh();
        showEditor(false);
    }

    private void refresh() {
        grid.setItems(companyService.findAll());
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
