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

package org.openkilda.wfm.topology.floodlightrouter.service;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.PortsCommandData;
import org.openkilda.messaging.command.flow.DeleteMeterRequest;
import org.openkilda.messaging.command.flow.MeterModifyCommandRequest;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpGroupsForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersRequest;
import org.openkilda.messaging.command.switches.DumpPortDescriptionRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesRequest;
import org.openkilda.messaging.command.switches.DumpSwitchPortsDescriptionRequest;
import org.openkilda.messaging.command.switches.PortConfigurationRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.floodlight.request.PingRequest;
import org.openkilda.messaging.floodlight.request.RemoveBfdSession;
import org.openkilda.messaging.floodlight.request.SetupBfdSession;
import org.openkilda.model.SwitchId;

public final class RouterUtils {
    private static final String unableToExtractSwitchIdErrorFormat =  "Unable to extract switchId from %s";

    private RouterUtils() {
    }

    /**
     * Checks if the message should be broadcasted among regions or not.
     */
    public static boolean isBroadcast(CommandData payload) {
        return payload instanceof PortsCommandData;
    }

    /**
     * lookup SwitchId in message object.
     *
     * @param message - target
     * @return - SwitchId
     */
    public static SwitchId lookupSwitchId(Message message) {
        if (message instanceof CommandMessage) {
            CommandData commandData = ((CommandMessage) message).getData();
            if (commandData instanceof DiscoverIslCommandData) {
                return ((DiscoverIslCommandData) commandData).getSwitchId();
            } else if (commandData instanceof PingRequest) {
                return ((PingRequest) commandData).getPing().getSource().getDatapath();
            } else if (commandData instanceof DiscoverPathCommandData) {
                return ((DiscoverPathCommandData) commandData).getSrcSwitchId();
            } else if (commandData instanceof SwitchRulesDeleteRequest) {
                return ((SwitchRulesDeleteRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpRulesRequest) {
                return ((DumpRulesRequest) commandData).getSwitchId();
            } else if (commandData instanceof DeleteMeterRequest) {
                return ((DeleteMeterRequest) commandData).getSwitchId();
            } else if (commandData instanceof PortConfigurationRequest) {
                return ((PortConfigurationRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpSwitchPortsDescriptionRequest) {
                return ((DumpSwitchPortsDescriptionRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpPortDescriptionRequest) {
                return ((DumpPortDescriptionRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpMetersRequest) {
                return ((DumpMetersRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpRulesForFlowHsRequest) {
                return ((DumpRulesForFlowHsRequest) commandData).getSwitchId();
            } else if (commandData instanceof MeterModifyCommandRequest) {
                return ((MeterModifyCommandRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpRulesForSwitchManagerRequest) {
                return ((DumpRulesForSwitchManagerRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpMetersForSwitchManagerRequest) {
                return ((DumpMetersForSwitchManagerRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpMetersForFlowHsRequest) {
                return ((DumpMetersForFlowHsRequest) commandData).getSwitchId();
            } else if (commandData instanceof SetupBfdSession) {
                return ((SetupBfdSession) commandData).getBfdSession().getTarget().getDatapath();
            } else if (commandData instanceof RemoveBfdSession) {
                return ((RemoveBfdSession) commandData).getBfdSession().getTarget().getDatapath();
            } else if (commandData instanceof DumpGroupsForSwitchManagerRequest) {
                return ((DumpGroupsForSwitchManagerRequest) commandData).getSwitchId();
            } else if (commandData instanceof DumpGroupsForFlowHsRequest) {
                return ((DumpGroupsForFlowHsRequest) commandData).getSwitchId();
            }
        }

        throw new IllegalArgumentException(String.format(unableToExtractSwitchIdErrorFormat, message));
    }

    /**
     * Lookup SwitchId in message object.
     *
     * @param message - target
     * @return - SwitchId
     */
    public static SwitchId lookupSwitchId(AbstractMessage message) {
        if (message instanceof SpeakerRequest) {
            return ((SpeakerRequest) message).getSwitchId();
        }
        throw new IllegalArgumentException(String.format(unableToExtractSwitchIdErrorFormat, message));
    }
}
