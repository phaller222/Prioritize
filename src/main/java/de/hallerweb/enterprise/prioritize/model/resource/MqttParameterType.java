package de.hallerweb.enterprise.prioritize.model.resource;

/**
 * Value type of an {@link MqttCommandParameter} as declared by a device during MQTT
 * discovery. Used for display and (later) input validation before a command is sent.
 */
public enum MqttParameterType {
    STRING,
    INT,
    FLOAT,
    BOOL,
    ENUM
}
