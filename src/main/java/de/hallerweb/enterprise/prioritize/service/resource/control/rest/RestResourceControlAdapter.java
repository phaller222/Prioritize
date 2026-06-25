package de.hallerweb.enterprise.prioritize.service.resource.control.rest;

import de.hallerweb.enterprise.prioritize.exception.ResourceCommandFailedException;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceCommandMessage;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceControlAdapter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ResourceControlAdapter} implementation for REST-controlled resources.
 * <p>
 * This is the standard/base transport: every resource with a set {@code ip} is
 * controllable via REST in principle. Sends the command as a JSON POST to
 * {@code http://<ip>:<port>/command}.
 * <p>
 * Always active (no profile/property gate), since REST is the always-available default.
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
        // Reachability is assumed optimistically here (IP set = addressable).
        // A real health check could be added later.
        return supports(resource);
    }

    @Override
    public void sendCommand(Resource resource, String command, String param, int slot) {
        int port = resource.getPort() != null ? resource.getPort() : 80;
        String url = "http://" + resource.getIp() + ":" + port + "/command";
        ResourceCommandMessage payload = new ResourceCommandMessage(command, param, slot);
        try {
            restClient.post()
                .uri(url)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            log.debug("REST command '{}' sent to resource {} (slot {}, {}).",
                command, resource.getId(), slot, url);
        } catch (RestClientException ex) {
            log.warn("REST command '{}' to resource {} ({}) failed: {}",
                command, resource.getId(), url, ex.getMessage());
            throw new ResourceCommandFailedException(
                "Device at " + url + " did not accept the command: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getTransportName() {
        return "REST";
    }
}