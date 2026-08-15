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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the URL that ends up on a physical sticker. A wrong one here is expensive in a way a wrong
 * screen is not: the tags are written once and handed out, and the error only shows up when someone
 * taps one on site.
 */
class ScanUrlTest {

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    @Test
    @DisplayName("origin: nimmt Schema, Host und Port so, wie der Browser die GUI aufgerufen hat")
    void origin_takesSchemeHostAndPortFromRequest() {
        assertEquals("http://192.168.178.10:8080",
                ScanUrl.origin(request("http", "192.168.178.10", 8080)));
    }

    @Test
    @DisplayName("origin: der Standardport des Schemas fällt weg")
    void origin_omitsDefaultPort() {
        assertEquals("http://prioritize.example", ScanUrl.origin(request("http", "prioritize.example", 80)));
        assertEquals("https://prioritize.example", ScanUrl.origin(request("https", "prioritize.example", 443)));
        // 443 under plain http is not the default and has to stay visible.
        assertEquals("http://prioritize.example:443", ScanUrl.origin(request("http", "prioritize.example", 443)));
    }

    @Test
    @DisplayName("forTag: hängt den Scan-Pfad an und kodiert die UUID")
    void forTag_appendsEncodedUuid() {
        assertEquals("http://192.168.178.10:8080/scan/abc-123",
                ScanUrl.forTag("http://192.168.178.10:8080", "abc-123"));
        assertEquals("http://host:8080/scan/a%20b%2Fc",
                ScanUrl.forTag("http://host:8080", "a b/c"));
    }

    @Test
    @DisplayName("looksLocalOnly: erkennt Adressen, die ein Handy nicht erreichen kann")
    void looksLocalOnly_detectsLoopbackAddresses() {
        assertTrue(ScanUrl.looksLocalOnly("http://localhost:8080"));
        assertTrue(ScanUrl.looksLocalOnly("http://127.0.0.1:8080"));
        assertTrue(ScanUrl.looksLocalOnly("http://[::1]:8080"));
        assertTrue(ScanUrl.looksLocalOnly("https://LOCALHOST"));

        assertFalse(ScanUrl.looksLocalOnly("http://192.168.178.10:8080"));
        assertFalse(ScanUrl.looksLocalOnly("https://prioritize.example"));
        assertFalse(ScanUrl.looksLocalOnly(null));
    }
}
