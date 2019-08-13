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

import org.openkilda.floodlight.api.FlowEndpoint;
import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.api.MeterConfig;
import org.openkilda.floodlight.model.SwitchDescriptor;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
abstract class IngressFlowSegmentBlankCommand extends AbstractIngressFlowSegmentCommand {
    // payload
    protected final Integer islPort;
    protected FlowTransitEncapsulation encapsulation;

    IngressFlowSegmentBlankCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, String flowId, Cookie cookie,
            FlowEndpoint endpoint, MeterConfig meterConfig, Integer islPort, FlowTransitEncapsulation encapsulation) {
        super(messageContext, switchId, commandId, flowId, cookie, endpoint, meterConfig);
        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    @Override
    protected List<OFAction> makeTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            // restore outer vlan removed by 'pre-match' rule
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
            actions.add(OfAdapter.INSTANCE.setVlanIdAction(of, endpoint.getOuterVlanId()));
        }

        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                actions.addAll(makeVlanEncapsulationTransformActions(of));
                break;
            default:
                throw new UnsupportedOperationException(String.format(
                        "%s do not support transit encapsulation type \"%s\" (dpId: %s, flowId: %s)",
                        getClass().getName(), encapsulation.getType(), switchId, flowId));
        }
        return actions;
    }

    private List<OFAction> makeVlanEncapsulationTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        if (! FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        }
        actions.add(OfAdapter.INSTANCE.setVlanIdAction(of, encapsulation.getId()));
        return actions;
    }

    @Override
    protected OFAction makeOutputAction(OFFactory of) {
        return super.makeOutputAction(of,  OFPort.of(islPort));
    }
}
