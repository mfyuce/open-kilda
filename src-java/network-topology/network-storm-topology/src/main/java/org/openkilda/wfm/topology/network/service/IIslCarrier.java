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

package org.openkilda.wfm.topology.network.service;

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.command.reroute.RerouteFlows;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;

import java.util.List;
import java.util.UUID;

public interface IIslCarrier {
    void bfdPropertiesApplyRequest(Endpoint physicalEndpoint, IslReference reference, BfdProperties properties);

    void bfdDisableRequest(Endpoint physicalEndpoint);

    void triggerReroute(RerouteFlows trigger);

    void islStatusUpdateNotification(IslStatusUpdateNotification trigger);

    void islRulesInstall(IslReference reference, Endpoint endpoint);

    void sendIslRulesInstallCommand(SwitchId switchId, UUID commandId, List<OfCommand> speakerData);

    void islRulesDelete(IslReference reference, Endpoint endpoint);

    void sendIslRulesDeleteCommand(SwitchId switchId, UUID commandId, List<OfCommand> speakerData);

    void islRulesInstalled(IslReference reference, Endpoint endpoint);

    void islRulesDeleted(IslReference reference, Endpoint endpoint);

    void islRulesFailed(IslReference reference, Endpoint endpoint);

    void auxiliaryPollModeUpdateRequest(Endpoint endpoint, boolean enableAuxiliaryPollMode);

    void islRemovedNotification(Endpoint endpoint, IslReference reference);

    void islChangedNotifyFlowMonitor(IslReference reference, boolean removed);
}
