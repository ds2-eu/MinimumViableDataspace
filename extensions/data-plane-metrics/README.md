# Data Plane Metrics Extension

This extension adds data transfer metrics tracking capabilities to the Eclipse Dataspace Connector (EDC) Data Plane. It tracks byte counts for all data transfers, stores usage information by contract and asset, and exposes metrics via API endpoints and Micrometer.

## Features

- Counts bytes transferred in all data transfers
- Associates metrics with contract IDs, asset IDs, and transfer process IDs
- Stores usage data in a contract usage store 
- Exposes metrics via REST API and Micrometer
- Non-intrusive implementation that decorates existing services

## Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `edc.dataplane.metrics.enabled` | Enable/disable metrics collection | `true` |

## Usage

### Adding the Extension to Your Runtime

In your build.gradle.kts file:

```kotlin
dependencies {
    implementation(project(":extensions:data-plane:data-plane-metrics"))
}
```

### Accessing Metrics via API

The extension exposes metrics via the following API endpoints:

- `GET /metrics/transfers/{transferProcessId}` - Gets bytes transferred for a specific transfer process
- `GET /metrics/transfers/contract/{contractId}` - Gets total bytes transferred for a contract
- `GET /metrics/transfers/contract/{contractId}/asset/{assetId}` - Gets bytes transferred for a specific contract and asset
- `GET /metrics/transfers/contract/{contractId}/details` - Gets detailed metrics for a contract

### Programmatic Access

You can also access metrics programmatically by injecting the `ContractUsageStore`:

```java
@Inject
private ContractUsageStore usageStore;

public long getContractUsage(String contractId) {
    return usageStore.getContractUsage(contractId);
}
```

### Monitoring with Micrometer

The extension also reports metrics to Micrometer with the following metrics:

- `edc.transfer.bytes` - Counter for bytes transferred with tags:
  - `transferProcessId` - The ID of the transfer process
  - `contractId` - The contract ID
  - `assetId` - The asset ID

These metrics can be viewed in your monitoring system (e.g., Prometheus, Grafana) if you have the appropriate Micrometer exporters configured.

## Customization

### Custom Contract Usage Store

To use a custom implementation of the `ContractUsageStore` (e.g., for persistent storage), implement the `ContractUsageStore` interface and register it:

```java
@Provider
public ContractUsageStore myCustomContractUsageStore(ServiceExtensionContext context) {
    return new MyCustomContractUsageStore();
}
```

## Example Use Cases

1. **Usage Monitoring** - Track how much data is being transferred for each contract
2. **Billing** - Use transfer metrics for billing based on data volume
3. **Usage Limits** - Track usage against contractual limits
4. **Auditing** - Keep records of data transfer volumes for compliance 