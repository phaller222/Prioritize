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

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * Application shell for the admin GUI: a title bar with a logout button and a navigation drawer.
 * Annotated with {@link Layout}, so Vaadin automatically wraps every route that does not opt out
 * (via {@code autoLayout = false}, as {@link LoginView} does) into this layout. Individual views
 * therefore only render their own content; the frame (navigation, current user, logout) lives here
 * and is shown on every authenticated page.
 * <p>
 * Current user and logout are taken from Vaadin's {@link AuthenticationContext} rather than the
 * static security context, mirroring {@link DashboardView}.
 *
 * @author peter haller
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authContext) {
        String user = authContext.getPrincipalName().orElse("unknown");

        H1 title = new H1("Prioritize Admin");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        Span currentUser = new Span(user);
        currentUser.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button logout = new Button("Logout", event -> authContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, currentUser, logout);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(title);
        header.setWidthFull();
        header.setPadding(true);
        addToNavbar(header);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard", DashboardView.class));
        nav.addItem(new SideNavItem("Companies", CompanyView.class));
        nav.addItem(new SideNavItem("Departments", DepartmentView.class));
        nav.addItem(new SideNavItem("Users", UserView.class));
        nav.addItem(new SideNavItem("Roles", RoleView.class));
        nav.addItem(new SideNavItem("Groups", GroupsView.class));
        nav.addItem(new SideNavItem("Resources", ResourcesView.class));
        nav.addItem(new SideNavItem("Documents", DocumentsView.class));
        nav.addItem(new SideNavItem("Skills", SkillsView.class));
        nav.addItem(new SideNavItem("Skill Categories", SkillCategoriesView.class));
        addToDrawer(nav);
    }
}
