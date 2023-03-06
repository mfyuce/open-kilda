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

package org.openkilda.rulemanager.factory.generator.service.lldp;

import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.List;
import java.util.Set;

public class LldpPostIngressOneSwitchRuleGenerator extends LldpRuleGenerator {

    @Builder
    public LldpPostIngressOneSwitchRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        RoutingMetadata metadata = makeMetadataMatch(sw);
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build()
        );

        Instructions instructions = buildSendToControllerInstructions();

        Cookie cookie = new Cookie(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE);

        return buildCommands(sw, cookie, OfTable.POST_INGRESS, Priority.LLDP_POST_INGRESS_ONE_SWITCH_PRIORITY,
                match, instructions);
    }

    private RoutingMetadata makeMetadataMatch(Switch sw) {
        return buildMetadata(RoutingMetadata.builder()
                .oneSwitchFlowFlag(true)
                .lldpFlag(true), sw);
    }
}
