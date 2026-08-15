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

package de.hallerweb.enterprise.prioritize.ui.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Builds the absolute URL that goes onto a physical NFC sticker, pointing at
 * {@link de.hallerweb.enterprise.prioritize.controller.scan.ScanPageController the scan page}.
 * <p>
 * <b>Why the address comes from the request.</b> The application does not know — and deliberately
 * does not configure — the address it is reachable under; the scan page itself only ever emits
 * relative links. But a sticker needs an absolute URL, and there is one address that is known to
 * work: the one the admin's browser is talking to right now. It loaded this GUI from it, so it
 * resolves and it is reachable. Reading it off the incoming request means an admin who opens the
 * GUI as {@code http://192.168.178.10:8080} gets exactly that host on the sticker, with no setting
 * to keep in sync.
 * <p>
 * The corollary is the warning {@link #looksLocalOnly(String)} exists for: an admin working on the
 * server itself sees {@code localhost}, and a phone tapping a sticker written with that name would
 * resolve it to the phone. A sticker is only as good as the address baked into it.
 *
 * @author peter haller
 */
public final class ScanUrl {

    private ScanUrl() {
    }

    /**
     * The scheme/host/port the given request was addressed with, as an origin without a trailing
     * slash (e.g. {@code http://192.168.178.10:8080}). The default port of the scheme is omitted,
     * so an installation behind 80/443 yields the short form a person would write down.
     */
    public static String origin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    /**
     * The URL to store on the tag with the given uuid, as an NDEF URL record.
     *
     * @param origin scheme/host/port, as returned by {@link #origin(HttpServletRequest)}
     * @param uuid   the uuid the {@code NfcUnit} was registered with
     */
    public static String forTag(String origin, String uuid) {
        return origin + "/scan/" + UriUtils.encodePathSegment(uuid, StandardCharsets.UTF_8);
    }

    /**
     * Whether the origin names the machine the browser itself runs on. Such an address is fine for
     * clicking around the GUI and useless on a sticker — the phone would resolve it to itself — so
     * the panel showing these URLs warns instead of pretending they are ready to write.
     */
    public static boolean looksLocalOnly(String origin) {
        if (origin == null) {
            return false;
        }
        String host = origin.replaceFirst("^[a-zA-Z]+://", "").replaceFirst(":\\d+$", "");
        return host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("[::1]")
                || host.equals("::1");
    }
}
