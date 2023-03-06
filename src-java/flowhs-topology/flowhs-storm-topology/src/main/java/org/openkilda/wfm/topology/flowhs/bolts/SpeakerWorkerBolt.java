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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_REQUEST_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandCarrier;
import org.openkilda.wfm.topology.flowhs.service.SpeakerWorkerService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.NonNull;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerBolt extends WorkerBolt implements SpeakerCommandCarrier {

    public static final String ID = "speaker.worker.bolt";
    private transient SpeakerWorkerService service;

    public SpeakerWorkerBolt(@NonNull Config config) {
        super(config);
    }

    @Override
    protected void init() {
        super.init();
        service = new SpeakerWorkerService(this);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        SpeakerRequest command = pullValue(input, FIELD_ID_PAYLOAD, SpeakerRequest.class);
        service.sendCommand(pullKey(), command);
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws PipelineException {
        Object payload = response.getValueByField(FIELD_ID_PAYLOAD);
        if (payload instanceof SpeakerResponse) {
            SpeakerResponse message = (SpeakerResponse) payload;
            service.handleResponse(pullKey(response), message);
        } else {
            log.debug("Unknown response received: {}", payload);
        }
    }

    @Override
    protected void onRequestTimeout(Tuple tuple) throws PipelineException {
        service.handleTimeout(pullKey(tuple));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(SPEAKER_WORKER_REQUEST_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }

    @Override
    public void sendCommand(@NonNull String key, @NonNull SpeakerRequest command) {
        emitWithContext(SPEAKER_WORKER_REQUEST_SENDER.name(), getCurrentTuple(), new Values(key, command));
    }

    @Override
    public void sendResponse(@NonNull String key, @NonNull SpeakerResponse response) {
        Values values = new Values(key, response, getCommandContext());
        emitResponseToHub(getCurrentTuple(), values);
    }
}
