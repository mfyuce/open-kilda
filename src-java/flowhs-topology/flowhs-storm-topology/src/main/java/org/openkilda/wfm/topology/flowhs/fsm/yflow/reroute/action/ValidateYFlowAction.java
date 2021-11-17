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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action;

import static java.lang.String.format;
import static java.util.Collections.emptySet;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateYFlowAction extends NbTrackableAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final YFlowRepository yFlowRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateYFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowRerouteContext context,
                                                    YFlowRerouteFsm stateMachine) {

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyYFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Y-flow reroute feature is disabled");
        }

        YFlowRerouteRequest request = context.getRerouteRequest();
        String yFlowId = request.getYFlowId();
        Set<IslEndpoint> affectedIsl =
                new HashSet<>(Optional.ofNullable(request.getAffectedIsl()).orElse(emptySet()));

        dashboardLogger.onYFlowReroute(yFlowId, affectedIsl, request.isForce());

        stateMachine.setAffectedIsls(affectedIsl);
        stateMachine.setRerouteReason(request.getReason());
        stateMachine.setForceReroute(request.isForce());
        stateMachine.setIgnoreBandwidth(request.isIgnoreBandwidth());

        YFlow yFlow = transactionManager.doInTransaction(() -> {
            YFlow result = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            if (result.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Y-flow %s is in progress now", yFlowId));
            }

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalYFlowStatus(result.getStatus());

            result.setStatus(FlowStatus.IN_PROGRESS);
            return result;
        });

        YSubFlow subFlow = yFlow.getSubFlows().stream().findAny()
                .orElseThrow(() -> new FlowProcessingException(ErrorType.DATA_INVALID,
                        format("Any sub-flow of the y-flow %s not found", yFlowId)));
        stateMachine.setMainAffinityFlowId(subFlow.getFlow().getAffinityGroupId());

        Set<String> subFlowIds = yFlow.getSubFlows().stream()
                .map(YSubFlow::getSubFlowId)
                .collect(Collectors.toSet());
        if (subFlowIds.size() < 2) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID,
                    format("The number of sub-flows of the y-flow %s is less then 2", yFlowId));
        }
        stateMachine.setTargetSubFlowIds(subFlowIds);

        YFlowResources oldYFlowResources = new YFlowResources();
        oldYFlowResources.setMainPathYPointResources(EndpointResources.builder()
                .endpoint(yFlow.getYPoint())
                .meterId(yFlow.getMeterId())
                .build());
        oldYFlowResources.setProtectedPathYPointResources(EndpointResources.builder()
                .endpoint(yFlow.getProtectedPathYPoint())
                .meterId(yFlow.getProtectedPathMeterId())
                .build());
        oldYFlowResources.setSharedEndpointResources(EndpointResources.builder()
                .endpoint(yFlow.getSharedEndpoint().getSwitchId())
                .meterId(yFlow.getSharedEndpointMeterId())
                .build());
        stateMachine.setOldResources(oldYFlowResources);

        stateMachine.saveNewEventToHistory("Y-flow was validated successfully", FlowEventData.Event.REROUTE);

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}
