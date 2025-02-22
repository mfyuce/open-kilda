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

package org.openkilda.messaging.nbtopology.response;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class FlowValidationResponse extends InfoData {

    String flowId;
    Boolean asExpected;
    List<Long> pktCounts;
    List<Long> byteCounts;
    List<PathDiscrepancyEntity> discrepancies;
    Integer flowRulesTotal;
    Integer switchRulesTotal;
    Integer flowMetersTotal;
    Integer switchMetersTotal;
    Boolean ingressMirrorFlowIsPresent;
    Boolean egressMirrorFlowIsPresent;
}
