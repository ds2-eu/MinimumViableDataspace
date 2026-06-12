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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
//import org.eclipse.edc.connector.dataplane.metrics.store.ContractUsageStore;
import org.eclipse.edc.connector.dataplane.metrics.store.PostgresContractUsageStore;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * A DataSink wrapper that counts bytes transferred and tracks usage metrics.
 */
public class ByteCountingDataSink implements DataSink {

    private final DataSink wrappedSink;
    private final String transferProcessId;
    private final String contractId;
    private final String assetId;
    private final Monitor monitor;
    private final MeterRegistry meterRegistry;
    //private final ContractUsageStore usageStore;
    private final PostgresContractUsageStore usageStore;
    private final AtomicLong bytesTransferred = new AtomicLong(0);

    /**
     * Creates a new ByteCountingDataSink.
     *
     * @param wrappedSink The original DataSink to wrap
     * @param message The DataFlowStartMessage containing transfer process details
     * @param monitor For logging
     * @param meterRegistry For metrics collection
     * @param usageStore The store for recording usage data
     */
    public ByteCountingDataSink(DataSink wrappedSink, DataFlowStartMessage message, 
                               Monitor monitor, MeterRegistry meterRegistry,
                               PostgresContractUsageStore usageStore) {
        this.wrappedSink = wrappedSink;
        this.transferProcessId = message.getProcessId();
        this.contractId = message.getAgreementId();
        this.assetId = message.getAssetId();
        this.monitor = monitor;
        this.meterRegistry = meterRegistry;
        this.usageStore = usageStore;
    }

    /**
     * Creates a new ByteCountingDataSink with explicit agreementId and assetId.
     *
     * @param wrappedSink The original DataSink to wrap
     * @param message The DataFlowStartMessage containing transfer process details
     * @param agreementId The explicit agreement ID (contract ID) to use
     * @param assetId The explicit asset ID to use
     * @param monitor For logging
     * @param meterRegistry For metrics collection
     * @param usageStore The store for recording usage data
     */
    public ByteCountingDataSink(DataSink wrappedSink, DataFlowStartMessage message,
                               String agreementId, String assetId,
                               Monitor monitor, MeterRegistry meterRegistry,
                               PostgresContractUsageStore usageStore) {
        this.wrappedSink = wrappedSink;
        this.transferProcessId = message.getProcessId();
        this.contractId = agreementId;
        this.assetId = assetId;
        this.monitor = monitor;
        this.meterRegistry = meterRegistry;
        this.usageStore = usageStore;        
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        // Wrap the DataSource to count bytes
        var countingSource = new ByteCountingDataSource(source, this::addBytes);
        monitor.info("Request in bytes calculation: " + countingSource);
        
        // Call the wrapped sink with our counting source
        return wrappedSink.transfer(countingSource)
                .thenApply(result -> {
                    if (result.succeeded()) {
                        // Get the byte count
                        long bytes = bytesTransferred.get();
                        
                        // Record metrics
                        recordMetrics(bytes);

                        
                        // Log the transfer
                        monitor.info(String.format("Transferred %d bytes for transferProcess=%s, contract=%s, asset=%s", bytes, transferProcessId, contractId, assetId));
                        

                        // Store for later querying
                        monitor.info("About to record usage for " + bytes + " bytes");

                        //usageStore.recordUsage(contractId, assetId, transferProcessId, bytes);
                        try {
                            usageStore.recordUsage(contractId, assetId, transferProcessId, bytes);
                            monitor.info("Successfully recorded usage");
                        } catch (Exception e) {
                            monitor.severe("Failed to record usage", e);
                        }
                                                                        
                    }
                    return result;
                });
    }

    /**
     * Records metrics about the transfer to the metrics system
     */
    private void recordMetrics(long bytesTransferred) {
        if (meterRegistry != null) {
            Counter.builder("edc.transfer.bytes")
                    .tag("transferProcessId", transferProcessId)
                    .tag("contractId", contractId)
                    .tag("assetId", assetId)
                    .register(meterRegistry)
                    .increment(bytesTransferred);
        }
    }

    private void addBytes(long count) {
        bytesTransferred.addAndGet(count);
    }

    /**
     * Inner class that wraps a DataSource to count bytes read from it
     */
    private static class ByteCountingDataSource implements DataSource {
        private final DataSource wrappedSource;
        private final LongConsumer bytesCounter;

        ByteCountingDataSource(DataSource wrappedSource, LongConsumer bytesCounter) {
            this.wrappedSource = wrappedSource;
            this.bytesCounter = bytesCounter;
        }

        @Override
        public StreamResult<java.util.stream.Stream<Part>> openPartStream() {
            StreamResult<java.util.stream.Stream<Part>> result = wrappedSource.openPartStream();
            
            if (result.succeeded()) {
                return StreamResult.success(
                    result.getContent()
                          .map(part -> new ByteCountingPart(part, bytesCounter))
                );
            } else {
                return result;
            }
        }

        @Override
        public void close() throws Exception {
            wrappedSource.close();
        }
    }

    /**
     * Inner class that wraps a Part to count bytes read from it
     */
    private static class ByteCountingPart implements DataSource.Part {
        private final DataSource.Part wrappedPart;
        private final LongConsumer bytesCounter;

        ByteCountingPart(DataSource.Part wrappedPart, LongConsumer bytesCounter) {
            this.wrappedPart = wrappedPart;
            this.bytesCounter = bytesCounter;
        }

        @Override
        public String name() {
            return wrappedPart.name();
        }

        @Override
        public long size() {
            return wrappedPart.size();
        }

        @Override
        public InputStream openStream() {
            // Create a counting wrapper around the original stream
            return new CountingInputStream(wrappedPart.openStream(), bytesCounter);
        }

        @Override
        public String mediaType() {
            return wrappedPart.mediaType();
        }

        @Override
        public void close() throws Exception {
            wrappedPart.close();
        }
    }

    /**
     * InputStream implementation that counts bytes read
     */
    private static class CountingInputStream extends InputStream {
        private final InputStream wrappedStream;
        private final LongConsumer bytesCounter;

        CountingInputStream(InputStream wrappedStream, LongConsumer bytesCounter) {
            this.wrappedStream = wrappedStream;
            this.bytesCounter = bytesCounter;
        }

        @Override
        public int read() throws IOException {
            int b = wrappedStream.read();
            if (b != -1) {
                bytesCounter.accept(1);
            }
            return b;
        }

        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            int bytesRead = wrappedStream.read(b, off, len);
            if (bytesRead != -1) {
                bytesCounter.accept(bytesRead);
            }
            return bytesRead;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = wrappedStream.skip(n);
            bytesCounter.accept(skipped);
            return skipped;
        }

        @Override
        public void close() throws IOException {
            wrappedStream.close();
        }

        @Override
        public int available() throws IOException {
            return wrappedStream.available();
        }

        @Override
        public boolean markSupported() {
            return wrappedStream.markSupported();
        }

        @Override
        public void mark(int readlimit) {
            wrappedStream.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            wrappedStream.reset();
        }
    }
} 