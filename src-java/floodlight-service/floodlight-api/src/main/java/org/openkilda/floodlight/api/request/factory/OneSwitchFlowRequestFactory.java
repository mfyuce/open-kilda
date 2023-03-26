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

package org.openkilda.floodlight.api.request.factory;

import org.openkilda.floodlight.api.request.OneSwitchFlowInstallRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowRemoveRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowRequest;
import org.openkilda.floodlight.api.request.OneSwitchFlowVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MirrorConfig;

import lombok.Builder;

import java.util.Set;
import java.util.UUID;

public class OneSwitchFlowRequestFactory extends FlowSegmentRequestFactory {
    private final OneSwitchFlowRequest requestBlank;

    @Builder
    public OneSwitchFlowRequestFactory(
            MessageContext messageContext, FlowSegmentMetadata metadata, FlowEndpoint endpoint, MeterConfig meterConfig,
            FlowEndpoint egressEndpoint, RulesContext rulesContext, MirrorConfig mirrorConfig, Set<Integer> statVlans) {
        this(new RequestBlank(messageContext, metadata, endpoint, meterConfig, egressEndpoint,
                rulesContext, mirrorConfig, statVlans));
    }

    private OneSwitchFlowRequestFactory(OneSwitchFlowRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public OneSwitchFlowRequest makeInstallRequest(UUID commandId) {
        return new OneSwitchFlowInstallRequest(requestBlank, commandId);
    }

    @Override
    public OneSwitchFlowRequest makeRemoveRequest(UUID commandId) {
        return new OneSwitchFlowRemoveRequest(requestBlank, commandId);
    }

    @Override
    public OneSwitchFlowRequest makeVerifyRequest(UUID commandId) {
        return new OneSwitchFlowVerifyRequest(requestBlank, commandId);
    }

    private static class RequestBlank extends OneSwitchFlowRequest {
        RequestBlank(
                MessageContext context, FlowSegmentMetadata metadata, FlowEndpoint endpoint, MeterConfig meterConfig,
                FlowEndpoint egressEndpoint, RulesContext rulesContext, MirrorConfig mirrorConfig,
                Set<Integer> statVlans) {
            super(context, dummyCommandId, metadata, endpoint, meterConfig, egressEndpoint, rulesContext, mirrorConfig,
                    statVlans);
        }
    }
}
