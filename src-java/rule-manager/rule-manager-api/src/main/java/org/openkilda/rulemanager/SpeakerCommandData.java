/* Copyright 2021 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.rulemanager;

import org.openkilda.floodlight.api.request.OfSpeakerBatchEntry;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@JsonSerialize
@Getter
@SuperBuilder
public abstract class SpeakerCommandData implements OfSpeakerBatchEntry {

    @Builder.Default
    protected UUID commandId = UUID.randomUUID();  // TODO: can collide

    @Singular
    protected Collection<UUID> dependsOnCommands;

    protected SwitchId switchId;  // TODO: is it really required here?

    protected OfVersion ofVersion;

    @Override
    public Set<UUID> dependencies() {
        return new HashSet<>(dependsOnCommands);
    }
}
