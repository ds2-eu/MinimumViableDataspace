package org.eclipse.edc.connector.dataplane.metrics.store;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DatabaseConfigManager {
    private final List<Consumer<Map<String, String>>> listeners = new ArrayList<>();
    private final ServiceExtensionContext context;
    private final Monitor monitor;
    private Map<String, String> currentConfig = new HashMap<>();

    public DatabaseConfigManager(ServiceExtensionContext context, Monitor monitor) {
        this.context = context;
        this.monitor = monitor;
        loadConfig();
    }

    private void loadConfig() {
        var url = context.getSetting("edc.datasource.default.url", "");
        var user = context.getSetting("edc.datasource.default.user", "");
        var password = context.getSetting("edc.datasource.default.password", "");

        currentConfig.put("url", url);
        currentConfig.put("user", user);
        currentConfig.put("password", password);
        currentConfig.put("controlPlaneUrl", context.getSetting("edc.datasource.controlplane.url", url));
        currentConfig.put("controlPlaneUser", context.getSetting("edc.datasource.controlplane.user", user));
        currentConfig.put("controlPlanePassword", context.getSetting("edc.datasource.controlplane.password", password));
    }

    public boolean updateConfig(Map<String, String> newConfig) {
        try {
            var normalizedConfig = normalizeConfig(newConfig);
            if (!testConnection(normalizedConfig.get("url"), normalizedConfig.get("user"), normalizedConfig.get("password"))) {
                return false;
            }
            if (!testConnection(normalizedConfig.get("controlPlaneUrl"),
                    normalizedConfig.get("controlPlaneUser"), normalizedConfig.get("controlPlanePassword"))) {
                return false;
            }
            currentConfig = normalizedConfig;
            notifyListeners();
            return true;
        } catch (Exception e) {
            monitor.severe("Failed to update config", e);
            return false;
        }
    }

    private Map<String, String> normalizeConfig(Map<String, String> newConfig) {
        var normalizedConfig = new HashMap<String, String>();
        var url = newConfig.getOrDefault("url", currentConfig.get("url"));
        var user = newConfig.getOrDefault("user", currentConfig.get("user"));
        var password = newConfig.getOrDefault("password", currentConfig.get("password"));

        normalizedConfig.put("url", url);
        normalizedConfig.put("user", user);
        normalizedConfig.put("password", password);
        normalizedConfig.put("controlPlaneUrl", newConfig.getOrDefault("controlPlaneUrl", url));
        normalizedConfig.put("controlPlaneUser", newConfig.getOrDefault("controlPlaneUser", user));
        normalizedConfig.put("controlPlanePassword", newConfig.getOrDefault("controlPlanePassword", password));
        return normalizedConfig;
    }

    private boolean testConnection(String url, String user, String password) throws Exception {
        DriverManager.getConnection(url, user, password).close();
        return true;
    }

    public Map<String, String> getCurrentConfig() {
        return currentConfig;
    }

    public void addListener(Consumer<Map<String, String>> listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (var listener : listeners) {
            try {
                listener.accept(currentConfig);
            } catch (Exception e) {
                monitor.severe("Listener notification failed", e);
            }
        }
    }
}
