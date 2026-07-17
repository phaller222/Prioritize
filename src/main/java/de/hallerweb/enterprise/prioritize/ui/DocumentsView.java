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
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.util.stream.Stream;

/**
 * Admin document screen — deliberately <b>view-only plus delete</b>. An administrator is not meant to do the
 * documents' actual editing (check-out / check-in / new versions); that belongs to the users and client apps
 * via the REST API. Here the admin only <em>lists</em> a group's documents, <em>downloads</em> the current
 * version for control, and <em>deletes</em> one in an emergency. There is intentionally no create/edit/lock.
 * <p>
 * Two selectors pick the context — a department ({@link DepartmentService#getAllDepartments()}) and one of its
 * document groups ({@link DocumentService#getDocumentGroupsByDepartment}). The grid is <b>lazy</b>: it is fed
 * by {@link DocumentService#getDocumentsInGroup(Long, PUser, org.springframework.data.domain.Pageable)} +
 * {@link DocumentService#countDocumentsInGroup} through a paged data provider, so a large group never loads its
 * whole document set into server memory (the plan calls for building this view paged from the start).
 * <p>
 * Both the group selector and the grid use small records ({@link GroupOption}, {@link DocumentSummaryDTO}),
 * never the {@code DocumentGroup}/{@code DocumentInfo} entities: {@code DocumentGroup} has an all-fields
 * {@code equals}/{@code hashCode} covering its lazy {@code department}, which would throw a
 * {@code LazyInitializationException} inside a Vaadin component's key mapper (see {@link GroupsView}).
 *
 * @author peter haller
 */
@Route("documents")
@PageTitle("Documents | Prioritize")
@PermitAll
public class DocumentsView extends VerticalLayout {

    /** A document-group option for the selector — decoupled from the lazy-relation-carrying entity. */
    private record GroupOption(Long id, String name) {
    }

    private final transient DocumentService documentService;
    private final transient DepartmentService departmentService;
    private final transient CurrentUser currentUser;

    private final ComboBox<Department> departmentSelect = new ComboBox<>("Department");
    private final ComboBox<GroupOption> groupSelect = new ComboBox<>("Document group");
    private final Grid<DocumentSummaryDTO> grid = new Grid<>(DocumentSummaryDTO.class, false);

    private final Span info = new Span();
    private final Anchor download = new Anchor();
    private final Button delete = new Button("Delete");
    private final VerticalLayout detail = new VerticalLayout();
    private final Span placeholder = new Span("Select a document on the left to download or delete it.");

    private Long selectedGroupId;
    private DocumentSummaryDTO selected;

    public DocumentsView(DocumentService documentService, DepartmentService departmentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.departmentService = departmentService;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(false);
        add(buildSelectors(), buildSplit());
        configureGrid();
        showDetail(false);
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
            grid.deselectAll();
            showDetail(false);
            grid.getDataProvider().refreshAll();
        });

        HorizontalLayout bar = new HorizontalLayout(departmentSelect, groupSelect);
        bar.setPadding(true);
        return bar;
    }

    private void onDepartmentChange(Department department) {
        selectedGroupId = null;
        grid.deselectAll();
        showDetail(false);
        if (department == null) {
            groupSelect.clear();
            groupSelect.setItems(java.util.List.of());
            groupSelect.setEnabled(false);
        } else {
            // Map to a record immediately; name/id are scalar and safe to read off the detached entities,
            // unlike the group's lazy department that its equals()/hashCode() would touch inside the ComboBox.
            var options = documentService.getDocumentGroupsByDepartment(department.getId(), currentUser.require())
                    .stream().map(g -> new GroupOption(g.getId(), g.getName())).toList();
            groupSelect.clear();
            groupSelect.setItems(options);
            groupSelect.setEnabled(true);
        }
        grid.getDataProvider().refreshAll();
    }

    private SplitLayout buildSplit() {
        SplitLayout split = new SplitLayout(buildGridSide(), buildDetail());
        split.setSizeFull();
        split.setSplitterPosition(50);
        return split;
    }

    private VerticalLayout buildGridSide() {
        grid.addColumn(DocumentSummaryDTO::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(DocumentSummaryDTO::getCurrentVersion).setHeader("Version").setAutoWidth(true);
        grid.addColumn(d -> d.isLocked() ? "Locked by " + d.getLockedBy() : "—").setHeader("Lock").setAutoWidth(true);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                select(e.getValue());
            }
        });

        VerticalLayout side = new VerticalLayout(grid);
        side.setSizeFull();
        side.setPadding(false);
        return side;
    }

    private void configureGrid() {
        // Lazy paged data provider: only the visible page of a group's documents is fetched (see class doc).
        grid.setItems(
                query -> {
                    if (selectedGroupId == null) {
                        return Stream.empty();
                    }
                    return documentService.getDocumentsInGroup(
                            selectedGroupId, currentUser.require(),
                            VaadinSpringDataHelpers.toSpringPageRequest(query)).stream();
                },
                query -> {
                    if (selectedGroupId == null) {
                        return 0;
                    }
                    return (int) documentService.countDocumentsInGroup(selectedGroupId, currentUser.require());
                });
    }

    private VerticalLayout buildDetail() {
        download.setText("Download current version");
        download.getElement().setAttribute("download", true);

        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> delete());

        placeholder.getStyle().set("color", "var(--lumo-secondary-text-color)");

        detail.add(new H4("Document"), info, download, delete);
        detail.setPadding(false);

        VerticalLayout side = new VerticalLayout(placeholder, detail);
        side.setPadding(true);
        side.setMinWidth("280px");
        return side;
    }

    private void select(DocumentSummaryDTO dto) {
        this.selected = dto;
        info.setText("\"" + dto.getName() + "\" — version " + dto.getCurrentVersion()
                + (dto.isLocked() ? " (locked by " + dto.getLockedBy() + ")" : ""));

        PUser user = currentUser.require();
        // Bytes are fetched lazily by the stream supplier only when the download is actually requested,
        // not on selection. The captured user is used so no re-resolution is needed on the resource thread.
        StreamResource resource = new StreamResource(dto.getName(),
                () -> new ByteArrayInputStream(documentService.getCurrentVersionForDownload(dto.getId(), user).data()));
        download.setHref(resource);
        showDetail(true);
    }

    private void delete() {
        if (selected == null) {
            return;
        }
        try {
            documentService.deleteDocument(selected.getId(), currentUser.require());
            notifySuccess("Document deleted");
            grid.deselectAll();
            showDetail(false);
            grid.getDataProvider().refreshAll();
        } catch (AccessDeniedException denied) {
            notifyError("You are not allowed to delete this document.");
        } catch (RuntimeException ex) {
            // e.g. IllegalStateException: locked documents cannot be deleted
            notifyError(ex.getMessage());
        }
    }

    private void showDetail(boolean visible) {
        detail.setVisible(visible);
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
