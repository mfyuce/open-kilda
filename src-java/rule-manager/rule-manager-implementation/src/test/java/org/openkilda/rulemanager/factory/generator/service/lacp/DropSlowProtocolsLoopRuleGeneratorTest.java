/* Copyright 2022 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.service.lacp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.rulemanager.Constants.Priority.DROP_LOOP_SLOW_PROTOCOLS_PRIORITY;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.ServiceCookie;
import org.openkilda.model.cookie.ServiceCookie.ServiceCookieTag;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DropSlowProtocolsLoopRuleGeneratorTest {
    private DropSlowProtocolsLoopRuleGenerator generator;

    @Before
    public void setup() {
        generator = DropSlowProtocolsLoopRuleGenerator.builder().build();
    }

    @Test
    public void shouldBuildCorrectRule() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new ServiceCookie(ServiceCookieTag.DROP_SLOW_PROTOCOLS_LOOP_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(DROP_LOOP_SLOW_PROTOCOLS_PRIORITY, flowCommandData.getPriority());
        assertEquals(Instructions.builder().build(), flowCommandData.getInstructions());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.SLOW_PROTOCOLS).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(sw.getSwitchId().toLong()).build(),
                FieldMatch.builder().field(Field.ETH_DST).value(MacAddress.SLOW_PROTOCOLS.toLong()).build());
        assertEqualsMatch(expectedMatch, flowCommandData.getMatch());
    }
}
