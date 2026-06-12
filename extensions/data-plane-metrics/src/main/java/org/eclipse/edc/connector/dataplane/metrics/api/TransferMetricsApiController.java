package org.eclipse.edc.connector.dataplane.metrics.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
//import org.eclipse.edc.connector.dataplane.metrics.store.ContractUsageStore;
import org.eclipse.edc.connector.dataplane.metrics.store.PostgresContractUsageStore;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/metrics/transfers")
@Produces(MediaType.APPLICATION_JSON)
public class TransferMetricsApiController {

    //private final ContractUsageStore usageStore;
    private final PostgresContractUsageStore usageStore;
    private final Monitor monitor;
    private static final String OPENAPI_RESOURCE_PATH = "openapi/mvd-openapi.json";

    public TransferMetricsApiController(PostgresContractUsageStore usageStore, Monitor monitor) {
        this.usageStore = usageStore;
        this.monitor = monitor;
    }

    @GET
    @Path("/swagger")
    @Produces(MediaType.TEXT_HTML)
    public Response swaggerUi() {
        var html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>MVD Swagger UI</title>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"/>
                  <style>
                    body { margin: 0; background: #f6f7f9; }
                    #swagger-ui { max-width: 1200px; margin: 0 auto; }
                    .doc-container { max-width: 1200px; margin: 24px auto 0; padding: 0 16px; }
                    details.doc { background: #fff; border: 1px solid #e4e7eb; border-radius: 8px; padding: 12px 16px; }
                    details.doc > summary { cursor: pointer; font-weight: 600; }
                    details.doc p { margin: 8px 0; }
                    details.doc code { background: #f0f3f7; padding: 2px 4px; border-radius: 4px; }
                    details.doc pre { background: #f0f3f7; padding: 10px; border-radius: 6px; overflow-x: auto; }
                  </style>
                </head>
                <body>
                  <div class="doc-container">
                    <h1>MVD API Documentation</h1>
                    <p>Welcome to the MVD API documentation. This is a placeholder for the API documentation content.</p>
                  </div>
                  <div id="swagger-ui"></div>
                  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                  <script>
                    const specPath = window.location.pathname.replace(/\\/swagger\\/?$/, '/swagger/openapi.json');
                    const specUrl = window.location.origin + specPath;
                    window.ui = SwaggerUIBundle({
                      url: specUrl,
                      dom_id: '#swagger-ui',
                      deepLinking: true,
                      presets: [SwaggerUIBundle.presets.apis],
                      layout: 'BaseLayout'
                    });
                  </script>
                </body>
                </html>
                """;

        return Response.ok()
                .entity(html)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .build();
    }

    @GET
    @Path("/swagger/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response openApiSpec(@Context HttpHeaders headers) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(OPENAPI_RESOURCE_PATH)) {
            if (stream == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "OpenAPI spec not found", "path", OPENAPI_RESOURCE_PATH))
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            }

            var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var updatedJson = applyDynamicServer(json, headers);
            return Response.ok()
                    .entity(updatedJson)
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {
            monitor.severe("Failed to load OpenAPI spec from classpath: " + OPENAPI_RESOURCE_PATH, e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to load OpenAPI spec"))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    private String applyDynamicServer(String json, HttpHeaders headers) throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.readTree(json);
        if (!(root instanceof ObjectNode rootObj)) {
            return json;
        }

        String hostHeader = headerValue(headers, "X-Forwarded-Host");
        if (hostHeader == null || hostHeader.isBlank()) {
            hostHeader = headerValue(headers, "Host");
        }

        String consumerBaseUrl;
        String providerBaseUrl;
        if (hostHeader != null && (hostHeader.startsWith("localhost") || hostHeader.startsWith("127.0.0.1"))) {
            consumerBaseUrl = "http://localhost/consumer/cp";
            providerBaseUrl = "http://localhost/provider-qna/public";
        } else {
            String hostOnly = hostHeader;
            if (hostOnly != null && hostOnly.contains(":")) {
                hostOnly = hostOnly.split(":", 2)[0];
            }
            String proto = headerValue(headers, "X-Forwarded-Proto");
            if (proto == null || proto.isBlank()) {
                proto = "https";
            }
            if (hostOnly == null || hostOnly.isBlank()) {
                return json;
            }
            consumerBaseUrl = proto + "://" + hostOnly + "/consumer/cp";
            providerBaseUrl = proto + "://" + hostOnly + "/provider-qna/public";
        }

        ArrayNode servers = mapper.createArrayNode();
        ObjectNode consumerServer = mapper.createObjectNode();
        consumerServer.put("url", consumerBaseUrl);
        consumerServer.put("description", "consumer_public_api");
        servers.add(consumerServer);

        ObjectNode providerServer = mapper.createObjectNode();
        providerServer.put("url", providerBaseUrl);
        providerServer.put("description", "provider_public_api");
        servers.add(providerServer);
        rootObj.set("servers", servers);
        return mapper.writeValueAsString(rootObj);
    }

    private String headerValue(HttpHeaders headers, String name) {
        if (headers == null) {
            return null;
        }
        var values = headers.getRequestHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    // @GET
    // @Path("/{transferProcessId}")
    // public Response getTransferredBytes(@PathParam("transferProcessId") String transferProcessId) {
    //     try {
    //         long bytes = usageStore.getTransferProcessUsage(transferProcessId);

    //         if (bytes == 0) {
    //             return Response.status(Response.Status.NOT_FOUND)
    //                     .entity(Map.of(
    //                             "message", "No metrics recorded for this transfer",
    //                             "transferProcessId", transferProcessId,
    //                             "likelyReason", "Transfer not completed or failed silently"
    //                     ))
    //                     .build();
    //         }

    //         return Response.ok()
    //                 .entity(Map.of(
    //                         "transferProcessId", transferProcessId,
    //                         "bytesTransferred", bytes
    //                 ))
    //                 .build();

    //     } catch (Exception e) {
    //         return Response.serverError()
    //                 .entity(Map.of(
    //                         "error", "Metrics retrieval failed",
    //                         "transferProcessId", transferProcessId,
    //                         "details", e.getMessage()
    //                 ))
    //                 .build();
    //     }
    // }

    // @GET
    // @Path("/contract/{contractId}")
    // public long getContractUsage(@PathParam("contractId") String contractId) {
    //     return usageStore.getContractUsage(contractId);
    // }

    // @GET
    // @Path("/contract/{contractId}/asset/{assetId}")
    // public long getContractAssetUsage(
    //         @PathParam("contractId") String contractId,
    //         @PathParam("assetId") String assetId) {
    //     return usageStore.getContractAssetUsage(contractId, assetId);
    // }

    // @GET
    // @Path("/contract/{contractId}/details")
    // public Map<String, Object> getContractDetails(@PathParam("contractId") String contractId) {
    //     var details = new HashMap<String, Object>();
    //     details.put("contractId", contractId);
    //     details.put("totalBytes", usageStore.getContractUsage(contractId));
    //     return details;
    // }

    @GET
    @Path("/users")
    public Response getAllUsersAndTransfers() {
        try {
            monitor.info("Fetching transfers from database...");
            var transfers = usageStore.getAllTransfersWithUsers();            

            if (transfers.isEmpty()) {
                monitor.info("In the transfer empty log");   
                return Response.ok()
                        .entity(Map.of(
                            "aggregated_bytes", 0L,
                            "latest_updated_at", 0L,
                            "users", new ArrayList<>()
                        ))
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            }

            double totalBytesTransferred = 0;
            long totalTransferTime = 0;

            // New: Group by userId -> providerId -> list of transfers
            Map<String, Map<String, List<Map<String, Object>>>> result = new HashMap<>();
            Map<String, Double> userProviderTotalBytes = new HashMap<>();

            for (Map<String, Object> transfer : transfers) {
                String userId = (String) transfer.get("assignee");
                String providerId = (String) transfer.get("assigner");                
                double bytes = (double) transfer.get("bytes_transferred");
                long transferTime = (long) transfer.get("transfer_time");                


                // Create transfer object
                Map<String, Object> contractTransfer = new HashMap<>();
                contractTransfer.put("contract_id", transfer.get("contract_id"));
                contractTransfer.put("bytes_transferred", bytes);
                contractTransfer.put("user_id", userId);
                contractTransfer.put("transfer_id", transfer.get("transfer_id"));
                contractTransfer.put("asset_id", transfer.get("asset_id"));
                contractTransfer.put("state", transfer.get("state"));
                contractTransfer.put("limit", transfer.get("limit"));
                contractTransfer.put("threshold", transfer.get("threshold"));
                contractTransfer.put("transfer_type", transfer.get("transfer_type"));
                contractTransfer.put("created_at", transfer.get("created_at"));
                contractTransfer.put("updated_at", transfer.get("updated_at"));
                contractTransfer.put("protocol", transfer.get("protocol"));
                contractTransfer.put("counter_party_address", transfer.get("counter_party_address"));
                contractTransfer.put("data_plane_id", transfer.get("data_plane_id"));
                contractTransfer.put("data_endpoint", transfer.get("data_endpoint"));
                contractTransfer.put("provider", providerId);

                // Insert into nested map
                result
                    .computeIfAbsent(userId, k -> new HashMap<>())
                    .computeIfAbsent(providerId, k -> new ArrayList<>())
                        .add(contractTransfer);

                // Update byte counters
                String key = userId + "::" + providerId;
                userProviderTotalBytes.merge(key, bytes, Double::sum);                
                totalTransferTime = transferTime; // will take the last one seen
            }

            // Build response list
            List<Map<String, Object>> response = new ArrayList<>();

            result.forEach((userId, providerMap) -> {
                providerMap.forEach((providerId, contracts) -> {
                    Map<String, Object> user = new HashMap<>();
                    user.put("user_id", userId);
                    user.put("provider", providerId);
                    user.put("contracts", contracts);

                    String key = userId + "::" + providerId;
                    user.put("total_bytes", userProviderTotalBytes.getOrDefault(key, 0.0));
                    user.put("data_plane_id", contracts.get(0).get("data_plane_id")); // assumes all from same plane

                    response.add(user);
                });
            });

            return Response.ok()
                    .entity(Map.of(
                        "transfer_time", totalTransferTime,
                        "aggregated_bytes", totalBytesTransferred,
                        "users", response
                    ))
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

    @GET
    @Path("/users/{id}")
    public Response getUsersWithId(@PathParam("id") String id) {
        try {
            monitor.info("Fetching transfers from database...");
            var transfers = usageStore.getUsersWithId(id);
            
            if (transfers.isEmpty()) {
                return Response.ok()
                        .entity(Map.of("users", new ArrayList<>()))
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            }

            // Group by user and collect all transfers
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            Map<String, Double> userTotalBytes = new HashMap<>();
            
            for (Map<String, Object> transfer : transfers) {
                String userId = (String) transfer.get("assignee");
                double bytes = (double) transfer.get("bytes_transferred");

                // Get or create user entry
                List<Map<String, Object>> userTransfers = result.computeIfAbsent(userId, k -> new ArrayList<>());
                
                // Create a new contract/transfer entry with all original fields
                Map<String, Object> contractTransfer = new HashMap<>();
                contractTransfer.put("contract_id", transfer.get("contract_id"));
                contractTransfer.put("bytes_transferred", bytes);
                contractTransfer.put("user_id", userId);
                contractTransfer.put("transfer_id", transfer.get("transfer_id"));
                contractTransfer.put("asset_id", transfer.get("asset_id"));
                contractTransfer.put("assigner", transfer.get("assigner"));

                // Add new transfer process fields
                contractTransfer.put("state", transfer.get("state"));
                contractTransfer.put("limit", transfer.get("limit"));
                contractTransfer.put("threshold", transfer.get("threshold"));
                contractTransfer.put("transfer_type", transfer.get("transfer_type"));
                contractTransfer.put("created_at", transfer.get("created_at"));
                contractTransfer.put("updated_at", transfer.get("updated_at"));
                contractTransfer.put("protocol", transfer.get("protocol"));
                contractTransfer.put("data_plane_id", transfer.get("data_plane_id"));
                contractTransfer.put("counter_party_address", transfer.get("counter_party_address"));
                
                contractTransfer.put("data_endpoint", transfer.get("data_endpoint"));
                
                userTransfers.add(contractTransfer);
                
                // Update total bytes for user
                userTotalBytes.merge(userId, bytes, Double::sum);
            }

            // Transform to final response structure
            List<Map<String, Object>> response = new ArrayList<>();
            result.forEach((userId, contracts) -> {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", userId);
                user.put("contracts", contracts);
                user.put("total_bytes", userTotalBytes.get(userId));
                response.add(user);
            });

            return Response.ok()
                    .entity(Map.of("users", response))
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

    @GET
    @Path("consumer/users/{id}")
    public Response getConsumerUsersWithId(@PathParam("id") String id) {
        try {
            monitor.info("Fetching transfers from database...");
            var transfers = usageStore.getConsumerUsersWithId(id);
            
            if (transfers.isEmpty()) {
                return Response.ok()
                        .entity(Map.of("users", new ArrayList<>()))
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            }

            // Group by user and collect all transfers
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
            Map<String, Double> userTotalBytes = new HashMap<>();
            
            for (Map<String, Object> transfer : transfers) {
                String userId = (String) transfer.get("assignee");
                double bytes = (double) transfer.get("bytes_transferred");

                // Get or create user entry
                List<Map<String, Object>> userTransfers = result.computeIfAbsent(userId, k -> new ArrayList<>());
                
                // Create a new contract/transfer entry with all original fields
                Map<String, Object> contractTransfer = new HashMap<>();
                contractTransfer.put("contract_id", transfer.get("contract_id"));
                contractTransfer.put("bytes_transferred", bytes);
                contractTransfer.put("user_id", userId);
                contractTransfer.put("transfer_id", transfer.get("transfer_id"));
                contractTransfer.put("asset_id", transfer.get("asset_id"));
                contractTransfer.put("assigner", transfer.get("assigner"));

                // Add new transfer process fields
                contractTransfer.put("state", transfer.get("state"));
                contractTransfer.put("limit", transfer.get("limit"));
                contractTransfer.put("threshold", transfer.get("threshold"));
                contractTransfer.put("transfer_type", transfer.get("transfer_type"));
                contractTransfer.put("created_at", transfer.get("created_at"));
                contractTransfer.put("updated_at", transfer.get("updated_at"));
                contractTransfer.put("protocol", transfer.get("protocol"));
                contractTransfer.put("data_plane_id", transfer.get("data_plane_id"));
                contractTransfer.put("counter_party_address", transfer.get("counter_party_address"));
                
                contractTransfer.put("data_endpoint", transfer.get("data_endpoint"));
                
                userTransfers.add(contractTransfer);
                
                // Update total bytes for user
                userTotalBytes.merge(userId, bytes, Double::sum);
            }

            // Transform to final response structure
            List<Map<String, Object>> response = new ArrayList<>();
            result.forEach((userId, contracts) -> {
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", userId);
                user.put("contracts", contracts);
                user.put("total_bytes", userTotalBytes.get(userId));
                response.add(user);
            });

            return Response.ok()
                    .entity(Map.of("users", response))
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

    @DELETE
    @Path("/terminate/{contractId}/{transferProcessId}")
    public Response terminateContract(@PathParam("contractId") String contractId, @PathParam("transferProcessId") String transferProcessId) {
        try {
            monitor.info("Terminating contract and access tokens for contract ID: " + contractId);
            
            // Delete access token data
            int deletedCount = usageStore.deleteAccessTokenDataByContractId(contractId, transferProcessId);
            
            if (deletedCount == 0) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of(
                            "error", "No access tokens found for contract",
                            "contractId", contractId
                        ))
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                        .build();
            }
            
            // Optionally update transfer state to TERMINATED if needed
            // usageStore.markTransferAsTerminated(contractId);
            
            return Response.ok()
                    .entity(Map.of(
                        "message", "Contract access terminated successfully",
                        "contractId", contractId,
                        "tokensDeleted", deletedCount
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
                    
        } catch (Exception e) {
            monitor.severe("Failed to terminate contract " + contractId, e);
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to terminate contract",
                        "contractId", contractId,
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @OPTIONS
    @Path("/terminate/{contractId}/{transferProcessId}")
    public Response optionsForTerminateContract(@PathParam("contractId") String contractId) {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .build();
    }

    @GET
    @Path("/updateLimit/{contractId}/{limit}")
    public Response updateContractLimit(@PathParam("contractId") String contractId, @PathParam("limit") Integer limit) {
        try {
            usageStore.updateContractLimit(contractId, limit);
            return Response.ok()
                    .entity(Map.of(
                        "message", "Limit has been updated sucessfully"                       
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",
                        "contractId", contractId,
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/updateThreshold/{contractId}/{threshold}")
    public Response updateThreshold(@PathParam("contractId") String contractId, @PathParam("threshold") Integer threshold) {
        try {
            usageStore.updateThreshold(contractId, threshold);
            return Response.ok()
                    .entity(Map.of(
                        "message", "Threshold has been updated sucessfully"                       
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",
                        "contractId", contractId,
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/insertAlerts")
    public Response insertAlerts(@QueryParam("contractId") String contractId, @QueryParam("alertType") String alertType, @QueryParam("alertMessage") String alertMessage, @QueryParam("consumerId") String consumerId) {
        try {
            usageStore.insertAlerts(contractId, alertType, alertMessage, consumerId);
            return Response.ok()
                    .entity(Map.of(
                        "message", "Alert has been added sucessfully"                       
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",
                        "contractId", contractId,
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }


    @GET
    @Path("/status/complete/{transfer_id}/{contract_id}")
    public Response completeTransfer(@PathParam("transfer_id") String transferId, @PathParam("contract_id") String contractId) {
        try {
            usageStore.completeTransfer(transferId, contractId);
            return Response.ok()
                    .entity(Map.of(
                        "message", "State updated successfully"
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",
                        "contractId", contractId,
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }


    @GET
    @Path("/getAlerts")
    public Response getAllAlerts() {
        try {
            var alerts = usageStore.getAllAlerts();
            return Response.ok()
                    .entity(Map.of("users", alerts))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",                        
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/getProviders")
    public Response getAllProviders() {
        try {
            var providers = usageStore.getAllProviders();
            return Response.ok()
                    .entity(Map.of("users", providers))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",                        
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/getAssets")
    public Response getAllAssets() {
        try {
            var assets = usageStore.getAllAssets();
            return Response.ok()
                    .entity(Map.of("users", assets))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",                        
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/getAllConsumers")
    public Response getAllConsumers() {
        try {
            var consumers = usageStore.getAllConsumers();
            return Response.ok()
                    .entity(Map.of("users", consumers))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",                        
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/dataplane/instances")
    public Response getAllDataPlaneInstances() {
        try {
            var instances = usageStore.getAllDataPlaneInstances();
            return Response.ok()
                    .entity(Map.of("instances", instances))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {
            monitor.severe("Failed to get data plane instances", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to get data plane instances"))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        }
    }

    @GET
    @Path("/dataplane/assets/{dbName}")
    public Response getAllDataPlaneAssets(@PathParam("dbName") String dbName) {
        try {
            var assets = usageStore.getAllDataPlaneAssets(dbName);
            return Response.ok()
                    .entity(Map.of("assets", assets))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {
            monitor.severe("Failed to get data plane instances", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to get data plane instances"))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        }
    }

    @GET
    @Path("/getPolicies")
    public Response getPolicies() {
        try {
            var policies = usageStore.getPolicies();
            return Response.ok()
                    .entity(Map.of("policies", policies))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .build();
        } catch (Exception e) {        
            return Response.serverError()
                    .entity(Map.of(
                        "error", "Failed to update limit for contract",                        
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

}
