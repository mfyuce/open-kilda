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

import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_ISL_RTT_TURNING_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_ISL_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Constants.SERVER_42_ISL_RTT_REVERSE_UDP_PORT;
import static org.openkilda.rulemanager.Field.UDP_SRC;

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
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Server42IslRttTurningRuleGenerator implements RuleGenerator {

    private final RuleManagerConfig config;

    @Builder
    public Server42IslRttTurningRuleGenerator(RuleManagerConfig config) {
        this.config = config;
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        Set<FieldMatch> match = buildMatch(new MacAddress(config.getServer42IslRttMagicMacAddress()));

        List<Action> actions = new ArrayList<>();
        actions.add(SetFieldAction.builder().field(UDP_SRC).value(SERVER_42_ISL_RTT_REVERSE_UDP_PORT).build());
        actions.add(new PortOutAction(new PortNumber(SpecialPortType.IN_PORT)));

        Instructions instructions = Instructions.builder().applyActions(actions).build();

        return Collections.singletonList(FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new Cookie(SERVER_42_ISL_RTT_TURNING_COOKIE))
                .table(OfTable.INPUT)
                .priority(SERVER_42_ISL_RTT_TURNING_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build());
    }

    private static Set<FieldMatch> buildMatch(MacAddress ethDstMacAddress) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(ethDstMacAddress.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(SERVER_42_ISL_RTT_FORWARD_UDP_PORT).build()
        );
    }
}
