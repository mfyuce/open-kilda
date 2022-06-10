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

package org.openkilda.wfm.topology.network.controller.sw;

import org.openkilda.model.Isl;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.model.OnlineStatus;
import org.openkilda.wfm.topology.network.model.PortDataHolder;
import org.openkilda.wfm.topology.network.service.ISwitchCarrier;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class PhysicalPort extends AbstractPort {
    private Isl history;

    PhysicalPort(Endpoint endpoint) {
        super(endpoint);
    }

    PhysicalPort(Endpoint endpoint, long maxSpeed, long currentSpeed) {
        super(endpoint, maxSpeed, currentSpeed);
    }

    @Override
    public void portAdd(ISwitchCarrier carrier) {
        carrier.setupPortHandler(getEndpoint(), history);
    }

    @Override
    public void portUpdate(ISwitchCarrier carrier) {
        PortDataHolder portData = new PortDataHolder(getMaxSpeed(), getCurrentSpeed());
        carrier.updatePortHandler(getEndpoint(), portData);
    }

    @Override
    public void portDel(ISwitchCarrier carrier) {
        carrier.removePortHandler(getEndpoint());
    }

    @Override
    public void updateOnlineStatus(ISwitchCarrier carrier, OnlineStatus onlineStatus) {
        PortDataHolder portData = new PortDataHolder(getMaxSpeed(), getCurrentSpeed());
        carrier.setOnlineMode(getEndpoint(), onlineStatus, portData);
    }

    @Override
    public void updatePortLinkMode(ISwitchCarrier carrier) {
        PortDataHolder portData = new PortDataHolder(getMaxSpeed(), getCurrentSpeed());
        carrier.setPortLinkMode(getEndpoint(), getLinkStatus(), portData);
    }

    @Override
    public String makeDashboardPortLabel(NetworkTopologyDashboardLogger dashboardLogger) {
        return dashboardLogger.makePortLabel(this);
    }
}
