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

package org.eclipse.edc.connector.dataplane.metrics.store;

import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation that accumulates usage by contract and asset.
 */
public class InMemoryContractUsageStore implements ContractUsageStore {

    private final Monitor monitor;
    private final Map<String, AtomicLong> contractUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> contractAssetUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> transferProcessUsage = new ConcurrentHashMap<>();

    public InMemoryContractUsageStore(Monitor monitor) {
        this.monitor = monitor;
        monitor.info("Initialized new InMemoryContractUsageStore instance");
    }

    // @Override
    // public void recordUsage(String contractId, String assetId, String transferProcessId, long bytesTransferred) {
    //     // Input validation
    //     if (contractId == null || assetId == null || transferProcessId == null) {
    //         monitor.warning("Attempted to record usage with null contractId, assetId, or transferProcessId");
    //         return;
    //     }
        
    //     if (bytesTransferred <= 0) {
    //         monitor.debug("Ignoring non-positive byte count " + bytesTransferred + " for contract " + contractId);
    //         return;
    //     }

    //     // Create final copies for use in lambdas
    //     final String normalizedContractId = normalizeId(contractId);
    //     final String normalizedAssetId = normalizeId(assetId);
    //     final String normalizedTransferId = normalizeId(transferProcessId);
    //     final long bytes = bytesTransferred;
        
    //     monitor.debug("Starting accumulation for contract " + normalizedContractId + 
    //             ", asset " + normalizedAssetId + 
    //             ", transfer " + normalizedTransferId + 
    //             ". Adding " + bytes + " bytes...");

    //     // Contract-level accumulation
    //     long newContractTotal = contractUsage
    //             .computeIfAbsent(normalizedContractId, k -> new AtomicLong(0))
    //             .addAndGet(bytes);

    //     // Contract-Asset pair accumulation
    //     String contractAssetKey = createCompositeKey(normalizedContractId, normalizedAssetId);
    //     long newContractAssetTotal = contractAssetUsage
    //             .computeIfAbsent(contractAssetKey, k -> new AtomicLong(0))
    //             .addAndGet(bytes);

    //     // Transfer-level tracking
    //     long newTransferTotal = transferProcessUsage
    //             .computeIfAbsent(normalizedTransferId, k -> new AtomicLong(0))
    //             .addAndGet(bytes);

    //     monitor.info("SUCCESS: Added " + bytes + " bytes to contract " + normalizedContractId + 
    //             " (new total: " + newContractTotal + "), asset " + normalizedAssetId + 
    //             " (new total: " + newContractAssetTotal + "), transfer " + 
    //             normalizedTransferId + " (new total: " + newTransferTotal + ")");
    // }

    @Override
    public long getContractUsage(String contractId) {
        String normalizedId = normalizeId(contractId);
        long total = contractUsage.getOrDefault(normalizedId, new AtomicLong(0)).get();
        monitor.debug("Current usage for contract " + normalizedId + ": " + total + " bytes");
        return total;
    }

    @Override
    public long getTotalBytesForContract(String contractId) {
        AtomicLong totalBytes = contractUsage.get(normalizeId(contractId));
        return totalBytes != null ? totalBytes.get() : 0L;
    }

    @Override
    public long getContractAssetUsage(String contractId, String assetId) {
        String normalizedContractId = normalizeId(contractId);
        String normalizedAssetId = normalizeId(assetId);
        String key = createCompositeKey(normalizedContractId, normalizedAssetId);
        long total = contractAssetUsage.getOrDefault(key, new AtomicLong(0)).get();
        monitor.debug("Current usage for contract " + normalizedContractId + 
                " asset " + normalizedAssetId + ": " + total + " bytes");
        return total;
    }

    @Override
    public long getTransferProcessUsage(String transferProcessId) {
        String normalizedId = normalizeId(transferProcessId);
        long total = transferProcessUsage.getOrDefault(normalizedId, new AtomicLong(0)).get();
        monitor.debug("Current usage for transfer " + normalizedId + ": " + total + " bytes");
        return total;
    }

    private String createCompositeKey(String contractId, String assetId) {
        return contractId + ":" + assetId;
    }

    private String normalizeId(String id) {
        return id.trim().toLowerCase();
    }

    public void logCurrentState() {
        monitor.info("=== Current Contract Usage Store State ===");
        
        contractUsage.forEach((contractId, total) -> 
                monitor.info("Contract " + contractId + ": " + total.get() + " bytes"));
        
        contractAssetUsage.forEach((key, total) -> {
            String[] parts = key.split(":");
            monitor.info("Contract " + parts[0] + " Asset " + parts[1] + ": " + 
                    total.get() + " bytes");
        });
        
        transferProcessUsage.forEach((transferId, total) ->
                monitor.info("Transfer " + transferId + ": " + total.get() + " bytes"));
        
        monitor.info("=========================================");
    }
}