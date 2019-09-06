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

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.command.flow.FlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterInstallDryRunCommand;
import org.openkilda.floodlight.command.meter.MeterInstallReport;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterRemoveReport;
import org.openkilda.floodlight.command.meter.MeterVerifyCommand;
import org.openkilda.floodlight.command.meter.MeterVerifyReport;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.MetadataAdapter.MetadataMatch;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Getter
abstract class IngressFlowSegmentCommand extends FlowSegmentCommand {
    // payload
    protected final FlowEndpoint endpoint;
    protected final MeterConfig meterConfig;
    protected final SwitchId egressSwitchId;

    // operation data
    @Getter(AccessLevel.PROTECTED)
    private Set<SwitchFeature> switchFeatures;

    IngressFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, MeterConfig meterConfig, @NonNull SwitchId egressSwitchId) {
        super(messageContext, switchId, commandId, metadata);
        this.endpoint = endpoint;
        this.meterConfig = meterConfig;
        this.egressSwitchId = egressSwitchId;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws Exception {
        super.setup(moduleContext);

        FeatureDetectorService featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
        switchFeatures = featureDetectorService.detectSwitch(getSw());

        ensureSwitchEnoughCapabilities();
    }

    @Override
    protected void validate() {
        super.validate();

        if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId()) && ! metadata.isMultiTable()) {
            throw new IllegalArgumentException("QinQ ingress flow segment can be installed only in multi table mode");
        }
    }

    protected CompletableFuture<FlowSegmentReport> makeInstallPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> future = CompletableFuture.completedFuture(null);
        if (meterConfig != null) {
            future = planMeterInstall(commandProcessor)
                    .thenApply(this::handleMeterReport);
        }
        return future.thenCompose(this::planOfFlowsInstall);
    }

    protected CompletableFuture<FlowSegmentReport> makeRemovePlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<Void> future = planOfFlowsRemove();
        if (meterConfig != null) {
            future = future.thenCompose(ignore -> planMeterRemove(commandProcessor));
        }
        return future.thenApply(ignore -> makeSuccessReport());
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> future = CompletableFuture.completedFuture(null);
        if (meterConfig != null) {
            future = planMeterVerify(commandProcessor)
                    .thenApply(this::handleMeterReport);
        }
        return future.thenCompose(this::planOfFlowsVerify);
    }

    protected CompletableFuture<FlowSegmentReport> makeSchemaPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterInstallReport> future = CompletableFuture.completedFuture(null);
        if (meterConfig != null) {
            future = planMeterDryRun(commandProcessor)
                    .thenApply(report -> {
                        ensureMeterSuccess(report);
                        return report;
                    });
        }
        return future.thenCompose(this::planOfFlowsSchema);
    }

    private CompletableFuture<MeterInstallReport> planMeterInstall(SpeakerCommandProcessor commandProcessor) {
        MeterInstallCommand meterCommand = new MeterInstallCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterCommand);
    }

    private CompletableFuture<Void> planMeterRemove(SpeakerCommandProcessor commandProcessor) {
        MeterRemoveCommand removeCommand = new MeterRemoveCommand(messageContext, switchId, meterConfig.getId());
        return commandProcessor.chain(removeCommand)
                .thenAccept(this::handleMeterRemoveReport);
    }

    private CompletableFuture<MeterVerifyReport> planMeterVerify(SpeakerCommandProcessor commandProcessor) {
        MeterVerifyCommand meterVerify = new MeterVerifyCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterVerify);
    }

    private CompletableFuture<MeterInstallReport> planMeterDryRun(SpeakerCommandProcessor commandProcessor) {
        MeterInstallDryRunCommand meterDryRun = new MeterInstallDryRunCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterDryRun);
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsInstall(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = makeIngressModMessages(effectiveMeterId);
        List<CompletableFuture<Optional<OFMessage>>> writeResults = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                writeResults.add(session.write(message));
            }
        }
        return CompletableFuture.allOf(writeResults.toArray(new CompletableFuture[0]))
                .thenApply(ignore -> makeSuccessReport());
    }

    private CompletableFuture<Void> planOfFlowsRemove() {
        MeterId meterId = null;
        if (meterConfig != null) {
            meterId = meterConfig.getId();
        }
        List<OFFlowMod> ofMessages = new ArrayList<>(makeIngressModMessages(meterId));

        // TODO(surabujin): drop after migration
        // to make smooth migration between different ingress rules format remove old (pre QinQ) rule by cookie match
        OFFactory of = getSw().getOFFactory();
        ofMessages.add(setFlowModTableId(of.buildFlowDelete(), SwitchManager.INGRESS_TABLE_ID)
                               .setCookie(U64.of(getCookie().getValue()))
                               .build());

        List<CompletableFuture<?>> requests = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                requests.add(session.write(message));
            }
        }

        return CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]));
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsVerify(MeterId effectiveMeterId) {
        return makeVerifyPlan(makeIngressModMessages(effectiveMeterId));
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsSchema(MeterInstallReport meterReport) {
        Optional<MeterSchema> meterSchema = Optional.ofNullable(meterReport)
                .flatMap(MeterInstallReport::getSchema);
        MeterId effectiveMeterId = meterSchema
                .map(MeterSchema::getMeterId)
                .orElse(null);
        return makeSchemaPlan(meterSchema.orElse(null), makeIngressModMessages(effectiveMeterId));
    }

    private MeterId handleMeterReport(MeterInstallReport report) {
        ensureMeterSuccess(report);
        return report.getMeterId()
                .orElse(null);
    }

    private MeterId handleMeterReport(MeterVerifyReport report) {
        ensureMeterSuccess(report);
        return report.getSchema()
                .map(MeterSchema::getMeterId)
                .orElse(null);
    }

    private void handleMeterRemoveReport(MeterRemoveReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not remove meter id {} from {} - {}", meterConfig.getId(), switchId, e.getMessage());
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    protected List<OFFlowMod> makeIngressModMessages(MeterId effectiveMeterId) {
        // TODO(surabujin): drop after migration
        if (metadata.isMultiTable()) {
            return makeIngressMultiTableModMessages(effectiveMeterId);
        } else {
            return makeIngressSingleTableModMessages(effectiveMeterId);
        }
    }

    protected List<OFFlowMod> makeIngressMultiTableModMessages(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = new ArrayList<>(2);
        OFFactory of = getSw().getOFFactory();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            ofMessages.add(makeOuterVlanMatchMessage(of));
            if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
                ofMessages.add(makeInnerVlanMatchAndForwardMessage(of, effectiveMeterId));
            } else {
                ofMessages.add(makeOuterVlanForwardMessage(of, effectiveMeterId));
            }
        } else {
            ofMessages.add(makeDefaultPortMatchAndForwardMessage(of, effectiveMeterId));
        }

        return ofMessages;
    }

    protected List<OFFlowMod> makeIngressSingleTableModMessages(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        OFFactory of = getSw().getOFFactory();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            ofMessages.add(makeOuterVlanMatchAndForwardMessage(of, effectiveMeterId));
        } else {
            ofMessages.add(makeDefaultPortMatchAndForwardMessage(of, effectiveMeterId));
        }

        return ofMessages;
    }

    private OFFlowMod makeOuterVlanMatchMessage(OFFactory of) {
        return makeFlowModBuilder(of)
                .setTableId(TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()))
                                  .build())
                .setInstructions(makeOuterVlanMatchMessageInstructions(of))
                .build();
    }

    protected List<OFInstruction> makeOuterVlanMatchMessageInstructions(OFFactory of) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        return ImmutableList.of(
                                of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                                of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()),
                                of.instructions().gotoTable(TableId.of(SwitchManager.INGRESS_TABLE_ID)));
    }

    private OFFlowMod makeOuterVlanForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        OFFlowMod.Builder builder = setFlowModTableId(makeFlowModBuilder(of), SwitchManager.INGRESS_TABLE_ID)
                .setPriority(FLOW_PRIORITY - 10)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeOuterVlanMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        OFFlowMod.Builder builder = setFlowModTableId(makeFlowModBuilder(of), SwitchManager.INGRESS_TABLE_ID)
                .setPriority(FLOW_PRIORITY)
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), getEndpoint().getOuterVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeInnerVlanMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        OFFlowMod.Builder builder = makeFlowModBuilder(of)
                .setTableId(TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getInnerVlanId()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeDefaultPortMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        OFFlowMod.Builder builder = setFlowModTableId(makeFlowModBuilder(of), SwitchManager.INGRESS_TABLE_ID)
                // FIXME we need some space between match rules (so it should be -10 instead of -1)
                .setPriority(FLOW_PRIORITY - 1)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeForwardMessage(OFFactory of, OFFlowMod.Builder builder, MeterId effectiveMeterId) {
        builder.setInstructions(makeForwardMessageInstructions(of, effectiveMeterId));
        if (getSwitchFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }
        return builder.build();
    }

    protected List<OFInstruction> makeForwardMessageInstructions(OFFactory of, MeterId effectiveMeterId) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();

        if (effectiveMeterId != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, effectiveMeterId, applyActions, instructions);
        }

        applyActions.addAll(makeTransformActions(of));
        applyActions.add(makeOutputAction(of));

        instructions.add(of.instructions().applyActions(applyActions));
        if (endpoint.isTrackConnectedDevices()) {
            instructions.add(of.instructions().gotoTable(TableId.of(SwitchManager.POST_INGRESS_TABLE_ID)));
        }

        return instructions;
    }

    protected abstract List<OFAction> makeTransformActions(OFFactory of);

    protected abstract OFAction makeOutputAction(OFFactory of);

    protected final OFAction makeOutputAction(OFFactory of, OFPort port) {
        return of.actions().buildOutput()
                .setPort(port)
                .build();
    }

    protected Set<SwitchFeature> getRequiredFeatures() {
        return new HashSet<>();
    }

    protected void ensureMeterSuccess(SpeakerCommandReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not install meter id {} on {} - {}", meterConfig.getId(), switchId, e.getMessage());
            // switch do not support meters, setup rules without meter
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    private void ensureSwitchEnoughCapabilities() throws UnsupportedSwitchOperationException {
        Set<SwitchFeature> required = getRequiredFeatures();
        required.removeAll(switchFeatures);
        if (required.isEmpty()) {
            return;
        }

        String requiredAsString = required.stream()
                .map(SwitchFeature::name)
                .sorted()
                .collect(Collectors.joining(", "));
        throw new UnsupportedSwitchOperationException(
                getSw().getId(), String.format("Switch %s do not support %s", switchId, requiredAsString));
    }
}
