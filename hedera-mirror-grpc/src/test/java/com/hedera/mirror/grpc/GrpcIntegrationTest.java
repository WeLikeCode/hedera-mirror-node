package com.hedera.mirror.grpc;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import java.util.Map;
import javax.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

@ContextConfiguration(initializers = GrpcIntegrationTest.TestDatabaseConfiguration.class)
@SpringBootTest
public abstract class GrpcIntegrationTest {

    /**
     * First try to use a Testcontainer. If Docker is not running or it fails to connect to the Testcontainer, fallback
     * to a database running on localhost.
     */
    @Log4j2
    @TestConfiguration
    static class TestDatabaseConfiguration implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static PostgreSQLContainer postgresql;

        static {
            // Lower timeouts from default to not slow down tests as much if not using Docker
            System.setProperty("testcontainers.environmentprovider.timeout", "1");
            System.setProperty("testcontainers.npipesocketprovider.timeout", "1");
            System.setProperty("testcontainers.unixsocketprovider.timeout", "1");
            System.setProperty("testcontainers.windowsprovider.timeout", "1");
        }

        @Bean
        ConnectionFactoryOptionsBuilderCustomizer testConnectionFactoryCustomizer() {
            Map<String, String> options = Map.of("default_transaction_read_only", "off");
            return builder -> builder.option(PostgresqlConnectionFactoryProvider.OPTIONS, options);
        }

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                log.info("Starting PostgreSQL");
                postgresql = new PostgreSQLContainer<>("postgres:9.6-alpine");
                postgresql.start();

                TestPropertyValues
                        .of("hedera.mirror.grpc.db.name=" + postgresql.getDatabaseName())
                        .and("hedera.mirror.grpc.db.password=" + postgresql.getPassword())
                        .and("hedera.mirror.grpc.db.username=" + postgresql.getUsername())
                        .and("spring.datasource.url=" + postgresql.getJdbcUrl())
                        .and("spring.r2dbc.url=" + postgresql.getJdbcUrl()
                                .replace("jdbc:", "r2dbc:"))
                        .applyTo(applicationContext);
            } catch (Throwable ex) {
                log.warn(ex.getMessage());
            }
        }

        @PreDestroy
        public void stop() {
            if (postgresql != null && postgresql.isRunning()) {
                log.info("Stopping PostgreSQL");
                postgresql.stop();
            }
        }
    }
}
