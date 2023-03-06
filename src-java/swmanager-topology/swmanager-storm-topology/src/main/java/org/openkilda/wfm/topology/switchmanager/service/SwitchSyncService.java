/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.grpc.CreateOrUpdateLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DeleteLogicalPortResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.configs.SwitchSyncConfig;
import org.openkilda.wfm.topology.switchmanager.service.impl.CommandBuilderImpl;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SwitchSyncService implements SwitchManagerHubService {
    @Getter
    private SwitchManagerCarrier carrier;

    private Map<String, SwitchSyncFsm> handlers = new HashMap<>();
    private StateMachineBuilder<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> builder;

    @Getter
    private boolean active = true;

    @VisibleForTesting
    CommandBuilder commandBuilder;
    private SwitchSyncConfig syncConfig;

    public SwitchSyncService(
            SwitchManagerCarrier carrier, PersistenceManager persistenceManager, SwitchSyncConfig syncConfig) {
        this(carrier, new CommandBuilderImpl(persistenceManager), syncConfig);
    }

    @VisibleForTesting
    SwitchSyncService(SwitchManagerCarrier carrier, CommandBuilder commandBuilder, SwitchSyncConfig syncConfig) {
        this.carrier = carrier;
        this.commandBuilder = commandBuilder;
        this.builder = SwitchSyncFsm.builder();
        this.builder = SwitchSyncFsm.builder();
        this.syncConfig = syncConfig;
    }

    @Override
    public void timeout(@NonNull MessageCookie cookie) throws MessageDispatchException {
        fireHandlerEvent(cookie, SwitchSyncEvent.TIMEOUT);
    }

    @Override
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof CreateOrUpdateLogicalPortResponse) {
            fireHandlerEvent(cookie, SwitchSyncEvent.LOGICAL_PORT_INSTALLED);
        } else if (payload instanceof DeleteLogicalPortResponse) {
            fireHandlerEvent(cookie, SwitchSyncEvent.LOGICAL_PORT_REMOVED);
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    @Override
    public void dispatchWorkerMessage(SpeakerResponse payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof SpeakerCommandResponse) {
            SpeakerCommandResponse response = (SpeakerCommandResponse) payload;
            if (response.isSuccess()) {
                fireHandlerEvent(cookie, SwitchSyncEvent.COMMANDS_PROCESSED);
            } else {
                ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, "OpenFlow commands failed",
                        response.getFailedCommandIds().values().toString());
                fireHandlerEvent(cookie, SwitchSyncEvent.ERROR, errorData);
            }
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    @Override
    public void dispatchErrorMessage(ErrorData payload, MessageCookie cookie) throws MessageDispatchException {
        fireHandlerEvent(cookie, SwitchSyncEvent.ERROR, payload);
    }

    /**
     * Handle switch sync request.
     */
    public void handleSwitchSync(String key, SwitchValidateRequest request, ValidationResult validationResult) {
        SwitchSyncFsm fsm =
                builder.newStateMachine(SwitchSyncState.INITIALIZED, carrier, key, commandBuilder, request,
                        validationResult, syncConfig);
        handlers.put(key, fsm);
        process(fsm);
    }

    private void fireHandlerEvent(MessageCookie cookie, SwitchSyncEvent event) throws MessageDispatchException {
        fireHandlerEvent(cookie, event, null);
    }

    private void fireHandlerEvent(MessageCookie cookie, SwitchSyncEvent event, Object context)
            throws MessageDispatchException {
        SwitchSyncFsm handler = null;
        if (cookie != null) {
            handler = handlers.get(cookie.getValue());
        }
        if (handler == null) {
            throw new MessageDispatchException(cookie);
        }

        handler.fire(event, context);
        process(handler);
    }

    // FIXME(surabujin): incorrect FSM usage
    private void process(SwitchSyncFsm fsm) {
        final List<SwitchSyncState> stopStates = Arrays.asList(
                SwitchSyncState.SEND_REMOVE_COMMANDS,
                SwitchSyncState.SEND_MODIFY_COMMANDS,
                SwitchSyncState.SEND_INSTALL_COMMANDS,
                SwitchSyncState.LOGICAL_PORTS_COMMANDS_SEND,
                SwitchSyncState.FINISHED,
                SwitchSyncState.FINISHED_WITH_ERROR
        );

        while (!stopStates.contains(fsm.getCurrentState())) {
            fsm.fire(SwitchSyncEvent.NEXT);
        }

        if (fsm.isTerminated()) {
            handlers.remove(fsm.getKey());
            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
        }
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
        return handlers.isEmpty();
    }
}
