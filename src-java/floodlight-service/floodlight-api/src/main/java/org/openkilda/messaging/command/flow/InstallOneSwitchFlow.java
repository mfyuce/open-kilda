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
 * Class represents flow through one switch installation info.
 * Input and output vlan ids are optional, because flow could be untagged on ingoing or outgoing side.
 * Output action depends on flow input and output vlan presence.
 * Bandwidth and meter id are used for flow throughput limitation.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "input_port",
        "output_port",
        "input_vlan_id",
        "output_vlan_id",
        "output_vlan_type",
        "bandwidth",
        "meter_id"})
public class InstallOneSwitchFlow extends BaseInstallFlow {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Allocated meter id.
     */
    @JsonProperty("meter_id")
    protected Long meterId;

    @JsonProperty("bandwidth")
    @Getter
    private long bandwidth;

    /**
     * Output action on the vlan tag.
     */
    @JsonProperty("output_vlan_type")
    private OutputVlanType outputVlanType;

    /**
     * Optional input vlan id value.
     */
    @JsonProperty("input_vlan_id")
    @Getter
    private int inputVlanId;

    @JsonProperty("input_inner_vlan_id")
    @Getter
    protected int inputInnerVlanId;

    /**
     * Optional output vlan id value.
     */
    @JsonProperty("output_vlan_id")
    @Getter
    private int outputVlanId;

    @JsonProperty("output_inner_vlan_id")
    @Getter
    protected int outputInnerVlanId;

    /**
     * LLDP flag. Packets will be send to LLDP rule if True.
     */
    @JsonProperty("enable_lldp")
    private boolean enableLldp;

    /**
     * ARP flag. Packets will be send to ARP rule if True.
     */
    @JsonProperty("enable_arp")
    private boolean enableArp;

    @JsonProperty("mirror_config")
    @Getter @Setter
    protected MirrorConfig mirrorConfig;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id id of the flow
     * @param cookie flow cookie
     * @param switchId switch ID for flow installation
     * @param inputPort input port of the flow
     * @param outputPort output port of the flow
     * @param inputVlanId input vlan id value
     * @param inputInnerVlanId input inner vlan id
     * @param outputVlanId output vlan id value
     * @param outputInnerVlanId output inner vlan id
     * @param outputVlanType output vlan tag action
     * @param bandwidth flow bandwidth
     * @param meterId source meter id
     * @param enableLldp install LLDP shared rule if True
     * @param enableArp install ARP shared rule if True
     * @param mirrorConfig   flow mirror config
     * @throws IllegalArgumentException if any of arguments is null
     */
    @JsonCreator
    public InstallOneSwitchFlow(@JsonProperty(TRANSACTION_ID) final UUID transactionId,
                                @JsonProperty(FLOW_ID) final String id,
                                @JsonProperty("cookie") final Long cookie,
                                @JsonProperty("switch_id") final SwitchId switchId,
                                @JsonProperty("input_port") final Integer inputPort,
                                @JsonProperty("output_port") final Integer outputPort,
                                @JsonProperty("input_vlan_id") final Integer inputVlanId,
                                @JsonProperty("input_inner_vlan_id") Integer inputInnerVlanId,
                                @JsonProperty("output_vlan_id") final Integer outputVlanId,
                                @JsonProperty("output_inner_vlan_id") Integer outputInnerVlanId,
                                @JsonProperty("output_vlan_type") final OutputVlanType outputVlanType,
                                @JsonProperty("bandwidth") final Long bandwidth,
                                @JsonProperty("meter_id") final Long meterId,
                                @JsonProperty("multi_table") final boolean multiTable,
                                @JsonProperty("enable_lldp") final boolean enableLldp,
                                @JsonProperty("enable_arp") final boolean enableArp,
                                @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(transactionId, id, cookie, switchId, inputPort, outputPort, multiTable);
        setInputVlanId(inputVlanId);
        setInputInnerVlanId(inputInnerVlanId);
        setOutputVlanId(outputVlanId);
        setOutputInnerVlanId(outputInnerVlanId);
        setOutputVlanType(outputVlanType);
        setBandwidth(bandwidth);
        setMeterId(meterId);
        setEnableLldp(enableLldp);
        setEnableArp(enableArp);
        setMirrorConfig(mirrorConfig);
    }

