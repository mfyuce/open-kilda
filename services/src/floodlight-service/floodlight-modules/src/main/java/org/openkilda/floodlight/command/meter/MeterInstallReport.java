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

package org.openkilda.floodlight.command.meter;

import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.model.MeterId;
import org.openkilda.model.of.MeterSchema;

import java.util.Optional;

public class MeterInstallReport extends SpeakerCommandReport {
    private final MeterInstallCommand command;
    private final MeterSchema schema;

    public MeterInstallReport(MeterInstallCommand command, MeterSchema schema) {
        this(command, schema, null);
    }

    public MeterInstallReport(MeterInstallCommand command, Exception error) {
        this(command, null, error);
    }

    private MeterInstallReport(MeterInstallCommand command, MeterSchema schema, Exception error) {
        super(command, error);
        this.command = command;
        this.schema = schema;
    }

    /**
     * Return meterId of installed meter, if it was installed.
     */
    public Optional<MeterId> getMeterId() {
        if (error != null) {
            return Optional.empty();
        }
        return Optional.of(command.getMeterConfig().getId());
    }

    public Optional<MeterSchema> getSchema() {
        return Optional.ofNullable(schema);
    }
}
