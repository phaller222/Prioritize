package de.hallerweb.enterprise.prioritize.model.resource;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.Entity;
import lombok.*;

/**
 * Represents MQTT IoT data (name-value pairs).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class NameValueEntry extends PObject implements Comparable<NameValueEntry> {

    @ToString.Include
    private String mqttName; // Name of the data point

    private String mqttValues; // Kommagetrennte Werte (historisch)

    @Override
    public int compareTo(NameValueEntry other) {
        if (this.mqttName == null || other.getMqttName() == null) {
            return 0;
        }
        return this.mqttName.compareTo(other.getMqttName());
    }
}