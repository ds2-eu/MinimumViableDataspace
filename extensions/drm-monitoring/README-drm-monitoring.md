# drmMonitoring Extension — Configuration Guide

This document is for whoever assembles/deploys the **runtime (launcher) module** that includes the `drmMonitoring` extension. It explains what settings the extension expects and how to supply them.

## Settings

| Setting Key | Type | Default | Description |
|---|---|---|---|
| `edc.controlplane.drm.monitor.endpoint` | String | `http://localhost:8080/default` | URL of the DRM monitor endpoint the extension will call/report to. |
| `edc.controlplane.drm.monitor.enabled` | boolean | `false` | Enables or disables DRM monitoring at runtime. |

These are declared in the extension source via `@Setting`, which only documents them for tooling — it does **not** load values automatically. The extension reads them at startup using:

```java
    @Setting(
        key = "edc.controlplane.drm.monitor.endpoint",
        description = "URL to post connector logs to DRM",
        defaultValue = "http://10.151.64.56:8001/receiveConnectorLog"
)
private static final String DRM_MONITOR_ENDPOINT = "edc.controlplane.drm.monitor.endpoint";

@Setting(
        key = "edc.controlplane.drm.monitor.enabled",
        description = "Enable or disable the DRM monitoring extension",
        defaultValue = "false"
)
private static final String DRM_MONITOR_ENABLED = "edc.controlplane.drm.monitor.enabled";
```

## How to configure

The extension does **not** bundle its own `config.properties` for runtime purposes — that file only ships defaults inside the jar and is not read by EDC's core configuration mechanism. To configure this extension for an actual deployment, the values must be supplied through the **runtime module's** external configuration, using one of the following (in order of precedence, per EDC convention):

1. **Environment variables** (highest precedence)
   ```bash
   EDC_CONTROLPLANE_DRM_MONITOR_ENDPOINT=https://drm.example.com/monitor
   EDC_CONTROLPLANE_DRM_MONITOR_ENABLED=true
   ```

2. **System properties**
   ```bash
   -Dedc.controlplane.drm.monitor.endpoint=https://drm.example.com/monitor \
   -Dedc.controlplane.drm.monitor.enabled=true
   ```

3. **`config.properties`** — pointed to by the `edc.fs.config` setting on the runtime module (not inside this extension's own resources):
   ```properties
   edc.controlplane.drm.monitor.endpoint=https://drm.example.com/monitor
   edc.controlplane.drm.monitor.enabled=true
   ```

## Steps to enable this extension in a runtime

1. Add the `drmMonitoring` extension as a dependency in the runtime module's `build.gradle.kts`.
2. In the runtime's config file (the one referenced by `edc.fs.config`), add the two keys above with real values.
3. Confirm `edc.controlplane.drm.monitor.enabled=true` if monitoring should be active for this deployment; leave `false` or omit it to disable.
4. Start the connector — on startup, the extension will log/use whichever endpoint value it resolved (env var → system property → config file → built-in default, in that order).

## Notes

- Omitting these keys entirely is safe — the extension falls back to its `@Setting` defaults (`http://localhost:8080/default`, disabled).
- If monitoring is enabled but no real endpoint is set, the extension will still start but will point at the placeholder default — check logs for a startup warning if one is implemented.
