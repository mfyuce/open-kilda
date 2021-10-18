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

import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.Utils;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.match.Match;

import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class MultiTableIngressRuleGenerator extends IngressRuleGenerator {

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        List<SpeakerCommandData> result = new ArrayList<>();
        FlowSideAdapter flowSide = FlowSideAdapter.makeIngressAdapter(flow, flowPath);
        FlowSpeakerCommandData command = buildFlowCommand(sw, flowSide);
        if (command == null) {
            return Collections.emptyList();
        }
        result.add(command);

        SpeakerCommandData meterCommand = buildMeter(flowPath.getMeterId());
        if (meterCommand != null) {
            result.add(meterCommand);
            command.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }

    private FlowSpeakerCommandData buildFlowCommand(Switch sw, FlowSideAdapter flowSide) {
        FlowEndpoint endpoint = flowSide.getEndpoint();
        Set<Match> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build());
        if (!isFullPortFlow(flowSide.getEndpoint())) {
            match.add(FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build());
        }
        List<Action> actions = new ArrayList<>();
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        // TODO should we check if switch supports encapsulation?
        actions.addAll(buildTransformActions(flowSide.getEndpoint()));
        addMeterToInstructions(flowPath.getMeterId(), sw, instructions);

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(OfTable.INPUT)
                .priority(isFullPortFlow(flowSide.getEndpoint()) ? Constants.Priority.DEFAULT_FLOW_PRIORITY
                        : Constants.Priority.FLOW_PRIORITY)
                .match(match)
                .instructions(instructions);

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private List<Action> buildTransformActions(FlowEndpoint ingressEndpoint) {
        List<Integer> currentStack = makeVlanStack(ingressEndpoint.getOuterVlanId());
        List<Integer> targetStack;
        if (flowPath.isOneSwitchFlow()) {
            FlowEndpoint egressEndpoint = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint();
            targetStack = makeVlanStack(egressEndpoint.getOuterVlanId());
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }

        List<Action> transformActions = new ArrayList<>(Utils.makeVlanReplaceActions(currentStack, targetStack));

        if (encapsulation.getType() == VXLAN && !flowPath.isOneSwitchFlow()) {
            transformActions.add(PushVxlanAction.builder().vni(encapsulation.getId()).build());
        }
        return transformActions;
    }
}
