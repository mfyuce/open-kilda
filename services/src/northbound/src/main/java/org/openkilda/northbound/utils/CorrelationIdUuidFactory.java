/* Copyright 2018 Telstra Open Source
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

package org.openkilda.northbound.utils;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationIdUuidFactory implements CorrelationIdFactory {
    private String lastId = null;

    @Override
    public String produce() {
        return setLastId(UUID.randomUUID().toString());
    }

    @Override
    public String produceChained(String outer) {
        return setLastId(RequestCorrelationId.chain(produceInner(), outer));
    }

    @Override
    public String produceChained(String inner, String outer) {
        return setLastId(RequestCorrelationId.chain(inner, outer));
    }

    @Override
    public String getLastId() {
        return lastId;
    }

    protected String produceInner() {
        return produce();
    }

    protected String setLastId(String lastId) {
        this.lastId = lastId;
        return lastId;
    }
}
