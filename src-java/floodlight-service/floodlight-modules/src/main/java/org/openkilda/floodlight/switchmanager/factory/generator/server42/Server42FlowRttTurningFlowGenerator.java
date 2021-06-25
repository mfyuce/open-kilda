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

package org.openkilda.floodlight.switchmanager.factory.generator.server42;

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetOutputPort;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetUdpSrcAction;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildInstructionApplyActions;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.convertDpIdToMac;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.MAC_ADDRESS_SIZE_IN_BITS;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FLOW_RTT_REVERSE_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FLOW_RTT_TURNING_PRIORITY;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_SWAP_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE;

import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;
import org.openkilda.model.SwitchFeature;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.List;
import java.util.Set;

@Builder
public class Server42FlowRttTurningFlowGenerator implements SwitchFlowGenerator {

    private final FeatureDetectorService featureDetectorService;

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        Set<SwitchFeature> features = featureDetectorService.detectSwitch(sw);
        if (!(features.contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST) || features.contains(KILDA_OVS_SWAP_FIELD))) {
            return SwitchFlowTuple.getEmpty();
        }

        OFFactory ofFactory = sw.getOFFactory();
        Match match = buildMatch(sw.getId(), ofFactory);
        List<OFAction> actions = Lists.newArrayList(
                actionSetUdpSrcAction(ofFactory, TransportPort.of(SERVER_42_FLOW_RTT_REVERSE_UDP_PORT)),
                actionSetOutputPort(ofFactory, OFPort.IN_PORT));
        if (features.contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST)) {
            actions.add(buildNoviflowSwapAction(ofFactory));
        } else if (features.contains(KILDA_OVS_SWAP_FIELD)) {
            actions.add(buildKildaOvsSwapAction(ofFactory));
        } else {
            return SwitchFlowTuple.getEmpty();
        }

        OFFlowMod flowMod = prepareFlowModBuilder(
                ofFactory, SERVER_42_FLOW_RTT_TURNING_COOKIE, SERVER_42_FLOW_RTT_TURNING_PRIORITY, INPUT_TABLE_ID)
                .setMatch(match)
                .setInstructions(ImmutableList.of(buildInstructionApplyActions(ofFactory, actions)))
                .build();
        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .build();
    }

    private static Match buildMatch(DatapathId dpid, OFFactory ofFactory) {
        return ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, convertDpIdToMac(dpid))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT))
                .build();
    }

    private static OFAction buildNoviflowSwapAction(OFFactory factory) {
        OFOxms oxms = factory.oxms();
        return factory.actions().buildNoviflowSwapField()
                .setNBits(MAC_ADDRESS_SIZE_IN_BITS)
                .setSrcOffset(0)
                .setDstOffset(0)
                .setOxmSrcHeader(oxms.buildEthSrc().getTypeLen())
                .setOxmDstHeader(oxms.buildEthDst().getTypeLen())
                .build();
    }

    private static OFAction buildKildaOvsSwapAction(OFFactory factory) {
        OFOxms oxms = factory.oxms();
        return factory.actions().buildKildaSwapField()
                .setNBits(MAC_ADDRESS_SIZE_IN_BITS)
                .setSrcOffset(0)
                .setDstOffset(0)
                .setOxmSrcHeader(oxms.buildEthSrc().getTypeLen())
                .setOxmDstHeader(oxms.buildEthDst().getTypeLen())
                .build();
    }
}
