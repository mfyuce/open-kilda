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

package org.openkilda.persistence.inmemory;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;

import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mockito;

import java.util.Properties;

public abstract class InMemoryGraphBasedTest {
    protected static ConfigurationProvider configurationProvider;
    protected static InMemoryGraphPersistenceManager inMemoryGraphPersistenceManager;
    protected static InMemoryGraphPersistenceImplementation inMemoryGraphPersistenceImplementation;
    protected static PersistenceManager persistenceManager;
    protected static TransactionManager transactionManager;
    protected static RepositoryFactory repositoryFactory;

    @BeforeClass
    public static void initPersistenceManager() {
        Properties properties = new Properties();
        properties.put("persistence.implementation.default", PersistenceImplementationType.IN_MEMORY_GRAPH.name());
        configurationProvider = new PropertiesBasedConfigurationProvider(properties);
        inMemoryGraphPersistenceImplementation = new InMemoryGraphPersistenceImplementation(configurationProvider);
        InMemoryGraphPersistenceImplementation persistenceImplementation = Mockito.spy(
                inMemoryGraphPersistenceImplementation);
        repositoryFactory = Mockito.spy(inMemoryGraphPersistenceImplementation.getRepositoryFactory());
        Mockito.when(persistenceImplementation.getRepositoryFactory()).thenReturn(repositoryFactory);

        inMemoryGraphPersistenceManager = new InMemoryGraphPersistenceManager(
                configurationProvider, PersistenceImplementationType.IN_MEMORY_GRAPH, persistenceImplementation);
        inMemoryGraphPersistenceManager.install();

        persistenceManager = Mockito.spy(inMemoryGraphPersistenceManager);
        transactionManager = inMemoryGraphPersistenceManager.getTransactionManager();
    }

    @Before
    public void cleanTinkerGraph() {
        inMemoryGraphPersistenceImplementation.purgeData();
    }

    protected Switch createTestSwitch(long switchId) {
        return createTestSwitch(new SwitchId(switchId));
    }

    protected Switch createTestSwitch(SwitchId switchId) {
        Switch sw = Switch.builder()
                .switchId(switchId)
                .description("test_description")
                .socketAddress(new IpSocketAddress("10.0.0.1", 30070))
                .controller("test_ctrl")
                .hostname("test_host_" + switchId)
                .status(SwitchStatus.ACTIVE)
                .build();
        repositoryFactory.createSwitchRepository().add(sw);
        return sw;
    }
}
