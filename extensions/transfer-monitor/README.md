# Transfer Monitor Extension

This extension monitors data transfers between participants in the Eclipse Dataspace Connector (EDC) and logs aggregated statistics about the amount of data exchanged.

## Features

- Tracks individual transfer processes from initiation to completion
- Logs detailed information about each transfer (ID, asset, contract, size, duration)
- Maintains and logs aggregated statistics (total transfers completed/failed, total bytes transferred)
- Tracks statistics by contract ID
- Calculates and logs transfer rates in Mbps
- Provides human-readable formatting for bytes and durations
- Configurable via runtime settings

## Components

This extension consists of two main components:

1. **TransferMonitorExtension**: Registers a listener with the TransferProcessObservable to track data transfer events
2. **EnhancedTransferObservableExtension**: Provides an enhanced implementation of TransferProcessObservable with additional monitoring capabilities

## Usage

### 1. Include the extension in your runtime

Add the extension to your runtime's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":extensions:common:transfer-monitor"))
}
```

### 2. Configuration

The extension can be configured using the following properties:

| Property | Description | Default |
| -------- | ----------- | ------- |
| `edc.transfer.monitor.enabled` | Enables or disables the transfer monitoring extension | `true` |
| `edc.transfer.observable.enhanced.enabled` | Enables or disables the enhanced transfer observable | `true` |

### 3. Log Output

The extension logs information at various stages of the transfer process:

#### Transfer Initiated
```
[Transfer Monitor] Transfer initiated - ID: 12345, Asset: asset-id-123, Contract: contract-id-456
```

#### Transfer Started
```
[Transfer Monitor] Transfer started - ID: 12345, Asset: asset-id-123, Contract: contract-id-456, Estimated size: 1,048,576 bytes
```

#### Transfer Completed
```
[Transfer Monitor] Transfer completed - ID: 12345, Asset: asset-id-123, Contract: contract-id-456, Size: 1,048,576 bytes, Duration: 1s 500ms, Rate: 5.60 Mbps
```

#### Contract-specific Statistics
```
[Transfer Monitor] Contract Statistics - Contract: contract-id-456, Completed: 5, Failed: 1, Active: 2, Total Data: 5,242,880 bytes
```

#### Aggregated Statistics
```
[Transfer Monitor] Aggregated Statistics - Total Transfers Completed: 10, Total Transfers Failed: 2, Total Data Transferred: 10,485,760 bytes
```

## Using the Enhanced Observable

The enhanced observable provides additional methods to manually trigger transfer process events, which can be useful for testing or for integrating with external monitoring systems:

```java
@Inject
private TransferProcessObservable transferProcessObservable;

public void monitorExternalTransfer(String transferId, String assetId, String contractId, long size) {
    // Cast to our enhanced implementation
    if (transferProcessObservable instanceof EnhancedTransferProcessObservable) {
        EnhancedTransferProcessObservable enhancedObservable = 
                (EnhancedTransferProcessObservable) transferProcessObservable;
        
        // Create a transfer process
        TransferProcess process = TransferProcess.Builder.newInstance()
                .id(transferId)
                .assetId(assetId)
                .contractId(contractId)
                .build();
        
        // Manually trigger events
        enhancedObservable.notifyInitiated(process);
        // ... later
        enhancedObservable.notifyCompleted(process);
    }
}
```

## Extending

This extension can be extended to:

1. Store statistics in a database
2. Expose metrics via Micrometer for monitoring systems
3. Send notifications when certain thresholds are reached
4. Generate reports on data exchange patterns

## Implementation Details

The extension implements the `TransferProcessListener` interface to monitor the lifecycle of transfer processes. It maintains statistics in memory and logs them at appropriate points in the transfer lifecycle.

The `EnhancedTransferProcessObservable` extends the default `TransferProcessObservableImpl` to provide additional monitoring capabilities and manual event triggering.

For production use, consider extending this implementation to persist statistics to a database or integrate with a monitoring system.
