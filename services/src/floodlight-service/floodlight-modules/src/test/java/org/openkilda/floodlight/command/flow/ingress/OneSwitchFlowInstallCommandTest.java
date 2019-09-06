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
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OneSwitchFlowInstallCommandTest extends IngressFlowSegmentInstallTest {
    private static final FlowEndpoint endpointEgressDefaultPort = new FlowEndpoint(
            endpointIngresDefaultPort.getDatapath(),
            IngressFlowSegmentInstallTest.endpointEgressDefaultPort.getPortNumber());
    private static final FlowEndpoint endpointEgressSingleVlan = new FlowEndpoint(
            endpointIngressSingleVlan.getDatapath(),
            IngressFlowSegmentInstallTest.endpointEgressSingleVlan.getPortNumber(),
            IngressFlowSegmentInstallTest.endpointEgressSingleVlan.getOuterVlanId());
    private static final FlowEndpoint endpointEgressDoubleVlan = new FlowEndpoint(
            endpointIngressDoubleVlan.getDatapath(),
            IngressFlowSegmentInstallTest.endpointEgressDoubleVlan.getPortNumber(),
            IngressFlowSegmentInstallTest.endpointEgressDoubleVlan.getOuterVlanId(),
            IngressFlowSegmentInstallTest.endpointEgressDoubleVlan.getInnerVlanId());

    @Test
    public void happyPathZeroVlan() throws Exception {
        OneSwitchFlowInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngresDefaultPort)
                .egressEndpoint(endpointEgressDefaultPort)
                .build();
        executeCommand(command, 1);

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        OFFlowAdd expected = of.buildFlowAdd()
                .setPriority(OneSwitchFlowInstallCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                         .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                         .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathSingleVlan() throws Exception {
        OneSwitchFlowInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressSingleVlan)
                .egressEndpoint(endpointEgressDefaultPort)
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);

        verifyOfMessageEquals(makeOuterVlanMatch(command), getWriteRecord(0).getRequest());

        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        OFFlowAdd expected = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(OneSwitchFlowInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .setMasked(MatchField.METADATA,
                                   OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathDoubleVlan() throws Exception {
        OneSwitchFlowInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressDoubleVlan)
                .egressEndpoint(endpointEgressSingleVlan)
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);

        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEgressEndpoint().getOuterVlanId()));
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        expect = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(OneSwitchFlowInstallCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getInnerVlanId())
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .setMasked(MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathSingleVlanSameOutput() throws Exception {
        OneSwitchFlowInstallCommand command = getCommandBuilder()
                .endpoint(endpointIngressSingleVlan)
                .egressEndpoint(new FlowEndpoint(
                        endpointEgressSingleVlan.getDatapath(), endpointIngressSingleVlan.getPortNumber(),
                        endpointIngressSingleVlan.getOuterVlanId() + 1))
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);

        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEgressEndpoint().getOuterVlanId()));
        applyActions.add(of.actions().buildOutput().setPort(OFPort.IN_PORT).build());
        instructions.add(of.instructions().applyActions(applyActions));

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        OFFlowAdd expected = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(OneSwitchFlowInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .setMasked(MatchField.METADATA,
                                OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                        .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());
    }

    @Test
    public void happyPathMultiTable() throws Exception {
        OneSwitchFlowInstallCommand command = getCommandBuilder()
                .updateMultiTableFlag(true)
                .build();
        executeCommand(command, 2);

        // table - dispatch
        OFFlowAdd expect = makeOuterVlanMatch(command);
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());

        // table - ingress
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();
        OfAdapter.INSTANCE.makeMeterCall(of, command.getMeterConfig().getId(), applyActions, instructions);
        applyActions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        applyActions.add(OfAdapter.INSTANCE.setVlanIdAction(of, command.getEgressEndpoint().getOuterVlanId()));
        applyActions.add(of.actions().buildOutput().setPort(
                OFPort.of(command.getEgressEndpoint().getPortNumber())).build());
        instructions.add(of.instructions().applyActions(applyActions));

        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));

        OFFlowAdd expected = of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setPriority(OneSwitchFlowInstallCommand.FLOW_PRIORITY - 10)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build())
                .setInstructions(instructions)
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(1).getRequest());
    }

    @Override
    protected void expectMeter() {
        expectMeterInstall();
    }

    @Override
    protected CommandBuilder getCommandBuilder() {
        return new CommandBuilder();
    }

    static class CommandBuilder implements ICommandBuilder {
        private final UUID commandId = UUID.randomUUID();
        private FlowSegmentMetadata metadata = new FlowSegmentMetadata(
                "single-switch-flow-install-flow-id", new Cookie(1), false);

        private MeterConfig meterConfig = OneSwitchFlowInstallCommandTest.meterConfig;
        private FlowEndpoint endpoint = OneSwitchFlowInstallCommandTest.endpointIngressSingleVlan;
        private FlowEndpoint egressEndpoint = OneSwitchFlowInstallCommandTest.endpointEgressSingleVlan;

        @Override
        public OneSwitchFlowInstallCommand build() {
            return new OneSwitchFlowInstallCommand(
                    new MessageContext(), commandId, metadata, endpoint, meterConfig, egressEndpoint);
        }

        public CommandBuilder updateMultiTableFlag(boolean isMultiTable) {
            this.metadata = metadata.toBuilder().multiTable(isMultiTable).build();
            return this;
        }

        @Override
        public CommandBuilder endpoint(FlowEndpoint endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public CommandBuilder egressEndpoint(FlowEndpoint endpoint) {
            this.egressEndpoint = endpoint;
            return this;
        }

        @Override
        public CommandBuilder meterConfig(MeterConfig meterConfig) {
            this.meterConfig = meterConfig;
            return this;
        }
    }
}
