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

package org.openkilda.wfm.topology.flowmonitoring.service;

import static java.lang.String.format;
import static org.openkilda.server42.messaging.FlowDirection.FORWARD;
import static org.openkilda.server42.messaging.FlowDirection.REVERSE;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.model.Flow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowmonitoring.bolt.FlowOperationsCarrier;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Context;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Event;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.FlowLatencyMonitoringFsmFactory;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ActionService implements FlowSlaMonitoringCarrier {

    private static final Set<PathComputationStrategy> LATENCY_BASED_STRATEGIES =
            Sets.newHashSet(PathComputationStrategy.LATENCY, PathComputationStrategy.MAX_LATENCY);

    private final FlowOperationsCarrier carrier;
    private final FlowRepository flowRepository;
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final FlowLatencyMonitoringFsmFactory fsmFactory;
    private final FsmExecutor<FlowLatencyMonitoringFsm, State, Event, Context> fsmExecutor;

    private final int shardCount;

    @VisibleForTesting
    protected Map<FsmKey, FlowLatencyMonitoringFsm> fsms = new HashMap<>();

    public ActionService(FlowOperationsCarrier carrier, PersistenceManager persistenceManager,
                         Clock clock, Duration timeout, float threshold, int shardCount) {
        this.carrier = carrier;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        fsmFactory = FlowLatencyMonitoringFsm.factory(clock, timeout, threshold);
        fsmExecutor = fsmFactory.produceExecutor();
        this.shardCount = shardCount;
    }

    /**
     * Update flow info.
     */
    public void updateFlowInfo(UpdateFlowCommand flowInfo) {
        String flowId = flowInfo.getFlowId();
        fsms.put(getFsmKey(flowId, FORWARD), fsmFactory.produce(flowId, FORWARD.name().toLowerCase(),
                flowInfo.getMaxLatency(), flowInfo.getMaxLatencyTier2()));
        fsms.put(getFsmKey(flowId, REVERSE), fsmFactory.produce(flowId, REVERSE.name().toLowerCase(),
                flowInfo.getMaxLatency(), flowInfo.getMaxLatencyTier2()));
    }

    /**
     * Remove flow info.
     */
    public void removeFlowInfo(String flowId) {
        fsms.remove(getFsmKey(flowId, FORWARD));
        fsms.remove(getFsmKey(flowId, REVERSE));
    }

    /**
     * Check flow SLA is violated.
     */
    public void processFlowLatencyMeasurement(String flowId, FlowDirection direction, Duration latency) {
        FsmKey key = getFsmKey(flowId, direction);
        FlowLatencyMonitoringFsm fsm = fsms.get(key);
        if (fsm == null) {
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new IllegalStateException(format("Flow %s not found.", flowId)));
            long maxLatency = flow.getMaxLatency() == null || flow.getMaxLatency() == 0
                    ? Long.MAX_VALUE : flow.getMaxLatency();
            long maxLatencyTier2 = flow.getMaxLatencyTier2() == null || flow.getMaxLatencyTier2() == 0
                    ? Long.MAX_VALUE : flow.getMaxLatencyTier2();
            fsm = fsmFactory.produce(flowId, direction.name().toLowerCase(), maxLatency, maxLatencyTier2);
            fsms.put(key, fsm);
        }

        Context context = Context.builder()
                .latency(latency.toNanos())
                .carrier(this)
                .build();
        fsm.processLatencyMeasurement(context);
    }

    /**
     * Process tick.
     */
    public void processTick(int shardNumber) {
        Context context = Context.builder()
                .carrier(this)
                .build();
        if (log.isDebugEnabled()) {
            log.debug("Processing flow SLA checks for shard {}", shardNumber);
        }
        for (FsmKey key : fsms.keySet()) {
            if (needToCheckSla(key.flowId.hashCode(), shardNumber)) {
                if (log.isTraceEnabled()) {
                    log.trace("Processing SLA check for flow FSM {}: Shard number: {}", key, shardNumber);
                }
                fsmExecutor.fire(fsms.get(key), Event.TICK, context);
            }
        }
    }

    @VisibleForTesting
    boolean needToCheckSla(int hashCode, int shardNumber) {
        // hashCode can be negative, so we can't use expression `hashCode() % shardCount == shardNumber`
        return (hashCode + shardNumber) % shardCount == 0;
    }

    private FsmKey getFsmKey(String flowId, FlowDirection direction) {
        return new FsmKey(flowId, direction);
    }

    /**
     * Remove all current fsms.
     */
    public void purge() {
        fsms.clear();
    }

    @Override
    public void saveFlowLatency(String flowId, String direction, long latency) {
        carrier.persistFlowStats(flowId, direction, latency);
    }

    @Override
    public void sendFlowSyncRequest(String flowId) {
        Optional<Flow> flow = flowRepository.findById(flowId);
        if (flow.isPresent()) {
            if (LATENCY_BASED_STRATEGIES.contains(flow.get().getPathComputationStrategy()) && isReactionsEnabled()) {
                log.info("Sending flow '{}' sync request.", flowId);
                carrier.sendFlowSyncRequest(flowId);
            }
        } else {
            log.warn("Can't send flow '{}' sync request. Flow not found.", flowId);
        }
    }

    @Override
    public void sendFlowRerouteRequest(String flowId) {
        Optional<Flow> flow = flowRepository.findById(flowId);
        if (flow.isPresent()) {
            if (LATENCY_BASED_STRATEGIES.contains(flow.get().getPathComputationStrategy()) && isReactionsEnabled()) {
                log.info("Sending flow '{}' reroute request.", flowId);
                carrier.sendFlowRerouteRequest(flowId);
            }
        } else {
            log.warn("Can't send flow '{}' reroute request. Flow is not found.", flowId);
        }
    }

    private boolean isReactionsEnabled() {
        return featureTogglesRepository.getOrDefault().getFlowLatencyMonitoringReactions();
    }

    @Value
    private static class FsmKey {
        String flowId;
        FlowDirection direction;
    }
}
