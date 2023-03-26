/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.converter.IofSwitchConverter;
import org.openkilda.floodlight.converter.OfPortDescConverter;
import org.openkilda.floodlight.error.InvalidConnectionDataException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.FloodlightDashboardLogger;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class SwitchTrackingService implements IOFSwitchListener, IService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchTrackingService.class);

    private static final FloodlightDashboardLogger dashboardLogger = new FloodlightDashboardLogger(logger);

    private final ReadWriteLock discoveryLock = new ReentrantReadWriteLock();

    private IKafkaProducerService producerService;
    private ISwitchManager switchManager;
    private FeatureDetectorService featureDetector;

    private String discoveryTopic;
    private String region;

    /**
     * Send dump contain all connected at this moment switches.
     */
    public void dumpAllSwitches(String dumpId) {
        discoveryLock.writeLock().lock();
        try {
            dumpAllSwitchesAction(dumpId);
        } finally {
            discoveryLock.writeLock().unlock();
        }
    }

    @Override
    @NewCorrelationContextRequired
    public void switchAdded(final DatapathId switchId) {
        dashboardLogger.onSwitchEvent(switchId, SwitchChangeType.ADDED);
        switchDiscovery(switchId, SwitchChangeType.ADDED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchRemoved(final DatapathId switchId) {
        dashboardLogger.onSwitchEvent(switchId, SwitchChangeType.REMOVED);

        // TODO(surabujin): must figure out events order/set during lost connection
        switchDiscovery(switchId, SwitchChangeType.REMOVED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchActivated(final DatapathId switchId) {
        dashboardLogger.onSwitchEvent(switchId, SwitchChangeType.ACTIVATED);

        discoveryLock.readLock().lock();
        try {
            switchDiscoveryAction(switchId, SwitchChangeType.ACTIVATED);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    @Override
    @NewCorrelationContextRequired
    public void switchDeactivated(final DatapathId switchId) {
        dashboardLogger.onSwitchEvent(switchId, SwitchChangeType.DEACTIVATED);

        switchDiscovery(switchId, SwitchChangeType.DEACTIVATED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchChanged(final DatapathId switchId) {
        dashboardLogger.onSwitchEvent(switchId, SwitchChangeType.CHANGED);
        switchDiscovery(switchId, SwitchChangeType.CHANGED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc portDesc, final PortChangeType type) {
        dashboardLogger.onPortEvent(switchId, portDesc, type);

        if (!OfPortDescConverter.INSTANCE.isReservedPort(portDesc.getPortNo())) {
            portDiscovery(switchId, portDesc, type);
        }
    }

    @Override
    public void setup(FloodlightModuleContext context) {
        producerService = context.getServiceImpl(IKafkaProducerService.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
        featureDetector = context.getServiceImpl(FeatureDetectorService.class);
        KafkaChannel kafkaChannel = context.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        discoveryTopic = kafkaChannel.getTopoDiscoTopic();
        region = kafkaChannel.getRegion();

        context.getServiceImpl(IOFSwitchService.class).addOFSwitchListener(this);
    }

    private void dumpAllSwitchesAction(String dumpId) {
        Collection<IOFSwitch> iofSwitches = switchManager.getAllSwitchMap(true).values();
        for (IOFSwitch sw : iofSwitches) {
            NetworkDumpSwitchData payload = null;
            try {
                payload = new NetworkDumpSwitchData(
                        buildSwitch(sw), dumpId, sw.getControllerRole() != OFControllerRole.ROLE_SLAVE);
                emitDiscoveryEvent(sw.getId(), payload);
            } catch (SwitchOperationException e) {
                log.error("Exclude {} from dump switches response - {}", sw.getId(), e.getMessage(), e);
            }
        }
    }

    private void switchDiscovery(DatapathId dpId, SwitchChangeType state) {
        discoveryLock.readLock().lock();
        try {
            switchDiscoveryAction(dpId, state);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    private void portDiscovery(DatapathId dpId, OFPortDesc portDesc, PortChangeType changeType) {
        discoveryLock.readLock().lock();
        try {
            portDiscoveryAction(dpId, portDesc, changeType);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    private void switchDiscoveryAction(DatapathId dpId, SwitchChangeType event) {
        logger.info("Send switch discovery ({} - {})", dpId, event);
        SwitchInfoData payload = null;
        if (SwitchChangeType.DEACTIVATED != event && SwitchChangeType.REMOVED != event) {
            try {
                IOFSwitch sw = switchManager.lookupSwitch(dpId);
                SpeakerSwitchView switchView = buildSwitch(sw);
                payload = buildSwitchMessage(sw, switchView, event);
            } catch (SwitchNotFoundException | InvalidConnectionDataException e) {
                logger.error(
                        "Switch {} is not in management state now({}), switch ISL discovery details will be degraded.",
                        dpId, e.getMessage());
            }
        }
        if (payload == null) {
            payload = buildSwitchMessage(dpId, event);
        }

        emitDiscoveryEvent(dpId, payload);
    }

    private void portDiscoveryAction(DatapathId dpId, OFPortDesc portDesc, PortChangeType changeType) {
        logger.info("Send port discovery ({}-{} - {})", dpId, portDesc.getPortNo(), changeType);
        InfoData payload = OfPortDescConverter.INSTANCE.toPortInfoData(dpId, portDesc, changeType);
        emitDiscoveryEvent(dpId, payload);
    }

    private void emitDiscoveryEvent(DatapathId dpId, InfoData payload) {
        Message message = buildMessage(payload);
        producerService.sendMessageAndTrackWithZk(discoveryTopic, dpId.toString(), message);
    }

    /**
     * Builds fully filled switch ISL discovery message.
     *
     * @param sw switch instance
     * @param eventType type of event
     * @return Message
     */
    private SwitchInfoData buildSwitchMessage(IOFSwitch sw, SpeakerSwitchView switchView, SwitchChangeType eventType) {
        return IofSwitchConverter.buildSwitchInfoData(sw, switchView, eventType);
    }

    /**
     * Builds degraded switch ISL discovery message.
     *
     * @param dpId switch datapath
     * @param eventType type of event
     * @return Message
     */
    private SwitchInfoData buildSwitchMessage(DatapathId dpId, SwitchChangeType eventType) {
        return new SwitchInfoData(new SwitchId(dpId.getLong()), eventType);
    }

    /**
     * Builds a generic message object.
     *
     * @param data data to use in the message body
     * @return Message
     */
    private Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), CorrelationContext.getId(), null, region);
    }

    private SpeakerSwitchView buildSwitch(IOFSwitch sw) throws InvalidConnectionDataException {
        SpeakerSwitchView.SpeakerSwitchViewBuilder builder = SpeakerSwitchView.builder()
                .datapath(new SwitchId(sw.getId().getLong()))
                .hostname(readHostname(sw.getInetAddress()))
                .ofVersion(sw.getOFFactory().getVersion().toString())
                .features(featureDetector.detectSwitch(sw));

        SwitchDescription ofDescription = sw.getSwitchDescription();
        builder.description(SpeakerSwitchDescription.builder()
                .manufacturer(ofDescription.getManufacturerDescription())
                .hardware(ofDescription.getHardwareDescription())
                .software(ofDescription.getSoftwareDescription())
                .serialNumber(ofDescription.getSerialNumber())
                .datapath(ofDescription.getDatapathDescription())
                .build());

        switchManager.getPhysicalPorts(sw).stream()
                .map(port -> new SpeakerSwitchPortView(
                        port.getPortNo().getPortNumber(),
                        port.isEnabled()
                                ? SpeakerSwitchPortView.State.UP
                                : SpeakerSwitchPortView.State.DOWN,
                        port.getMaxSpeed(),
                        port.getCurrSpeed()))
                .forEach(builder::port);

        buildSwitchAddress(builder, sw);
        buildSwitchSpeakerAddress(builder, sw);

        return builder.build();
    }

    private void buildSwitchAddress(SpeakerSwitchView.SpeakerSwitchViewBuilder builder, IOFSwitch sw)
            throws InvalidConnectionDataException {
        SocketAddress socketAddress = sw.getInetAddress();
        try {
            builder.switchSocketAddress(readSocketAddress(socketAddress));
        } catch (IllegalArgumentException e) {
            throw InvalidConnectionDataException.ofSwitchSocket(sw.getId(), socketAddress);
        }
    }

    private void buildSwitchSpeakerAddress(SpeakerSwitchView.SpeakerSwitchViewBuilder builder, IOFSwitch sw)
            throws InvalidConnectionDataException {
        SocketAddress socketAddress = sw.getConnectionByCategory(
                LogicalOFMessageCategory.MAIN).getLocalInetAddress();
        try {
            builder.speakerSocketAddress(readSocketAddress(socketAddress));
        } catch (IllegalArgumentException e) {
            throw InvalidConnectionDataException.ofSpeakerSocket(sw.getId(), socketAddress);
        }
    }

    private String readHostname(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return readHostname((InetSocketAddress) address);
        }
        throw new IllegalArgumentException(String.format("Unsupported socket address format: %s", address));
    }

    private String readHostname(InetSocketAddress socketAddress) {
        return socketAddress.getHostName();
    }

    private IpSocketAddress readSocketAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return readSocketAddress((InetSocketAddress) address);
        }
        throw new IllegalArgumentException(String.format("Unsupported socket address format: %s", address));
    }

    private IpSocketAddress readSocketAddress(InetSocketAddress socketAddress) {
        if (socketAddress.isUnresolved()) {
            return new IpSocketAddress(socketAddress.getHostString(), socketAddress.getPort());
        }

        InetAddress address = socketAddress.getAddress();
        if (address != null) {
            return new IpSocketAddress(address.getHostAddress(), socketAddress.getPort());
        }
        throw new IllegalArgumentException(String.format("Address %s is not resolvable", socketAddress));
    }
}
