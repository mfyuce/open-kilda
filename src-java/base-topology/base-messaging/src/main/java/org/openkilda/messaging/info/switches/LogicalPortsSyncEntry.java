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

package org.openkilda.messaging.info.switches;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class LogicalPortsSyncEntry implements Serializable {
    private List<LogicalPortInfoEntry> missing;
    private List<LogicalPortInfoEntry> misconfigured;
    private List<LogicalPortInfoEntry> proper;
    private List<LogicalPortInfoEntry> excess;
    private List<LogicalPortInfoEntry> installed;
    private List<LogicalPortInfoEntry> removed;
    private String error;
}
