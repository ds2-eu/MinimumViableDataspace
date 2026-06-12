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
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Enhanced implementation of TransferProcessObservable that provides additional monitoring capabilities.
 * <p>
 * This class implements the TransferProcessObservable interface to add custom logging and monitoring
 * of transfer process events. It can be used as a drop-in replacement for the default implementation
 * to provide more detailed insights into data transfers.
 */
public class EnhancedTransferProcessObservable implements TransferProcessObservable {

    private final Monitor monitor;
    private final Collection<TransferProcessListener> listeners = new ConcurrentLinkedQueue<>();

    public EnhancedTransferProcessObservable(Monitor monitor) {
        this.monitor = monitor;
        monitor.info("Enhanced Transfer Process Observable initialized");
    }

    @Override
    public Collection<TransferProcessListener> getListeners() {
        return listeners;
    }

    @Override
    public void registerListener(TransferProcessListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            monitor.debug("Registered TransferProcessListener: " + listener.getClass().getName());
        }
    }

    @Override
    public void unregisterListener(TransferProcessListener listener) {
        listeners.remove(listener);
        monitor.debug("Unregistered TransferProcessListener: " + listener.getClass().getName());
    }

    /**
     * Notifies all listeners that a transfer process has been initiated.
     * This method can be called to manually trigger the initiated event.
     */
    public void notifyInitiated(TransferProcess process) {
        monitor.debug("Notifying listeners of transfer process initiated: " + process.getId());
        getListeners().forEach(listener -> listener.initiated(process));
    }

    /**
     * Notifies all listeners that a transfer process has been started.
     * This method can be called to manually trigger the started event.
     */
    public void notifyStarted(TransferProcess process, TransferProcessStartedData data) {
        monitor.debug("Notifying listeners of transfer process started: " + process.getId());
        getListeners().forEach(listener -> listener.started(process, data));
    }

    /**
     * Notifies all listeners that a transfer process has been completed.
     * This method can be called to manually trigger the completed event.
     */
    public void notifyCompleted(TransferProcess process) {
        monitor.debug("Notifying listeners of transfer process completed: " + process.getId());
        getListeners().forEach(listener -> listener.completed(process));
    }

    /**
     * Notifies all listeners that a transfer process has been terminated.
     * This method can be called to manually trigger the terminated event.
     */
    public void notifyTerminated(TransferProcess process) {
        monitor.debug("Notifying listeners of transfer process terminated: " + process.getId());
        getListeners().forEach(listener -> listener.terminated(process));
    }

    /**
     * Notifies all listeners that a transfer process has been requested.
     */
    public void notifyRequested(TransferProcess process) {
        monitor.debug("Notifying listeners of transfer process requested: " + process.getId());
        getListeners().forEach(listener -> listener.requested(process));
    }

    /**
     * Notifies all listeners that a transfer process provisioning has been requested.
     */
    public void notifyProvisioningRequested(TransferProcess process) {
        monitor.debug("Notifying listeners of transfer process provisioning requested: " + process.getId());
        getListeners().forEach(listener -> listener.provisioningRequested(process));
    }

    /**
     * Notifies all listeners that a transfer process has been provisioned.
     */
    public void notifyProvisioned(TransferProcess process) {
        monitor.debug("Notifying listeners of transfer process provisioned: " + process.getId());
        getListeners().forEach(listener -> listener.provisioned(process));
    }
}
