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

package org.openkilda.wfm.topology.network.storm.bolt.speaker.command;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRulesWorker;

public class ProcessSpeakerRulesCommand extends SpeakerRulesWorkerCommand {

    private final BaseSpeakerCommandsRequest request;

    public ProcessSpeakerRulesCommand(BaseSpeakerCommandsRequest request) {
        super(request.getCommandId().toString());
        this.request = request;
    }

    @Override
    public void apply(SpeakerRulesWorker handler) {
        handler.processIslRulesRequest(getKey(), request);
    }

    @Override
    public void timeout(SpeakerRulesWorker handler) {
        handler.timeoutIslRuleRequest(getKey(), request);
    }
}
