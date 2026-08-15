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

package de.hallerweb.enterprise.prioritize.ui.nfc;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServletRequest;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.TagOverview;
import de.hallerweb.enterprise.prioritize.ui.common.CurrentUser;
import de.hallerweb.enterprise.prioritize.ui.common.ScanUrl;
import de.hallerweb.enterprise.prioritize.ui.resource.ResourcesView;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lists the NFC tags mounted on one {@link de.hallerweb.enterprise.prioritize.model.resource.Resource}
 * and hands out the URL each one has to carry, embedded in the detail pane of {@link ResourcesView}.
 * <p>
 * <b>What it is for.</b> Writing a sticker means storing an NDEF URL record on it, and that URL
 * contains the tag's uuid — which only exists once the tag has been registered in Prioritize. The
 * order is therefore always: register the tag, then write it. Without this panel the uuid had to be
 * read out of a REST response and typed off by hand, one transcription error away from a sticker
 * that points at nothing. Here it is a ready-made URL and a copy button.
 * <p>
 * <b>Read-only on purpose.</b> Registering, binding and deleting tags stay with the REST API; this
 * panel exists for the one step that had no home. It shows what a tag is bound to so the admin can
 * tell which sticker is which before writing it.
 *
 * @author peter haller
 */
public class NfcTagsPanel extends VerticalLayout {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    /**
     * Copies text without assuming the clipboard API is there. {@code navigator.clipboard} only
     * exists in a secure context, and this GUI is typically reached over plain HTTP on a LAN — the
     * exact situation these URLs are for — so the hidden-textarea route is the one that actually
     * runs here, and it is also the only one that reports back synchronously: Flow serializes the
     * expression's return value and does not resolve a returned promise, so awaiting the clipboard
     * API would answer with an empty object rather than a result. The URL stays selectable in the
     * grid either way, which is what the notification points at when this fails.
     */
    private static final String COPY_JS = """
            const text = $0;
            try {
              if (window.isSecureContext && navigator.clipboard) {
                navigator.clipboard.writeText(text);
                return true;
              }
              const area = document.createElement('textarea');
              area.value = text;
              area.style.position = 'fixed';
              area.style.opacity = '0';
              document.body.appendChild(area);
              area.select();
              const ok = document.execCommand('copy');
              document.body.removeChild(area);
              return ok;
            } catch (e) {
              return false;
            }
            """;

    private final transient NfcUnitService nfcUnitService;
    private final transient CurrentUser currentUser;

    private final H4 title = new H4("NFC tags");
    private final Span hint = new Span();
    private final Span localHostWarning = new Span();
    private final Grid<TagOverview> grid = new Grid<>(TagOverview.class, false);

    private Long resourceId;

    /** The address the admin's browser is talking to; see {@link ScanUrl}. */
    private String origin = "";

    public NfcTagsPanel(NfcUnitService nfcUnitService, CurrentUser currentUser) {
        this.nfcUnitService = nfcUnitService;
        this.currentUser = currentUser;

        setPadding(false);
        setSpacing(true);
        setWidthFull();

        hint.setText("Write the scan URL onto the sticker as an NDEF URL record (e.g. with the "
                + "\"NFC Tools\" app). Tags themselves are registered over the REST API.");
        hint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        localHostWarning.getStyle()
                .set("color", "#b26a00")
                .set("font-size", "var(--lumo-font-size-s)");
        localHostWarning.setVisible(false);

        configureGrid();
        add(title, hint, localHostWarning, grid);
    }

    /**
     * Binds the panel to a resource and loads its tags; {@code null} clears it — used while a new,
     * not-yet-persisted resource is being created, which cannot carry tags yet.
     */
    public void setResource(Long resourceId) {
        this.resourceId = resourceId;
        boolean bound = resourceId != null;
        title.setVisible(bound);
        hint.setVisible(bound);
        grid.setVisible(bound);
        if (bound) {
            load();
        } else {
            grid.setItems(List.of());
            localHostWarning.setVisible(false);
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        rememberOrigin();
    }

    private void configureGrid() {
        grid.addColumn(TagOverview::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(t -> t.type() != null ? t.type().name() : "").setHeader("Type").setAutoWidth(true);
        grid.addColumn(this::taskText).setHeader("Task").setAutoWidth(true);
        grid.addColumn(t -> t.lastScanTime() != null ? TS.format(t.lastScanTime()) : "never")
                .setHeader("Last scan").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::scanUrlCell)).setHeader("Scan URL").setFlexGrow(1);
        grid.setAllRowsVisible(true);
    }

    /**
     * A tracker tag without a task is the one broken state that matters here: its sticker renders a
     * "no task bound" page instead of a clock, so it is called out rather than left blank.
     */
    private String taskText(TagOverview tag) {
        if (tag.taskName() != null) {
            return tag.taskName();
        }
        return tag.type() == NfcUnitType.TIMETRACKER ? "— (no task bound)" : "—";
    }

    private HorizontalLayout scanUrlCell(TagOverview tag) {
        String url = ScanUrl.forTag(origin, tag.uuid());

        Span text = new Span(url);
        text.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "var(--lumo-font-size-s)")
                // One click selects the whole URL, which is what a person wants here — and the
                // fallback when the browser refuses the clipboard altogether.
                .set("user-select", "all")
                .set("word-break", "break-all");

        Button copy = new Button("Copy");
        copy.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        copy.addClickListener(e -> copyToClipboard(url));

        HorizontalLayout cell = new HorizontalLayout(text, copy);
        cell.setAlignItems(Alignment.CENTER);
        cell.setSpacing(true);
        cell.setWidthFull();
        return cell;
    }

    private void copyToClipboard(String url) {
        getUI().ifPresent(ui -> ui.getPage().executeJs(COPY_JS, url)
                .then(Boolean.class, copied -> {
                    if (Boolean.TRUE.equals(copied)) {
                        notifySuccess("Scan URL copied");
                    } else {
                        notifyError("The browser would not copy — select the URL in the row instead.");
                    }
                }));
    }

    private void load() {
        rememberOrigin();
        try {
            grid.setItems(nfcUnitService.getTagOverview(resourceId, currentUser.require()));
        } catch (AccessDeniedException denied) {
            grid.setItems(List.of());
        }
        localHostWarning.setText("This GUI is open as " + origin + ", so the URLs below point at the "
                + "machine the browser runs on. A phone cannot reach that — open the GUI under the "
                + "server's LAN address before writing stickers.");
        localHostWarning.setVisible(resourceId != null && ScanUrl.looksLocalOnly(origin));
    }

    /**
     * Keeps the last known origin rather than overwriting it with a blank: a UI event arriving over
     * push carries no servlet request, and a row rendered with a bare {@code /scan/...} would look
     * like a valid URL while being useless on a sticker.
     */
    private void rememberOrigin() {
        if (VaadinRequest.getCurrent() instanceof VaadinServletRequest servletRequest) {
            origin = ScanUrl.origin(servletRequest.getHttpServletRequest());
        }
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
