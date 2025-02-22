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

package org.openkilda.northbound.service;

import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.info.switches.SwitchPortsDescription;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.switches.PortConfigurationPayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.v1.switches.DeleteSwitchResult;
import org.openkilda.northbound.dto.v1.switches.PortDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v1.switches.UnderMaintenanceDto;
import org.openkilda.northbound.dto.v2.switches.CreateLagPortDto;
import org.openkilda.northbound.dto.v2.switches.LagPortDto;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.PortPropertiesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectedDevicesResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchConnectionsResponse;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;
import org.openkilda.northbound.dto.v2.switches.SwitchPropertiesDump;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SwitchService {

    /**
     * Get all available switches.
     * @return list of switches.
     */
    CompletableFuture<List<SwitchDto>> getSwitches();

    /**
     * Get available switch.
     * @return switch.
     */
    CompletableFuture<SwitchDto> getSwitch(SwitchId switchId);

    /**
     * Get all rules from the switch. If cookie is specified, then return just that cookie rule.
     *
     * @param switchId the switch
     * @param cookie if > 0, then filter the results based on that cookie
     * @return the list of rules
     */
    CompletableFuture<SwitchFlowEntries> getRules(SwitchId switchId, Long cookie);

    /**
     * Get all rules from the switch. If cookie is specified, then return just that cookie rule.
     *
     * @param switchId the switch
     * @param cookie if > 0, then filter the results based on that cookie
     * @param correlationId passed correlation id
     * @return the list of rules
     */
    CompletableFuture<SwitchFlowEntries> getRules(SwitchId switchId, Long cookie, String correlationId);

    /**
     * Deletes rules from the switch. The flag (@code deleteAction) defines which rules to delete.
     *
     * @param switchId switch id
     * @param deleteAction defines which rules to delete.
     * @return the list of cookies of removed rules.
     */
    CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesAction deleteAction);

    /**
     * Deletes rules from the switch.
     *
     * @param switchId switch id
     * @param criteria defines criteria for rules to delete.
     * @return the list of cookies of removed rules.
     */
    CompletableFuture<List<Long>> deleteRules(SwitchId switchId, DeleteRulesCriteria criteria);

    /**
     * Install default rules on the switch. The flag (@code installAction) defines what to do about the default rules.
     *
     * @param switchId switch id
     * @param installAction defines what to do about the default rules
     * @return the list of cookies for installed rules
     */
    CompletableFuture<List<Long>> installRules(SwitchId switchId, InstallRulesAction installAction);


    /**
     * Sets (or just gets) the connection mode for the switches. The connection mode governs the
     * policy for what Floodlight does.
     *
     * @param mode the mode to use. If null, then just return existing value.
     * @return the value of connection mode after the operation
     */
    CompletableFuture<ConnectModeRequest.Mode> connectMode(ConnectModeRequest.Mode mode);

    /**
     * Validate the rules installed on the switch against the flows in the database.
     *
     * @param switchId switch to validate rules on.
     * @return the validation details.
     */
    CompletableFuture<RulesValidationResult> validateRules(SwitchId switchId);

    /**
     * Validate the rules and the meters installed on the switch against the flows in the database.
     *
     * @param switchId switch to validate.
     * @return the validation details.
     */
    CompletableFuture<SwitchValidationResult> validateSwitch(SwitchId switchId);

    /**
     * Synchronize (install) missing rules that should be on the switch but exist only in the database.
     *
     * @param switchId switch to synchronize rules on.
     * @return the synchronization result.
     */
    CompletableFuture<RulesSyncResult> syncRules(SwitchId switchId);

    /**
     * Synchronize (install) missing rules and optionally remove excess rules and meters on the switch.
     *
     * @param switchId switch to synchronize.
     * @param removeExcess remove excess rules and meters flag.
     * @return the synchronization result.
     */
    CompletableFuture<SwitchSyncResult> syncSwitch(SwitchId switchId, boolean removeExcess);

    /**
     * Dumps all meters from the switch.
     * @param switchId switch datapath id.
     * @return meters dump.
     */
    CompletableFuture<SwitchMeterEntries> getMeters(SwitchId switchId);

    /**
     * Removes meter from the switch.
     * @param switchId switch datapath id.
     * @param meterId meter to be deleted.
     */
    CompletableFuture<DeleteMeterResult> deleteMeter(SwitchId switchId, long meterId);

    /**
     * Configure switch port. <br>
     * Configurations
     * <ul>
     * <li> UP/DOWN port </li>
     * <li> Change port speed </li>
     * </ul>
     *
     * @param switchId switch whose port is to configure
     * @param port port to configure
     * @param portConfig port configuration that needs to apply on port
     * @return portDto
     */
    CompletableFuture<PortDto> configurePort(SwitchId switchId,  int port, PortConfigurationPayload portConfig);

    /**
     * Get a description of the switch ports.
     *
     * @param switchId the switch id.
     * @return the list of port descriptions.
     */
    CompletableFuture<SwitchPortsDescription> getSwitchPortsDescription(SwitchId switchId);

    /**
     * Get a description of the switch port.
     *
     * @param switchId the switch id.
     * @param port the port of switch.
     * @return the port description.
     */
    CompletableFuture<PortDescription> getPortDescription(SwitchId switchId, int port);

    /**
     * Get a list of states with reference to time.
     * @param switchId the switch id.
     * @param port the port number.
     * @param from start date for a search.
     * @param to end date for a search.
     * @return port history.
     */
    CompletableFuture<List<PortHistoryResponse>> getPortHistory(SwitchId switchId, int port, Instant from, Instant to);

    /**
     * Update "Under maintenance" flag for the switch.
     *
     * @param switchId the switch id.
     * @param underMaintenanceDto under maintenance flag.
     * @return updated switch.
     */
    CompletableFuture<SwitchDto> updateSwitchUnderMaintenance(SwitchId switchId,
                                                              UnderMaintenanceDto underMaintenanceDto);

    /**
     * Delete switch.
     *
     * @param switchId id of switch to delete
     * @param force True value means that all switch checks (switch is deactivated, there is no flow with this switch,
     *              switch has no ISLs) will be ignored.
     * @return result of the operation wrapped into {@link DeleteSwitchResult}. True means no errors is occurred.
     */
    CompletableFuture<DeleteSwitchResult> deleteSwitch(SwitchId switchId, boolean force);

    /**
     * Get a list of flows that goes through a particular switch.
     *
     * @param switchId the switch id.
     * @param port the port of switch.
     * @return list of flows that goes through a particular switch.
     */
    CompletableFuture<List<FlowPayload>> getFlowsForSwitch(SwitchId switchId, Integer port);

    /**
     * Get switch properties.
     *
     * @param switchId id of the switch
     * @return switch properties object
     */
    CompletableFuture<SwitchPropertiesDto> getSwitchProperties(SwitchId switchId);


    /**
     * Dump switch properties.
     *
     * @return switch properties object
     */
    CompletableFuture<SwitchPropertiesDump> dumpSwitchProperties();

    /**
     * Update switch properties.
     *
     * @param switchId id of the switch
     * @return switch properties object
     */
    CompletableFuture<SwitchPropertiesDto> updateSwitchProperties(SwitchId switchId,
                                                                  SwitchPropertiesDto switchPropertiesDto);

    /**
     * Get port properties.
     *
     * @param switchId id of the switch
     * @param port port number
     * @return port properties object
     */
    CompletableFuture<PortPropertiesResponse> getPortProperties(SwitchId switchId, int port);

    /**
     * Update port properties.
     *
     * @param switchId id of the switch
     * @param port port number
     * @param portPropertiesDto port properties
     * @return port properties object
     */
    CompletableFuture<PortPropertiesResponse> updatePortProperties(SwitchId switchId, int port,
                                                                   PortPropertiesDto portPropertiesDto);

    /**
     * Get devices connected to the switch.
     *
     * @param switchId id of the switch
     * @return port properties object
     */
    CompletableFuture<SwitchConnectedDevicesResponse> getSwitchConnectedDevices(SwitchId switchId, Instant since);

    /**
     * Update switch.
     *
     * @return switch.
     */
    CompletableFuture<SwitchDtoV2> patchSwitch(SwitchId switchId, SwitchPatchDto dto);

    CompletableFuture<SwitchConnectionsResponse> getSwitchConnections(SwitchId switchId);

    CompletableFuture<LagPortDto> createLag(SwitchId switchId, CreateLagPortDto createLagPortDto);

    CompletableFuture<List<LagPortDto>> getLagPorts(SwitchId switchId);

    CompletableFuture<LagPortDto> deleteLagPort(SwitchId switchId, Integer logicalPortNumber);
}
