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

package org.openkilda.wfm.share.utils.rule.validation;

import org.openkilda.model.SwitchId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"pktCount", "byteCount", "version", "ingressRule", "egressRule"})
public class SimpleSwitchRule {
    private SwitchId switchId;
    private long cookie;
    private int inPort;
    private int outPort;
    private int inVlan;
    private int tunnelId;
    @Default
    private List<Integer> outVlan = Collections.emptyList();
    private Long meterId;
    private long pktCount;
    private long byteCount;
    private String version;
    private Long meterRate;
    private Long meterBurstSize;
    private String[] meterFlags;
    private int groupId;
    @Default
    private List<SimpleGroupBucket> groupBuckets = Collections.emptyList();
    private boolean ingressRule;
    private boolean egressRule;

    @Override
    public String toString() {
        String outVlanString = outVlan.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
        if (! outVlanString.isEmpty()) {
            outVlanString = "-" + outVlanString;
        }
        return "{sw:" + switchId
                + ", ck:" + cookie
                + ", in:" + inPort + "-" + inVlan
                + ", out:" + outPort + outVlanString
                + '}';
    }

    @Data
    @AllArgsConstructor
    public static class SimpleGroupBucket {
        private int outPort;
        private int outVlan;
        private int vni;
    }
}
