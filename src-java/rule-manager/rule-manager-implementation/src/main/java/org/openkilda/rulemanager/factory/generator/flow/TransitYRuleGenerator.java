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

package org.openkilda.rulemanager.factory.generator.flow;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
public class TransitYRuleGenerator extends TransitRuleGenerator implements MeteredRuleGenerator {
    protected final MeterId sharedMeterId;
    protected RuleManagerConfig config;


    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        if (flowPath.isOneSwitchFlow()) {
            return new ArrayList<>();
        }

        List<SpeakerCommandData> result = new ArrayList<>();
        SpeakerCommandData command = buildTransitCommand(sw, inPort, outPort);
        result.add(command);

        // TODO(tdurakov): since it's shared meter, this build might be moved outside.
        SpeakerCommandData meterCommand = buildMeter(flowPath, config, sharedMeterId, sw);
        if (meterCommand != null) {
            result.add(meterCommand);
            command.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }


    private SpeakerCommandData buildTransitCommand(Switch sw, int inPort, int outPort) {
        Instructions instructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PortOutAction(new PortNumber(outPort))))
                .build();
        if (sharedMeterId != null && sharedMeterId.getValue() != 0L) {
            addMeterToInstructions(sharedMeterId, sw, instructions);
        }
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().yFlow(true).build())
                .table(multiTable ? OfTable.TRANSIT : OfTable.INPUT)
                .priority(Priority.Y_FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(instructions);

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }
}
