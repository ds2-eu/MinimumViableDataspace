/*
 *  Copyright (c) 2025 Your Organization
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

// package org.eclipse.edc.connector.extension.monitor;

// import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
// import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
// import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
// import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
// import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
// import org.eclipse.edc.spi.command.CommandHandlerRegistry;
// import org.eclipse.edc.spi.command.CommandResult;
// import org.eclipse.edc.spi.monitor.Monitor;
// import org.eclipse.edc.spi.types.domain.DataAddress;

// import java.text.DecimalFormat;
// import java.time.Instant;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.atomic.AtomicLong;

// public class DataTransferMonitor implements TransferProcessListener {

//     private final Monitor monitor;
//     private final TransferProcessStore transferProcessStore;
//     private final CommandHandlerRegistry commandHandlerRegistry;
//     private final Map<String, TransferStats> transferStatsMap = new ConcurrentHashMap<>();
//     private final AtomicLong totalBytesTransferred = new AtomicLong(0);
//     private final AtomicLong totalTransfersCompleted = new AtomicLong(0);
//     private final AtomicLong totalTransfersFailed = new AtomicLong(0);
//     private final Map<String, ContractStats> contractStatsMap = new ConcurrentHashMap<>();
//     private final DecimalFormat decimalFormat = new DecimalFormat("#,###");

//     public DataTransferMonitor(Monitor monitor,
//                              TransferProcessStore transferProcessStore,
//                              CommandHandlerRegistry commandHandlerRegistry) {
//         this.monitor = monitor;
//         this.transferProcessStore = transferProcessStore;
//         this.commandHandlerRegistry = commandHandlerRegistry;
//     }


//     @Override
//     public void initiated(TransferProcess process) {
//         String transferId = process.getId();
//         TransferStats stats = new TransferStats(transferId, process.getAssetId(), process.getContractId());
//         transferStatsMap.put(transferId, stats);
//         contractStatsMap.computeIfAbsent(stats.getContractId(), ContractStats::new);
//         monitor.info(createLogMessage("Transfer initiated", stats));
//     }

//     @Override
//     public void started(TransferProcess process, TransferProcessStartedData additionalData) {
//         String transferId = process.getId();
//         TransferStats stats = transferStatsMap.get(transferId);

//         if (stats != null) {
//             stats.setEstimatedSize(getEstimatedSize(additionalData.getDataAddress()));
//             stats.setStartTime(Instant.now());
//             contractStatsMap.get(stats.getContractId()).incrementActiveTransfers();
//             monitor.info(createLogMessage("Transfer started", stats));
//             // For testing properties
//             monitor.info("Testing data transfer stats - Private Properties: " +
//                     process.getPrivateProperties());

//             // For testing data address
//             DataAddress contentAddress = process.getContentDataAddress();
//             monitor.info("Content Data Address - Type: " +
//                     (contentAddress != null ? contentAddress.getType() : "null") +
//                     ", Properties: " +
//                     (contentAddress != null ? contentAddress.getProperties() : "{}"));

//             // For data destination (where data is being transferred to)
//             DataAddress destination = process.getDataDestination();
//             monitor.info("Data Destination - Type: " +
//                     (destination != null ? destination.getType() : "null") +
//                     ", Properties: " +
//                     (destination != null ? destination.getProperties() : "{}"));
//         }

//         if (shouldTerminate(process)) {
//             handleTermination(process);
//         }
//     }


//     @Override
//     public void completed(TransferProcess process) {
//         updateTransferCompletion(process, false);
//     }

//     @Override
//     public void terminated(TransferProcess process) {
//         updateTransferCompletion(process, true);
//     }

//     private void handleTermination(TransferProcess process) {
//         String transferId = process.getId();
//         String reason = "Policy violation";

//         try {
//             var command = new TerminateTransferCommand(transferId, reason);
//             CommandResult result = commandHandlerRegistry.execute(command);

//             if (result.failed()) {
//                 monitor.warning("Failed to terminate transfer " + transferId + ": " + result.getFailureMessages());
//             } else {
//                 monitor.warning(createLogMessage("Transfer terminated",
//                         transferStatsMap.get(transferId), reason));
//             }
//         } catch (Exception e) {
//             monitor.severe("Failed to terminate transfer " + transferId, e);
//         }
//     }

//     private void updateTransferCompletion(TransferProcess process, boolean failed) {
//         String transferId = process.getId();
//         TransferStats stats = transferStatsMap.get(transferId);

//         if (stats != null) {
//             stats.setEndTime(Instant.now());
//             long bytes = stats.getEstimatedSize();

//             if (failed) {
//                 totalTransfersFailed.incrementAndGet();
//             } else {
//                 totalBytesTransferred.addAndGet(bytes);
//                 totalTransfersCompleted.incrementAndGet();
//             }

//             ContractStats contractStats = contractStatsMap.get(stats.getContractId());
//             contractStats.addBytesTransferred(bytes);
//             contractStats.decrementActiveTransfers();

//             if (failed) {
//                 contractStats.incrementFailed();
//             } else {
//                 contractStats.incrementCompleted();
//             }

//             String message = failed ? "Transfer terminated" : "Transfer completed";
//             monitor.info(createLogMessage(message, stats, process.getErrorDetail()));
//             logStatistics(stats.getContractId());
//         }
//     }

//     private boolean shouldTerminate(TransferProcess process) {
//         // Implement your termination policy here
//         return true; // Temporary implementation
//     }

//     private long getEstimatedSize(DataAddress dataAddress) {
//         if (dataAddress == null) {
//             return 0;
//         }
//         try {
//             String sizeStr = dataAddress.getStringProperty("size");
//             return sizeStr != null ? Long.parseLong(sizeStr) : 1024 * 1024;
//         } catch (NumberFormatException e) {
//             return 1024 * 1024;
//         }
//     }

//     private void logStatistics(String contractId) {
//         ContractStats stats = contractStatsMap.get(contractId);
//         monitor.info(String.format(
//                 "Contract %s: Completed: %d, Failed: %d, Active: %d, Data: %s bytes | " +
//                          "Global: Completed: %d, Failed: %d, Data: %s bytes",
//                 contractId,
//                 stats.getCompleted(),
//                 stats.getFailed(),
//                 stats.getActiveTransfers(),
//                 formatBytes(stats.getBytesTransferred()),
//                 totalTransfersCompleted.get(),
//                 totalTransfersFailed.get(),
//                 formatBytes(totalBytesTransferred.get())
//         ));
//     }

//     private String createLogMessage(String prefix, TransferStats stats) {
//         return String.format("%s - ID: %s, Asset: %s, Contract: %s, Size: %s bytes",
//                 prefix,
//                 stats.getTransferId(),
//                 stats.getAssetId(),
//                 stats.getContractId(),
//                 formatBytes(stats.getEstimatedSize()));
//     }

//     private String createLogMessage(String prefix, TransferStats stats, String detail) {
//         return detail != null
//                 ? createLogMessage(prefix, stats) + ", Reason: " + detail
//                 : createLogMessage(prefix, stats);
//     }

//     private String formatBytes(long bytes) {
//         return decimalFormat.format(bytes);
//     }

//     private static class TransferStats {
//         private final String transferId;
//         private final String assetId;
//         private final String contractId;
//         private long estimatedSize;
//         private Instant startTime;
//         private Instant endTime;

//         TransferStats(String transferId, String assetId, String contractId) {
//             this.transferId = transferId;
//             this.assetId = assetId;
//             this.contractId = contractId;
//         }

//         String getTransferId() {
//             return transferId;
//         }

//         String getAssetId() {
//             return assetId;
//         }

//         String getContractId() {
//             return contractId;
//         }

//         long getEstimatedSize() {
//             return estimatedSize;
//         }

//         void setEstimatedSize(long estimatedSize) {
//             this.estimatedSize = estimatedSize;
//         }

//         Instant getStartTime() {
//             return startTime;
//         }

//         void setStartTime(Instant startTime) {
//             this.startTime = startTime;
//         }

//         Instant getEndTime() {
//             return endTime;
//         }

//         void setEndTime(Instant endTime) {
//             this.endTime = endTime;
//         }
//     }

//     private static class ContractStats {
//         private final String contractId;
//         private final AtomicLong bytesTransferred = new AtomicLong(0);
//         private final AtomicLong completed = new AtomicLong(0);
//         private final AtomicLong failed = new AtomicLong(0);
//         private final AtomicLong activeTransfers = new AtomicLong(0);

//         ContractStats(String contractId) {
//             this.contractId = contractId;
//         }

//         String getContractId() {
//             return contractId;
//         }

//         long getBytesTransferred() {
//             return bytesTransferred.get();
//         }

//         long getCompleted() {
//             return completed.get();
//         }

//         long getFailed() {
//             return failed.get();
//         }

//         long getActiveTransfers() {
//             return activeTransfers.get();
//         }

//         void addBytesTransferred(long bytes) {
//             bytesTransferred.addAndGet(bytes);
//         }

//         void incrementCompleted() {
//             completed.incrementAndGet();
//         }

//         void incrementFailed() {
//             failed.incrementAndGet();
//         }

//         void incrementActiveTransfers() {
//             activeTransfers.incrementAndGet();
//         }

//         void decrementActiveTransfers() {
//             activeTransfers.decrementAndGet();
//         }
//     }
// }


/*
 *  Copyright (c) 2025 Your Organization
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.edc.connector.extension.monitor;

import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors data transfers and logs aggregated statistics.
 * Tracks data exchange by contract and provides detailed logging.
 */
