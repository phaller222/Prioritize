package de.hallerweb.enterprise.prioritize.service.resource.control.rest;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.exception.ResourceCommandFailedException;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceCommandMessage;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceControlAdapter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ResourceControlAdapter}-Implementierung für REST-gesteuerte Resourcen.
 * <p>
 * Dies ist der Standard-/Basis-Transport: jede Resource mit gesetzter {@code ip} ist
 * grundsätzlich per REST steuerbar. Sendet das Kommando als JSON-POST an
 * {@code http://<ip>/command}.
 * <p>
 * Immer aktiv (kein Profil/Property-Gate), da REST der immer verfügbare Default ist.
 *
 * @author peter haller
 */
@Component
@Log4j2
public class RestResourceControlAdapter implements ResourceControlAdapter {

    private final RestClient restClient = RestClient.create();

    @Override
    public boolean supports(Resource resource) {
        return resource.getIp() != null && !resource.getIp().isBlank();
    }

    @Override
    public boolean isAvailable(Resource resource) {
        // Erreichbarkeit wird hier optimistisch angenommen (IP gesetzt = ansprechbar).
        // Ein echter Health-Check könnte später ergänzt werden.
        return supports(resource);
    }

    @Override
    public void sendCommand(Resource resource, String command, String param) {
        int port = resource.getPort() != null ? resource.getPort() : 80;
        String url = "http://" + resource.getIp() + ":" + port + "/command";
        ResourceCommandMessage payload = new ResourceCommandMessage(command, param, 0);
        try {
            restClient.post()
                    .uri(url)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("REST-Command '{}' an Resource {} ({}) gesendet.",
                    command, resource.getId(), url);
        } catch (RestClientException ex) {
            log.warn("REST-Command '{}' an Resource {} ({}) fehlgeschlagen: {}",
                    command, resource.getId(), url, ex.getMessage());
            throw new ResourceCommandFailedException(
                    "Gerät unter " + url + " hat das Kommando nicht angenommen: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getTransportName() {
        return "REST";
    }
}
