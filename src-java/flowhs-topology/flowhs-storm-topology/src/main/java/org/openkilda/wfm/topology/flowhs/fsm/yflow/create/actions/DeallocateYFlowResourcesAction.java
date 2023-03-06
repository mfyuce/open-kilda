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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.actions;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class DeallocateYFlowResourcesAction extends
        YFlowProcessingWithHistorySupportAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final FlowResourcesManager resourcesManager;

    public DeallocateYFlowResourcesAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlowResources newResources = stateMachine.getNewResources();
        if (newResources == null) {
            log.debug("No resources were allocated for y-flow {}", yFlowId);
            return;
        }

        notifyStats(stateMachine, newResources);

        Optional<EndpointResources> sharedEndpointResources = ofNullable(newResources.getSharedEndpointResources());
        Optional<MeterId> sharedEndpointMeterId = sharedEndpointResources.map(EndpointResources::getMeterId);
        if (sharedEndpointMeterId.isPresent()) {
            MeterId meterId = sharedEndpointMeterId.get();
            SwitchId endpoint = sharedEndpointResources.get().getEndpoint();
            resourcesManager.deallocateMeter(endpoint, meterId);
            newResources.setSharedEndpointResources(null);
            stateMachine.saveActionToHistory("The meter was deallocated",
                    format("The meter %s / %s was deallocated", endpoint, meterId));
        } else {
            log.debug("No meter was allocated for y-flow {} (shared endpoint)", yFlowId);
        }

        Optional<EndpointResources> mainResources = ofNullable(newResources.getMainPathYPointResources());
        Optional<MeterId> mainMeterId = mainResources.map(EndpointResources::getMeterId);
        if (mainMeterId.isPresent()) {
            MeterId meterId = mainMeterId.get();
            SwitchId endpoint = mainResources.get().getEndpoint();
            resourcesManager.deallocateMeter(endpoint, meterId);
            newResources.setMainPathYPointResources(null);
            stateMachine.saveActionToHistory("The meter was deallocated",
                    format("The meter %s / %s was deallocated", endpoint, meterId));
        } else {
            log.debug("No meter was allocated for y-flow {} (main paths)", yFlowId);
        }

        Optional<EndpointResources> protectedResources = ofNullable(newResources.getProtectedPathYPointResources());
        Optional<MeterId> protectedMeterId = protectedResources.map(EndpointResources::getMeterId);
        if (protectedMeterId.isPresent()) {
            MeterId meterId = protectedMeterId.get();
            SwitchId endpoint = protectedResources.get().getEndpoint();
            resourcesManager.deallocateMeter(endpoint, meterId);
            newResources.setProtectedPathYPointResources(null);
            stateMachine.saveActionToHistory("The meter was deallocated",
                    format("The meter %s / %s was deallocated", endpoint, meterId));
        } else {
            log.debug("No meter was allocated for y-flow {} (protected paths)", yFlowId);
        }
    }

    private void notifyStats(YFlowCreateFsm fsm, YFlowResources resources) {
        fsm.sendRemoveStatsNotification(resources);
    }
}
