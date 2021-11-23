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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.command.flow.FlowMirrorPointDeleteRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowMirrorPointDeleteService extends FsmBasedFlowProcessingService<FlowMirrorPointDeleteFsm, Event,
        FlowMirrorPointDeleteContext, FlowMirrorPointDeleteHubCarrier> {
    private final FlowMirrorPointDeleteFsm.Factory fsmFactory;

    public FlowMirrorPointDeleteService(FlowMirrorPointDeleteHubCarrier carrier, PersistenceManager persistenceManager,
                                        FlowResourcesManager flowResourcesManager, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new FlowMirrorPointDeleteFsm.Factory(carrier, persistenceManager, flowResourcesManager,
                speakerCommandRetriesLimit);
    }

    /**
     * Handles request for delete flow mirror point.
     *
     * @param key command identifier.
     * @param request request data.
     */

    public void handleDeleteMirrorPointRequest(String key, CommandContext commandContext,
                                               FlowMirrorPointDeleteRequest request) {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        handleRequest(key, commandContext, request);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        FlowMirrorPointDeleteFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowMirrorPointDeleteContext context = FlowMirrorPointDeleteContext.builder()
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
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowMirrorPointDeleteFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void handleRequest(String key, CommandContext commandContext, FlowMirrorPointDeleteRequest request) {
        String flowId = request.getFlowId();
        log.debug("Handling flow delete mirror point request with key {}, flow ID: {}, and flow mirror ID: {}",
                key, flowId, request.getMirrorPointId());

        if (hasRegisteredFsmWithKeyOrFlowId(key, flowId)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }

        FlowMirrorPointDeleteFsm fsm = fsmFactory.newInstance(commandContext, flowId);
        registerFsm(key, fsm);

        FlowMirrorPointDeleteContext context = FlowMirrorPointDeleteContext.builder()
                .flowMirrorPointId(request.getMirrorPointId())
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowMirrorPointDeleteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);

            if (!isActive() && !hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }
}
