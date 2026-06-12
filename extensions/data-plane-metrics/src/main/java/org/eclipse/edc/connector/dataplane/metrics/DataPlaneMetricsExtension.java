/*
 *  Copyright (c) 2024 Contributor to Eclipse Foundation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.connector.dataplane.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.edc.connector.dataplane.metrics.api.DatabaseConfigApiController;
import org.eclipse.edc.connector.dataplane.metrics.api.MetricsStateApiController;
import org.eclipse.edc.connector.dataplane.metrics.api.TransferMetricsApiController;
import org.eclipse.edc.connector.dataplane.metrics.store.DatabaseConfigManager;
import org.eclipse.edc.connector.dataplane.metrics.store.PostgresContractUsageStore;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
//import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

//import java.sql.Connection;
import java.sql.DriverManager;
//import java.sql.ResultSet;
import java.sql.SQLException;
//import java.util.HashMap;
import java.util.Map;
//import java.util.stream.Collectors;
//import java.util.Optional;
import javax.sql.DataSource;


@Extension(value = DataPlaneMetricsExtension.NAME)
public class DataPlaneMetricsExtension implements ServiceExtension {

    public static final String NAME = "Data Plane Metrics";

    @Setting(required = false, defaultValue = "true")
    private static final String ENABLE_METRICS_SETTING = "edc.dataplane.metrics.enabled";

    private static final int DEFAULT_PUBLIC_PORT = 8185;
    private static final String DEFAULT_PUBLIC_PATH = "/api/public";
    private ServiceExtensionContext context;
    private boolean portMappingRegistered = false;
    private MetricsTrackingTransferService transferServiceDecorator;
    private PostgresContractUsageStore usageStore;
    private DatabaseConfigManager configManager;
    private TokenInterceptor tokenInterceptor;



    @Inject
    private PipelineService pipelineService;
    @Inject
    private WebService webService;
    @Inject
    private Monitor monitor;
    @Inject
    private PortMappingRegistry portMappingRegistry;
    @Inject(required = false)
    private MeterRegistry meterRegistry;
    @Inject(required = false)
    private DataSource dataSource;

    @Setting(value = "edc.datasource.default.url")
    private String dbUrl;
    @Setting(value = "edc.datasource.default.user")
    private String dbUser;
    @Setting(value = "edc.datasource.default.password")
    private String dbPassword;
    private String controlPlaneDbUrl;
    private String controlPlaneDbUser;
    private String controlPlaneDbPassword;

    @Override
    public String name() {
        return NAME;
    }    

    @Override
    public void initialize(ServiceExtensionContext context) {
        this.context = context;
        this.configManager = new DatabaseConfigManager(context, monitor);
        // Initialize database configuration
        Map<String, String> config = configManager.getCurrentConfig();
        dbUrl = config.get("url");
        monitor.info("Initial database URL: " + dbUrl);
        dbUser = config.get("user");
        monitor.info("Initial database URL: " + dbUser);
        dbPassword = config.get("password");
        monitor.info("Initial database URL: " + dbPassword);
        controlPlaneDbUrl = config.get("controlPlaneUrl");
        controlPlaneDbUser = config.get("controlPlaneUser");
        controlPlaneDbPassword = config.get("controlPlanePassword");
        
        usageStore = new PostgresContractUsageStore(dbUrl, dbUser, dbPassword,
                controlPlaneDbUrl, controlPlaneDbUser, controlPlaneDbPassword, monitor);
 
        configManager.addListener(newConfig -> {
            dbUrl = newConfig.get("url");
            dbUser = newConfig.get("user");
            dbPassword = newConfig.get("password");
            controlPlaneDbUrl = newConfig.get("controlPlaneUrl");
            controlPlaneDbUser = newConfig.get("controlPlaneUser");
            controlPlaneDbPassword = newConfig.get("controlPlanePassword");
            usageStore.updateConfiguration(dbUrl, dbUser, dbPassword,
                    controlPlaneDbUrl, controlPlaneDbUser, controlPlaneDbPassword);
            monitor.info("Database configuration updated successfully");
            testDatabaseConnection();
        });
         
        testDatabaseConnection();
        
        // Register port mapping only once during initialization
        registerPortMapping();
        
        boolean enableMetrics = context.getSetting(ENABLE_METRICS_SETTING, true);
        if (enableMetrics) {
            initializeMetricsComponents();
        } else {
            monitor.info("Data Plane Metrics extension is disabled");
        }
    }

    private void registerPortMapping() {
        if (!portMappingRegistered) {
            var portMapping = new PortMapping(ApiContext.PUBLIC, DEFAULT_PUBLIC_PORT, DEFAULT_PUBLIC_PATH);
            portMappingRegistry.register(portMapping);
            portMappingRegistered = true;
            monitor.debug("Port mapping registered for metrics API");
        }
    }

    public synchronized void setMetricsEnabled(boolean enabled) { 
        if (context == null) {
            monitor.severe("Cannot set metrics state - extension context is null");
            return;
        }
        
        monitor.info("Data Plane Metrics extension request came: " + enabled);
        // Store the setting in the configuration store
        //context.getMonitor().info("Setting metrics enabled to: " + enabled);
        context.getSetting(ENABLE_METRICS_SETTING, enabled);
        if (enabled) {
            initializeMetricsComponents();
        } else {
            initializeMetricsComponents();
        }
    }

    private void initializeMetricsComponents() {
        monitor.info("Initializing Data Plane Metrics components");
        
        // Register API controllers
        webService.registerResource(ApiContext.PUBLIC, new MetricsStateApiController(this, monitor));
        webService.registerResource(ApiContext.PUBLIC, new DatabaseConfigApiController(this, configManager, monitor));
        webService.registerResource(ApiContext.PUBLIC, new TransferMetricsApiController(usageStore, monitor));
        
        // Register token interceptor
        tokenInterceptor = new TokenInterceptor(monitor);
        webService.registerResource(ApiContext.PUBLIC, tokenInterceptor);

        // Initialize transfer service decorator if not already done
        if (transferServiceDecorator == null) {
            transferServiceDecorator = new MetricsTrackingTransferService(
                    pipelineService, 
                    this::createCountingSink,
                    monitor,
                    usageStore
            );
            context.registerService(PipelineService.class, transferServiceDecorator);
        }
        
        monitor.info("Data Plane Metrics components initialized");
    }

    private void shutdownMetricsComponents() {
        monitor.info("Shutting down Data Plane Metrics components");
        // Implementation remains the same
    }

    private void testDatabaseConnection() {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            monitor.info("Database connection successful to: " + dbUrl);
        } catch (SQLException e) {
            monitor.severe("Database connection failed to: " + dbUrl, e);
        }
    }

    private DataSink createCountingSink(DataSink originalSink, DataFlowStartMessage message) {
        monitor.info("createCountingSink thread: " + Thread.currentThread().getName());

        String jwt = tokenInterceptor != null ? tokenInterceptor.getToken() : null;
        tokenInterceptor.clear();

        monitor.info("JWT token: " + jwt);
        monitor.info("Direct agreementId from message: " + message.getAgreementId());
        monitor.info("Direct assetId from message: " + message.getAssetId());
        monitor.info("Direct processId from message: " + message.getProcessId());

        String agreementId = message.getAgreementId();
        String assetId = message.getAssetId();

        if ((agreementId == null || agreementId.isEmpty()) && jwt != null) {
            String jti = extractJtiFromJwt(jwt);
            if (jti != null) agreementId = queryDatabaseForAgreementId(jti);
        }

        if ((assetId == null || assetId.isEmpty()) && jwt != null) {
            String jti = extractJtiFromJwt(jwt);
            if (jti != null) assetId = queryDatabaseForAssetId(jti);
        }

        if (agreementId == null || agreementId.isEmpty()) agreementId = message.getProcessId();
        if (assetId == null || assetId.isEmpty()) assetId = message.getProcessId();

        monitor.info("Final agreementId: " + agreementId);
        monitor.info("Final assetId: " + assetId);

        return new ByteCountingDataSink(
                originalSink,
                message,
                agreementId,
                assetId,
                monitor,
                meterRegistry,
                usageStore
            );
    }

    private void logMessageAttributes(DataFlowStartMessage message) {
        monitor.debug("DataFlowStartMessage attributes:");
        monitor.debug("  - ProcessId: " + message.getProcessId());
        monitor.debug("  - AgreementId: " + message.getAgreementId());
        monitor.debug("  - AssetId: " + message.getAssetId());
        monitor.debug("  - Properties: " + message.getProperties());
        monitor.debug("  - Source: " + message.getSourceDataAddress());
        monitor.debug("  - Destination: " + message.getDestinationDataAddress());      
        monitor.debug("  - ID: " + message.getId()); 
        monitor.debug("  - Participant: " + message.getParticipantId());        
    }

    private String getAgreementId(DataFlowStartMessage message) {        
        String agreementId = message.getAgreementId();
        if (agreementId == null || agreementId.isEmpty()) {
            monitor.debug("AgreementId is missing, attempting to retrieve from database");
            agreementId = retrieveAgreementIdFromDatabase(message);
            monitor.debug("Retrieved agreementId: " + agreementId);            
        }
        return agreementId;
    }

    private String getAssetId(DataFlowStartMessage message) {
        String assetId = message.getAssetId();
        if (assetId == null || assetId.isEmpty()) {
            monitor.debug("AssetId is missing, attempting to retrieve from database");
            assetId = retrieveAssetIdFromDatabase(message);
            monitor.debug("Retrieved AssetId: " + assetId);
        }
        return assetId;
    }
       
    private String retrieveAgreementIdFromDatabase(DataFlowStartMessage message) {
        String fallbackId = message.getProcessId();
        String jwtToken = extractJwtToken(message);
        
        if (jwtToken == null || jwtToken.isEmpty()) {
            monitor.warning("JWT token not found");
            return fallbackId;
        }

        String jti = extractJtiFromJwt(jwtToken);
        if (jti == null || jti.isEmpty()) {
            monitor.warning("Could not extract jti from JWT");
            return fallbackId;
        }

        String agreementId = queryDatabaseForAgreementId(jti);
        return agreementId != null ? agreementId : fallbackId;
    }

    private String retrieveAssetIdFromDatabase(DataFlowStartMessage message) {
        String fallbackId = message.getProcessId();
        String jwtToken = extractJwtToken(message);
        
        if (jwtToken == null || jwtToken.isEmpty()) {
            monitor.warning("JWT token not found");
            return fallbackId;
        }

        String jti = extractJtiFromJwt(jwtToken);
        if (jti == null || jti.isEmpty()) {
            monitor.warning("Could not extract jti from JWT");
            return fallbackId;
        }

        String assetId = queryDatabaseForAssetId(jti);
        return assetId != null ? assetId : fallbackId;
    }

    private String extractJwtToken(DataFlowStartMessage message) {
        String jwtToken = null;
        var properties = message.getProperties();

        if (properties == null) {
            monitor.warning("Message properties are null");
            return null;
        }

        // Check direct properties
        jwtToken = properties.get("Authorization");        
        
        // Check httpHeaders if present
        if (jwtToken == null && properties.containsKey("httpHeaders")) {
            monitor.debug("Checking httpHeaders for Authorization");
            Object httpHeaders = properties.get("httpHeaders");
            if (httpHeaders instanceof java.util.Map) {
                try {
                    @SuppressWarnings("unchecked")
                    var headers = (java.util.Map<String, Object>) httpHeaders;
                    Object authHeader = headers.get("Authorization");
                    if (authHeader instanceof String) {
                        jwtToken = (String) authHeader;
                    }
                } catch (ClassCastException e) {
                    monitor.warning("Error casting httpHeaders to Map: " + e.getMessage());
                }
            }
            monitor.debug("Authorization header found in httpHeaders: " + jwtToken);
        }
        
        // Check data address properties
        if (jwtToken == null) {
            monitor.debug("Checking data address properties for Authorization");
            if (message.getDestinationDataAddress() != null) {
                jwtToken = extractTokenFromProperties(message.getDestinationDataAddress().getProperties());
            }
            if (jwtToken == null && message.getSourceDataAddress() != null) {
                jwtToken = extractTokenFromProperties(message.getSourceDataAddress().getProperties());
            }
            monitor.debug("Authorization header found in data address properties: " + jwtToken);
        }

        if (jwtToken != null && jwtToken.startsWith("Bearer ")) {
            monitor.debug("Extracting JWT token from Authorization header");
            jwtToken = jwtToken.substring(7);
            monitor.debug("JWT token extracted: " + jwtToken);
        }
        monitor.debug("Final JWT token: " + jwtToken);
        
        return jwtToken;
    }

    private String extractTokenFromProperties(java.util.Map<String, Object> properties) {
        if (properties.containsKey("authorization")) {
            Object authValue = properties.get("authorization");
            if (authValue instanceof String) {
                return (String) authValue;
            }
        }
        return null;
    }

    private String extractJtiFromJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                monitor.warning("Invalid JWT token format");
                return null;
            }
            
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            
            if (payload.contains("\"jti\"")) {
                monitor.debug("Extracting jti from JWT payload");
                int jtiIndex = payload.indexOf("\"jti\"");
                int valueStart = payload.indexOf(":", jtiIndex) + 1;
                int valueEnd = payload.indexOf(",", valueStart);
                if (valueEnd == -1) {
                    valueEnd = payload.indexOf("}", valueStart);
                }
                String jtiValue = payload.substring(valueStart, valueEnd).trim();
                if (jtiValue.startsWith("\"") && jtiValue.endsWith("\"")) {
                    jtiValue = jtiValue.substring(1, jtiValue.length() - 1);
                }
                monitor.debug("Extracted jti from JWT token: " + jtiValue);
                return jtiValue;
            }
            return null;
        } catch (Exception e) {
            monitor.warning("Error parsing JWT token: " + e.getMessage());
            return null;
        }
    }

    private String queryDatabaseForAgreementId(String jti) {
        String query = "SELECT additional_properties->>'agreement_id' AS agreement_id " +
                       "FROM edc_accesstokendata " +
                       "WHERE claim_token->'claims'->>'jti' = ?";
        return executeQuery(jti, query, "agreement_id");
    }    

    private String queryDatabaseForAssetId(String jti) {
        String query = "SELECT additional_properties->>'asset_id' AS asset_id " +
                       "FROM edc_accesstokendata " +
                       "WHERE claim_token->'claims'->>'jti' = ?";
        return executeQuery(jti, query, "asset_id");
    }

    private String executeQuery(String jti, String query, String columnName) {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                var stmt = conn.prepareStatement(query)) {

            stmt.setString(1, jti);
            monitor.debug("Executing query: " + stmt);

            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(columnName);
                    monitor.debug("Found " + columnName + ": " + value);
                    return value;
                }
                monitor.debug("No " + columnName + " found for jti: " + jti);
            }
        } catch (SQLException e) {
            monitor.severe("Database query failed", e);
        }
        return null;
    }

    @Provider(isDefault = true)
    public PostgresContractUsageStore contractUsageStore() {
        return usageStore;
    }
     
    @Provider
    public DatabaseConfigManager databaseConfigManager(ServiceExtensionContext context) {
        return configManager;
    }
}
