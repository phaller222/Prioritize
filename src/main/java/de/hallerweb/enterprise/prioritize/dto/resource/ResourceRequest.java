/*
 * Copyright 2026 Peter Michael Haller and contributors
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

package de.hallerweb.enterprise.prioritize.dto.resource;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;

/**
 * Request body for creating or patching a {@link Resource}. Carries only the client-writable base data —
 * never an {@code id}, the owning department/group (those come from the path), the runtime MQTT payload
 * buffers, reservations or skills. All fields are boxed/nullable so the same record serves a PATCH (only
 * non-null fields are applied by the service); {@code createResource} fills its own defaults for the ones
 * left null.
 *
 * @author peter haller
 */
public record ResourceRequest(String name,
                              String description,
                              String ip,
                              Integer port,
                              Integer maxSlots,
                              Boolean stationary,
                              Boolean remote,
                              Boolean agent,
                              String latitude,
                              String longitude,
                              Boolean mqttResource,
                              String mqttUUID,
                              String mqttDataSendTopic,
                              String mqttDataReceiveTopic,
                              Boolean mqttOnline) {

    /**
     * Builds a plain, id-less {@link Resource} carrying exactly the supplied fields. Uses the no-arg
     * constructor and setters on purpose — NOT the Lombok builder, whose {@code @Builder.Default} values
     * would turn a null (= "unchanged") into a concrete value and break the PATCH semantics. This mirrors
     * what Jackson used to deserialize straight into the entity.
     */
    public Resource toResource() {
        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setIp(ip);
        resource.setPort(port);
        resource.setMaxSlots(maxSlots);
        resource.setStationary(stationary);
        resource.setRemote(remote);
        resource.setAgent(agent);
        resource.setLatitude(latitude);
        resource.setLongitude(longitude);
        resource.setMqttResource(mqttResource);
        resource.setMqttUUID(mqttUUID);
        resource.setMqttDataSendTopic(mqttDataSendTopic);
        resource.setMqttDataReceiveTopic(mqttDataReceiveTopic);
        resource.setMqttOnline(mqttOnline);
        return resource;
    }
}
