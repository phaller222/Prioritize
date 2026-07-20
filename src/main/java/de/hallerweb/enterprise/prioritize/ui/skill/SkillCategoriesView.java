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

package de.hallerweb.enterprise.prioritize.ui.skill;
import de.hallerweb.enterprise.prioritize.ui.security.RoleView;
import de.hallerweb.enterprise.prioritize.ui.group.GroupsView;

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
import de.hallerweb.enterprise.prioritize.dto.skill.SkillCategoryDTO;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import jakarta.annotation.security.PermitAll;

import java.util.List;

/**
 * Master-detail admin CRUD screen for the {@link SkillCategory} tree that the {@link SkillsView} categories hang
 * off: name, description and an optional parent category. Admin-only (behind the login), so — like the underlying
 * {@code SkillService} category methods and the sibling {@link RoleView} — it carries no {@code PUser}/permission
 * check.
 * <p>
 * Rows and the parent selector are {@link SkillCategoryDTO}, never the entity: {@code SkillCategory}'s all-fields
 * {@code equals}/{@code hashCode} touches its lazy {@code parentCategory}/{@code subCategories} and would throw a
 * {@code LazyInitializationException} inside a Vaadin grid/ComboBox (see {@link GroupsView}). The parent selector
 * excludes the category being edited (blocks trivial self-parenting); deleting a category removes its whole
 * subtree and detaches affected skills — {@code SkillService#deleteCategory} does this via a recursive query, and
 * the notification says so.
 *
 * @author peter haller
 */
@Route("skill-categories")
@PageTitle("Skill Categories | Prioritize")
@PermitAll
public class SkillCategoriesView extends SplitLayout {

    private final transient SkillService skillService;

    private final Grid<SkillCategoryDTO> grid = new Grid<>(SkillCategoryDTO.class, false);

    private final TextField name = new TextField("Name");
    private final TextField description = new TextField("Description");
    private final ComboBox<SkillCategoryDTO> parent = new ComboBox<>("Parent category");
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a category on the left, or create a new one.");

    private Long editingId;   // null while creating
    private boolean creating;

    public SkillCategoriesView(SkillService skillService) {
        this.skillService = skillService;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildEditor());
        setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        refresh();
        showEditor(false);
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(SkillCategoryDTO::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(SkillCategoryDTO::getDescription).setHeader("Description").setAutoWidth(true);
        grid.addColumn(c -> c.getParentName() != null ? c.getParentName() : "—")
                .setHeader("Parent").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        Button add = new Button("New category", e -> {
            grid.deselectAll();
            edit(null, true);
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
        parent.setWidthFull();
        parent.setClearButtonVisible(true);
        parent.setItemLabelGenerator(SkillCategoryDTO::getName);

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, description, parent, actions);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("320px");
        editor.setPadding(true);
        editor.setHeightFull();
        editor.getStyle().set("overflow-y", "auto");
        return editor;
    }

    private void edit(SkillCategoryDTO row, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : row.getId();

        // Parent options = all categories except the one being edited (blocks self-parenting).
        List<SkillCategoryDTO> options = skillService.getAllCategorySummaries().stream()
                .filter(c -> creating || !c.getId().equals(row.getId()))
                .toList();
        parent.setItems(options);

        name.setInvalid(false);
        if (creating) {
            name.clear();
            description.clear();
            parent.setValue(null);
        } else {
            name.setValue(nullToEmpty(row.getName()));
            description.setValue(nullToEmpty(row.getDescription()));
            parent.setValue(row.getParentId() == null ? null
                    : options.stream().filter(c -> c.getId().equals(row.getParentId())).findFirst().orElse(null));
        }
        delete.setVisible(!creating);
        showEditor(true);
    }

    private void save() {
        String newName = name.getValue() != null ? name.getValue().trim() : "";
        if (newName.isEmpty()) {
            name.setInvalid(true);
            name.setErrorMessage("Name is required");
            return;
        }
        try {
            if (creating) {
                skillService.createCategory(buildCategoryBean());
                notifySuccess("Category created");
            } else {
                skillService.updateCategory(editingId, buildCategoryBean());
                notifySuccess("Category updated");
            }
            reset();
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    /**
     * A fresh {@link SkillCategory} bean carrying only name/description plus a parent reference by id. Built with
     * {@code new SkillCategory()} (not the builder) so {@code subCategories} stays {@code null} and no lazy
     * relation is round-tripped; create/update resolve the parent by id.
     */
    private SkillCategory buildCategoryBean() {
        SkillCategory cat = new SkillCategory();
        cat.setName(name.getValue().trim());
        cat.setDescription(emptyToNull(description.getValue()));
        SkillCategoryDTO selectedParent = parent.getValue();
        if (selectedParent != null) {
            SkillCategory ref = new SkillCategory();
            ref.setId(selectedParent.getId());
            cat.setParentCategory(ref);
        }
        return cat;
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        try {
            skillService.deleteCategory(editingId);
            notifySuccess("Category deleted (including any sub-categories)");
            reset();
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
        refresh();
        showEditor(false);
    }

    private void refresh() {
        grid.setItems(skillService.getAllCategorySummaries());
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
