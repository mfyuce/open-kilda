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

package org.openkilda.wfm.topology.switchmanager.service.impl.fsmhandlers;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.grpc.CreateLogicalPortResponse;
import org.openkilda.messaging.swmanager.request.CreateLagRequest;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.error.OperationTimeoutException;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagFsm.CreateLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagFsm.CreateLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagFsm.CreateLagState;
import org.openkilda.wfm.topology.switchmanager.service.CreateLagService;
import org.openkilda.wfm.topology.switchmanager.service.LagOperationService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.impl.LagOperationServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CreateLagServiceImpl implements CreateLagService {

    private final SwitchManagerCarrier carrier;
    private final LagOperationService lagOperationService;
    private final Map<String, CreateLagFsm> fsms = new HashMap<>();
    private final StateMachineBuilder<CreateLagFsm, CreateLagState, CreateLagEvent, CreateLagContext> builder;
    private final FsmExecutor<CreateLagFsm, CreateLagState, CreateLagEvent, CreateLagContext> fsmExecutor;

    private boolean active = true;

    public CreateLagServiceImpl(SwitchManagerCarrier carrier, RepositoryFactory repositoryFactory,
                                TransactionManager transactionManager, int bfdPortOffset, int bfdPortMaxNumber,
                                int lagPortOffset) {
        this.lagOperationService = new LagOperationServiceImpl(repositoryFactory, transactionManager, bfdPortOffset,
                bfdPortMaxNumber, lagPortOffset);
        this.builder = CreateLagFsm.builder();
        this.fsmExecutor = new FsmExecutor<>(CreateLagEvent.NEXT);
        this.carrier = carrier;
    }

    @Override
    public void handleCreateLagRequest(String key, CreateLagRequest request) {
        CreateLagFsm fsm = builder.newStateMachine(CreateLagState.START, carrier, key, request, lagOperationService);
        fsms.put(key, fsm);

        fsm.start();
        fireFsmEvent(fsm, CreateLagEvent.NEXT, CreateLagContext.builder().build());
    }

    @Override
    public void handleTaskError(String key, ErrorMessage message) {
        if (!fsms.containsKey(key)) {
            logCreateFsmNotFound(key);
            return;
        }
        CreateLagContext context = CreateLagContext.builder()
                .error(new SpeakerFailureException(message.getData()))
                .build();
        fireFsmEvent(fsms.get(key), CreateLagEvent.ERROR, context);
    }


    @Override
    public void handleTaskTimeout(String key) {
        if (!fsms.containsKey(key)) {
            logCreateFsmNotFound(key);
            return;
        }

        CreateLagFsm fsm = fsms.get(key);
        OperationTimeoutException error = new OperationTimeoutException(
                format("LAG create operation timeout. Switch %s", fsm.getSwitchId()));
        fireFsmEvent(fsm, CreateLagEvent.ERROR, CreateLagContext.builder().error(error).build());
    }

    @Override
    public void handleGrpcResponse(String key, CreateLogicalPortResponse response) {
        if (!fsms.containsKey(key)) {
            logCreateFsmNotFound(key);
            return;
        }
        fireFsmEvent(fsms.get(key), CreateLagEvent.LAG_INSTALLED,
                CreateLagContext.builder().createdLogicalPort(response.getLogicalPort()).build());
    }

    @Override
    public void activate() {
        active = true;
    }

    @Override
    public boolean deactivate() {
        active = false;
        return isAllOperationsCompleted();
    }

    @Override
    public boolean isAllOperationsCompleted() {
        return fsms.isEmpty();
    }

    private void logCreateFsmNotFound(String key) {
        log.warn("Create LAG fsm with key {} not found", key);
    }

    private void fireFsmEvent(CreateLagFsm fsm, CreateLagEvent event, CreateLagContext context) {
        fsmExecutor.fire(fsm, event, context);
        removeIfCompleted(fsm);
    }

    private void removeIfCompleted(CreateLagFsm fsm) {
        if (fsm.isTerminated()) {
            log.info("Create LAG {} FSM have reached termination state (key={})", fsm.getRequest(), fsm.getKey());
            fsms.remove(fsm.getKey());
            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
        }
    }
}
