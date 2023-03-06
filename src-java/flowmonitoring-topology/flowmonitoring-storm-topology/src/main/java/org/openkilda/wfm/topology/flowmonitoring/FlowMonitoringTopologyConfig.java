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

package org.openkilda.wfm.topology.flowmonitoring;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

public interface FlowMonitoringTopologyConfig extends AbstractTopologyConfig {
    default String getKafkaFlowHsToFlowMonitoringTopic() {
        return getKafkaTopics().getFlowHsFlowMonitoringNotifyTopic();
    }

    default String getServer42StatsFlowRttTopic() {
        return getKafkaTopics().getServer42StatsFlowRttTopic();
    }

    default String getTopoIslLatencyTopic() {
        return getKafkaTopics().getTopoIslLatencyTopic();
    }

    default String getNetworkFlowMonitoringNotifyTopic() {
        return getKafkaTopics().getNetworkFlowMonitoringNotifyTopic();
    }

    default String getKafkaOtsdbTopic() {
        return getKafkaTopics().getOtsdbTopic();
    }

    default String getKafkaTopoRerouteTopic() {
        return getKafkaTopics().getTopoRerouteTopic();
    }

    default String getKafkaTopoFlowHsTopic() {
        return getKafkaTopics().getFlowHsTopic();
    }

    @Key("flow.sla.check.interval.seconds")
    @Default("30")
    int getFlowSlaCheckIntervalSeconds();

    /*
     * flow.sla.check.shard.count parameter is needed to distribute load of Flow SLA
     * check operation. Instead of checking of all flows ones in flow.sla.check.interval.seconds
     * flows will be checked by chunks. That is why parameter flow.sla.check.interval.seconds
     * must be divisible by flow.sla.check.shard.count
     */
    @Key("flow.sla.check.shard.count")
    @Default("10")
    int getFlowSlaCheckShardCount();

    @Key("flow.rtt.stats.expiration.seconds")
    @Default("3")
    int getFlowRttStatsExpirationSeconds();

    @Key("isl.rtt.latency.expiration.seconds")
    @Default("10")
    int getIslRttLatencyExpirationSeconds();

    @Key("opentsdb.metric.prefix")
    @Default("kilda.")
    String getMetricPrefix();

    @Key("flow.latency.sla.timeout.seconds")
    @Default("90")
    int getFlowLatencySlaTimeoutSeconds();

    @Key("flow.latency.sla.threshold.percent")
    @Default("0.05")
    float getFlowLatencySlaThresholdPercent();
}
