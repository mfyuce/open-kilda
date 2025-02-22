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

package org.openkilda.messaging.command.flow;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.messaging.Utils;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Class represents egress flow installation info.
 * Transit vlan id is used in matching.
 * Output action depends on flow input and output vlan presence, but should at least contain transit vlan stripping.
 * Output vlan id is optional, because flow could be untagged on outgoing side.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "command",
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "input_port",
        "output_port",
        "transit_encapsulation_id",
        "transit_encapsulation_type",
        "output_vlan_id",
        "output_vlan_type"})
public class InstallEgressFlow extends InstallTransitFlow {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Output action on the vlan tag.
     */
    @JsonProperty("output_vlan_type")
    protected OutputVlanType outputVlanType;

    @JsonProperty("output_vlan_id")
    @Getter
    protected int outputVlanId;

    @JsonProperty("output_inner_vlan_id")
    @Getter
    protected int outputInnerVlanId;

    @JsonProperty("ingress_endpoint")
    @Getter @Setter
    protected FlowEndpoint ingressEndpoint;

    @JsonProperty("mirror_config")
    @Getter @Setter
    protected MirrorConfig mirrorConfig;

    /**
     * Instance constructor.
     *
     * @param transactionId  transaction id
     * @param id             id of the flow
     * @param cookie         flow cookie
     * @param switchId       switch ID for flow installation
     * @param inputPort      input port of the flow
     * @param outputPort     output port of the flow
     * @param transitEncapsulationId  transit encapsulation id value
     * @param transitEncapsulationType  transit encapsulation type value
     * @param outputVlanId   output vlan id value
     * @param outputInnerVlanId output inner vlan id value
     * @param outputVlanType output vlan tag action
     * @param multiTable     multitable flag
     * @param ingressEndpoint ingress flow endpoint
     * @param mirrorConfig   flow mirror config
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public InstallEgressFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                             @JsonProperty(FLOW_ID) final String id,
                             @JsonProperty("cookie") final Long cookie,
                             @JsonProperty("switch_id") final SwitchId switchId,
                             @JsonProperty("input_port") final Integer inputPort,
                             @JsonProperty("output_port") final Integer outputPort,
                             @JsonProperty("transit_encapsulation_id") final Integer transitEncapsulationId,
                             @JsonProperty("transit_encapsulation_type") FlowEncapsulationType transitEncapsulationType,
                             @JsonProperty("output_vlan_id") final Integer outputVlanId,
                             @JsonProperty("output_inner_vlan_id") Integer outputInnerVlanId,
                             @JsonProperty("output_vlan_type") final OutputVlanType outputVlanType,
                             @JsonProperty("multi_table") final boolean multiTable,
                             @JsonProperty("ingress_endpoint") FlowEndpoint ingressEndpoint,
                             @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, transitEncapsulationId,
                transitEncapsulationType, multiTable);
        setOutputVlanId(outputVlanId);
        setOutputInnerVlanId(outputInnerVlanId);
        setOutputVlanType(outputVlanType);
        setIngressEndpoint(ingressEndpoint);
        setMirrorConfig(mirrorConfig);
    }

    /**
     * Returns output action on the vlan tag.
     *
     * @return output action on the vlan tag
     */
    public OutputVlanType getOutputVlanType() {
        return outputVlanType;
    }

    /**
     * Sets output action on the vlan tag.
     *
     * @param outputVlanType action on the vlan tag
     */
    public void setOutputVlanType(final OutputVlanType outputVlanType) {
        if (outputVlanType == null) {
            throw new IllegalArgumentException("need to set output_vlan_type");
        } else if (!Utils.validateOutputVlanType(outputVlanId, outputVlanType)) {
            throw new IllegalArgumentException("need to set valid values for output_vlan_id and output_vlan_type");
        } else {
            this.outputVlanType = outputVlanType;
        }
    }

    public void setOutputVlanId(final Integer value) {
        outputVlanId = adaptVlanId(value);
    }

    public void setOutputInnerVlanId(Integer value) {
        outputInnerVlanId = adaptVlanId(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TRANSACTION_ID, transactionId)
                .add(FLOW_ID, id)
                .add("cookie", cookie)
                .add("switch_id", switchId)
                .add("input_port", inputPort)
                .add("output_port", outputPort)
                .add("transit_encapsulation_id", transitEncapsulationId)
                .add("transit_encapsulation_type", transitEncapsulationType)
                .add("output_vlan_id", outputVlanId)
                .add("output_inner_vlan_id", outputInnerVlanId)
                .add("output_vlan_type", outputVlanType)
                .add("multi_table", multiTable)
                .add("ingress_endpoint", ingressEndpoint)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        InstallEgressFlow that = (InstallEgressFlow) object;
        return Objects.equals(getTransactionId(), that.getTransactionId())
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getCookie(), that.getCookie())
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getInputPort(), that.getInputPort())
                && Objects.equals(getOutputPort(), that.getOutputPort())
                && Objects.equals(getTransitEncapsulationId(), that.getTransitEncapsulationId())
                && Objects.equals(getTransitEncapsulationType(), that.getTransitEncapsulationType())
                && Objects.equals(getOutputVlanId(), that.getOutputVlanId())
                && Objects.equals(getOutputInnerVlanId(), that.getOutputInnerVlanId())
                && Objects.equals(getOutputVlanType(), that.getOutputVlanType())
                && Objects.equals(isMultiTable(), that.isMultiTable())
                && Objects.equals(getIngressEndpoint(), that.getIngressEndpoint());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId, id, cookie, switchId, inputPort, outputPort, transitEncapsulationId,
                transitEncapsulationType, outputVlanType, outputVlanId, outputInnerVlanId, multiTable, ingressEndpoint);
    }
}
