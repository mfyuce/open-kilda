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

package org.openkilda.wfm.topology.flowhs.service;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.CreateFlowLoopRequest;
import org.openkilda.messaging.command.flow.DeleteFlowLoopRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class FlowUpdateService extends FlowProcessingWithEventSupportService<FlowUpdateFsm, Event, FlowUpdateContext,
        FlowUpdateHubCarrier, FlowUpdateEventListener> {
    private final FlowUpdateFsm.Factory fsmFactory;
    private final KildaConfigurationRepository kildaConfigurationRepository;

    public FlowUpdateService(FlowUpdateHubCarrier carrier, PersistenceManager persistenceManager,
                             PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                             int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                             int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();

        Config fsmConfig = Config.builder()
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .resourceAllocationRetriesLimit(resourceAllocationRetriesLimit)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new FlowUpdateFsm.Factory(carrier, fsmConfig, persistenceManager, pathComputer,
                flowResourcesManager);
    }

    /**
     * Handles request for flow update.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleUpdateRequest(String key, CommandContext commandContext, FlowRequest request)
            throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        RequestedFlow requestedFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request);
        startFlowUpdating(key, commandContext, requestedFlow,
                request.isDoNotRevert(), request.getBulkUpdateFlowIds(), true);
    }

    /**
     * Start flow updating without reverting for the provided information.
     */
    public void startFlowUpdating(CommandContext commandContext, RequestedFlow request) {
        try {
            startFlowUpdating(request.getFlowId(), commandContext, request, true, Collections.emptySet(), false);
        } catch (DuplicateKeyException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Failed to initiate flow updating for %s / %s: %s", request.getFlowId(), e.getKey(),
                            e.getMessage()));
        }
    }

    private void startFlowUpdating(String key, CommandContext commandContext, RequestedFlow request,
                                   boolean doNotRevert, Set<String> bulkUpdateFlowIds, boolean allowNorthboundResponse)
            throws DuplicateKeyException {
        String flowId = request.getFlowId();
        log.debug("Handling flow update request with key {} and flow ID: {}", key, request.getFlowId());

        if (hasRegisteredFsmWithKeyOrFlowId(key, flowId)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        FlowUpdateFsm fsm = fsmFactory.newInstance(request.getFlowId(), commandContext, allowNorthboundResponse,
                eventListeners);
        registerFsm(key, fsm);

        if (request.getFlowEncapsulationType() == null) {
            request.setFlowEncapsulationType(kildaConfigurationRepository.getOrDefault()
                    .getFlowEncapsulationType());
        }
        if (request.getPathComputationStrategy() == null) {
            request.setPathComputationStrategy(
                    kildaConfigurationRepository.getOrDefault().getPathComputationStrategy());
        }
        FlowUpdateContext context = FlowUpdateContext.builder()
                .targetFlow(request)
                .doNotRevert(doNotRevert)
                .bulkUpdateFlowIds(bulkUpdateFlowIds)
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) throws UnknownKeyException {
        log.debug("Received flow command response {}", flowResponse);
        FlowUpdateFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            throw new UnknownKeyException(key);
        }

        FlowUpdateContext context = FlowUpdateContext.builder()
                .speakerFlowResponse(flowResponse)
                .build();

        if (flowResponse instanceof FlowErrorResponse) {
            fsmExecutor.fire(fsm, Event.ERROR_RECEIVED, context);
        } else {
            fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleAsyncResponseByFlowId(String flowId, SpeakerFlowSegmentResponse flowResponse)
            throws UnknownKeyException {
        String commandKey = getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleAsyncResponse(commandKey, flowResponse);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        FlowUpdateFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            throw new UnknownKeyException(key);
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleTimeoutByFlowId(String flowId) throws UnknownKeyException {
        String commandKey = getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleTimeout(commandKey);
    }

    /**
     * Handles create flow loop request.
     *
     * @param request request to handle.
     */
    public void handleCreateFlowLoopRequest(String key, CommandContext commandContext,
                                            CreateFlowLoopRequest request) throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        if (flow.isPresent()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(flow.get());
            if (flowRequest.getLoopSwitchId() == null || flowRequest.getLoopSwitchId().equals(request.getSwitchId())) {
                flowRequest.setLoopSwitchId(request.getSwitchId());
                handleUpdateRequest(key, commandContext, flowRequest);
            } else {
                carrier.sendNorthboundResponse(buildFlowAlreadyLoopedErrorMessage(flowRequest, commandContext));
            }
        } else {
            carrier.sendNorthboundResponse(buildFlowNotFoundErrorMessage(request.getFlowId(), commandContext));
        }
    }

    /**
     * Handles delete flow loop request.
     *
     * @param request request to handle.
     */
    public void handleDeleteFlowLoopRequest(String key, CommandContext commandContext,
                                            DeleteFlowLoopRequest request) throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        if (flow.isPresent()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(flow.get());
            flowRequest.setLoopSwitchId(null);
            handleUpdateRequest(key, commandContext, flowRequest);
        } else {
            carrier.sendNorthboundResponse(buildFlowNotFoundErrorMessage(request.getFlowId(), commandContext));
        }
    }

    private Message buildFlowNotFoundErrorMessage(String flowId, CommandContext commandContext) {
        String description = String.format("Flow '%s' not found.", flowId);
        ErrorData error = new ErrorData(ErrorType.NOT_FOUND, "Flow not found", description);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    private Message buildFlowAlreadyLoopedErrorMessage(FlowRequest flow, CommandContext commandContext) {
        String description = String.format("Flow is already looped on switch '%s'", flow.getLoopSwitchId());
        ErrorData error = new ErrorData(ErrorType.UNPROCESSABLE_REQUEST,
                String.format("Can't create flow loop on '%s'", flow.getFlowId()), description);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    private void removeIfFinished(FlowUpdateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);

            processFsmListeners(fsm);

            if (!isActive() && !hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }

    private void processFsmListeners(FlowUpdateFsm fsm) {
        if (fsm.getEventListeners() != null && !fsm.getEventListeners().isEmpty()) {
            switch (fsm.getCurrentState()) {
                case FINISHED:
                    fsm.getEventListeners().forEach(listener -> listener.onCompleted(fsm.getFlowId()));
                    break;
                case FINISHED_WITH_ERROR:
                    ErrorType errorType = Optional.ofNullable(fsm.getOperationResultMessage())
                            .filter(message -> message instanceof ErrorMessage)
                            .map(message -> ((ErrorMessage) message).getData())
                            .map(ErrorData::getErrorType).orElse(ErrorType.INTERNAL_ERROR);
                    fsm.getEventListeners().forEach(listener -> listener.onFailed(fsm.getFlowId(),
                            fsm.getErrorReason(), errorType));
                    break;
                default:
                    // ignore
            }
        }
    }
}
