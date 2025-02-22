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
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
public class EgressRuleGenerator extends NotIngressRuleGenerator {

    private final FlowPath flowPath;
    private final Flow flow;
    private final FlowTransitEncapsulation encapsulation;

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        if (flowPath.isOneSwitchFlow() || flowPath.getSegments().isEmpty()) {
            return new ArrayList<>();
        }
        if (!sw.getSwitchId().equals(flowPath.getDestSwitchId())) {
            throw new IllegalArgumentException(format("Destination switch %s of flow path %s is not equal to "
                            + "generator switch %s",
                    flowPath.getDestSwitchId(), flowPath.getPathId(), sw.getSwitchId()));
        }

        PathSegment lastSegment = flowPath.getSegments().get(flowPath.getSegments().size() - 1);
        FlowEndpoint endpoint = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint();
        return Lists.newArrayList(buildEgressCommand(sw, lastSegment.getDestPort(), endpoint));
    }

    private SpeakerCommandData buildEgressCommand(Switch sw, int inPort, FlowEndpoint egressEndpoint) {
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(flowPath.getDestSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(flowPath.isDestWithMultiTable() ? OfTable.EGRESS : OfTable.INPUT)
                .priority(Priority.FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder()
                        .applyActions(buildApplyActions(egressEndpoint, sw))
                        .build());
        // TODO add mirror group support

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private List<Action> buildApplyActions(FlowEndpoint egressEndpoint, Switch sw) {
        List<Action> result = new ArrayList<>();
        if (VXLAN.equals(encapsulation.getType())) {
            if (sw.getFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
                result.add(new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW));
            } else if (sw.getFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
                result.add(new PopVxlanAction(ActionType.POP_VXLAN_OVS));
            } else {
                throw new IllegalArgumentException(format("Switch %s must support one of following features to pop "
                        + "VXLAN: [%s, %s]", sw.getSwitchId(), NOVIFLOW_PUSH_POP_VXLAN,
                        KILDA_OVS_PUSH_POP_MATCH_VXLAN));
            }
        }

        List<Integer> targetVlanStack = egressEndpoint.getVlanStack();
        List<Integer> currentVlanStack = new ArrayList<>();
        if (TRANSIT_VLAN.equals(encapsulation.getType())) {
            currentVlanStack.add(encapsulation.getId());
        }
        result.addAll(Utils.makeVlanReplaceActions(currentVlanStack, targetVlanStack));

        result.add(new PortOutAction(new PortNumber(egressEndpoint.getPortNumber())));
        return result;
    }
}
