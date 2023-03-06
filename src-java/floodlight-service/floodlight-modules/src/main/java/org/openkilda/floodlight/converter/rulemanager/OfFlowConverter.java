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

package org.openkilda.floodlight.converter.rulemanager;

import static java.lang.String.format;

import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
@Slf4j
public abstract class OfFlowConverter {
    public static final OfFlowConverter INSTANCE = Mappers.getMapper(OfFlowConverter.class);

    /**
     * Convert stats reply.
     */
    public List<FlowSpeakerData> convertToFlowSpeakerData(OFFlowStatsReply statsReply, SwitchId switchId) {
        return statsReply.getEntries().stream()
                .map(entry -> convertToFlowSpeakerData(entry, switchId))
                .collect(Collectors.toList());
    }

    /**
     * Convert stats entry.
     */
    public FlowSpeakerData convertToFlowSpeakerData(OFFlowStatsEntry entry, SwitchId switchId) {
        return FlowSpeakerData.builder()
                .switchId(switchId)
                .ofVersion(OfVersion.of(entry.getVersion().name()))
                .cookie(new Cookie(entry.getCookie().getValue()))
                .flags(convertToRuleManagerFlags(entry.getFlags()))
                .priority(entry.getPriority())
                .table(OfTable.fromInt(entry.getTableId().getValue()))
                .match(OfMatchConverter.INSTANCE.convertToRuleManagerMatch(entry.getMatch()))
                .instructions(
                        OfInstructionsConverter.INSTANCE.convertToRuleManagerInstructions(entry.getInstructions()))
                .durationSeconds(entry.getDurationSec())
                .durationNanoSeconds(entry.getDurationNsec())
                .packetCount(entry.getPacketCount().getValue())
                .idleTimeout(entry.getIdleTimeout())
                .hardTimeout(entry.getHardTimeout())
                .byteCount(entry.getByteCount().getValue())
                .build();
    }

    /**
     * Convert flow speaker command data into OfFlowMod representation.
     */
    public OFFlowMod convertInstallFlowCommand(FlowSpeakerData commandData, OFFactory ofFactory) {
        return setupBuilder(ofFactory.buildFlowAdd(), commandData, ofFactory)
                .build();
    }

    /**
     * Convert flow speaker command data into OfFlowMod representation for Flow modify.
     */
    public OFFlowMod convertModifyFlowCommand(FlowSpeakerData commandData, OFFactory ofFactory) {
        return setupBuilder(ofFactory.buildFlowModify(), commandData, ofFactory)
                .build();
    }

    private OFFlowMod.Builder setupBuilder(OFFlowMod.Builder builder,
                                           FlowSpeakerData commandData, OFFactory ofFactory) {
        return builder.setCookie(U64.of(commandData.getCookie().getValue()))
                .setTableId(TableId.of(commandData.getTable().getTableId()))
                .setPriority(commandData.getPriority())
                .setMatch(OfMatchConverter.INSTANCE.convertMatch(commandData.getMatch(), ofFactory))
                .setInstructions(
                        OfInstructionsConverter.INSTANCE.convertInstructions(commandData.getInstructions(), ofFactory))
                .setFlags(convertToOfFlags(commandData.getFlags()));

    }

    /**
     * Convert Flow Delete Command.
     *
     * @param commandData data
     * @param ofFactory factory
     * @return mod
     */
    public OFFlowMod convertDeleteFlowCommand(FlowSpeakerData commandData, OFFactory ofFactory) {
        return ofFactory.buildFlowDeleteStrict()
                .setCookie(U64.of(commandData.getCookie().getValue()))
                .setCookieMask(U64.NO_MASK)
                .setTableId(TableId.of(commandData.getTable().getTableId()))
                .setPriority(commandData.getPriority())
                .setMatch(OfMatchConverter.INSTANCE.convertMatch(commandData.getMatch(), ofFactory))
                .build();
    }

    private Set<OFFlowModFlags> convertToOfFlags(Set<OfFlowFlag> flags) {
        Set<OFFlowModFlags> ofFlowModFlags = new HashSet<>();
        for (OfFlowFlag flag : flags) {
            switch (flag) {
                case RESET_COUNTERS:
                    ofFlowModFlags.add(OFFlowModFlags.RESET_COUNTS);
                    break;
                default:
                    throw new IllegalStateException(format("Unknown flow mod flag %s", flag));
            }
        }
        return ofFlowModFlags;
    }

    private Set<OfFlowFlag> convertToRuleManagerFlags(Set<OFFlowModFlags> flags) {
        Set<OfFlowFlag> flowModFlags = new HashSet<>();
        for (OFFlowModFlags flag : flags) {
            switch (flag) {
                case RESET_COUNTS:
                    flowModFlags.add(OfFlowFlag.RESET_COUNTERS);
                    break;
                default:
                    throw new IllegalStateException(format("Unknown flow mod flag %s", flag));
            }
        }
        return flowModFlags;
    }
}
