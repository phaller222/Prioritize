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
import de.hallerweb.enterprise.prioritize.dto.skill.SkillSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * Master-detail admin CRUD screen for the global {@link Skill} master-data catalogue: name, description,
 * keywords and an optional {@link SkillCategory}. The per-skill property-definitions editor
 * ({@code SkillPropertyNumeric}/{@code SkillPropertyText}) is deliberately deferred (like the address sub-form
 * was), so this view edits the base fields plus the category only.
 * <p>
 * Conventions follow {@link UserView}/{@link GroupsView}: a {@link SplitLayout} 30/70 with a draggable divider,
 * the {@code @Service} layer called directly with the logged-in {@link PUser} (resolved via {@link CurrentUser}),
 * denied/invalid operations surfaced as an error notification, and a fresh-bean lazy guard on save.
 * <p>
 * Grid rows and the category selector are {@link SkillSummaryDTO}/{@link SkillCategoryDTO}, never the entities:
 * {@code Skill} has a degenerate {@code equals}/{@code hashCode} (nothing included) and a lazy {@code category},
 * and {@code SkillCategory}'s all-fields hashCode touches lazy relations — both break a Vaadin grid/ComboBox key
 * mapper, so the category id/name are resolved into DTOs inside the service (see {@link SkillService}).
 *
 * @author peter haller
 */
@Route("skills")
@PageTitle("Skills | Prioritize")
@PermitAll
public class SkillsView extends SplitLayout {

    private final transient SkillService skillService;
    private final transient CurrentUser currentUser;

    private final Grid<SkillSummaryDTO> grid = new Grid<>(SkillSummaryDTO.class, false);

    private final TextField name = new TextField("Name");
    private final TextField description = new TextField("Description");
    private final TextField keywords = new TextField("Keywords");
    private final ComboBox<SkillCategoryDTO> category = new ComboBox<>("Category");
    private final Button save = new Button("Save");
    private final Button delete = new Button("Delete");
    private final Button cancel = new Button("Cancel");
    private final VerticalLayout editor = new VerticalLayout();
    private final VerticalLayout formFields = new VerticalLayout();
    private final Span placeholder = new Span("Select a skill on the left, or create a new one.");

    private Long editingId;   // null while creating
    private boolean creating;

    public SkillsView(SkillService skillService, CurrentUser currentUser) {
        this.skillService = skillService;
        this.currentUser = currentUser;

        setSizeFull();
        addToPrimary(buildGridSide());
        addToSecondary(buildEditor());
        setSplitterPosition(30); // start at 30/70 (grid/form); the user can drag the divider either way
        refresh();
        showEditor(false);
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(SkillSummaryDTO::getName).setHeader("Name").setAutoWidth(true).setSortable(true);
        grid.addColumn(SkillSummaryDTO::getDescription).setHeader("Description").setAutoWidth(true);
        grid.addColumn(SkillSummaryDTO::getKeywords).setHeader("Keywords").setAutoWidth(true);
        grid.addColumn(s -> s.getCategoryName() != null ? s.getCategoryName() : "—")
                .setHeader("Category").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                edit(e.getValue(), false);
            }
        });

        Button add = new Button("New skill", e -> {
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
        keywords.setWidthFull();
        category.setWidthFull();
        category.setClearButtonVisible(true);
        category.setItemLabelGenerator(SkillCategoryDTO::getName);

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        formFields.add(name, description, keywords, category, actions);
        formFields.setPadding(false);

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        editor.add(placeholder, formFields);
        editor.setMinWidth("320px");
        editor.setPadding(true);
        editor.setHeightFull();
        editor.getStyle().set("overflow-y", "auto");
        return editor;
    }

    private void edit(SkillSummaryDTO row, boolean creating) {
        this.creating = creating;
        this.editingId = creating ? null : row.getId();

        // Refresh the category options each time so newly created categories are available.
        List<SkillCategoryDTO> options = skillService.getAllCategorySummaries();
        category.setItems(options);

        name.setInvalid(false);
        if (creating) {
            name.clear();
            description.clear();
            keywords.clear();
            category.setValue(null);
        } else {
            name.setValue(nullToEmpty(row.getName()));
            description.setValue(nullToEmpty(row.getDescription()));
            keywords.setValue(nullToEmpty(row.getKeywords()));
            category.setValue(row.getCategoryId() == null ? null
                    : options.stream().filter(c -> c.getId().equals(row.getCategoryId())).findFirst().orElse(null));
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
        PUser user = currentUser.require();
        try {
            if (creating) {
                skillService.createSkill(buildSkillBean(), user);
                notifySuccess("Skill created");
            } else {
                skillService.updateSkill(editingId, buildSkillBean(), user);
                notifySuccess("Skill updated");
            }
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to save this skill.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    /**
     * A fresh {@link Skill} bean carrying only the edited base fields plus a category reference by id. Built with
     * {@code new Skill()} (not the builder) so {@code skillProperties} stays {@code null} — on update the service
     * then leaves the existing properties untouched instead of clearing them, and no lazy relation is round-tripped.
     */
    private Skill buildSkillBean() {
        Skill skill = new Skill();
        skill.setName(name.getValue().trim());
        skill.setDescription(emptyToNull(description.getValue()));
        skill.setKeywords(emptyToNull(keywords.getValue()));
        SkillCategoryDTO selected = category.getValue();
        if (selected != null) {
            SkillCategory ref = new SkillCategory();
            ref.setId(selected.getId());
            skill.setCategory(ref);
        }
        return skill;
    }

    private void delete() {
        if (editingId == null || creating) {
            return;
        }
        try {
            skillService.deleteSkill(editingId, currentUser.require());
            notifySuccess("Skill deleted");
            reset();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this skill.");
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
        grid.setItems(skillService.getAllSkillSummaries());
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
