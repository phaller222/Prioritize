package de.hallerweb.enterprise.prioritize.service.resource.control;

/**
 * JSON-Wire-Format für ein an eine Resource gesendetes Steuerkommando.
 * <p>
 * Ersetzt das alte Doppelpunkt-getrennte Textformat ({@code COMMAND;PARAM:SLOT}) durch
 * ein erweiterbares, selbstbeschreibendes JSON-Objekt. Neue Felder können ergänzt werden,
 * ohne bestehende Geräte-Parser zu brechen.
 * <p>
 * Beispiel-Payload:
 * <pre>{ "command": "SET_TEMP", "param": "21", "slot": 1 }</pre>
 *
 * @param command Kommando-Bezeichner
 * @param param   optionaler freier Parameterwert (kann {@code null} sein)
 * @param slot    reservierter Slot des Aufrufers (0, falls nicht slot-gebunden)
 */
public record ResourceCommandMessage(String command, String param, int slot) {
}
