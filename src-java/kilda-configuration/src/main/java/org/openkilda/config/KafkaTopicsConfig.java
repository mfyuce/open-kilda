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

package org.openkilda.config;

import static org.openkilda.config.KafkaTopicsConfig.KAFKA_TOPIC_MAPPING;

import org.openkilda.config.mapping.Mapping;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.FallbackKey;
import com.sabre.oss.conf4j.annotation.Key;

@Configuration
@Key("kafka.topic")
@Mapping(target = KAFKA_TOPIC_MAPPING)
public interface KafkaTopicsConfig {
    String KAFKA_TOPIC_MAPPING = "KAFKA_TOPIC";

    @Key("ctrl")
    @Default("kilda.ctrl")
    String getCtrlTopic();

    @Key("northbound.flow.priv")
    @Default("kilda.northbound.flowhs.priv")
    String getFlowHsTopic();

    @Key("flow.status")
    @Default("kilda.flow.status")
    String getFlowStatusTopic();

    @Key("northbound")
    @FallbackKey("kafka.northbound.topic")
    @Default("kilda.northbound.storm")
    String getNorthboundTopic();

    @Key("northbound.region")
    @FallbackKey("kafka.northbound.topic.region")
    @Default("kilda.northbound")
    String getNorthboundRegionTopic();

    @Key("opentsdb")
    @Default("kilda.otsdb")
    String getOtsdbTopic();

    @Key("speaker")
    @FallbackKey("kafka.speaker.topic")
    @Default("kilda.speaker.storm")
    String getSpeakerTopic();

    @Key("speaker.region")
    @FallbackKey("kafka.speaker.region.topic")
    @Default("kilda.speaker")
    String getSpeakerRegionTopic();

    @Key("speaker.disco")
    @FallbackKey("kafka.speaker.disco")
    @Default("kilda.speaker.disco.storm")
    String getSpeakerDiscoTopic();

    @Key("speaker.disco.region")
    @FallbackKey("kafka.speaker.disco.region")
    @Default("kilda.speaker.disco")
    String getSpeakerDiscoRegionTopic();

    @Key("speaker.flowhs")
    @Default("kilda.speaker.flowhs.storm")
    String getSpeakerFlowHsTopic();

    @Key("speaker.flow.region")
    @FallbackKey("kafka.speaker.flow.region")
    @Default("kilda.speaker.flow")
    String getSpeakerFlowRegionTopic();

    @Key("speaker.flowhs.priv")
    @Default("kilda.speaker.flowhs.priv")
    String getFlowHsSpeakerTopic();

    @Key("speaker.flowhs.priv.region")
    @Default("kilda.speaker.flowhs.priv.region")
    String getFlowHsSpeakerRegionTopic();

    @Key("speaker.flow.ping")
    @FallbackKey("kafka.speaker.flow.ping")
    @Default("kilda.speaker.flow.ping.storm")
    String getSpeakerFlowPingTopic();

    @Key("speaker.flow.ping.region")
    @FallbackKey("kafka.speaker.flow.ping.region")
    @Default("kilda.speaker.flow.ping")
    String getSpeakerFlowPingRegionTopic();

    @Key("grpc.speaker")
    @Default("kilda.grpc.speaker")
    String getGrpcSpeakerTopic();

    @Key("ping")
    @Default("kilda.ping.storm")
    String getPingTopic();

    @Key("ping.region")
    @Default("kilda.ping")
    String getPingRegionTopic();

    @Key("stats")
    @Default("kilda.stats.storm")
    String getStatsTopic();

    @Key("stats.region")
    @Default("kilda.stats")
    String getStatsRegionTopic();

    @Key("topo.disco")
    @Default("kilda.topo.disco.storm")
    String getTopoDiscoTopic();

    @Key("topo.disco.region")
    @Default("kilda.topo.disco")
    String getTopoDiscoRegionTopic();

    @Key("topo.nbworker")
    @FallbackKey("kafka.nbworker.topic")
    @Default("kilda.topo.nb.storm")
    String getTopoNbTopic();

    @Key("topo.nbworker.region")
    @FallbackKey("kafka.nbworker.topic.region")
    @Default("kilda.topo.nb")
    String getTopoNbRegionTopic();

    @Key("topo.reroute")
    @Default("kilda.topo.reroute.storm")
    String getTopoRerouteTopic();

    @Key("topo.isl.status")
    @Default("kilda.network.isl.status.priv")
    String getNetworkIslStatusTopic();

    @Key("topo.switch.manager.nb")
    @Default("kilda.nb.swmanager_request.priv")
    String getTopoSwitchManagerNbTopic();

    @Key("topo.switch.manager.network")
    @Default("kilda.network.swmanager_request.priv")
    String getTopoSwitchManagerNetworkTopic();

    @Key("topo.switch.manager.nbworker")
    @Default("kilda.nbworker.swmanager_request.priv")
    String getTopoSwitchManagerNbWorkerTopic();

    @Key("topo.switch.manager")
    @Default("kilda.topo.switch.manager.storm")
    String getTopoSwitchManagerTopic();

    @Key("topo.switch.manager.region")
    @Default("kilda.topo.switch.manager")
    String getTopoSwitchManagerRegionTopic();

    @Key("topo.isl.latency")
    @Default("kilda.topo.isl.latency.storm")
    String getTopoIslLatencyTopic();

    @Key("topo.isl.latency.region")
    @Default("kilda.topo.isl.latency")
    String getTopoIslLatencyRegionTopic();

    @Key("topo.router.connected.devices.storm")
    @Default("kilda.connected.devices.priv.storm")
    String getTopoConnectedDevicesTopic();

    @Key("topo.floodlight.connected.devices.region")
    @Default("kilda.floodlight.connected.devices.priv")
    String getTopoConnectedDevicesRegionTopic();

    // TODO(surabujin): check usage
    @Key("grpc.response")
    @Default("kilda.grpc.response.priv")
    String getGrpcResponseTopic();

    @Key("server42-stats.flowrtt")
    @Default("kilda.server42-stats.flowrtt.priv")
    String getServer42StatsFlowRttTopic();

    @Key("server42-stats.islrtt")
    @Default("kilda.server42-stats.islrtt.priv")
    String getServer42StatsIslRttTopic();

    @Key("server42-storm.commands")
    @Default("kilda.server42-storm.commands.priv")
    String getServer42StormCommandsTopic();

    @Key("server42-control.commands-reply")
    @Default("kilda.server42-control.commands-reply.priv")
    String getServer42ControlCommandsReplyTopic();

    @Key("topo.flowhs.server42-storm")
    @Default("kilda.flowhs.server42-storm-notify.priv")
    String getFlowHsServer42StormNotifyTopic();

    @Key("topo.nbworker.server42-storm")
    @Default("kilda.nbworker.server42-storm-notify.priv")
    String getNbWorkerServer42StormNotifyTopic();

    @Key("topo.flowhs.flow.monitoring")
    @Default("kilda.flowhs.flowmonitoring-notify.priv")
    String getFlowHsFlowMonitoringNotifyTopic();

    @Key("topo.network.flow.monitoring")
    @Default("kilda.network.flowmonitoring-notify.priv")
    String getNetworkFlowMonitoringNotifyTopic();

    @Key("stats.notify.priv")
    @Default("kilda.stats.notify.priv")
    String getFlowStatsNotifyTopic();
}