    /**
     * Sets flow bandwidth value.
     *
     * @param bandwidth bandwidth value
     */
    public void setBandwidth(final Long bandwidth) {
        if (bandwidth == null) {
            throw new IllegalArgumentException("need to set bandwidth");
        } else if (bandwidth < 0L) {
            throw new IllegalArgumentException("need to set non negative bandwidth");
        }
        this.bandwidth = bandwidth;
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

    public void setInputVlanId(final Integer value) {
        inputVlanId = adaptVlanId(value);
    }

    public void setInputInnerVlanId(Integer value) {
        inputInnerVlanId = adaptVlanId(value);
    }

    public void setOutputVlanId(final Integer value) {
        outputVlanId = adaptVlanId(value);
    }

    public void setOutputInnerVlanId(Integer value) {
        outputInnerVlanId = adaptVlanId(value);
    }

    /**
     * Returns meter id for the flow.
     *
     * @return meter id for the flow
     */
    public Long getMeterId() {
        return meterId;
    }

    /**
     * Sets meter id for the flow.
     *
     * @param meterId meter id for the flow
     */
    public void setMeterId(final Long meterId) {
        if (meterId != null && meterId <= 0L) {
            throw new IllegalArgumentException("Meter id value should be positive");
        }
        this.meterId = meterId;
    }

    /**
     * Get enable LLDP flag.
     */
    public boolean isEnableLldp() {
        return enableLldp;
    }

    /**
     * Set enable LLDP flag.
     */
    public void setEnableLldp(boolean enableLldp) {
        this.enableLldp = enableLldp;
    }

    /**
     * Get enable ARP flag.
     */
    public boolean isEnableArp() {
        return enableArp;
    }

    /**
     * Set enable ARP flag.
     */
    public void setEnableArp(boolean enableArp) {
        this.enableArp = enableArp;
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
                .add("input_vlan_id", inputVlanId)
                .add("input_inner_vlan_id", inputInnerVlanId)
                .add("output_vlan_id", outputVlanId)
                .add("output_inner_vlan_id", outputInnerVlanId)
                .add("output_vlan_type", outputVlanType)
                .add("bandwidth", bandwidth)
                .add("meter_id", meterId)
                .add("multi_table", multiTable)
                .add("enable_lldp", enableLldp)
                .add("enable_arp", enableArp)
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

        InstallOneSwitchFlow that = (InstallOneSwitchFlow) object;
        return Objects.equals(getTransactionId(), that.getTransactionId())
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getCookie(), that.getCookie())
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getInputPort(), that.getInputPort())
                && Objects.equals(getOutputPort(), that.getOutputPort())
                && Objects.equals(getInputVlanId(), that.getInputVlanId())
                && Objects.equals(getInputInnerVlanId(), that.getInputInnerVlanId())
                && Objects.equals(getOutputVlanId(), that.getOutputVlanId())
                && Objects.equals(getOutputInnerVlanId(), that.getOutputInnerVlanId())
                && Objects.equals(getOutputVlanType(), that.getOutputVlanType())
                && Objects.equals(getBandwidth(), that.getBandwidth())
                && Objects.equals(getMeterId(), that.getMeterId())
                && Objects.equals(isMultiTable(), that.isMultiTable())
                && Objects.equals(isEnableLldp(), that.isEnableLldp())
                && Objects.equals(isEnableArp(), that.isEnableArp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId, id, cookie, switchId, inputPort, outputPort,
                inputVlanId, inputInnerVlanId, outputVlanId, outputInnerVlanId, outputVlanType, bandwidth, meterId,
                multiTable, enableLldp, enableArp);
    }
}
