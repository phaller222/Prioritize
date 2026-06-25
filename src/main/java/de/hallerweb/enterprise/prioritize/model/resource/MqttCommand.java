package de.hallerweb.enterprise.prioritize.model.resource;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A control command a device declared during MQTT discovery (e.g. {@code ON},
 * {@code SET_BRIGHTNESS}). A command may carry zero or more {@link MqttCommandParameter}s.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class MqttCommand extends PObject {

    /** Command identifier, unique per device (e.g. {@code SET_BRIGHTNESS}). */
    @EqualsAndHashCode.Include
    private String name;

    /** Optional human-readable description. */
    private String description;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "command_id") // unidirectional; avoids a join table
    private Set<MqttCommandParameter> parameters = new HashSet<>();
}
