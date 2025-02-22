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

package org.openkilda.floodlight.switchmanager.factory;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.floodlight.switchmanager.factory.generator.BfdCatchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.DropFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.DropLoopFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.RoundTripLatencyFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.TablePassThroughDefaultFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.UnicastVerificationVxlanRuleGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.VerificationFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.arp.ArpIngressFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.arp.ArpInputPreDropFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.arp.ArpPostIngressFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.arp.ArpPostIngressOneSwitchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.arp.ArpPostIngressVxlanFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.arp.ArpTransitFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpIngressFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpInputPreDropFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpPostIngressFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpPostIngressOneSwitchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpPostIngressVxlanFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpTransitFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42FlowRttInputFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42FlowRttOutputVlanFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42FlowRttOutputVxlanFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42FlowRttTurningFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42FlowRttVxlanTurningFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42IslRttInputFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42IslRttOutputFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42IslRttTurningFlowGenerator;
import org.openkilda.model.MacAddress;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class SwitchFlowFactory implements IService {
    private SwitchManagerConfig config;
    private FeatureDetectorService featureDetectorService;
    private KildaCore kildaCore;
    private String verificationBcastPacketDst;

    @Override
    public void setup(FloodlightModuleContext context) {
        ISwitchManager switchManager = context.getServiceImpl(ISwitchManager.class);
        config = switchManager.getSwitchManagerConfig();
        featureDetectorService = context.getServiceImpl(FeatureDetectorService.class);
        kildaCore = context.getServiceImpl(KildaCore.class);

        verificationBcastPacketDst =
                context.getServiceImpl(IPathVerificationService.class).getConfig().getVerificationBcastPacketDst();
    }

    /**
     * Get default drop switch flow generator.
     */
    public DropFlowGenerator getDropFlowGenerator(long cookie, int tableId) {
        return DropFlowGenerator.builder()
                .cookie(cookie)
                .tableId(tableId)
                .build();
    }

    /**
     * Get verification switch flow generator.
     */
    public VerificationFlowGenerator getVerificationFlow(boolean broadcast) {
        return VerificationFlowGenerator.builder()
                .broadcast(broadcast)
                .config(config)
                .featureDetectorService(featureDetectorService)
                .kildaCore(kildaCore)
                .verificationBcastPacketDst(verificationBcastPacketDst)
                .build();
    }

    /**
     * Get drop loop switch flow generator.
     */
    public SwitchFlowGenerator getDropLoopFlowGenerator() {
        return DropLoopFlowGenerator.builder()
                .verificationBcastPacketDst(verificationBcastPacketDst)
                .build();
    }

    /**
     * Get BFD catch switch flow generator.
     */
    public BfdCatchFlowGenerator getBfdCatchFlowGenerator() {
        return BfdCatchFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get round trip latency switch flow generator.
     */
    public RoundTripLatencyFlowGenerator getRoundTripLatencyFlowGenerator() {
        return RoundTripLatencyFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .verificationBcastPacketDst(verificationBcastPacketDst)
                .build();
    }

    /**
     * Get unicast verification VXLAN switch flow generator.
     */
    public UnicastVerificationVxlanRuleGenerator getUnicastVerificationVxlanFlowGenerator() {
        return UnicastVerificationVxlanRuleGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .kildaCore(kildaCore)
                .build();
    }

    /**
     * Get table pass through default switch flow generator.
     */
    public TablePassThroughDefaultFlowGenerator getTablePassThroughDefaultFlowGenerator(
            long cookie, int goToTableId, int tableId) {
        return TablePassThroughDefaultFlowGenerator.builder()
                .cookie(cookie)
                .goToTableId(goToTableId)
                .tableId(tableId)
                .build();
    }

    /**
     * Get LLDP input pre drop switch flow generator.
     */
    public LldpInputPreDropFlowGenerator getLldpInputPreDropFlowGenerator() {
        return LldpInputPreDropFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP ingress switch flow generator.
     */
    public LldpIngressFlowGenerator getLldpIngressFlowGenerator() {
        return LldpIngressFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP post ingress switch flow generator.
     */
    public LldpPostIngressFlowGenerator getLldpPostIngressFlowGenerator() {
        return LldpPostIngressFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP post ingress VXLAN switch flow generator.
     */
    public LldpPostIngressVxlanFlowGenerator getLldpPostIngressVxlanFlowGenerator() {
        return LldpPostIngressVxlanFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP one switch post ingress switch flow generator.
     */
    public LldpPostIngressOneSwitchFlowGenerator getLldpPostIngressOneSwitchFlowGenerator() {
        return LldpPostIngressOneSwitchFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP transit switch flow generator.
     */
    public LldpTransitFlowGenerator getLldpTransitFlowGenerator() {
        return LldpTransitFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get ARP input pre drop switch flow generator.
     */
    public ArpInputPreDropFlowGenerator getArpInputPreDropFlowGenerator() {
        return ArpInputPreDropFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get ARP ingress switch flow generator.
     */
    public ArpIngressFlowGenerator getArpIngressFlowGenerator() {
        return ArpIngressFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get ARP post ingress switch flow generator.
     */
    public ArpPostIngressFlowGenerator getArpPostIngressFlowGenerator() {
        return ArpPostIngressFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get ARP post ingress VXLAN switch flow generator.
     */
    public ArpPostIngressVxlanFlowGenerator getArpPostIngressVxlanFlowGenerator() {
        return ArpPostIngressVxlanFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get ARP one switch post ingress switch flow generator.
     */
    public ArpPostIngressOneSwitchFlowGenerator getArpPostIngressOneSwitchFlowGenerator() {
        return ArpPostIngressOneSwitchFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get ARP transit switch flow generator.
     */
    public ArpTransitFlowGenerator getArpTransitFlowGenerator() {
        return ArpTransitFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get Server 42 Flow RTT input flow generator.
     */
    public Server42FlowRttInputFlowGenerator getServer42FlowRttInputFlowGenerator(int server42Port, int customerPort,
                                                                                  MacAddress server42MacAddress) {
        return Server42FlowRttInputFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .kildaCore(kildaCore)
                .server42Port(server42Port)
                .customerPort(customerPort)
                .server42macAddress(server42MacAddress)
                .build();
    }

    /**
     * Get Server 42 Flow RTT turning flow generator.
     */
    public SwitchFlowGenerator getServer42FlowRttTurningFlowGenerator() {
        return Server42FlowRttTurningFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get Server 42 Flow RTT Vxlan turning flow generator.
     */
    public SwitchFlowGenerator getServer42FlowRttVxlanTurningFlowGenerator() {
        return Server42FlowRttVxlanTurningFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get Server 42 Flow RTT output vlan flow generator.
     */
    public Server42FlowRttOutputVlanFlowGenerator getServer42FlowRttOutputVlanFlowGenerator(
            int server42Port, int server42Vlan, MacAddress server42MacAddress) {
        return Server42FlowRttOutputVlanFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .server42Port(server42Port)
                .server42Vlan(server42Vlan)
                .server42MacAddress(server42MacAddress)
                .build();
    }

    /**
     * Get Server 42 Flow RTT output VXLAN flow generator.
     */
    public Server42FlowRttOutputVxlanFlowGenerator getServer42FlowRttOutputVxlanFlowGenerator(
            int server42Port, int server42Vlan, MacAddress server42MacAddress) {
        return Server42FlowRttOutputVxlanFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .server42Port(server42Port)
                .server42Vlan(server42Vlan)
                .server42MacAddress(server42MacAddress)
                .build();
    }

    /**
     * Get Server 42 ISL RTT input flow generator.
     */
    public Server42IslRttInputFlowGenerator getServer42IslRttInputFlowGenerator(int server42Port, int islPort) {
        return Server42IslRttInputFlowGenerator.builder()
                .kildaCore(kildaCore)
                .server42Port(server42Port)
                .islPort(islPort)
                .build();
    }

    /**
     * Get Server 42 ISL RTT turning flow generator.
     */
    public Server42IslRttTurningFlowGenerator getServer42IslRttTurningFlowGenerator() {
        return Server42IslRttTurningFlowGenerator.builder()
                .kildaCore(kildaCore)
                .build();
    }

    /**
     * Get Server 42 ISL RTT output flow generator.
     */
    public Server42IslRttOutputFlowGenerator getServer42IslRttOutputFlowGenerator(int server42Port, int server42Vlan,
                                                                                  MacAddress server42MacAddress) {
        return Server42IslRttOutputFlowGenerator.builder()
                .kildaCore(kildaCore)
                .server42Port(server42Port)
                .server42Vlan(server42Vlan)
                .server42MacAddress(server42MacAddress)
                .build();
    }
}
