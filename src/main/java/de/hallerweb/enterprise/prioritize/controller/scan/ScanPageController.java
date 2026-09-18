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

package de.hallerweb.enterprise.prioritize.controller.scan;

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.util.UriUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

/**
 * The page a phone lands on after tapping an NFC sticker.
 * <p>
 * <b>Why a page and not just the REST endpoint:</b> a tag carries an NDEF URL record, so tapping it
 * opens the phone's browser — the request arrives as a <b>GET</b>, and browsers repeat GETs freely
 * (prefetch, reload, back button). {@code scan} <em>toggles</em> tracking, so a repeated GET would
 * stop a running clock by accident. The GET here therefore only renders; the confirming POST does
 * the work and redirects back to the GET, which makes a reload harmless.
 * <p>
 * The extra tap is not a tax. Without a screen the person at the container gets no feedback at all
 * that anything happened; with it they read what the tag points at and see the clock afterwards.
 * <p>
 * <b>Deliberately hand-written HTML.</b> This build is offline, so no template engine can be added,
 * and a Vaadin view would ship a lot of JavaScript to a phone for a page that has one button. Every
 * value interpolated below comes from user-supplied data (task and tag names) and is escaped.
 * <p>
 * Lives outside {@code /api/**} on purpose: that path is the stateless Basic-auth chain, while
 * everything else falls into the session-based chain with the form login, which is exactly what a
 * browser needs.
 * <p>
 * <b>Where the host comes from.</b> Nothing here knows the address this application is reachable
 * under, and nothing should: the form action and the redirect below are relative. The absolute URL
 * — scheme, host, port — lives on the <em>tag</em>. Writing a sticker means storing an NDEF URL
 * record like {@code http://<your-host>:8080/scan/<uuid>} on it, with any NFC writer; the uuid is
 * the one the {@code NfcUnit} was registered with, so the tag is written after the tag exists in
 * Prioritize, not before. Two consequences worth knowing before writing a batch: the host is baked
 * into each sticker, so moving the installation means rewriting them, and a name only works if the
 * scanning phone can resolve it — an IP address is the safer choice on a local network.
 * <p>
 * <b>Not part of the REST contract.</b> {@link Hidden} keeps this page out of the OpenAPI document.
 * The handlers below carry {@code @ResponseBody}, which is all springdoc needs to document them, and
 * a browser page handing back HTML would otherwise become an API class returning {@code String} in
 * every generated client — published, and only removable by breaking the contract again.
 *
 * @author peter haller
 */
