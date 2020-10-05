/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.inmemory.repositories;

import org.openkilda.model.ExclusionId.ExclusionIdData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.ExclusionIdFrame;
import org.openkilda.persistence.ferma.repositories.FermaExclusionIdRepository;
import org.openkilda.persistence.repositories.ExclusionIdRepository;
import org.openkilda.persistence.tx.TransactionManager;

/**
 * In-memory implementation of {@link ExclusionIdRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryExclusionIdRepository extends FermaExclusionIdRepository {
    InMemoryExclusionIdRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    protected ExclusionIdFrame doAdd(ExclusionIdData data) {
        if (find(data.getFlowId(), data.getRecordId()).isPresent()) {
            throw new ConstraintViolationException("Unable to create " + ExclusionIdFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
