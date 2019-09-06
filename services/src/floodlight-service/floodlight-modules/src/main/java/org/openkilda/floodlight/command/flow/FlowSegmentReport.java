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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.command.SpeakerCommandRemoteReport;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchMissingFlowsException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.FlowErrorResponseBuilder;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.errormsg.OFFlowModFailedErrorMsg;

@Slf4j
public class FlowSegmentReport extends SpeakerCommandRemoteReport {
    private final FlowSegmentCommand segmentCommand;

    protected FlowSegmentReport(FlowSegmentCommand command) {
        this(command, null);
    }

    protected FlowSegmentReport(@NonNull FlowSegmentCommand command, Exception error) {
        super(command, error);
        this.segmentCommand = command;
    }

    @Override
    protected String getReplyTopic(KafkaChannel kafkaChannel) {
        return kafkaChannel.getSpeakerFlowHsTopic();
    }

    @Override
    protected SpeakerResponse assembleResponse() {
        FlowErrorResponseBuilder errorResponse = makeErrorTemplate();
        try {
            raiseError();
            return makeSuccessReply();
        } catch (SwitchNotFoundException e) {
            errorResponse.errorCode(ErrorCode.SWITCH_UNAVAILABLE);
        } catch (SessionErrorResponseException e) {
            decodeError(errorResponse, e.getErrorResponse());
        } catch (SwitchMissingFlowsException e) {
            errorResponse.errorCode(ErrorCode.MISSING_OF_FLOWS);
            errorResponse.description(e.getMessage());
        } catch (SwitchOperationException e) {
            errorResponse.errorCode(ErrorCode.UNKNOWN);
            errorResponse.description(e.getMessage());
        } catch (Exception e) {
            log.error(String.format("Unhandled exception while processing command %s", segmentCommand), e);
            errorResponse.errorCode(ErrorCode.UNKNOWN);
        }

        FlowErrorResponse response = errorResponse.build();
        log.error("Command {} have failed - {}:{}", segmentCommand, response.getErrorCode(), response.getDescription());
        return response;
    }

    @Override
    protected SpeakerResponse makeSuccessReply() {
        log.debug("Command {} successfully completed", segmentCommand);
        return SpeakerFlowSegmentResponse.builder()
                .commandId(segmentCommand.getCommandId())
                .metadata(segmentCommand.getMetadata())
                .messageContext(segmentCommand.getMessageContext())
                .switchId(segmentCommand.getSwitchId())
                .success(true)
                .build();
    }

    private void decodeError(FlowErrorResponseBuilder errorResponse, OFErrorMsg error) {
        if (error instanceof OFFlowModFailedErrorMsg) {
            decodeError(errorResponse, (OFFlowModFailedErrorMsg) error);
        } else {
            log.error("Unable to decode OF error response: {}", error);
            errorResponse.errorCode(ErrorCode.UNKNOWN);
        }
    }

    private void decodeError(FlowErrorResponseBuilder errorResponse, OFFlowModFailedErrorMsg error) {
        switch (error.getCode()) {
            case UNSUPPORTED:
                errorResponse.errorCode(ErrorCode.UNSUPPORTED);
                break;
            case BAD_COMMAND:
                errorResponse.errorCode(ErrorCode.BAD_COMMAND);
                break;
            case BAD_FLAGS:
                errorResponse.errorCode(ErrorCode.BAD_FLAGS);
                break;
            default:
                errorResponse.errorCode(ErrorCode.UNKNOWN);
        }
    }

    private FlowErrorResponse.FlowErrorResponseBuilder makeErrorTemplate() {
        return FlowErrorResponse.errorBuilder()
                .messageContext(segmentCommand.getMessageContext())
                .commandId(segmentCommand.getCommandId())
                .switchId(segmentCommand.getSwitchId())
                .metadata(segmentCommand.getMetadata());
    }
}
