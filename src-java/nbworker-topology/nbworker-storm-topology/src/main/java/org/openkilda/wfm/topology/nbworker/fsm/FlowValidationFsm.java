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

package org.openkilda.wfm.topology.nbworker.fsm;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent.ERROR;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent.GROUPS_RECEIVED;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent.METERS_RECEIVED;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent.NEXT;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState.FINISHED;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState.INITIALIZED;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState.RECEIVE_DATA;
import static org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState.VALIDATE_FLOW;

import org.openkilda.messaging.command.switches.DumpGroupsForNbWorkerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForNbworkerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForNbworkerRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.messaging.nbtopology.request.FlowValidationRequest;
import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.topology.nbworker.bolts.FlowValidationHubCarrier;
import org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent;
import org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState;
import org.openkilda.wfm.topology.nbworker.services.FlowValidationService;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FlowValidationFsm
        extends AbstractStateMachine<FlowValidationFsm, FlowValidationState, FlowValidationEvent, Object> {
    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final int TERMINATION_SWITCHES_COUNT = 2;

    private final String key;
    private final FlowValidationRequest request;
    private final FlowValidationHubCarrier carrier;
    private final PersistenceManager persistenceManager;
    private final FlowResourcesConfig flowResourcesConfig;
    private String flowId;
    private FlowValidationService service;
    private int awaitingRules;
    private int awaitingMeters;
    private int awaitingGroups;
    private final List<SwitchFlowEntries> receivedRules = new ArrayList<>();
    private final List<SwitchMeterEntries> receivedMeters = new ArrayList<>();
    private final List<SwitchGroupEntries> receivedGroups = new ArrayList<>();
    private List<FlowValidationResponse> response;

    public FlowValidationFsm(FlowValidationHubCarrier carrier, String key, FlowValidationRequest request,
                             PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.persistenceManager = persistenceManager;
        this.flowResourcesConfig = flowResourcesConfig;
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<FlowValidationFsm, FlowValidationState, FlowValidationEvent, Object> builder() {
        StateMachineBuilder<FlowValidationFsm, FlowValidationState, FlowValidationEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        FlowValidationFsm.class,
                        FlowValidationState.class,
                        FlowValidationEvent.class,
                        Object.class,
                        FlowValidationHubCarrier.class,
                        String.class,
                        FlowValidationRequest.class,
                        PersistenceManager.class,
                        FlowResourcesConfig.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(RECEIVE_DATA).on(NEXT)
                .callMethod("receiveData");
        builder.internalTransition().within(RECEIVE_DATA).on(RULES_RECEIVED).callMethod("receivedRules");
        builder.internalTransition().within(RECEIVE_DATA).on(METERS_RECEIVED).callMethod("receivedMeters");
        builder.internalTransition().within(RECEIVE_DATA).on(GROUPS_RECEIVED).callMethod("receivedGroups");

        builder.externalTransition().from(RECEIVE_DATA).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(RECEIVE_DATA).to(VALIDATE_FLOW).on(NEXT)
                .callMethod("validateFlow");

        builder.externalTransition().from(VALIDATE_FLOW).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_FLOW).to(FINISHED).on(NEXT).callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(FlowValidationState from, FlowValidationState to,
                               FlowValidationEvent event, Object context) {
        flowId = request.getFlowId();
        log.info("Key: {}, flow: {}; FSM initialized", key, flowId);

        service = new FlowValidationService(persistenceManager, flowResourcesConfig,
                carrier.getFlowMeterMinBurstSizeInKbits(), carrier.getFlowMeterBurstCoefficient());
    }

    protected void receiveData(FlowValidationState from, FlowValidationState to,
                               FlowValidationEvent event, Object context) {
        Flow flow;
        try {
            flow = service.checkFlowStatusAndGetFlow(flowId);
        } catch (FlowNotFoundException e) {
            log.error("Key: {}; Flow {} not found when sending commands to SpeakerWorkerBolt", key, flowId, e);
            sendException(e.getMessage(), "Receiving rules operation in FlowValidationFsm", ErrorType.NOT_FOUND);
            return;
        } catch (IllegalFlowStateException e) {
            log.error("Key: {}; Could not validate flow: Flow {} is in DOWN state", key, flowId, e);
            sendException("Could not validate flow",
                    format("Could not validate flow: Flow %s is in DOWN state", flowId),
                    ErrorType.UNPROCESSABLE_REQUEST);
            return;
        }

        List<SwitchId> switchIds = service.getSwitchIdListByFlowId(flowId);

        awaitingRules = switchIds.size();
        log.debug("Key: {}; Send commands to get rules on the switches", key);
        switchIds.forEach(switchId ->
                carrier.sendCommandToSpeakerWorker(key, new DumpRulesForNbworkerRequest(switchId)));

        log.debug("Key: {}; Send commands to get meters on the termination switches", key);
        awaitingMeters = TERMINATION_SWITCHES_COUNT;
        carrier.sendCommandToSpeakerWorker(key, new DumpMetersForNbworkerRequest(flow.getSrcSwitchId()));
        carrier.sendCommandToSpeakerWorker(key, new DumpMetersForNbworkerRequest(flow.getDestSwitchId()));

        log.debug("Key: {}; Send commands to get groups on the termination switches", key);
        awaitingGroups = TERMINATION_SWITCHES_COUNT;
        carrier.sendCommandToSpeakerWorker(key, new DumpGroupsForNbWorkerRequest(flow.getSrcSwitchId()));
        carrier.sendCommandToSpeakerWorker(key, new DumpGroupsForNbWorkerRequest(flow.getDestSwitchId()));
    }

    protected void receivedRules(FlowValidationState from, FlowValidationState to,
                                 FlowValidationEvent event, Object context) {
        SwitchFlowEntries switchFlowEntries = (SwitchFlowEntries) context;
        log.info("Key: {}; Switch rules received for switch {}", key, switchFlowEntries.getSwitchId());
        receivedRules.add(switchFlowEntries);
        awaitingRules--;
        checkOfCompleteDataCollection();
    }

    protected void receivedMeters(FlowValidationState from, FlowValidationState to,
                                  FlowValidationEvent event, Object context) {
        SwitchMeterEntries switchMeterEntries = (SwitchMeterEntries) context;
        log.info("Key: {}; Switch meters received for switch {}", key, switchMeterEntries.getSwitchId());
        receivedMeters.add(switchMeterEntries);
        awaitingMeters--;
        checkOfCompleteDataCollection();
    }

    protected void receivedGroups(FlowValidationState from, FlowValidationState to,
                                  FlowValidationEvent event, Object context) {
        SwitchGroupEntries switchGroupEntries = (SwitchGroupEntries) context;
        log.info("Key: {}; Switch meters received for switch {}", key, switchGroupEntries.getSwitchId());
        receivedGroups.add(switchGroupEntries);
        awaitingGroups--;
        checkOfCompleteDataCollection();
    }

    private void checkOfCompleteDataCollection() {
        if (awaitingRules == 0 && awaitingMeters == 0 && awaitingGroups == 0) {
            fire(NEXT);
        }
    }

    protected void validateFlow(FlowValidationState from, FlowValidationState to,
                                FlowValidationEvent event, Object context) {
        try {
            response = service.validateFlow(flowId, receivedRules, receivedMeters, receivedGroups);
        } catch (FlowNotFoundException e) {
            log.error("Key: {}; Flow {} not found during flow validation", key, flowId, e);
            sendException(e.getMessage(), "Flow validation operation in FlowValidationFsm", ErrorType.NOT_FOUND);
        } catch (SwitchNotFoundException e) {
            log.error("Key: {}; {}", key, e.getMessage(), e);
            sendException(e.getMessage(), "Flow validation operation in FlowValidationFsm", ErrorType.NOT_FOUND);
        } catch (Exception e) {
            log.error("Key: {}; {}", key, e.getMessage(), e);
            sendException(e.getMessage(), "Flow validation operation in FlowValidationFsm", ErrorType.INTERNAL_ERROR);
        }
    }

    protected void finished(FlowValidationState from, FlowValidationState to,
                            FlowValidationEvent event, Object context) {
        log.info("Key: {}; FSM finished work", key);
        carrier.endProcessing(key);
        carrier.sendToResponseSplitterBolt(key, response);
    }

    protected void finishedWithError(FlowValidationState from, FlowValidationState to,
                                     FlowValidationEvent event, Object context) {
        ErrorMessage message = (ErrorMessage) context;
        ErrorData data = message.getData();
        log.error("Key: {}; Message: {}", key, data.getErrorMessage());
        carrier.endProcessing(key);
        carrier.sendToMessageEncoder(key, new ErrorData(data.getErrorType(),
                format("Could not validate flow: %s", data.getErrorMessage()), data.getErrorDescription()));
    }

    private void sendException(String message, String description, ErrorType errorType) {
        ErrorData errorData = new ErrorData(errorType, message, description);
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    public enum FlowValidationState {
        INITIALIZED,
        RECEIVE_DATA,
        VALIDATE_FLOW,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum FlowValidationEvent {
        NEXT,
        RULES_RECEIVED,
        METERS_RECEIVED,
        GROUPS_RECEIVED,
        ERROR
    }
}