@Hidden
@Controller
@RequestMapping("/scan")
@RequiredArgsConstructor
@Log4j2
public class ScanPageController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NfcUnitService nfcUnitService;
    private final TaskService taskService;

    /** Renders the tag's current state. Never changes anything — see the class comment. */
    @GetMapping(value = "/{uuid}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String show(@PathVariable String uuid,
                       @RequestParam(required = false) String done,
                       @AuthenticatedUser PUser currentUser,
                       HttpServletRequest request) {
        try {
            NfcUnit unit = nfcUnitService.getByUuid(uuid);
            return switch (unit.getType()) {
                case TIMETRACKER -> renderTracker(unit, uuid, done, currentUser, request);
                case EQUIPMENT -> renderEquipment(unit, uuid, done, currentUser, request);
                default -> page(escape(label(unit)),
                        "<p class=\"state\">Tag vom Typ " + escape(unit.getType().name()) + ".</p>",
                        button(uuid, "Scan registrieren", request));
            };
        } catch (NoSuchElementException e) {
            return problem("Unbekannter Aufkleber",
                    "Zu diesem Tag ist in Prioritize nichts hinterlegt. Wurde er schon registriert?");
        } catch (AccessDeniedException e) {
            return problem("Kein Zugriff",
                    "Du gehörst nicht zu dem Projekt, auf das dieser Aufkleber zeigt.");
        }
    }

    /**
     * Performs the scan and redirects back to the GET, so the browser's address bar ends up on a
     * page that is safe to reload. Failures are carried over as a rendered problem rather than an
     * error page — the person is standing at a container, not at a console.
     */
    @PostMapping("/{uuid}")
    @ResponseBody
    public ResponseEntity<String> perform(@PathVariable String uuid,
                                          @AuthenticatedUser PUser currentUser,
                                          HttpServletRequest request) {
        try {
            NfcUnitService.ScanResult result = nfcUnitService.scan(uuid, currentUser);
            log.info("Scan page: tag '{}' scanned by '{}' -> {}", uuid, currentUser.getUsername(), result.action());

            // Post/Redirect/Get: answering with the page itself would leave the browser sitting on the
            // POST, and a pull-to-refresh would then re-submit it — toggling the clock a second time.
            // 303 puts the address bar back on the GET, which is safe to repeat.
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(URI.create("/scan/" + UriUtils.encodePathSegment(uuid, StandardCharsets.UTF_8)
                            + "?done=" + UriUtils.encodeQueryParam(result.action(), StandardCharsets.UTF_8)))
                    .build();
        } catch (NoSuchElementException e) {
            return html(problem("Unbekannter Aufkleber",
                    "Zu diesem Tag ist in Prioritize nichts hinterlegt."));
        } catch (IllegalStateException e) {
            return html(scanRefused(uuid));
        } catch (AccessDeniedException e) {
            return html(problem("Kein Zugriff",
                    "Du gehörst nicht zu dem Projekt, auf das dieser Aufkleber zeigt."));
        }
    }

    private ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    /**
     * Why a scan bounced. Two different situations reach this point with the same exception — the tag
     * has no task, or an equipment tag's device is still clocked in on another job — and they need
     * different instructions, so the tag is read again to tell them apart. Whoever is standing at the
     * sticker needs to know what to do next, not which exception was thrown.
     */
    private String scanRefused(String uuid) {
        NfcUnit unit;
        try {
            unit = nfcUnitService.getByUuid(uuid);
        } catch (RuntimeException lookupFailed) {
            return problem("Scan nicht möglich", "Der Aufkleber lässt sich gerade nicht verwenden.");
        }
        if (unit.getTask() == null) {
            return unit.getType() == NfcUnit.NfcUnitType.EQUIPMENT
                    ? problem("Aufkleber ohne Aufgabe",
                        "Dieser Geräte-Aufkleber ist mit keiner Aufgabe verbunden. Im Admin-Bereich die "
                                + "Aufgabe zuordnen, an der das Gerät gerade arbeitet.")
                    : problem("Aufkleber ohne Aufgabe",
                        "Dieser Zeit-Aufkleber ist mit keiner Aufgabe verbunden, es gibt also nichts zu "
                                + "starten. Im Admin-Bereich eine Aufgabe zuordnen.");
        }
        return problem("Gerät ist noch woanders eingestochen",
                "Dieses Gerät läuft noch auf einer anderen Aufgabe — ein Gerät kann nicht an zwei "
                        + "Stellen gleichzeitig sein. Dort zuerst ausstechen, dann hier erneut scannen.");
    }

    // ==========================================
    // Rendering
    // ==========================================

    private String renderTracker(NfcUnit unit, String uuid, String done, PUser user, HttpServletRequest request) {
        Task task = unit.getTask();
        if (task == null) {
            return problem("Aufkleber ohne Aufgabe",
                    "Dieser Zeit-Aufkleber ist mit keiner Aufgabe verbunden. Im Admin-Bereich eine "
                            + "Aufgabe zuordnen, dann funktioniert er.");
        }

        TaskService.TrackingSummary summary = taskService.getTrackingSummary(task.getId(), user);
        // "running" is this user's own clock: several people can be on the same task at once, so the
        // button has to reflect the scanner, not the task.
        boolean running = summary.trackingForMe();
        int others = summary.runningCount() - (running ? 1 : 0);

        StringBuilder state = new StringBuilder();
        if (done != null) {
            state.append("<p class=\"done\">")
                    .append("TRACKING_STARTED".equals(done) ? "Eingestochen." : "Ausgestochen.")
                    .append("</p>");
        }
        state.append("<p class=\"state\">")
                .append(running
                        ? "Deine Uhr läuft" + since(summary.runningSince()) + "."
                        : "Du bist nicht eingestochen.")
                .append("</p>")
                .append(othersLine(others))
                .append("<p class=\"total\">Bisher gebucht: ")
                .append(escape(humanDuration(summary.totalSeconds())))
                .append("</p>");

        return page(escape(task.getName()), state.toString(),
                button(uuid, running ? "Ausstechen" : "Einstechen", request));
    }

    /**
     * The equipment counterpart of {@link #renderTracker}: the sticker sits on the device, so the
     * page talks about the device's clock, never the reader's. Someone scanning a lift is recording
     * where the machine is, and telling them "deine Uhr läuft" would be answering a question they
     * did not ask.
     */
    private String renderEquipment(NfcUnit unit, String uuid, String done, PUser user, HttpServletRequest request) {
        Task task = unit.getTask();
        if (task == null) {
            return problem("Aufkleber ohne Aufgabe",
                    "Dieser Geräte-Aufkleber ist mit keiner Aufgabe verbunden. Im Admin-Bereich die "
                            + "Aufgabe zuordnen, an der das Gerät gerade arbeitet.");
        }
        Resource device = unit.getResource();
        if (device == null) {
            return problem("Aufkleber ohne Gerät",
                    "Dieser Aufkleber hängt an keinem Betriebsmittel — ohne Gerät gibt es keine "
                            + "Gerätezeit zu buchen.");
        }

        TaskService.EquipmentUsageSummary usage = taskService.getEquipmentUsage(task.getId(), user).stream()
                .filter(u -> device.getId().equals(u.resourceId()))
                .findFirst()
                .orElse(null);
        boolean running = usage != null && usage.running();

        StringBuilder state = new StringBuilder();
        if (done != null) {
            state.append("<p class=\"done\">")
                    .append("EQUIPMENT_CLOCKED_IN".equals(done) ? "Gerät eingestochen." : "Gerät ausgestochen.")
                    .append("</p>");
        }
        state.append("<p class=\"state\">")
                .append(running
                        ? escape(device.getName()) + " läuft auf dieser Aufgabe"
                            + since(usage.runningSince()) + "."
                        : escape(device.getName()) + " ist hier nicht eingestochen.")
                .append("</p>")
                .append("<p class=\"total\">Bisher gebucht: ")
                .append(escape(humanDuration(usage == null ? 0 : usage.totalSeconds())))
                .append("</p>");

        return page(escape(task.getName()), state.toString(),
                button(uuid, running ? "Gerät ausstechen" : "Gerät einstechen", request));
    }

    /**
     * Who else is on this task right now. Worth showing because the clocks are independent: without
     * this line a second person scanning the same sticker gets no hint that a colleague is already
     * booked in, and the crew cannot tell a shared task from a lonely one.
     */
    private String othersLine(int others) {
        if (others <= 0) {
            return "";
        }
        return "<p class=\"state\">" + (others == 1
                ? "Außerdem ist gerade 1 Kollege eingestochen."
                : "Außerdem sind gerade " + others + " Kollegen eingestochen.") + "</p>";
    }

    private String since(Instant runningSince) {
        if (runningSince == null) {
            return "";
        }
        return " seit " + TIME.format(runningSince.atZone(ZoneId.systemDefault()));
    }

    /** Whole hours and minutes; seconds are noise for someone reading this on a phone. */
    private String humanDuration(long totalSeconds) {
        Duration d = Duration.ofSeconds(totalSeconds);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours == 0 && minutes == 0) {
            return "noch nichts";
        }
        return hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
    }

    private String label(NfcUnit unit) {
        if (unit.getName() != null && !unit.getName().isBlank()) {
            return unit.getName();
        }
        return unit.getResource() != null ? unit.getResource().getName() : "Aufkleber";
    }

    /**
     * The form carries the CSRF token when the filter chain put one in the request. Reading it from
     * the attribute rather than assuming either setting keeps the page working whether or not CSRF
     * protection is active on the chain this path falls into.
     */
    private String button(String uuid, String caption, HttpServletRequest request) {
        Object token = request.getAttribute(CsrfToken.class.getName());
        String hidden = "";
        if (token instanceof CsrfToken csrf) {
            hidden = "<input type=\"hidden\" name=\"" + escape(csrf.getParameterName())
                    + "\" value=\"" + escape(csrf.getToken()) + "\">";
        }
        return "<form method=\"post\" action=\"/scan/" + escape(uuid) + "\">"
                + hidden
                + "<button type=\"submit\">" + escape(caption) + "</button>"
                + "</form>";
    }

    private String problem(String title, String message) {
        return page(escape(title), "<p class=\"problem\">" + escape(message) + "</p>", "");
    }

    /**
     * The whole document. Inline styles because a separate stylesheet would be one more round trip
     * on a phone that just woke up on site WLAN, and the page is this short.
     * <p>
     * Filled by replacing placeholders rather than with {@code String.formatted}: the stylesheet
     * contains a {@code 100%} width, and a percent sign in a format string blows up at runtime with
     * an unhelpful "Conversion" error. Placeholders keep the CSS free to grow.
     */
    private String page(String heading, String body, String action) {
        return TEMPLATE
                .replace("{{heading}}", heading)
                .replace("{{body}}", body)
                .replace("{{action}}", action);
    }

    private static final String TEMPLATE = """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Prioritize</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 0; padding: 2rem 1.5rem;
                           background: #f7f7f8; color: #1a1a1a; }
                    h1 { font-size: 1.6rem; line-height: 1.2; margin: 0 0 1.5rem; }
                    p { font-size: 1.15rem; margin: .4rem 0; }
                    .done { font-weight: 600; color: #14663a; }
                    .state { font-size: 1.35rem; font-weight: 600; }
                    .total, .problem { color: #555; }
                    button { display: block; width: 100%; margin-top: 2.5rem; padding: 1.3rem;
                             font-size: 1.3rem; font-weight: 600; color: #fff; background: #1f6feb;
                             border: 0; border-radius: .75rem; }
                    button:active { background: #1a5fcc; }
                  </style>
                </head>
                <body>
                  <h1>{{heading}}</h1>
                  {{body}}
                  {{action}}
                </body>
                </html>
                """;

    /** Task and tag names are user input and land in HTML — escape before they do. */
    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
