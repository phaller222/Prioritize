package de.hallerweb.enterprise.prioritize.service.resource.control;

/**
 * JSON wire format for a control command sent to a resource.
 * <p>
 * Replaces the old colon-delimited text format ({@code COMMAND;PARAM:SLOT}) with
 * an extensible, self-describing JSON object. New fields can be added
 * without breaking existing device parsers.
 * <p>
 * Example payload:
 * <pre>{ "command": "SET_TEMP", "param": "21", "slot": 1 }</pre>
 *
 * @param command command identifier
 * @param param   optional free parameter value (may be {@code null})
 * @param slot    the caller's reserved slot (0 if not slot-bound)
 */
public record ResourceCommandMessage(String command, String param, int slot) {
}
