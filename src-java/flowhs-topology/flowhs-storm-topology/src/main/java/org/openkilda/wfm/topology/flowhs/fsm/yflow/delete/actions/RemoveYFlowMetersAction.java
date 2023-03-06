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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class RemoveYFlowMetersAction extends
        YFlowRuleManagerProcessingAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {

    public RemoveYFlowMetersAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowDeleteContext context, YFlowDeleteFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = getYFlow(yFlowId);

        YFlowResources oldResources;
        // This could be a retry.
        if (stateMachine.getOldResources() != null) {
            oldResources = stateMachine.getOldResources();
        } else {
            oldResources = new YFlowResources();
            stateMachine.setOldResources(oldResources);
        }

        if (oldResources.getSharedEndpointResources() == null) {
            oldResources.setSharedEndpointResources(EndpointResources.builder()
                    .endpoint(yFlow.getSharedEndpoint().getSwitchId())
                    .meterId(yFlow.getSharedEndpointMeterId())
                    .build());
        }

        if (oldResources.getMainPathYPointResources() == null) {
            oldResources.setMainPathYPointResources(EndpointResources.builder()
                    .endpoint(yFlow.getYPoint())
                    .meterId(yFlow.getMeterId())
                    .build());
        }

        if (yFlow.isAllocateProtectedPath() && yFlow.getProtectedPathYPoint() != null
                && oldResources.getProtectedPathYPointResources() == null) {
            oldResources.setProtectedPathYPointResources(EndpointResources.builder()
                    .endpoint(yFlow.getProtectedPathYPoint())
                    .meterId(yFlow.getProtectedPathMeterId())
                    .build());
        }

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        Collection<DeleteSpeakerCommandsRequest> commands = stateMachine.getDeleteOldYFlowCommands();

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove y-flow meters");
            stateMachine.fire(Event.ALL_YFLOW_METERS_REMOVED);
        } else {
            // emitting
            commands.forEach(command -> {
                stateMachine.getCarrier().sendSpeakerRequest(command);
                stateMachine.addDeleteSpeakerCommand(command.getCommandId(), command);
                stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for removing y-flow rules have been sent");
        }
    }
}
