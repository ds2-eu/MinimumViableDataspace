package org.eclipse.edc.connector.extension.monitor;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;

/**
 * PostgreSQL implementation of ContractUsageStore.
 */
public class PostgresContractUsageStore {    
    private final Monitor monitor;   
    private final DataPlaneUpdaterStore dataPlaneUpdaterStore; 

    public PostgresContractUsageStore(Monitor monitor, DataPlaneUpdaterStore dataPlaneUpdaterStore) {        
        this.monitor = monitor;  
        this.dataPlaneUpdaterStore = dataPlaneUpdaterStore;   
        //this.process = process;   
    }    
    
    public void testing(TransferProcess process) {
        monitor.info("This is a testing request and it is working");  
        String testDataPlaneId = "test-dataplane-789";
        String processId = process.getId();
        monitor.info("Process ID: " + processId);  
        StoreResult<Void> result = dataPlaneUpdaterStore.updateDataPlaneId(testDataPlaneId, processId);
        if (result.succeeded()) {
            monitor.info("Test successful - data plane ID updated");
        } else {
            monitor.severe("Test failed: " + result.getFailureDetail());
        }
    }    
}