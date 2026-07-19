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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleDTO;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryOperator;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.service.telemetry.TelemetryRuleService;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/**
 * Full-CRUD admin panel for the {@link de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule}s of
 * one {@link de.hallerweb.enterprise.prioritize.model.resource.Resource}, embedded in the detail pane of
 * {@link ResourcesView}. A resource's rules watch its telemetry data points and flip an OK/ALARM state on a
 * threshold flank; here the admin authors and tunes them (there is no other home for rule editing — hence full
 * CRUD, not the display-and-delete-only treatment the reservations panel gets).
 * <p>
 * It is a self-contained component: {@link #setResource(Long)} binds it to a resource (loads its rules, hides
 * the editor) or clears it with {@code null}. All operations delegate to {@link TelemetryRuleService}, which
 * authorizes each against the owning resource ({@code READ} to list, {@code UPDATE} to mutate) and validates the
 * rule definition — so this panel only maps its exceptions to notifications. Rules are carried as the flat
 * {@link TelemetryRuleDTO} (never the lazy-relation entity), so they are grid-safe.
 * <p>
 * The grid offers an inline <b>enabled</b> toggle (a common tuning action) plus per-row Edit/Delete; the editor
 * below it creates or updates a rule, revealing the upper-bound field only for a {@code RANGE} operator.
 *
 * @author peter haller
 */
public class TelemetryRulesPanel extends VerticalLayout {

    private final transient TelemetryRuleService ruleService;
    private final transient CurrentUser currentUser;

    private final H4 title = new H4("Telemetry rules");
    private final Grid<TelemetryRuleDTO> grid = new Grid<>(TelemetryRuleDTO.class, false);
    private final Button add = new Button("New rule");

    // --- rule editor ---
    private final TextField datapoint = new TextField("Data point");
    private final ComboBox<TelemetryOperator> operator = new ComboBox<>("Operator");
    private final NumberField threshold = new NumberField("Threshold");
    private final NumberField thresholdHigh = new NumberField("Upper threshold");
    private final NumberField hysteresis = new NumberField("Hysteresis");
    private final ComboBox<Severity> severity = new ComboBox<>("Severity");
    private final Checkbox enabled = new Checkbox("Enabled");
    private final Button saveRule = new Button("Save rule");
    private final Button cancelRule = new Button("Cancel");
    private final VerticalLayout ruleForm = new VerticalLayout();

    private Long resourceId;
    private Long editingRuleId; // null while creating a new rule
    private boolean creatingRule;

    public TelemetryRulesPanel(TelemetryRuleService ruleService, CurrentUser currentUser) {
        this.ruleService = ruleService;
        this.currentUser = currentUser;

        setPadding(false);
        setSpacing(true);
        setWidthFull();

        configureGrid();
        buildRuleForm();

        add.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> newRule());

