/*
 *  Copyright (c) 2024 Contributor to Eclipse Foundation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.edc.connector.dataplane.metrics;

import org.eclipse.edc.connector.dataplane.metrics.store.PostgresContractUsageStore;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
//import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
//import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

//import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Decorates the PipelineService to add metrics tracking.
 */
public class MetricsTrackingTransferService implements PipelineService {

    private final PipelineService wrappedService;
    private final BiFunction<DataSink, DataFlowStartMessage, DataSink> sinkFactory;
    private final Monitor monitor;
    private final PostgresContractUsageStore usageStore;


    public MetricsTrackingTransferService(PipelineService wrappedService, 
                                        BiFunction<DataSink, DataFlowStartMessage, DataSink> sinkFactory, Monitor monitor, PostgresContractUsageStore usageStore) {
        this.wrappedService = wrappedService;
        this.sinkFactory = sinkFactory;
        this.monitor = monitor;
        this.usageStore = usageStore;

    }

    public Map<String, String> getTransferMetadata(String transferId) {
        return usageStore.getTransferMetadata(transferId);
    }

    @Override
    public boolean canHandle(DataFlowStartMessage request) {
        return wrappedService.canHandle(request);
    }

    @Override
    public Result<Void> validate(DataFlowStartMessage request) {
        return wrappedService.validate(request);
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataFlowStartMessage request) {
        try {
            // Get properties with proper type casting
            Map<String, Object> sourceProps = request.getSourceDataAddress().getProperties();
            Map<String, Object> destProps = request.getDestinationDataAddress().getProperties();
            Map<String, String> metadata = getTransferMetadata(request.getProcessId());
            String agreementId = metadata.get("contractId");
            String assetId = metadata.get("assetId");

            monitor.debug("Retrieved contractId: " + agreementId);
            monitor.debug("Retrieved assetId: " + assetId);


            // Extract URLs using the correct property names
            String sourceUrl = getPropertyAsString(sourceProps, "https://w3id.org/edc/v0.0.1/ns/baseUrl");
            String destinationUrl = getPropertyAsString(destProps, "https://w3id.org/edc/v0.0.1/ns/baseUrl");

            // Calculate size
            long dataSize = (sourceUrl != null) ? calculateDataSize(sourceUrl) : -1;

            // Log the results
            monitor.debug(String.format(
                    "PUSH TRANSFER METRICS - Process: %s | Size: %d bytes | From: %s -> To: %s | Asset: %s | ContractId: %s",
                    request.getProcessId(),
                    dataSize,
                    sourceUrl,
                    destinationUrl,
                    assetId,
                    agreementId               
            ));
            try {
                usageStore.recordUsage(agreementId, assetId, request.getProcessId(), dataSize);
                monitor.info("Successfully recorded usage");
            } catch (Exception e) {
                monitor.severe("Failed to record usage", e);
            }
            
        } catch (Exception e) {
            monitor.warning("Metrics collection failed: " + e.getMessage());
        }
        
        return wrappedService.transfer(request);
    }

    private String getPropertyAsString(Map<String, Object> props, String key) {
        Object value = props.get(key);
        return (value != null) ? value.toString() : null;
    }

    private long calculateDataSize(String sourceUrl) {
        try {
            URL url = new URL(sourceUrl);
            try (InputStream in = url.openStream()) {
                long size = 0;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    size += bytesRead;
                }
                return size;
            }
        } catch (Exception e) {
            monitor.warning("Failed to calculate size for " + sourceUrl + ": " + e.getMessage());
            return -1;
        }
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataFlowStartMessage request, DataSink sink) {
        // Wrap the sink with our metrics collector
        monitor.info("First request: " + sink);
        var trackingSink = sinkFactory.apply(sink, request);
        return wrappedService.transfer(request, trackingSink);
    }

    @Override
    public StreamResult<Void> terminate(DataFlow dataFlow) {
        return wrappedService.terminate(dataFlow);
    }

    @Override
    public void closeAll() {
        wrappedService.closeAll();
    }

    @Override
    public void registerFactory(DataSourceFactory factory) {
        wrappedService.registerFactory(factory);
    }

    @Override
    public void registerFactory(DataSinkFactory factory) {
        wrappedService.registerFactory(factory);
    }

    @Override
    public Set<String> supportedSourceTypes() {
        return wrappedService.supportedSourceTypes();
    }

    @Override
    public Set<String> supportedSinkTypes() {
        return wrappedService.supportedSinkTypes();
    }
}
