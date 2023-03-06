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

package org.openkilda.rulemanager.factory.generator.service.server42;

import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_ISL_RTT_OUTPUT_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_ISL_RTT_REVERSE_UDP_PORT;
import static org.openkilda.rulemanager.Field.ETH_DST;
import static org.openkilda.rulemanager.Field.ETH_SRC;

import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Server42IslRttOutputRuleGenerator implements RuleGenerator {

    private final RuleManagerConfig config;
    private final int server42Port;
    private final int server42Vlan;
    private final MacAddress server42MacAddress;

    @Builder
    public Server42IslRttOutputRuleGenerator(
            RuleManagerConfig config, int server42Port, int server42Vlan, MacAddress server42MacAddress) {
        this.config = config;
        this.server42Port = server42Port;
        this.server42Vlan = server42Vlan;
        this.server42MacAddress = server42MacAddress;
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {

        List<Action> actions = new ArrayList<>();
        if (server42Vlan > 0) {
            actions.add(new PushVlanAction());
            actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(server42Vlan).build());
        }

        actions.add(SetFieldAction.builder().field(ETH_SRC).value(sw.getSwitchId().toMacAddressAsLong()).build());
        actions.add(SetFieldAction.builder().field(ETH_DST).value(server42MacAddress.toLong()).build());
        actions.add(new PortOutAction(new PortNumber(server42Port)));

        Instructions instructions = Instructions.builder().applyActions(actions).build();
        Set<FieldMatch> match = buildMatch(new MacAddress(config.getServer42IslRttMagicMacAddress()));

        return Collections.singletonList(FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new Cookie(SERVER_42_ISL_RTT_OUTPUT_COOKIE))
                .table(OfTable.INPUT)
                .priority(SERVER_42_ISL_RTT_OUTPUT_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build());
    }

    private static Set<FieldMatch> buildMatch(MacAddress ethDstMacAddress) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(ethDstMacAddress.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(SERVER_42_ISL_RTT_REVERSE_UDP_PORT).build()
        );
    }
}
