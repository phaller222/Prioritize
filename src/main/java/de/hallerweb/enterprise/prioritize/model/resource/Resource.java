package de.hallerweb.enterprise.prioritize.model.resource;
/*
 * Copyright 2015-2024 Peter Michael Haller and contributors
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


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity to represent a {@link Resource}. Demo UUID: 69178331-8dd9-4dd1-87f6-368f424006c2
 * <p>
 * Copyright: (c) 2014
 * </p>
 * <p>
 * Peter Haller
 * </p>
 *
 * @author peter haller
 */


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Resource extends PActor implements PAuthorizedObject, Comparable<Resource> {

    @Transient
    @Builder.Default
    private Integer currentOccupiedSlots = 0;

    @ToString.Include
    private String name;
    @ToString.Include
    private String description;

    // --- Physische Eigenschaften ---
    @Builder.Default
    private Boolean stationary = true;
    @Builder.Default
    private Boolean remote = true;
    private String ip;
    @Builder.Default
    private Integer port = 80;
    @Builder.Default
    private Boolean busy = false;
    @Builder.Default
    private Integer maxSlots = 1;

    // --- Geolocation ---
    private String latitude;
    private String longitude;

    // --- MQTT Eigenschaften ---
    @Builder.Default
    private Boolean mqttResource=false;
    private String mqttUUID;
    private String mqttDataSendTopic;
    private String mqttDataReceiveTopic;
    @Builder.Default
    private Boolean mqttOnline = false;
    @Builder.Default
    private LocalDateTime mqttLastPing = LocalDateTime.now();
    @Builder.Default
    private Boolean agent = false;

    @ElementCollection
    private Set<String> mqttCommands;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "resource_id") // Verhindert Join-Table für NameValueEntries
    @OrderBy("mqttName")
    private Set<NameValueEntry> mqttValues;


    @Basic(fetch = FetchType.LAZY)
    @Column(name = "mqtt_data_received", columnDefinition = "bytea")
    @Builder.Default
    private byte[] mqttDataReceived = new byte[]{};


    @Basic(fetch = FetchType.LAZY)
    @Column(name = "mqtt_data_to_send", columnDefinition = "bytea")
    @Builder.Default
    private byte[] mqttDataToSend = new byte[]{};

    // --- Beziehungen ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @JsonBackReference(value = "department-resources-fallback") // Eindeutige Back-Reference!
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"password", "roles", "departments", "reservations"})
    private PUser busyBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_group_id")
    @JsonBackReference("resourceGroupResources")
    private ResourceGroup resourceGroup;

    @Builder.Default
    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "resource-reservations")
    private Set<ResourceReservation> reservations = new HashSet<>();

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "resource_id")
    @JsonIgnore
    private Set<SkillRecord> skills = new HashSet<>();

    // --- Helper ---
    public void addReservation(ResourceReservation reservation) {
        this.reservations.add(reservation);
        reservation.setResource(this); // Wichtig für bidirektionale Bindung
    }

    @Override
    public int compareTo(Resource other) {
        return Long.compare(this.id, other.id);
    }

    public Boolean isFull() {
        return currentOccupiedSlots >= maxSlots;
    }

}