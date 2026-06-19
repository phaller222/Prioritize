package de.hallerweb.enterprise.prioritize.service.resource.control;

import de.hallerweb.enterprise.prioritize.exception.ResourceOfflineException;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.resource.control.mqtt.MqttResourceControlAdapter;
import de.hallerweb.enterprise.prioritize.service.resource.control.rest.RestResourceControlAdapter;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Zentraler Einstiegspunkt für die Steuerung von Resourcen. Wählt pro Kommando den
 * passenden Transport und setzt die Berechtigungsprüfung durch (Konvention: Autorisierung
 * im Service-Layer per Exception).
 * <p>
 * <strong>Resolution-Strategie (Capability-Set mit Fallback):</strong>
 * <ol>
 *   <li>Resource hat MQTT-Capability und ist online → MQTT</li>
 *   <li>Resource hat MQTT-Capability, ist aber offline → REST-Fallback,
 *       sofern ein REST-Endpunkt (ip) existiert</li>
 *   <li>Keine MQTT-Capability → REST</li>
 *   <li>Kein erreichbarer Transport → {@link ResourceOfflineException}</li>
 * </ol>
 * <p>
 * REST ist die immer aktive Basis; MQTT ist eine optionale, zusätzliche Capability.
 * Der MQTT-Adapter ist nur vorhanden, wenn {@code prioritize.mqtt.enabled=true} —
 * daher wird er über einen {@link ObjectProvider} optional aufgelöst.
 *
 * @author peter haller
 */
@Service
@Log4j2
public class ResourceControlService {

    private final AuthorizationService authService;
    private final RestResourceControlAdapter restAdapter;
    private final ObjectProvider<MqttResourceControlAdapter> mqttAdapterProvider;

    public ResourceControlService(AuthorizationService authService,
                                  RestResourceControlAdapter restAdapter,
                                  ObjectProvider<MqttResourceControlAdapter> mqttAdapterProvider) {
        this.authService = authService;
        this.restAdapter = restAdapter;
        this.mqttAdapterProvider = mqttAdapterProvider;
    }

    /**
     * Sendet ein Steuerkommando an eine Resource. Steuern gilt als Zustandsänderung und
     * erfordert daher {@link Action#UPDATE}-Berechtigung auf der Resource.
     *
     * @param resource Ziel-Resource
     * @param command  Kommando-Bezeichner
     * @param param    optionaler freier Parameter (darf {@code null} sein)
     * @param user     der ausführende Benutzer (Rechteprüfung)
     * @throws AccessDeniedException     wenn der Benutzer nicht steuern darf
     * @throws ResourceOfflineException  wenn kein erreichbarer Transport existiert
     */
    public void sendCommand(Resource resource, String command, String param, PUser user) {
        if (!authService.hasPermission(user, resource, Action.UPDATE)) {
            throw new AccessDeniedException(
                    "Keine Berechtigung, diese Resource zu steuern.");
        }

        ResourceControlAdapter adapter = resolveAdapter(resource);
        log.info("Command '{}' an Resource {} via {} (User: {}).",
                command, resource.getId(), adapter.getTransportName(), user.getUsername());
        adapter.sendCommand(resource, command, param);
    }

    /**
     * Wählt den Transport gemäß Capability-Set-Strategie mit REST-Fallback.
     */
    private ResourceControlAdapter resolveAdapter(Resource resource) {
        MqttResourceControlAdapter mqtt = mqttAdapterProvider.getIfAvailable();

        // 1. MQTT bevorzugt, wenn Capability vorhanden UND online
        if (mqtt != null && mqtt.isAvailable(resource)) {
            return mqtt;
        }

        // 2./3. REST-Fallback, sofern ein REST-Endpunkt existiert
        if (restAdapter.isAvailable(resource)) {
            if (mqtt != null && mqtt.supports(resource)) {
                log.debug("Resource {} ist MQTT-Resource, aber offline → REST-Fallback.",
                        resource.getId());
            }
            return restAdapter;
        }

        // 4. Kein Transport erreichbar
        throw new ResourceOfflineException(
                "Resource " + resource.getId() + " ist offline und besitzt keinen Steuerkanal "
                        + "(MQTT offline, kein REST-Endpunkt).");
    }
}
