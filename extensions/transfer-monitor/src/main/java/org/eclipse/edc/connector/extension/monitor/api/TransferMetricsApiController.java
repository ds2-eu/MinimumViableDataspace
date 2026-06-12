package org.eclipse.edc.connector.extension.monitor.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.extension.monitor.PostgresContractUsageStore;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Map;

@Path("/dataplane")
@Produces(MediaType.APPLICATION_JSON)
public class TransferMetricsApiController {

    private final PostgresContractUsageStore usageStore;
    private final Monitor monitor;
    private final TransferProcessStore transferProcessStore;
    //private final TransferProcess process;

    public TransferMetricsApiController(PostgresContractUsageStore usageStore, Monitor monitor, TransferProcessStore transferProcessStore) {
        this.usageStore = usageStore;
        this.monitor = monitor;
        this.transferProcessStore = transferProcessStore;
    }

    @GET
    @Path("/testing/{processId}")
    public Response testUpdate(@PathParam("processId") String processId) {
        try {
            TransferProcess process = transferProcessStore.findById(processId);
            monitor.info("Fetching transfers from database...");
            usageStore.testing(process);

            return Response.ok()
                    .entity(Map.of("users", "This is a testing response of success"))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {
            monitor.severe("Failed to get all users and transfers", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to get all users and transfers"))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        }
    }
}