public class DataTransferMonitor implements TransferProcessListener {

    private final Monitor monitor;
    private final TransferProcessStore transferProcessStore;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final Map<String, TransferStats> transferStatsMap = new ConcurrentHashMap<>();

    // Global statistics
    private final AtomicLong totalBytesTransferred = new AtomicLong(0);
    private final AtomicLong totalTransfersCompleted = new AtomicLong(0);
    private final AtomicLong totalTransfersFailed = new AtomicLong(0);

    // Contract-specific statistics
    private final Map<String, ContractStats> contractStatsMap = new ConcurrentHashMap<>();

    // Formatting for human-readable output
    private final DecimalFormat decimalFormat = new DecimalFormat("#,###");

    public DataTransferMonitor(Monitor monitor, TransferProcessStore transferProcessStore,
                               String dbUrl, String dbUser, String dbPassword) {
        this.monitor = monitor;
        this.transferProcessStore = transferProcessStore;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        monitor.info("[Transfer Monitor] Initialized - Starting to monitor data transfers");
    }


    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public void insertData(String contractId, String assetId, String id) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction

            String selectQuery = """
                INSERT INTO transfer_metadata (contract_id, asset_id, id)
                VALUES (?, ?, ?)
                    """;

            try (var selectStmt = conn.prepareStatement(selectQuery)) {
                selectStmt.setString(1, contractId);
                selectStmt.setString(2, assetId);
                selectStmt.setString(3, id);
                selectStmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initiated(TransferProcess process) {
        String id = process.getId();
        String transferId = process.getId();
        String assetId = process.getAssetId();
        String contractId = process.getContractId();

        TransferStats stats = new TransferStats(transferId, assetId, contractId);
        transferStatsMap.put(transferId, stats);

        insertData(contractId, assetId, id);

        // Initialize contract stats if not already present
        contractStatsMap.computeIfAbsent(contractId, k -> new ContractStats(contractId));


        monitor.info(String.format("[Transfer Monitor] Transfer initiated - ID: %s, Asset: %s, Contract: %s",
                transferId, assetId, contractId));
    }

    @Override
    public void started(TransferProcess process, TransferProcessStartedData additionalData) {
        String transferId = process.getId();
        TransferStats stats = transferStatsMap.get(transferId);

        if (stats != null) {
            // long estimatedSize = getEstimatedSize(dataAddress);
            // stats.setEstimatedSize(estimatedSize);
            // stats.setStartTime(Instant.now());

            // Update contract stats
            ContractStats contractStats = contractStatsMap.get(stats.getContractId());
            if (contractStats != null) {
                contractStats.incrementActiveTransfers();
            }

            monitor.info(String.format("[Transfer Monitor] Transfer started - ID: %s, Asset: %s, Contract: %s",
                    transferId, stats.getAssetId(), stats.getContractId()));

            //For testing data address
            DataAddress contentAddress = process.getContentDataAddress();
            monitor.info("Content Data Address - Type: " +
                    (contentAddress != null ? contentAddress.getType() : "null") +
                    ", Properties: " +
                    (contentAddress != null ? contentAddress.getProperties() : "{}"));

            // For data destination (where data is being transferred to)
            DataAddress destination = process.getDataDestination();
            monitor.info("Data Destination - Type: " +
                    (destination != null ? destination.getType() : "null") +
                    ", Properties: " +
                    (destination != null ? destination.getProperties() : "{}"));
        }
        this.completed(process);
    }

    // @Override
    // public void completed(TransferProcess process) {
    //         String transferId = process.getId();
    //         TransferStats stats = transferStatsMap.get(transferId);
    //         print("Transfer stats: ", stats )
    //         stats.setEndTime(Instant.now());
    //         Duration duration = Duration.between(stats.getStartTime(), stats.getEndTime());

    //         // In a real implementation, you would get the actual transferred bytes
    //         // For now, we'll use the estimated size
    //         long bytesTransferred = stats.getEstimatedSize();
    //         totalBytesTransferred.addAndGet(bytesTransferred);
    //         totalTransfersCompleted.incrementAndGet();

    //         // Update contract-specific statistics
    //         ContractStats contractStats = contractStatsMap.get(stats.getContractId());
    //         if (contractStats != null) {
    //             contractStats.addBytesTransferred(bytesTransferred);
    //             contractStats.incrementCompletedTransfers();
    //             contractStats.decrementActiveTransfers();
    //         }

    //         Calculate transfer rate
    //         double transferRateMbps = calculateTransferRateMbps(bytesTransferred, duration);

    //         Log individual transfer completion
    //         monitor.info(String.format(
    //                 "[Transfer Monitor] Transfer completed - ID: %s, Asset: %s, Contract: %s",
    //                 transferId, stats.getAssetId(), stats.getContractId()));

    //         // Log contract-specific statistics
    //         logContractStats(stats.getContractId());

    //         // Log global aggregated statistics
    //         logAggregatedStats();
    // }

    // @Override
    // public void terminated(TransferProcess process) {
    //     String transferId = process.getId();
    //     TransferStats stats = transferStatsMap.get(transferId);

    //     if (stats != null) {
    //         totalTransfersFailed.incrementAndGet();

    //         // Update contract-specific statistics
    //         ContractStats contractStats = contractStatsMap.get(stats.getContractId());
    //         if (contractStats != null) {
    //             contractStats.incrementFailedTransfers();
    //             contractStats.decrementActiveTransfers();
    //         }

    //         monitor.warning(String.format(
    //                 "[Transfer Monitor] Transfer terminated - ID: %s, Asset: %s, Contract: %s, Reason: %s",
    //                 transferId, stats.getAssetId(), stats.getContractId(), process.getErrorDetail()));

    //         // Log contract-specific statistics
    //         logContractStats(stats.getContractId());

    //         // Log global aggregated statistics
    //         logAggregatedStats();
    //     }
    // }

    /**
     * Extracts the estimated size from the data address properties.
     * In a real implementation, this would be more sophisticated.
     */
    private long getEstimatedSize(DataAddress dataAddress) {
        if (dataAddress == null) {
            return 0;
        }

        String sizeStr = dataAddress.getStringProperty("size");
        if (sizeStr != null) {
            try {
                return Long.parseLong(sizeStr);
            } catch (NumberFormatException e) {
                monitor.warning("Could not parse size from data address: " + sizeStr);
            }
        }

        // Default estimated size if not available
        return 1024 * 1024; // 1MB default
    }

    /**
     * Formats bytes into a human-readable string with commas as thousands separators.
     */
    private String formatBytes(long bytes) {
        return decimalFormat.format(bytes);
    }

    /**
     * Formats a duration into a human-readable string (e.g., "1m 30s 250ms").
     */
    private String formatDuration(Duration duration) {
        long totalMillis = duration.toMillis();
        long minutes = totalMillis / (60 * 1000);
        long seconds = (totalMillis % (60 * 1000)) / 1000;
        long millis = totalMillis % 1000;

        StringBuilder sb = new StringBuilder();
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || minutes > 0) {
            sb.append(seconds).append("s ");
        }
        sb.append(millis).append("ms");

        return sb.toString();
    }

