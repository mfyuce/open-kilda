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

package org.openkilda.rulemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.buildSwitchProperties;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchProperties;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BfdCatchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BroadCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.DropDiscoveryLoopRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.RoundTripLatencyRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TableDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UniCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UnicastVerificationVxlanRuleGenerator;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RuleManagerServiceRulesTest {

    private RuleManagerImpl ruleManager;

    @Before
    public void setup() {
        RuleManagerConfig config = mock(RuleManagerConfig.class);
        when(config.getBroadcastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getFlowPingMagicSrcMacAddress()).thenReturn("00:26:E1:FF:FF:FE");
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");

        ruleManager = new RuleManagerImpl(config);
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInSingleTableMode() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchProperties switchProperties = buildSwitchProperties(sw, false);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(switchProperties);

        assertEquals(7, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof TableDefaultRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropDiscoveryLoopRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BfdCatchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof RoundTripLatencyRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UnicastVerificationVxlanRuleGenerator));
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInMultiTableMode() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchProperties switchProperties = buildSwitchProperties(sw, true);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(switchProperties);

        assertEquals(10, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropDiscoveryLoopRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BfdCatchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof RoundTripLatencyRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UnicastVerificationVxlanRuleGenerator));

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
    }
}
