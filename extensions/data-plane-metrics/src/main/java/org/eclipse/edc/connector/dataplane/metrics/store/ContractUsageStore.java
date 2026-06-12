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

/**
 * Stores contract usage data.
 */
public interface ContractUsageStore {
    // /**
    //  * Adds bytes to the usage counter for a contract
    //  *
    //  * @param contractId The contract ID
    //  * @param assetId The asset ID 
    //  * @param transferProcessId The transfer process ID
    //  * @param bytesTransferred The number of bytes transferred
    //  */
    // void recordUsage(String contractId, String assetId, String transferProcessId, long bytesTransferred);

    /**
     * Gets the total bytes used for a contract
     *
     * @param contractId The contract ID
     * @return The total bytes used
     */
    long getContractUsage(String contractId);

    /**
     * Gets the total bytes used for a contract and asset
     *
     * @param contractId The contract ID
     * @param assetId The asset ID
     * @return The total bytes used
     */
    long getContractAssetUsage(String contractId, String assetId);

    /**
     * Gets the total bytes used for a specific transfer process
     *
     * @param transferProcessId The transfer process ID
     * @return The total bytes used
     */
    long getTransferProcessUsage(String transferProcessId);

    /**
     * Gets the total bytes used for a contract
     *
     * @param contractId The contract ID
     * @return The total bytes used
     */
    long getTotalBytesForContract(String contractId);
} 