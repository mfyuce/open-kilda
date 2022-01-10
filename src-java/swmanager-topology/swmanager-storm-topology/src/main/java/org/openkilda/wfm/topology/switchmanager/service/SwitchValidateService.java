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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.grpc.DumpLogicalPortsResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;

public interface SwitchValidateService {

    void handleSwitchValidateRequest(String key, SwitchValidateRequest data);

    void handleFlowEntriesResponse(String key, FlowDumpResponse data);

    void handleGroupEntriesResponse(String key, GroupDumpResponse data);

    void handleLogicalPortResponse(String key, DumpLogicalPortsResponse data);

    void handleMeterEntriesResponse(String key, MeterDumpResponse data);

    void handleMetersUnsupportedResponse(String key);

    void handleTaskTimeout(String key);

    void handleTaskError(String key, ErrorMessage message);

    void activate();

    boolean deactivate();

    boolean isAllOperationsCompleted();
}
