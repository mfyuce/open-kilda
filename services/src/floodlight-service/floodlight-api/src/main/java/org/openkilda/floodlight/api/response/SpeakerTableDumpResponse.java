/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.api.response;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.OfFlowSchema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@Getter
@ToString(callSuper = true)
public class SpeakerTableDumpResponse extends SpeakerResponse {
    @JsonProperty("table_id")
    private final int tableId;

    @JsonProperty("entries")
    private final List<OfFlowSchema> entries;

    @Builder
    @JsonCreator
    public SpeakerTableDumpResponse(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("table_id") int tableId,
            @JsonProperty("entries") List<OfFlowSchema> entries) {
        super(messageContext, commandId, switchId);
        this.tableId = tableId;
        this.entries = entries;
    }
}
