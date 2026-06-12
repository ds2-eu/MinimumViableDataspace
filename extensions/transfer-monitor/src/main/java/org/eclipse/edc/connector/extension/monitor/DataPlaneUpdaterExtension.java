// package org.eclipse.edc.connector.extension.monitor;
// import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
// import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
// import org.eclipse.edc.connector.extension.monitor.api.TransferMetricsApiController;
// import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
// import org.eclipse.edc.runtime.metamodel.annotation.Extension;
// import org.eclipse.edc.runtime.metamodel.annotation.Inject;
// import org.eclipse.edc.runtime.metamodel.annotation.Provides;
// import org.eclipse.edc.runtime.metamodel.annotation.Setting;
// import org.eclipse.edc.spi.monitor.Monitor;
// import org.eclipse.edc.spi.system.ServiceExtension;
// import org.eclipse.edc.spi.system.ServiceExtensionContext;
// import org.eclipse.edc.web.spi.WebService;
// import org.eclipse.edc.web.spi.configuration.ApiContext;

// import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
// import org.eclipse.edc.web.spi.configuration.context.ManagementApiUrl;

// @Provides(ManagementApiUrl.class)
// @Extension("Testing Data Plane Updater Extension")
// public class DataPlaneUpdaterExtension implements ServiceExtension {

//     // @Setting(required = false, defaultValue = "true")
//     // private static final String ENABLE_METRICS_SETTING = "edc.management.context.enabled";

//     // private static final int DEFAULT_MANAGEMENT_PORT = 8191;
//     // private static final String DEFAULT_MANAGEMENT_PATH = "api/management";

//     @Configuration
//     private ManagementApiConfiguration apiConfiguration;

//     @Inject
//     private TransferProcessStore defaultStore;

//     @Inject
//     private Monitor monitor;

//     @Inject
//     private WebService webService;

//     @Inject
//     private PortMappingRegistry portMappingRegistry;

//     private PostgresContractUsageStore usageStore;
    
//     public void initialize(ServiceExtensionContext context, TransferProcess process) {
//         monitor.info("Testing port for MANAGEMENT API: " + apiConfiguration.getPort());
//         monitor.info("Testing path for MANAGEMENT API: " + apiConfiguration.getPath());

      

//         var enhancedStore = new DataPlaneUpdaterStore(defaultStore, monitor);
//         context.registerService(TransferProcessStore.class, enhancedStore);

//         usageStore = new PostgresContractUsageStore(monitor, enhancedStore, process);

//         var apiController = new TransferMetricsApiController(usageStore, monitor);
//         webService.registerResource(ApiContext.MANAGEMENT, apiController);
//         monitor.info("Data Plane Updater Extension initialized");
//     }
//     @Settings
//     record ManagementApiConfiguration(
//             @Setting(key = "web.http." + ApiContext.MANAGEMENT + ".port", description = "Port for " + ApiContext.MANAGEMENT + " api context", defaultValue = DEFAULT_MANAGEMENT_PORT + "")
//             int port,
//             @Setting(key = "web.http." + ApiContext.MANAGEMENT + ".path", description = "Path for " + ApiContext.MANAGEMENT + " api context", defaultValue = DEFAULT_MANAGEMENT_PATH)
//             String path
//     ) {

//     }
// }

package org.eclipse.edc.connector.extension.monitor;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
//import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.extension.monitor.api.TransferMetricsApiController;
//import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.edc.web.spi.configuration.context.ManagementApiUrl;
//import org.eclipse.edc.web.spi.configuration.PortMapping;

@Provides(ManagementApiUrl.class)
@Extension("Testing Data Plane Updater Extension")
public class DataPlaneUpdaterExtension implements ServiceExtension {

    private static final int DEFAULT_MANAGEMENT_PORT = 8191;
    private static final String DEFAULT_MANAGEMENT_PATH = "api/management";

    @Setting(key = "web.http." + ApiContext.MANAGEMENT + ".port", 
             description = "Port for " + ApiContext.MANAGEMENT + " api context", 
             defaultValue = "8191")
    private int managementPort;

    @Setting(key = "web.http." + ApiContext.MANAGEMENT + ".path", 
             description = "Path for " + ApiContext.MANAGEMENT + " api context", 
             defaultValue = "api/management")
    private String managementPath;

    @Inject
    private TransferProcessStore defaultStore;

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    @Inject
    private PortMappingRegistry portMappingRegistry;

    // @Inject
    // private TransferProcess process; 

    private PostgresContractUsageStore usageStore;

    public void initialize(ServiceExtensionContext context) {        
        monitor.info("Testing port for MANAGEMENT API: " + managementPort);
        monitor.info("Testing path for MANAGEMENT API: " + managementPath);

        // var enableMetrics = context.getSetting(ENABLE_METRICS_SETTING, true);
        // if (!enableMetrics) {
        //     monitor.info("Data Plane Metrics extension is disabled");
        //     return;
        // }

        // var portMapping = new PortMapping(ApiContext.MANAGEMENT, DEFAULT_MANAGEMENT_PORT, DEFAULT_MANAGEMENT_PATH);
        // portMappingRegistry.register(portMapping);

        var enhancedStore = new DataPlaneUpdaterStore(defaultStore, monitor);
        context.registerService(TransferProcessStore.class, enhancedStore);

        usageStore = new PostgresContractUsageStore(monitor, enhancedStore);

        var apiController = new TransferMetricsApiController(usageStore, monitor, defaultStore);
        webService.registerResource(ApiContext.MANAGEMENT, apiController);

        monitor.info("Data Plane Updater Extension initialized");
    }
}
