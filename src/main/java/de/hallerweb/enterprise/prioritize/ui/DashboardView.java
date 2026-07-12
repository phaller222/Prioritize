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

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * Authenticated landing page of the admin GUI: greets the logged-in user. {@link PermitAll} allows any
 * authenticated user; anonymous visitors are redirected to {@link LoginView} by the Vaadin navigation
 * access control. The surrounding frame (navigation, current user, logout) is provided by
 * {@link MainLayout}, into which this view is wrapped automatically. The current user name is taken
 * from Vaadin's {@link AuthenticationContext}.
 *
 * @author peter haller
 */
@Route("")
@PageTitle("Prioritize")
@PermitAll
public class DashboardView extends VerticalLayout {

    public DashboardView(AuthenticationContext authContext) {
        String user = authContext.getPrincipalName().orElse("unknown");
        add(new H1("Welcome, " + user));
        add(new Paragraph("Use the navigation drawer to administer companies."));
    }
}
