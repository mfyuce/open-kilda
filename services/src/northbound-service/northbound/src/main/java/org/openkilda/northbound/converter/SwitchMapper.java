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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.messaging.payload.history.PortHistoryPayload;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;
import org.openkilda.model.of.MeterSchemaBand;
import org.openkilda.model.validate.FlowSegmentReference;
import org.openkilda.model.validate.OfFlowReference;
import org.openkilda.model.validate.ValidateDefaultOfFlowsReport;
import org.openkilda.model.validate.ValidateDefect;
import org.openkilda.model.validate.ValidateFlowSegmentReport;
import org.openkilda.model.validate.ValidateOfFlowDefect;
import org.openkilda.model.validate.ValidateOfMeterDefect;
import org.openkilda.model.validate.ValidateSwitchReport;
import org.openkilda.northbound.dto.v1.switches.MeterInfoDto;
import org.openkilda.northbound.dto.v1.switches.MeterMisconfiguredInfoDto;
import org.openkilda.northbound.dto.v1.switches.MetersSyncDto;
import org.openkilda.northbound.dto.v1.switches.MetersValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mapper(componentModel = "spring", uses = {FlowMapper.class}, imports = {Date.class})
public abstract class SwitchMapper {

    @Mapping(source = "ofDescriptionManufacturer", target = "manufacturer")
    @Mapping(source = "ofDescriptionHardware", target = "hardware")
    @Mapping(source = "ofDescriptionSoftware", target = "software")
    @Mapping(source = "ofDescriptionSerialNumber", target = "serialNumber")
    @Mapping(source = "status", target = "state")
    @Mapping(source = "socketAddress.address.hostAddress", target = "address")
    @Mapping(source = "socketAddress.port", target = "port")
    public abstract SwitchDto toSwitchDto(Switch data);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation().stream()"
                       + ".map(e -> e.toString().toLowerCase()).collect(java.util.stream.Collectors.toList()))")
    public abstract SwitchPropertiesDto map(org.openkilda.messaging.model.SwitchPropertiesDto entry);

    @Mapping(target = "supportedTransitEncapsulation",
            expression = "java(entry.getSupportedTransitEncapsulation().stream()"
                    + ".map(e-> org.openkilda.messaging.payload.flow.FlowEncapsulationType.valueOf(e.toUpperCase()))"
                    + ".collect(java.util.stream.Collectors.toSet()))")
    public abstract org.openkilda.messaging.model.SwitchPropertiesDto map(SwitchPropertiesDto entry);

    @Mapping(source = "upEventsCount", target = "upCount")
    @Mapping(source = "downEventsCount", target = "downCount")
    @Mapping(target = "date", expression = "java(Date.from(response.getTime()))")
    public abstract PortHistoryResponse map(PortHistoryPayload response);

    /**
     * Decode {@code SwitchSyncResponse} into {@code RulesSyncResult}.
     */
    public RulesSyncResult toRulesSyncResult(SwitchSyncResponse response) {
        RulesValidationDto rules = toRulesValidationDto(response.getValidateReport());

        // sync switch action can't produce partial success response (at least now)
        return new RulesSyncResult(
                rules.getMissing(), rules.getProper(), rules.getExcess(),
                response.isSuccess() ? rules.getMissing() : Collections.emptyList());
    }

    public SwitchSyncResult toSwitchSyncResult(SwitchSyncResponse response) {
        return new SwitchSyncResult(toRulesSyncDto(response), toMetersSyncDto(response));
    }

    /**
     * Decode {@code SwitchSyncResponse} into {@code RulesSyncDto}.
     */
    public RulesSyncDto toRulesSyncDto(SwitchSyncResponse response) {
        RulesValidationDto rules = toRulesValidationDto(response.getValidateReport());
        return new RulesSyncDto(
                rules.getMissing(), Collections.emptyList(), rules.getProper(), rules.getExcess(),
                response.isSuccess() ? rules.getMissing() : Collections.emptyList(),
                response.isSuccess() ? rules.getExcess() : Collections.emptyList());
    }

    /**
     * Decode {@code SwitchSyncResponse} into {@code MetersSyncDto}.
     */
    public MetersSyncDto toMetersSyncDto(SwitchSyncResponse response) {
        MetersValidationDto meters = toMetersValidationDto(response.getValidateReport());
        return new MetersSyncDto(
                meters.getMissing(), meters.getMisconfigured(), meters.getProper(), meters.getExcess(),
                response.isSuccess() ? meters.getMissing() : Collections.emptyList(),
                response.isSuccess() ? meters.getExcess() : Collections.emptyList());
    }

    @Mapping(source = "report", target = "rules")
    @Mapping(source = "report", target = "meters")
    public abstract SwitchValidationResult toSwitchValidationResult(SwitchValidationResponse response);

    public RulesValidationResult toRulesValidationResult(SwitchValidationResponse response) {
        RulesValidationDto rules = toRulesValidationDto(response.getReport());
        return new RulesValidationResult(rules.getMissing(), rules.getProper(), rules.getExcess());
    }

    /**
     * Decode {@code ValidateSwitchReport} into {@code RulesValidationDto}.
     */
    public RulesValidationDto toRulesValidationDto(ValidateSwitchReport report) {
        ArrayList<Long> excess = new ArrayList<>(lookupTableZeroCookies(report.getExcessOfFlows()));
        RulesValidationDto result = new RulesValidationDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), excess);

        for (ValidateFlowSegmentReport segmentReport : report.getSegmentReports()) {
            result.getProper().addAll(lookupTableZeroCookies(segmentReport.getProperOfFlows()));
            for (ValidateDefect defect : segmentReport.getDefects()) {
                collectOfFlowDefects(result, defect);
            }
        }

        ValidateDefaultOfFlowsReport defaultOfFlowReport = report.getDefaultFlowsReport();
        result.getProper().addAll(lookupTableZeroCookies(defaultOfFlowReport.getProperOfFlows()));
        for (ValidateDefect defect : defaultOfFlowReport.getDefects()) {
            collectOfFlowDefects(result, defect);
        }

        return result;
    }

    /**
     * Decode {@code ValidateSwitchReport} into {@code MetersValidationDto}.
     */
    public MetersValidationDto toMetersValidationDto(ValidateSwitchReport report) {
        List<MeterInfoDto> excess = report.getExcessMeters().stream()
                .map(entry -> toMeterInfoDto(null, entry))
                .collect(Collectors.toList());
        MetersValidationDto result = new MetersValidationDto(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), excess);

        for (ValidateFlowSegmentReport segment : report.getSegmentReports()) {
            result.getProper().addAll(
                    segment.getProperMeters().stream()
                    .map(entry -> toMeterInfoDto(entry, entry))
                    .collect(Collectors.toList()));
            for (ValidateDefect defect : segment.getDefects()) {
                collectOfMeterDefects(result, segment.getSegmentRef(), defect);
            }
        }

        ValidateDefaultOfFlowsReport defaultFlowsReport = report.getDefaultFlowsReport();
        result.getProper().addAll(
                defaultFlowsReport.getProperMeters().stream()
                        .map(entry -> toMeterInfoDto(entry, entry))
                        .collect(Collectors.toList()));
        for (ValidateDefect defect : defaultFlowsReport.getDefects()) {
            collectOfMeterDefects(result, null, defect);
        }
        return result;
    }

    /**
     * Decode {@code MeterSchema} into {@code MeterInfoDto}.
     */
    public MeterInfoDto toMeterInfoDto(MeterSchema expected, MeterSchema actual) {
        MeterSchema schema = actual;
        if (schema == null) {
            schema = expected;
        }
        if (schema == null) {
            throw new IllegalArgumentException(String.format(
                    "Can't make %s because both actual and expected arguments are null", MeterInfoDto.class.getName()));
        }

        MeterInfoDto result = new MeterInfoDto();
        result.setMeterId(schema.getMeterId().getValue());
        result.setRate(getMeterSchemaRate(schema));
        result.setBurstSize(getMeterSchemaBurstSize(schema));
        result.setFlags(schema.getFlags().toArray(new String[0]));

        if (expected != null) {
            result.setExpected(toMeterMisconfiguredInfoDto(expected));
        }
        if (actual != null) {
            result.setActual(toMeterMisconfiguredInfoDto(actual));
        }

        return result;
    }

    /**
     * Decode {@code MeterSchema} into {@code MeterMisconfiguredInfoDto}.
     */
    public MeterMisconfiguredInfoDto toMeterMisconfiguredInfoDto(MeterSchema schema) {
        MeterMisconfiguredInfoDto result = new MeterMisconfiguredInfoDto();
        result.setRate(getMeterSchemaRate(schema));
        result.setBurstSize(getMeterSchemaBurstSize(schema));
        result.setFlags(schema.getFlags().toArray(new String[0]));
        return result;
    }

    public String toSwitchId(SwitchId switchId) {
        return switchId.toString();
    }

    private void collectOfFlowDefects(RulesValidationDto result, ValidateDefect defect) {
        if (! defect.getFlow().isPresent()) {
            return;
        }
        ValidateOfFlowDefect flowDefect = defect.getFlow().get();
        if (flowDefect.getReference().getTableId() != 0) {
            return;
        }

        long cookie = flowDefect.getReference().getCookie().getValue();
        if (flowDefect.isMissing()) {
            result.getMissing().add(cookie);
        } else if (flowDefect.isExcess()) {
            result.getExcess().add(cookie);
        } else if (flowDefect.isMismatch()) {
            result.getMissing().add(cookie);
        } else {
            throw makeUnsupportedDefectException(flowDefect);
        }
    }

    private void collectOfMeterDefects(
            MetersValidationDto result, FlowSegmentReference segmentRef, ValidateDefect defect) {
        if (! defect.getMeter().isPresent()) {
            return;
        }

        ValidateOfMeterDefect meterDefect = defect.getMeter().get();
        MeterInfoDto meterInfo = null;
        if (meterDefect.isMissing()) {
            meterInfo = toMeterInfoDto(meterDefect.getExpected(), null);
            result.getMissing().add(extendMeterInfoWithSegmentReference(segmentRef, meterInfo));
        } else if (meterDefect.isExcess()) {
            meterInfo = toMeterInfoDto(null, meterDefect.getActual());
            result.getExcess().add(meterInfo);
        } else if (meterDefect.isMismatch()) {
            meterInfo = toMeterInfoDto(meterDefect.getExpected(), meterDefect.getActual());
            result.getMisconfigured().add(extendMeterInfoWithSegmentReference(segmentRef, meterInfo));
        } else {
            throw makeUnsupportedDefectException(meterDefect);
        }
    }

    private MeterInfoDto extendMeterInfoWithSegmentReference(FlowSegmentReference ref, MeterInfoDto meterInfo) {
        if (ref != null) {
            meterInfo.setFlowId(ref.getFlowId());
            meterInfo.setCookie(ref.getCookie().getValue());
        }
        return meterInfo;
    }

    private Long getMeterSchemaRate(MeterSchema schema) {
        Long value = null;
        for (MeterSchemaBand band : schema.getBands()) {
            value = band.getRate();
            if (value != null) {
                break;
            }
        }
        return value;
    }

    private Long getMeterSchemaBurstSize(MeterSchema schema) {
        Long value = null;
        for (MeterSchemaBand band : schema.getBands()) {
            value = band.getBurstSize();
            if (value != null) {
                break;
            }
        }
        return value;
    }

    private List<Long> lookupTableZeroCookies(List<OfFlowReference> references) {
        return lookupTableZeroCookies(references.stream());
    }

    private List<Long> lookupTableZeroCookies(Stream<OfFlowReference> references) {
        return references.filter(entry -> entry.getTableId() == 0)
                .map(entry -> entry.getCookie().getValue())
                .collect(Collectors.toList());
    }

    private IllegalArgumentException makeUnsupportedDefectException(Object defect) {
        return new IllegalArgumentException(String.format("Unsupported defect kind: %s", defect));
    }
}
