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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;

import lombok.Getter;

import java.util.UUID;

@Getter
public abstract class IngressFlowLoopCommand extends IngressFlowSegmentBase {

    IngressFlowLoopCommand(MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
                           FlowEndpoint endpoint) {
        super(messageContext, endpoint.getSwitchId(), commandId, metadata, endpoint,
                null, endpoint.getSwitchId(), null, null, null);
    }

    /**
     * Make string representation of object (irreversible).
     */
    public String toString() {
        return String.format(
                "<ingress-flow-loop-%s{id=%s, metadata=%s, endpoint=%s}>",
                getSegmentAction(), commandId, metadata, endpoint);
    }

    protected abstract SegmentAction getSegmentAction();
}
