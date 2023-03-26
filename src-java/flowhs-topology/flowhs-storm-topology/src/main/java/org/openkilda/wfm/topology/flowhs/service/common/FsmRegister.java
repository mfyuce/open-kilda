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

package org.openkilda.wfm.topology.flowhs.service.common;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class FsmRegister<K, T extends StateMachine<T, ?, ?, ?>> {
    private final Map<K, T> fsmByKey = new HashMap<>();

    public void registerFsm(K key, T fsm) {
        fsmByKey.put(key, fsm);
    }

    public boolean hasRegisteredFsmWithKey(K key) {
        return fsmByKey.containsKey(key);
    }

    public boolean hasAnyRegisteredFsm() {
        return !fsmByKey.isEmpty();
    }

    public Optional<T> getFsmByKey(K key) {
        return Optional.ofNullable(fsmByKey.get(key));
    }

    public T unregisterFsm(K key) {
        return fsmByKey.remove(key);
    }
}
