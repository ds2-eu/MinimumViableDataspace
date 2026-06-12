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

import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
//import org.eclipse.edc.spi.command.CommandHandlerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Extension that monitors data transfer processes and logs aggregated statistics.
 * <p>
 * This extension registers a listener with the TransferProcessObservable to track
 * data transfer events and log information about the amount of data exchanged.
 * It provides detailed logging of transfer processes including size, duration, and
 * aggregated statistics by contract.
 */
@Extension(value = TransferMonitorExtension.NAME)
public class TransferMonitorExtension implements ServiceExtension {

    public static final String NAME = "Transfer Monitor Extension";
    
    @Setting(value = "Enables or disables the transfer monitoring extension", defaultValue = "true")
    public static final String TRANSFER_MONITOR_ENABLED = "edc.transfer.monitor.enabled";

    @Inject
    private TransferProcessStore transferProcessStore;

    @Inject
    private TransferProcessObservable transferProcessObservable;

    @Inject
    private Monitor monitor;    
    private DataTransferMonitor dataTransferMonitor;

    @Setting(value = "Transfer monitor database URL", defaultValue = "")
    private String dbUrl;
    @Setting(value = "Transfer monitor database user", defaultValue = "")
    private String dbUser;
    @Setting(value = "Transfer monitor database password", defaultValue = "")
    private String dbPassword;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var enabled = context.getSetting(TRANSFER_MONITOR_ENABLED, true);
        if (!enabled) {
            monitor.info("Transfer Monitor Extension: disabled by configuration");
            return;
        }

        dbUrl = context.getSetting("edc.datasource.default.url", dbUrl);
        dbUser = context.getSetting("edc.datasource.default.user", dbUser);
        dbPassword = context.getSetting("edc.datasource.default.password", dbPassword);

        dataTransferMonitor = new DataTransferMonitor(monitor, transferProcessStore, dbUrl, dbUser, dbPassword);
        monitor.info("Transfer Monitor Extension initialized and ready to monitor data transfers");
    }
    
    @Override
    public void start() {
        if (dataTransferMonitor != null) {
            transferProcessObservable.registerListener(dataTransferMonitor);
            monitor.info("Transfer Monitor Extension: started and registered with TransferProcessObservable");
        }
    }
    
    @Override
    public void shutdown() {
        if (dataTransferMonitor != null) {
            transferProcessObservable.unregisterListener(dataTransferMonitor);
            monitor.info("Transfer Monitor Extension: shutdown and unregistered from TransferProcessObservable");
        }
    }
    
    /**
     * Sets the monitor for testing purposes.
     */
    void setMonitor(Monitor monitor) {
        this.monitor = monitor;
    }
    
    /**
     * Sets the transfer process observable for testing purposes.
     */
    void setTransferProcessObservable(TransferProcessObservable transferProcessObservable) {
        this.transferProcessObservable = transferProcessObservable;
    }
}
