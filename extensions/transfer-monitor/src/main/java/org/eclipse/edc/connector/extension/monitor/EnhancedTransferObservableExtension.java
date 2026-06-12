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
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Extension that provides an enhanced implementation of TransferProcessObservable.
 * <p>
 * This extension registers an EnhancedTransferProcessObservable as a service, which can be
 * used to get more detailed monitoring of transfer processes. It's designed to be used as
 * an alternative to the default TransferProcessObservableImpl.
 */
@Provides(TransferProcessObservable.class)
@Extension(value = EnhancedTransferObservableExtension.NAME)
public class EnhancedTransferObservableExtension implements ServiceExtension {

    public static final String NAME = "Enhanced Transfer Observable";
    
    @Setting(value = "Enables or disables the enhanced transfer observable", defaultValue = "true")
    public static final String ENHANCED_OBSERVABLE_ENABLED = "edc.transfer.observable.enhanced.enabled";

    @Inject
    private Monitor monitor;

    private EnhancedTransferProcessObservable observable;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var enabled = context.getSetting(ENHANCED_OBSERVABLE_ENABLED, true);
        if (!enabled) {
            monitor.info("Enhanced Transfer Observable: disabled by configuration");
            return;
        }

        observable = new EnhancedTransferProcessObservable(monitor);
        context.registerService(TransferProcessObservable.class, observable);
        
        monitor.info("Enhanced Transfer Observable initialized and registered");
    }
}