        add(title, add, grid, ruleForm);
        showRuleForm(false);
    }

    /**
     * Binds the panel to a resource: loads its rules and hides the editor. Passing {@code null} clears the
     * panel (empty grid, no editor) — used while a new, not-yet-persisted resource is being created.
     */
    public void setResource(Long resourceId) {
        this.resourceId = resourceId;
        showRuleForm(false);
        boolean bound = resourceId != null;
        title.setVisible(bound);
        add.setVisible(bound);
        grid.setVisible(bound);
        if (bound) {
            loadRules();
        } else {
            grid.setItems(List.of());
        }
    }

    private void configureGrid() {
        grid.addColumn(TelemetryRuleDTO::datapoint).setHeader("Data point").setAutoWidth(true);
        grid.addColumn(this::conditionText).setHeader("Condition").setAutoWidth(true);
        grid.addColumn(r -> r.severity() != null ? r.severity().name() : "").setHeader("Severity").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::stateBadge)).setHeader("State").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::enabledToggle)).setHeader("Enabled").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::rowActions)).setHeader("").setAutoWidth(true);
        grid.setAllRowsVisible(true);
    }

    /** A human-readable summary of the rule's trigger condition. */
    private String conditionText(TelemetryRuleDTO r) {
        String hyst = (r.hysteresis() != null && r.hysteresis() != 0.0) ? " (±" + fmt(r.hysteresis()) + ")" : "";
        return switch (r.operator()) {
            case GT -> "> " + fmt(r.threshold()) + hyst;
            case LT -> "< " + fmt(r.threshold()) + hyst;
            case RANGE -> "outside " + fmt(r.threshold()) + " – " + fmt(r.thresholdHigh()) + hyst;
        };
    }

    /** OK/ALARM badge. Explicit hex colours: the {@code --lumo-*} custom props do not resolve in a grid cell's
     * light DOM (a background styled with {@code var(...)} there falls back to transparent). */
    private Span stateBadge(TelemetryRuleDTO r) {
        boolean alarm = r.state() == TelemetryState.ALARM;
        Span badge = new Span(alarm ? "ALARM" : "OK");
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "1px 8px")
                .set("border-radius", "8px")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "#ffffff")
                .set("background-color", alarm ? "#e53935" : "#43a047");
        return badge;
    }

    private Checkbox enabledToggle(TelemetryRuleDTO r) {
        Checkbox cb = new Checkbox(r.enabled());
        cb.addValueChangeListener(e -> {
            if (Boolean.valueOf(e.getValue()).equals(r.enabled())) {
                return; // programmatic / no-op
            }
            toggleEnabled(r, e.getValue());
        });
        return cb;
    }

    private HorizontalLayout rowActions(TelemetryRuleDTO r) {
        Button edit = new Button("Edit");
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(e -> editRule(r));
        Button del = new Button("Delete");
        del.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        del.addClickListener(e -> deleteRule(r));
        HorizontalLayout actions = new HorizontalLayout(edit, del);
        actions.setSpacing(true);
        return actions;
    }

    private void buildRuleForm() {
        operator.setItems(TelemetryOperator.values());
        operator.setItemLabelGenerator(Enum::name);
        operator.addValueChangeListener(e -> updateRangeFieldVisibility());
        severity.setItems(Severity.values());
        severity.setItemLabelGenerator(Enum::name);

        datapoint.setWidthFull();

        HorizontalLayout row1 = new HorizontalLayout(datapoint, operator);
        row1.setWidthFull();
        HorizontalLayout row2 = new HorizontalLayout(threshold, thresholdHigh, hysteresis);
        HorizontalLayout row3 = new HorizontalLayout(severity, enabled);
        row3.setAlignItems(Alignment.END);

        saveRule.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveRule.addClickListener(e -> saveRule());
        cancelRule.addClickListener(e -> showRuleForm(false));
        HorizontalLayout actions = new HorizontalLayout(saveRule, cancelRule);

        ruleForm.add(row1, row2, row3, actions);
        ruleForm.setPadding(false);
        ruleForm.setSpacing(false);
        ruleForm.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");
        ruleForm.getStyle().set("padding-top", "var(--lumo-space-s)");
    }

    private void updateRangeFieldVisibility() {
        thresholdHigh.setVisible(operator.getValue() == TelemetryOperator.RANGE);
    }

    private void newRule() {
        creatingRule = true;
        editingRuleId = null;
        datapoint.clear();
        datapoint.setReadOnly(false);
        operator.setValue(TelemetryOperator.GT);
        threshold.clear();
        thresholdHigh.clear();
        hysteresis.clear();
        severity.setValue(Severity.WARNING);
        enabled.setValue(true);
        clearInvalid();
        updateRangeFieldVisibility();
        showRuleForm(true);
    }

    private void editRule(TelemetryRuleDTO r) {
        creatingRule = false;
        editingRuleId = r.id();
        datapoint.setValue(r.datapoint() != null ? r.datapoint() : "");
        // Data point is the rule's identity key on the resource; keep it stable while editing an existing rule.
        datapoint.setReadOnly(true);
        operator.setValue(r.operator());
        threshold.setValue(r.threshold());
        thresholdHigh.setValue(r.thresholdHigh());
        hysteresis.setValue(r.hysteresis());
        severity.setValue(r.severity());
        enabled.setValue(r.enabled());
        clearInvalid();
        updateRangeFieldVisibility();
        showRuleForm(true);
    }

    private void saveRule() {
        if (resourceId == null) {
            return;
        }
        clearInvalid();
        boolean invalid = false;
        if (creatingRule && (datapoint.getValue() == null || datapoint.getValue().isBlank())) {
            datapoint.setInvalid(true);
            datapoint.setErrorMessage("Data point is required");
            invalid = true;
        }
        if (operator.getValue() == null) {
            operator.setInvalid(true);
            operator.setErrorMessage("Operator is required");
            invalid = true;
        }
        if (threshold.getValue() == null) {
            threshold.setInvalid(true);
            threshold.setErrorMessage("Threshold is required");
            invalid = true;
        }
        if (invalid) {
            return;
        }

        Double high = operator.getValue() == TelemetryOperator.RANGE ? thresholdHigh.getValue() : null;
        TelemetryRuleRequest req = new TelemetryRuleRequest(
                creatingRule ? datapoint.getValue().trim() : null,
                operator.getValue(), threshold.getValue(), high,
                hysteresis.getValue(), severity.getValue(), enabled.getValue());
        PUser user = currentUser.require();
        try {
            if (creatingRule) {
                ruleService.createRule(resourceId, req, user);
                notifySuccess("Rule created");
            } else {
                ruleService.updateRule(editingRuleId, req, user);
                notifySuccess("Rule updated");
            }
            showRuleForm(false);
            loadRules();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to change this resource's rules.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    private void toggleEnabled(TelemetryRuleDTO r, boolean value) {
        try {
            ruleService.updateRule(r.id(),
                    new TelemetryRuleRequest(null, null, null, null, null, null, value), currentUser.require());
            loadRules();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to change this resource's rules.");
            loadRules();
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
            loadRules();
        }
    }

    private void deleteRule(TelemetryRuleDTO r) {
        try {
            ruleService.deleteRule(r.id(), currentUser.require());
            notifySuccess("Rule deleted");
            if (!creatingRule && r.id().equals(editingRuleId)) {
                showRuleForm(false);
            }
            loadRules();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this rule.");
        } catch (RuntimeException ex) {
            notifyError(ex.getMessage());
        }
    }

    /**
     * Reloads the rules so their live {@code state} (OK/ALARM) stays current — called from the parent view's
     * poll tick. Skips while unbound or while the admin is mid-edit (an open editor form), so a background
     * refresh never disturbs an edit in progress.
     */
    public void refresh() {
        if (resourceId == null || ruleForm.isVisible()) {
            return;
        }
        loadRules();
    }

    private void loadRules() {
        if (resourceId == null) {
            grid.setItems(List.of());
            return;
        }
        try {
            grid.setItems(ruleService.getRules(resourceId, currentUser.require()));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
        }
    }

    private void showRuleForm(boolean visible) {
        ruleForm.setVisible(visible);
    }

    private void clearInvalid() {
        datapoint.setInvalid(false);
        operator.setInvalid(false);
        threshold.setInvalid(false);
    }

    private static String fmt(Double d) {
        if (d == null) {
            return "";
        }
        return d == Math.floor(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
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
