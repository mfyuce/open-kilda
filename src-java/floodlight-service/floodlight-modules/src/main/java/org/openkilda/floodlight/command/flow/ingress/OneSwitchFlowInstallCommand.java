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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.flow.ingress.of.OneSwitchFlowInstallMultiTableFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.OneSwitchFlowInstallSingleTableFlowModFactory;
import org.openkilda.floodlight.model.EffectiveIds;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchFeature;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class OneSwitchFlowInstallCommand extends OneSwitchFlowCommand {
    @JsonCreator
    public OneSwitchFlowInstallCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("meter_config") MeterConfig meterConfig,
            @JsonProperty("egress_endpoint") FlowEndpoint egressEndpoint,
            @JsonProperty("rules_context") RulesContext rulesContext,
            @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(context, commandId, metadata, endpoint, meterConfig, egressEndpoint, rulesContext, mirrorConfig);
    }

    @Override
    protected void setupFlowModFactory() {
        if (metadata.isMultiTable()) {
            setFlowModFactory(new OneSwitchFlowInstallMultiTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        } else {
            setFlowModFactory(new OneSwitchFlowInstallSingleTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        }
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        return makeInstallPlan(commandProcessor);
    }

    @Override
    protected List<OFFlowMod> makeFlowModMessages(EffectiveIds effectiveIds) {
        List<OFFlowMod> ofMessages = super.makeFlowModMessages(effectiveIds);
        ofMessages.addAll(makeSharedFlowModInstallMessages());
        return ofMessages;
    }

    @Override
    protected List<Set<SwitchFeature>> getRequiredFeatures() {
        List<Set<SwitchFeature>> required = super.getRequiredFeatures();
        if (metadata.isMultiTable()) {
            required.add(Sets.newHashSet(SwitchFeature.MULTI_TABLE));
        }

        return required;
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
