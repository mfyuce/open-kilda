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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action;

import static java.lang.String.format;

import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateYFlowAction extends YFlowProcessingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    public UpdateYFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        YFlowRequest targetFlow = stateMachine.getTargetFlow();

        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            YFlow yFlow = getYFlow(targetFlow.getYFlowId());
            updateFlow(yFlow, YFlowRequestMapper.INSTANCE.toYFlow(targetFlow));
            return yFlow.getStatus();
        });

        stateMachine.saveActionToHistory(format("The y-flow was updated. The status %s", flowStatus));
    }

    private void updateFlow(YFlow yFlow, YFlow targetFlow) {
        yFlow.setSharedEndpoint(targetFlow.getSharedEndpoint());
        yFlow.setMaximumBandwidth(targetFlow.getMaximumBandwidth());
        yFlow.setPathComputationStrategy(targetFlow.getPathComputationStrategy());
        yFlow.setEncapsulationType(targetFlow.getEncapsulationType());
        yFlow.setMaxLatency(targetFlow.getMaxLatency());
        yFlow.setMaxLatencyTier2(targetFlow.getMaxLatencyTier2());
        yFlow.setIgnoreBandwidth(targetFlow.isIgnoreBandwidth());
        yFlow.setPeriodicPings(targetFlow.isPeriodicPings());
        yFlow.setPinned(targetFlow.isPinned());
        yFlow.setPriority(targetFlow.getPriority());
        yFlow.setStrictBandwidth(targetFlow.isStrictBandwidth());
        yFlow.setDescription(targetFlow.getDescription());
        yFlow.setAllocateProtectedPath(targetFlow.isAllocateProtectedPath());
    }
}
