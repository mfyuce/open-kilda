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

package org.openkilda.rulemanager.factory.generator.flow;

import static java.lang.String.format;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;
import static org.openkilda.rulemanager.utils.Utils.isFullPortEndpoint;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class MultiTableIngressRuleGenerator extends IngressRuleGenerator {

    @Default
    protected final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        List<SpeakerCommandData> result = new ArrayList<>();
        FlowEndpoint ingressEndpoint = FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint();
        if (!ingressEndpoint.getSwitchId().equals(sw.getSwitchId())) {
            throw new IllegalArgumentException(format("Path %s has ingress endpoint %s with switchId %s. But switchId "
                    + "must be equal to target switchId %s", flowPath.getPathId(), ingressEndpoint,
                    ingressEndpoint.getSwitchId(), sw.getSwitchId()));
        }
        FlowSpeakerCommandData command = buildFlowIngressCommand(sw, ingressEndpoint);
        if (command == null) {
            return Collections.emptyList();
        }
        result.add(command);
        if (needToBuildFlowPreIngressRule(ingressEndpoint)) {
            result.add(buildFlowPreIngressCommand(sw, ingressEndpoint));
        }
        if (overlappingIngressAdapters.isEmpty()) {
            result.add(buildCustomerPortSharedCatchCommand(sw, ingressEndpoint));
        }

        SpeakerCommandData meterCommand = buildMeter(flowPath, config, flowPath.getMeterId(), sw);
        if (meterCommand != null) {
            result.add(meterCommand);
            command.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }

    private boolean needToBuildFlowPreIngressRule(FlowEndpoint ingressEndpoint) {
        if (!isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            // Full port flows do not need pre ingress shared rule
            return false;
        }
        for (FlowSideAdapter overlappingIngressAdapter : overlappingIngressAdapters) {
            if (overlappingIngressAdapter.getEndpoint().getOuterVlanId() == ingressEndpoint.getOuterVlanId()) {
                // some other flow already has shared rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    private FlowSpeakerCommandData buildCustomerPortSharedCatchCommand(Switch sw, FlowEndpoint endpoint) {
        PortColourCookie cookie = new PortColourCookie(CookieType.MULTI_TABLE_INGRESS_RULES, endpoint.getPortNumber());

        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.PRE_INGRESS)
                .build();

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.INPUT)
                .priority(Priority.INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE)
                .match(Sets.newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build()))
                .instructions(instructions);

        return builder.build();
    }

    private FlowSpeakerCommandData buildFlowPreIngressCommand(Switch sw, FlowEndpoint endpoint) {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(endpoint.getPortNumber())
                .vlanId(endpoint.getOuterVlanId())
                .build();

        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId()).build();
        Instructions instructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PopVlanAction()))
                .writeMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()))
                .goToTable(OfTable.INGRESS)
                .build();

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.PRE_INGRESS)
                .priority(Constants.Priority.FLOW_PRIORITY)
                .match(buildPreIngressMatch(endpoint))
                .instructions(instructions);

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private FlowSpeakerCommandData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        // TODO should we check if switch supports encapsulation?
        List<Action> actions = new ArrayList<>(buildTransformActions(
                ingressEndpoint.getInnerVlanId(), sw.getFeatures()));
        actions.add(new PortOutAction(new PortNumber(getOutPort(flowPath, flow))));

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(OfTable.INGRESS)
                .priority(isFullPortEndpoint(ingressEndpoint) ? Constants.Priority.DEFAULT_FLOW_PRIORITY
                        : Constants.Priority.FLOW_PRIORITY)
                .match(buildIngressMatch(ingressEndpoint))
                .instructions(buildInstructions(sw, actions));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private Instructions buildInstructions(Switch sw, List<Action> actions) {
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .goToTable(OfTable.POST_INGRESS)
                .build();
        addMeterToInstructions(flowPath.getMeterId(), sw, instructions);
        if (flowPath.isOneSwitchFlow()) {
            RoutingMetadata metadata = RoutingMetadata.builder().oneSwitchFlowFlag(true).build();
            instructions.setWriteMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()));
        }
        return instructions;
    }

    private Set<FieldMatch> buildPreIngressMatch(FlowEndpoint endpoint) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build());
    }

    @VisibleForTesting
    Set<FieldMatch> buildIngressMatch(FlowEndpoint endpoint) {
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build());

        if (isVlanIdSet(endpoint.getOuterVlanId())) {
            RoutingMetadata metadata = RoutingMetadata.builder()
                    .outerVlanId(endpoint.getOuterVlanId())
                    .build();
            match.add(FieldMatch.builder()
                    .field(Field.METADATA)
                    .value(metadata.getValue())
                    .mask(metadata.getMask())
                    .build());
        }

        if (isVlanIdSet(endpoint.getInnerVlanId())) {
            match.add(FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getInnerVlanId()).build());
        }
        return match;
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int innerVlan, Set<SwitchFeature> features) {
        List<Integer> currentStack = makeVlanStack(innerVlan);
        List<Integer> targetStack;
        if (flowPath.isOneSwitchFlow()) {
            targetStack = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint().getVlanStack();
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }
        // TODO do something with groups

        List<Action> transformActions = new ArrayList<>(Utils.makeVlanReplaceActions(currentStack, targetStack));

        if (encapsulation.getType() == VXLAN && !flowPath.isOneSwitchFlow()) {
            transformActions.add(buildPushVxlan(encapsulation.getId(), flowPath.getSrcSwitchId(),
                    flowPath.getDestSwitchId(), features));
        }
        return transformActions;
    }
}
