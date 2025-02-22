/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.test.standard;

import static java.util.Collections.singletonList;
import static org.openkilda.floodlight.pathverification.PathVerificationService.DISCOVERY_PACKET_UDP_PORT;
import static org.openkilda.floodlight.pathverification.PathVerificationService.LATENCY_PACKET_UDP_PORT;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ROUND_TRIP_LATENCY_T1_OFFSET;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ROUND_TRIP_LATENCY_TIMESTAMP_SIZE;
import static org.openkilda.floodlight.switchmanager.SwitchManager.BDF_DEFAULT_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.CATCH_BFD_RULE_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_COOKIE_MASK;
import static org.openkilda.floodlight.switchmanager.SwitchManager.FLOW_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.MINIMAL_POSITIVE_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.ROUND_TRIP_LATENCY_RULE_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_FORWARD_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_REVERSE_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.VERIFICATION_RULE_VXLAN_PRIORITY;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;

import org.openkilda.floodlight.OFFactoryMock;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.GroupId;
import org.openkilda.model.cookie.Cookie;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.Arrays;

public interface OutputCommands {
    String VERIFICATION_BCAST_PACKET_DST = "00:26:E1:FF:FF:FF";
    MacAddress FLOW_PING_MAGIC_SRC_MAC_ADDRESS = MacAddress.of("00:26:E1:FF:FF:FE");

    OFFactory ofFactory = new OFFactoryMock();

