package de.hallerweb.enterprise.prioritize.service.resource.control;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;

/**
 * Port (Hexagonal Architecture) für die ausgehende Steuerung einer {@link Resource}.
 * <p>
 * Der Rest des Systems steuert eine Resource ausschließlich über dieses Interface und
 * weiß nicht, <em>wie</em> (über welchen Transport) das Kommando das Gerät erreicht.
 * Konkrete Implementierungen kapseln den Transport (MQTT, REST, ...).
 * <p>
 * Die <em>eingehende</em> Richtung (Gerät → System: Discovery, Status, Telemetrie) ist
 * bewusst NICHT Teil dieses Interfaces, da sie zu REST asymmetrisch ist und über einen
 * separaten Inbound-Pfad verarbeitet wird.
 *
 * @author peter haller
 */
public interface ResourceControlAdapter {

    /**
     * Gibt an, ob dieser Adapter die übergebene Resource grundsätzlich steuern kann
     * (Capability-Check, unabhängig vom aktuellen Online-Zustand).
     *
     * @param resource die zu prüfende Resource
     * @return {@code true}, wenn dieser Adapter für die Resource zuständig ist
     */
    boolean supports(Resource resource);

    /**
     * Gibt an, ob die Resource über diesen Adapter aktuell erreichbar ist
     * (z.B. MQTT: online; REST: IP gesetzt). Nur sinnvoll, wenn {@link #supports} true ist.
     *
     * @param resource die zu prüfende Resource
     * @return {@code true}, wenn die Resource über diesen Transport gerade steuerbar ist
     */
    boolean isAvailable(Resource resource);

    /**
     * Sendet ein Steuerkommando mit optionalem freiem Parameter an die Resource.
     *
     * @param resource Ziel-Resource
     * @param command  Kommando-Bezeichner (frei definiert)
     * @param param    optionaler, frei definierter Parameterwert (darf {@code null} sein)
     */
    void sendCommand(Resource resource, String command, String param);

    /**
     * Transport-Kennung für Logging/Diagnose (z.B. "MQTT", "REST").
     *
     * @return kurzer Transport-Name
     */
    String getTransportName();
}
