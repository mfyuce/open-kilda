package org.openkilda.functionaltests.helpers

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.otsdb.OtsdbQueryService
import org.openkilda.testing.tools.SoftAssertions

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StatsHelper {
    private static int STATS_INTERVAL = 10

    @Autowired
    OtsdbQueryService otsdb
    @Autowired
    Database database
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    void verifyFlowsWriteStats(List<String> flowIds) {
        def soft = new SoftAssertions()
        withPool(Math.min(flowIds.size(), 15)) {
            flowIds.eachParallel { String flowId ->
                soft.checkSucceeds { verifyFlowWritesStats(flowId) }
            }
        }
        soft.verify()
    }

    //TODO (dpoltavets): Need to revise approach of verifying flow writes stats due to changes in pings
    void verifyFlowWritesStats(String flowId, boolean pingFlow = true) {
        def beforePing = new Date()
        pingFlow && northbound.pingFlow(flowId, new PingInput())
        verifyFlowWritesStats(flowId, beforePing, pingFlow)
    }

    void verifyFlowWritesStats(String flowId, Date from, boolean expectTraffic) {
        Wrappers.wait(STATS_INTERVAL) {
            def dps = otsdb.query(from, metricPrefix + "flow.raw.bytes", [flowid: flowId]).dps
            if(expectTraffic) {
                assert dps.values().any { it > 0 }, flowId
            } else {
                assert dps.size() > 0, flowId
            }
        }
    }

    void verifyYFlowWritesMeterStats(YFlow yFlow, Date from, boolean expectTraffic) {
        Wrappers.wait(STATS_INTERVAL) {
            def yFlowId = yFlow.YFlowId
            def dpsShared = otsdb.query(from, metricPrefix + "yFlow.meter.shared.bytes", ["y_flow_id": yFlowId]).dps
            def dpsYPoint = otsdb.query(from, metricPrefix + "yFlow.meter.yPoint.bytes", ["y_flow_id": yFlowId]).dps
            if (expectTraffic) {
                assert dpsShared.values().any { it > 0 }, yFlowId
                assert dpsYPoint.values().any { it > 0 }, yFlowId
            } else {
                assert dpsShared.size() > 0, yFlowId
                assert dpsYPoint.size() > 0, yFlowId
            }
        }
    }
}
