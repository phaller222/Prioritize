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


import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
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

    @ToString.Include
    private String name;
    private String description;

    // --- Physische Eigenschaften ---
    private boolean stationary;
    private boolean remote;
    private String ip;
    private boolean busy;
    private int maxSlots = 1;

    // --- Geolocation ---
    private String latitude;
    private String longitude;

    // --- MQTT Eigenschaften ---
    private boolean mqttResource;
    private String mqttUUID;
    private String mqttDataSendTopic;
    private String mqttDataReceiveTopic;
    private boolean mqttOnline;
    private Date mqttLastPing;
    private boolean agent;

    @ElementCollection
    private Set<String> mqttCommands;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "resource_id") // Verhindert Join-Table für NameValueEntries
    @OrderBy("mqttName")
    private Set<NameValueEntry> mqttValues;

    @Lob
    @Basic(fetch = FetchType.LAZY) // Lob nur laden, wenn man es wirklich braucht
    private byte[] mqttDataReceived;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] mqttDataToSend;

    // --- Beziehungen ---
    @ManyToOne(fetch = FetchType.LAZY) // Eine Resource gehört zu einer Abteilung
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    private PUser busyBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_group_id")
    private ResourceGroup resourceGroup;

    @Builder.Default
    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ResourceReservation> reservations = new HashSet<>();

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "resource_id")
    private Set<SkillRecord> skills = new HashSet<>();

    // --- Helper ---
    public void addReservation(ResourceReservation reservation) {
        this.reservations.add(reservation);
        reservation.setResource(this); // Wichtig für bidirektionale Bindung
    }

    @Override
    public int compareTo(Resource other) {
        return Integer.compare(this.id, other.id);
    }
}