    /**
     * Build transit rule for flow.
     *
     * @param inputPort input port.
     * @param outputPort output port.
     * @param tunnelId vlan value.
     * @param cookie cookie for the rule.
     * @return built command.
     */
    default OFFlowAdd transitFlowMod(int inputPort, int outputPort, int tunnelId, long cookie,
                                     FlowEncapsulationType encapsulationType) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(cookie & FLOW_COOKIE_MASK))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(FLOW_PRIORITY)
                .setMatch(matchFlow(null, inputPort, tunnelId, encapsulationType))
                .setInstructions(singletonList(
                        ofFactory.instructions().applyActions(singletonList(
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(outputPort))
                                        .build()))
                                .createBuilder()
                                .build()))
                .setXid(0L)
                .build();
    }

    default Match matchFlow(DatapathId dpid, int inputPort, int tunnelId, FlowEncapsulationType encapsulationType) {
        Match.Builder matchBuilder = ofFactory.buildMatch();
        matchBuilder.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        switch (encapsulationType) {
            default:
            case TRANSIT_VLAN:
                matchBuilder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(tunnelId));
                break;
            case VXLAN:
                matchBuilder.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
                matchBuilder.setExact(MatchField.TUNNEL_ID, U64.of(tunnelId));
                break;
        }

        return matchBuilder.build();
    }

    /**
     * Create droop loop rule.
     *
     * @param dpid datapath of the switch.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installDropLoopRule(DatapathId dpid) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, MacAddress.of(VERIFICATION_BCAST_PACKET_DST))
                .setExact(MatchField.ETH_SRC, MacAddress.of(Arrays.copyOfRange(dpid.getBytes(), 2, 8)))
                .build();

        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE))
                .setPriority(SwitchManager.DROP_VERIFICATION_LOOP_RULE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .build();
    }

    /**
     * Create drop flow for specific table.
     *
     * @param dpid datapath of the switch.
     * @param tableId table to install.
     * @param cookie target cookie.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installDropFlowForTable(DatapathId dpid, int tableId, long cookie) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(tableId))
                .setCookie(U64.of(cookie))
                .setPriority(MINIMAL_POSITIVE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(ofFactory.buildMatch().build())
                .build();
    }

    /**
     * Create pass through egress isl vxlan default rule for table 0.
     *
     * @param dpid datapath of the switch.
     * @param port isl port.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installEgressIslVxlanRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, MacAddress.of(Arrays.copyOfRange(dpid.getBytes(), 2, 8)))
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .setExact(MatchField.UDP_SRC, TransportPort.of(SwitchManager.STUB_VXLAN_UDP_SRC))
                .setExact(MatchField.UDP_DST, TransportPort.of(SwitchManager.VXLAN_UDP_DST))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIslVxlanEgress(port)))
                .setPriority(SwitchManager.ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.EGRESS_TABLE_ID))))
                .build();
    }

    /**
     * Create pass through transit isl vxlan default rule for table 0.
     *
     * @param dpid datapath of the switch.
     * @param port isl port.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installTransitIslVxlanRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .setExact(MatchField.UDP_SRC, TransportPort.of(SwitchManager.STUB_VXLAN_UDP_SRC))
                .setExact(MatchField.UDP_DST, TransportPort.of(SwitchManager.VXLAN_UDP_DST))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIslVxlanTransit(port)))
                .setPriority(SwitchManager.ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.TRANSIT_TABLE_ID))))
                .build();
    }

    /**
     * Create pass through egress isl vlan default rule for table 0.
     *
     * @param dpid datapath of the switch.
     * @param port isl port.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installEgressIslVlanRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIslVlanEgress(port)))
                .setPriority(SwitchManager.ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.EGRESS_TABLE_ID))))
                .build();
    }


    /**
     * Install intermediate rule for isl on switch in table 0 to route ingress traffic.
     * @param dpid datapathId of the switch
     * @param port customer port
     */
    default OFFlowAdd installIntermediateIngressRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIngressRulePassThrough(port)))
                .setPriority(SwitchManager.INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID))))
                .build();
    }

    /**
     * Install default pass through rule for pre ingress table.
     * @param dpid datapathId of the switch
     */
    default OFFlowAdd installPreIngressTablePassThroughDefaultRule(DatapathId dpid) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID))
                .setCookie(U64.of(Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE))
                .setPriority(MINIMAL_POSITIVE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(ofFactory.buildMatch().build())
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.INGRESS_TABLE_ID))))
                .build();
    }

    /**
     * Install default pass through rule for pre egress table.
     * @param dpid datapathId of the switch
     */
    default OFFlowAdd installEgressTablePassThroughDefaultRule(final DatapathId dpid) {
        return ofFactory.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.EGRESS_TABLE_ID))
                .setCookie(U64.of(Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE))
                .setPriority(MINIMAL_POSITIVE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(ofFactory.buildMatch().build())
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.TRANSIT_TABLE_ID))))
                .build();
    }

    default OFFlowAdd installVerificationBroadcastRule(boolean supportsUpdPortMatch) {

        Match.Builder matchBuilder = ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, MacAddress.of(VERIFICATION_BCAST_PACKET_DST));
        if (supportsUpdPortMatch) {
            matchBuilder
                    .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                    .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                    .setExact(MatchField.UDP_DST, TransportPort.of(DISCOVERY_PACKET_UDP_PORT));
        }
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.VERIFICATION_BROADCAST_RULE_COOKIE))
                .setPriority(SwitchManager.VERIFICATION_RULE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(matchBuilder.build())
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(2L).build(),
                        ofFactory.instructions().applyActions(singletonList(
                                ofFactory.actions()
                                        .group(OFGroup.of(GroupId.ROUND_TRIP_LATENCY_GROUP_ID.intValue()))))))
                .build();
    }

    default OFFlowAdd installVerificationUnicastRule(DatapathId defaultDpId) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_SRC, FLOW_PING_MAGIC_SRC_MAC_ADDRESS)
                .setExact(MatchField.ETH_DST, MacAddress.of(defaultDpId))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.VERIFICATION_UNICAST_RULE_COOKIE))
                .setPriority(SwitchManager.VERIFICATION_RULE_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().buildMeter().setMeterId(3L).build(),
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.CONTROLLER)
                                        .build(),
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildEthDst()
                                                        .setValue(MacAddress.of(defaultDpId))
                                                        .build())
                                        .build()))))
                .build();
    }

    /**
     * Expected result for install default rules.
     *
     * @return expected OFFlowAdd instance.
     */
    default OFFlowAdd installDropFlowRule() {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.DROP_RULE_COOKIE))
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(MINIMAL_POSITIVE_PRIORITY)
                .setMatch(ofFactory.buildMatch().build())
                .setXid(0L)
                .build();
    }

    /**
     * Expected result for install default BFD catch rule.
     *
     * @param dpid datapath of the switch.
     * @return expected OFFlowAdd instance.
     */
    default OFFlowAdd installBfdCatchRule(DatapathId dpid) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, MacAddress.of(dpid))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_DST, TransportPort.of(BDF_DEFAULT_PORT))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.CATCH_BFD_RULE_COOKIE))
                .setMatch(match)
                .setPriority(CATCH_BFD_RULE_PRIORITY)
                .setActions(ImmutableList.of(
                        ofFactory.actions().buildOutput()
                                .setPort(OFPort.LOCAL)
                                .build()))
                .build();
    }

    /**
     * Expected result for install default round trip latency rule.
     *
     * @param dpid datapath of the switch.
     * @return expected OFFlowAdd instance.
     */
    default OFFlowAdd installRoundTripLatencyRule(DatapathId dpid) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.ETH_SRC, MacAddress.of(dpid))
                .setExact(MatchField.ETH_DST, MacAddress.of(VERIFICATION_BCAST_PACKET_DST))
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_DST, TransportPort.of(LATENCY_PACKET_UDP_PORT))
                .build();
        OFOxms oxms = ofFactory.oxms();

        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE))
                .setMatch(match)
                .setPriority(ROUND_TRIP_LATENCY_RULE_PRIORITY)
                .setActions(ImmutableList.of(
                        ofFactory.actions().buildNoviflowCopyField()
                                .setNBits(ROUND_TRIP_LATENCY_TIMESTAMP_SIZE)
                                .setSrcOffset(0)
                                .setDstOffset(ROUND_TRIP_LATENCY_T1_OFFSET)
                                .setOxmSrcHeader(oxms.buildNoviflowRxtimestamp().getTypeLen())
                                .setOxmDstHeader(oxms.buildNoviflowPacketOffset().getTypeLen())
                                .build(),
                        ofFactory.actions().buildOutput()
                                .setPort(OFPort.CONTROLLER)
                                .setMaxLen(0xFFFFFFFF)
                                .build()))
                .build();
    }

    /**
     * Expected result for install default unicast verification rule for vxlan.
     *
     * @param dpid datapath of the switch.
     * @return expected OFFlowAdd instance.
     */
    default OFFlowAdd installUnicastVerificationRuleVxlan(DatapathId dpid) {
        Match match = ofFactory.buildMatch()
                .setMasked(MatchField.ETH_SRC, FLOW_PING_MAGIC_SRC_MAC_ADDRESS, MacAddress.NO_MASK)
                .setMasked(MatchField.ETH_DST, MacAddress.of(dpid), MacAddress.NO_MASK)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(4500))
                .build();

        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE))
                .setMatch(match)
                .setPriority(VERIFICATION_RULE_VXLAN_PRIORITY)
                .setInstructions(Arrays.asList(ofFactory.instructions().buildMeter().setMeterId(7L).build(),
                        ofFactory.instructions().applyActions(ImmutableList.of(
                                ofFactory.actions().noviflowPopVxlanTunnel(),
                                ofFactory.actions().buildOutput()
                                        .setPort(OFPort.CONTROLLER)
                                        .setMaxLen(0xFFFFFFFF)
                                        .build(),
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildEthDst()
                                                        .setValue(MacAddress.of(dpid))
                                                        .build()).build()))
                ))
                .build();
    }

    default OFFlowAdd installServer42IslRttInputFlowMod(DatapathId dpid, int server42Port, int islPort,
                                                        String fakedMacAddress) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(server42Port))
                .setMasked(MatchField.ETH_DST, MacAddress.of(dpid), MacAddress.NO_MASK)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(10000 + islPort))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeServer42IslRttInput(islPort)))
                .setPriority(SwitchManager.SERVER_42_ISL_RTT_INPUT_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildUdpSrc()
                                                        .setValue(TransportPort.of(SERVER_42_ISL_RTT_FORWARD_UDP_PORT))
                                                        .build()).build(),
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildEthDst()
                                                        .setValue(MacAddress.of(fakedMacAddress))
                                                        .build()).build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(islPort))
                                        .build()))))
                .build();
    }

    default OFFlowAdd installServer42IslRttTurningFlowMod(String fakedMacAddress) {
        Match match = ofFactory.buildMatch()
                .setMasked(MatchField.ETH_DST, MacAddress.of(fakedMacAddress), MacAddress.NO_MASK)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(SERVER_42_ISL_RTT_FORWARD_UDP_PORT))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(SERVER_42_ISL_RTT_TURNING_COOKIE))
                .setPriority(SwitchManager.SERVER_42_ISL_RTT_TURNING_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildUdpSrc()
                                                        .setValue(TransportPort.of(SERVER_42_ISL_RTT_REVERSE_UDP_PORT))
                                                        .build()).build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.IN_PORT)
                                        .build()))))
                .build();
    }

    default OFFlowAdd installServer42IslRttOutputFlowMod(DatapathId dpid, int server42Port,
                                                         String server42MacAddress, String fakedMacAddress) {
        Match match = ofFactory.buildMatch()
                .setMasked(MatchField.ETH_DST, MacAddress.of(fakedMacAddress), MacAddress.NO_MASK)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(SERVER_42_ISL_RTT_REVERSE_UDP_PORT))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(SERVER_42_ISL_RTT_OUTPUT_COOKIE))
                .setPriority(SwitchManager.SERVER_42_ISL_RTT_OUTPUT_PRIORITY)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(Arrays.asList(
                        ofFactory.instructions().applyActions(Arrays.asList(
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildEthSrc()
                                                        .setValue(MacAddress.of(dpid))
                                                        .build()).build(),
                                ofFactory.actions().buildSetField()
                                        .setField(
                                                ofFactory.oxms().buildEthDst()
                                                        .setValue(MacAddress.of(server42MacAddress))
                                                        .build()).build(),
                                ofFactory.actions().buildOutput()
                                        .setMaxLen(0xFFFFFFFF)
                                        .setPort(OFPort.of(server42Port))
                                        .build()))))
                .build();
    }
}