    /**
     * Calculates the transfer rate in Mbps (Megabits per second).
     */
    private double calculateTransferRateMbps(long bytes, Duration duration) {
        double seconds = duration.toMillis() / 1000.0;
        if (seconds <= 0) {
            return 0.0;
        }

        // Convert bytes to bits and then to megabits
        double bits = bytes * 8.0;
        double megabits = bits / (1024 * 1024);

        return megabits / seconds;
    }

    /**
     * Inner class to track statistics for a single transfer.
     */
    private static class TransferStats {
        private final String transferId;
        private final String assetId;
        private final String contractId;
        private long estimatedSize;
        private Instant startTime;
        private Instant endTime;

        TransferStats(String transferId, String assetId, String contractId) {
            this.transferId = transferId;
            this.assetId = assetId;
            this.contractId = contractId;
        }

        public String getTransferId() {
            return transferId;
        }

        public String getAssetId() {
            return assetId;
        }

        public String getContractId() {
            return contractId;
        }

        public long getEstimatedSize() {
            return estimatedSize;
        }

        public void setEstimatedSize(long estimatedSize) {
            this.estimatedSize = estimatedSize;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
    }

    /**
     * Inner class to track statistics for a specific contract.
     */
    private static class ContractStats {
        private final String contractId;
        private final AtomicLong bytesTransferred = new AtomicLong(0);
        private final AtomicLong completedTransfers = new AtomicLong(0);
        private final AtomicLong failedTransfers = new AtomicLong(0);
        private final AtomicLong activeTransfers = new AtomicLong(0);

        ContractStats(String contractId) {
            this.contractId = contractId;
        }

        public String getContractId() {
            return contractId;
        }

        public long getBytesTransferred() {
            return bytesTransferred.get();
        }

        public void addBytesTransferred(long bytes) {
            bytesTransferred.addAndGet(bytes);
        }

        public long getCompletedTransfers() {
            return completedTransfers.get();
        }

        public void incrementCompletedTransfers() {
            completedTransfers.incrementAndGet();
        }

        public long getFailedTransfers() {
            return failedTransfers.get();
        }

        public void incrementFailedTransfers() {
            failedTransfers.incrementAndGet();
        }

        public long getActiveTransfers() {
            return activeTransfers.get();
        }

        public void incrementActiveTransfers() {
            activeTransfers.incrementAndGet();
        }

        public void decrementActiveTransfers() {
            activeTransfers.decrementAndGet();
        }
    }
}
