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

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest.Type;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import com.google.common.io.BaseEncoding;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class YFlowUpdateService
        extends YFlowProcessingService<YFlowUpdateFsm, Event, YFlowUpdateContext, YFlowUpdateHubCarrier> {
    private final YFlowUpdateFsm.Factory fsmFactory;
    private final NoArgGenerator flowIdGenerator = Generators.timeBasedGenerator();
    private final String prefixForGeneratedYFlowId;
    private final String prefixForGeneratedSubFlowId;
    private final YFlowRepository yFlowRepository;
    private final FlowUpdateService flowUpdateService;

    public YFlowUpdateService(YFlowUpdateHubCarrier carrier, PersistenceManager persistenceManager,
                              PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                              FlowUpdateService flowUpdateService,
                              int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit,
                              String prefixForGeneratedYFlowId, String prefixForGeneratedSubFlowId) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new YFlowUpdateFsm.Factory(carrier, persistenceManager, pathComputer,
                flowResourcesManager, flowUpdateService, resourceAllocationRetriesLimit, speakerCommandRetriesLimit);
        this.prefixForGeneratedYFlowId = prefixForGeneratedYFlowId;
        this.prefixForGeneratedSubFlowId = prefixForGeneratedSubFlowId;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.flowUpdateService = flowUpdateService;
        addFlowUpdateEventListener();
    }

    private void addFlowUpdateEventListener() {
        flowUpdateService.addEventListener(new FlowUpdateEventListener() {
            @Override
            public void onResourcesAllocated(String flowId) {
                YFlowUpdateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowUpdate.ResourcesAllocated event for unknown sub-flow " + flowId));
                YFlowUpdateContext context = YFlowUpdateContext.builder().subFlowId(flowId).build();
                fsmExecutor.fire(fsm, Event.SUB_FLOW_ALLOCATED, context);
            }

            @Override
            public void onCompleted(String flowId) {
                YFlowUpdateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowUpdate.Completed event for unknown sub-flow " + flowId));
                YFlowUpdateContext context = YFlowUpdateContext.builder().subFlowId(flowId).build();
                fsmExecutor.fire(fsm, Event.SUB_FLOW_UPDATED, context);
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowUpdateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowUpdate.Failed event for unknown sub-flow " + flowId));
                YFlowUpdateContext context = YFlowUpdateContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsmExecutor.fire(fsm, Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    /**
     * Handles request for y-flow updating.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(String key, CommandContext commandContext, YFlowRequest request)
            throws DuplicateKeyException {
        String yFlowId = request.getYFlowId();
        log.debug("Handling y-flow update request with key {} and flow ID: {}", key, request.getYFlowId());

        if (hasRegisteredFsmWithKeyOrFlowId(key, yFlowId)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        if (yFlowId == null) {
            yFlowId = generateFlowId(prefixForGeneratedYFlowId);
            request.setYFlowId(yFlowId);
        }
        request.getSubFlows().forEach(subFlow -> {
            if (subFlow.getFlowId() == null) {
                subFlow.setFlowId(generateFlowId(prefixForGeneratedSubFlowId));
            }
        });

        YFlowUpdateFsm fsm = fsmFactory.newInstance(commandContext, yFlowId, eventListeners);
        registerFsm(key, fsm);

        YFlowUpdateContext context = YFlowUpdateContext.builder()
                .targetFlow(request)
                .build();
        fsm.start(context);
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles request for y-flow patch updating.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handlePartialUpdateRequest(String key, CommandContext commandContext, YFlowPartialUpdateRequest request)
            throws DuplicateKeyException {
        YFlowRequest target = null;
        if (request.getYFlowId() != null) {
            YFlow yFlow = yFlowRepository.findById(request.getYFlowId()).orElse(null);
            target = YFlowRequestMapper.INSTANCE.toYFlowRequest(yFlow);
        }

        if (target == null) {
            target = YFlowRequest.builder().build();
        }

        Optional.ofNullable(request.getYFlowId()).ifPresent(target::setYFlowId);
        if (request.getSharedEndpoint() != null) {
            if (target.getSharedEndpoint() == null) {
                target.setSharedEndpoint(new FlowEndpoint(request.getSharedEndpoint().getSwitchId(),
                        request.getSharedEndpoint().getPortNumber()));
            } else {
                SwitchId switchId = Optional.ofNullable(request.getSharedEndpoint().getSwitchId())
                        .orElse(target.getSharedEndpoint().getSwitchId());
                int portNumber = Optional.ofNullable(request.getSharedEndpoint().getPortNumber())
                        .orElse(target.getSharedEndpoint().getPortNumber());
                target.setSharedEndpoint(new FlowEndpoint(switchId, portNumber));
            }
        }
        Optional.ofNullable(request.getMaximumBandwidth()).ifPresent(target::setMaximumBandwidth);
        Optional.ofNullable(request.getPathComputationStrategy()).ifPresent(target::setPathComputationStrategy);
        Optional.ofNullable(request.getEncapsulationType()).ifPresent(target::setEncapsulationType);
        Optional.ofNullable(request.getMaxLatency()).ifPresent(target::setMaxLatency);
        Optional.ofNullable(request.getMaxLatencyTier2()).ifPresent(target::setMaxLatencyTier2);
        Optional.ofNullable(request.getIgnoreBandwidth()).ifPresent(target::setIgnoreBandwidth);
        Optional.ofNullable(request.getPeriodicPings()).ifPresent(target::setPeriodicPings);
        Optional.ofNullable(request.getPinned()).ifPresent(target::setPinned);
        Optional.ofNullable(request.getPriority()).ifPresent(target::setPriority);
        Optional.ofNullable(request.getStrictBandwidth()).ifPresent(target::setStrictBandwidth);
        Optional.ofNullable(request.getDescription()).ifPresent(target::setDescription);
        Optional.ofNullable(request.getAllocateProtectedPath()).ifPresent(target::setAllocateProtectedPath);

        if (request.getSubFlows() != null && !request.getSubFlows().isEmpty()) {
            if (target.getSubFlows() != null) {
                for (SubFlowPartialUpdateDto subFlowPartialUpdate : request.getSubFlows()) {
                    target.getSubFlows().stream()
                            .filter(f -> f.getFlowId().equals(subFlowPartialUpdate.getFlowId()))
                            .findAny()
                            .ifPresent(subFlow -> {
                                if (subFlowPartialUpdate.getEndpoint() != null) {
                                    if (subFlow.getEndpoint() == null) {
                                        subFlow.setEndpoint(new FlowEndpoint(
                                                subFlowPartialUpdate.getEndpoint().getSwitchId(),
                                                subFlowPartialUpdate.getEndpoint().getPortNumber(),
                                                subFlowPartialUpdate.getEndpoint().getVlanId(),
                                                subFlowPartialUpdate.getEndpoint().getInnerVlanId()));
                                    } else {
                                        SwitchId switchId =
                                                Optional.ofNullable(subFlowPartialUpdate.getEndpoint().getSwitchId())
                                                        .orElse(subFlow.getEndpoint().getSwitchId());
                                        int portNumber =
                                                Optional.ofNullable(subFlowPartialUpdate.getEndpoint().getPortNumber())
                                                        .orElse(subFlow.getEndpoint().getPortNumber());
                                        int vlanId =
                                                Optional.ofNullable(subFlowPartialUpdate.getEndpoint().getVlanId())
                                                        .orElse(subFlow.getEndpoint().getOuterVlanId());
                                        int innerVlanId =
                                                Optional.ofNullable(subFlowPartialUpdate.getEndpoint().getInnerVlanId())
                                                        .orElse(subFlow.getEndpoint().getInnerVlanId());

                                        subFlow.setEndpoint(
                                                new FlowEndpoint(switchId, portNumber, vlanId, innerVlanId));
                                    }
                                }

                                if (subFlowPartialUpdate.getSharedEndpoint() != null) {
                                    if (subFlow.getSharedEndpoint() == null) {
                                        subFlow.setSharedEndpoint(new SubFlowSharedEndpointEncapsulation(
                                                subFlowPartialUpdate.getSharedEndpoint().getVlanId(),
                                                subFlowPartialUpdate.getSharedEndpoint().getInnerVlanId()));
                                    } else {
                                        int vlanId = Optional.ofNullable(
                                                subFlowPartialUpdate.getSharedEndpoint().getVlanId())
                                                .orElse(subFlow.getSharedEndpoint().getVlanId());
                                        int innerVlanId =
                                                Optional.ofNullable(
                                                        subFlowPartialUpdate.getSharedEndpoint().getInnerVlanId())
                                                        .orElse(subFlow.getSharedEndpoint().getInnerVlanId());

                                        subFlow.setSharedEndpoint(
                                                new SubFlowSharedEndpointEncapsulation(vlanId, innerVlanId));
                                    }
                                }

                                Optional.ofNullable(subFlowPartialUpdate.getDescription())
                                        .ifPresent(subFlow::setDescription);
                            });
                }
            } else {
                List<SubFlowDto> subFlows = new ArrayList<>();

                for (SubFlowPartialUpdateDto subFlowPatch : request.getSubFlows()) {

                    SubFlowDto.SubFlowDtoBuilder subFlowBuilder = SubFlowDto.builder();
                    if (subFlowPatch.getEndpoint() != null) {
                        int portNumber = Optional.ofNullable(subFlowPatch.getEndpoint().getPortNumber()).orElse(0);
                        int vlanId = Optional.ofNullable(subFlowPatch.getEndpoint().getVlanId()).orElse(0);
                        int innerVlanId = Optional.ofNullable(subFlowPatch.getEndpoint().getInnerVlanId()).orElse(0);
                        subFlowBuilder.endpoint(new FlowEndpoint(subFlowPatch.getEndpoint().getSwitchId(),
                                portNumber, vlanId, innerVlanId));
                    }

                    if (subFlowPatch.getSharedEndpoint() != null) {
                        int vlanId = Optional.ofNullable(subFlowPatch.getSharedEndpoint().getVlanId()).orElse(0);
                        int innerVlanId = Optional.ofNullable(subFlowPatch.getSharedEndpoint().getInnerVlanId())
                                .orElse(0);
                        subFlowBuilder.sharedEndpoint(new SubFlowSharedEndpointEncapsulation(vlanId, innerVlanId));
                    }

                    subFlowBuilder.description(subFlowPatch.getDescription());

                    subFlows.add(subFlowBuilder.build());
                }

                target.setSubFlows(subFlows);
            }
        }

        target.setType(Type.UPDATE);

        handleRequest(key, commandContext, target);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) throws UnknownKeyException {
        log.debug("Received flow command response {}", flowResponse);
        YFlowUpdateFsm fsm = getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        String flowId = flowResponse.getMetadata().getFlowId();
        if (fsm.getUpdatingSubFlows().contains(flowId)) {
            flowUpdateService.handleAsyncResponseByFlowId(flowId, flowResponse);
        } else {
            YFlowUpdateContext context = YFlowUpdateContext.builder()
                    .speakerResponse(flowResponse)
                    .build();
            if (flowResponse instanceof FlowErrorResponse) {
                fsmExecutor.fire(fsm, Event.ERROR_RECEIVED, context);
            } else {
                fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
            }
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        YFlowUpdateFsm fsm = getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        // Propagate timeout event to all sub-flow processing FSMs.
        fsm.getUpdatingSubFlows().forEach(flowId -> {
            try {
                flowUpdateService.handleTimeoutByFlowId(flowId);
            } catch (UnknownKeyException e) {
                log.error("Failed to handle a timeout event by FlowCreateService for {}.", flowId);
            }
        });

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(YFlowUpdateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);
            if (!isActive() && !hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }

    private String generateFlowId(String prefix) {
        byte[] uuidAsBytes = UUIDUtil.asByteArray(flowIdGenerator.generate());
        String uuidAsBase32 = BaseEncoding.base32().omitPadding().lowerCase().encode(uuidAsBytes);
        return prefix + uuidAsBase32;
    }
}
