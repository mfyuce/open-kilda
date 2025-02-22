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

package org.openkilda.northbound.dto.v2.yflows;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
public class YFlow {
    @JsonProperty("y_flow_id")
    String yFlowId;
    String status;

    YFlowSharedEndpoint sharedEndpoint;

    long maximumBandwidth;
    String pathComputationStrategy;
    String encapsulationType;
    Long maxLatency;
    Long maxLatencyTier2;
    boolean ignoreBandwidth;
    boolean periodicPings;
    boolean pinned;
    Integer priority;
    boolean strictBandwidth;
    String description;

    @JsonProperty("y_point")
    SwitchId yPoint;
    @JsonProperty("protected_path_y_point")
    SwitchId protectedPathYPoint;

    List<SubFlow> subFlows;

    String timeCreate;
    String timeUpdate;
}
