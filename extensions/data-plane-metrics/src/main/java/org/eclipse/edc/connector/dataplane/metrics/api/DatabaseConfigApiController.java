package org.eclipse.edc.connector.dataplane.metrics.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.dataplane.metrics.DataPlaneMetricsExtension;
import org.eclipse.edc.connector.dataplane.metrics.store.DatabaseConfigManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

@Path("/database-config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DatabaseConfigApiController {

    private final DatabaseConfigManager configManager;
    private final DataPlaneMetricsExtension extension;
    private final Monitor monitor;

    public DatabaseConfigApiController(DataPlaneMetricsExtension extension, DatabaseConfigManager configManager, Monitor monitor) {
        this.configManager = configManager;
        this.monitor = monitor;
        this.extension = extension;
    }

    @GET
    public Response getCurrentConfig() {
        try {
            Map<String, String> config = configManager.getCurrentConfig();
            return Response.ok(config)
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")            
            .build();
        } catch (Exception e) {
            monitor.severe("Error retrieving database configuration", e);
            return Response.serverError()
            .entity("Error retrieving configuration")
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
            .build();
        }
    }

    @POST
    public Response updateConfig(Map<String, String> newConfig) {
        try {
            if (newConfig == null) {
                throw new InvalidRequestException("Configuration cannot be null");
            }
            
            boolean success = configManager.updateConfig(newConfig);
            monitor.info("Testing the update config: " + success);
            if (success) {
                return Response.ok()
                .entity("Database configuration updated successfully")                
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")                
                .build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                .entity("Failed to update database configuration")
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
                .build();
            }
        } catch (InvalidRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(e.getMessage())
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
                .build();
        } catch (Exception e) {
            monitor.severe("Error updating database configuration", e);
            return Response.serverError()
            .entity("Error updating configuration")
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
            .build();
        }
    }

    @POST
    @Path("/test")
    public Response testConnection(Map<String, String> config) {
        try {
            if (config == null || 
                    !config.containsKey("url") || 
                    !config.containsKey("user") || 
                    !config.containsKey("password")) {
                
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of(
                            "success", false,
                            "error", "Missing required parameters (url, user, password)"
                        ))
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
                        .build();
            }

            DriverManager.getConnection(
                config.get("url"),
                config.get("user"),
                config.get("password")
            ).close();

            return Response.ok()
                    .entity(Map.of("success", true))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
                    .build();

        } catch (SQLException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                        "success", false,
                        "error", "Connection failed",
                        "details", e.getMessage()
                    ))
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
                    .build();
        }
    }

    @POST
    @Path("/state")
    public Response setMetricsState(Map<String, Boolean> request) {
        try {
            monitor.info("Metrics state request: " + request);
            if (request == null || !request.containsKey("enabled")) {
                throw new InvalidRequestException("Request must contain 'enabled' field");
            }
            
            boolean enabled = request.get("enabled");
            extension.setMetricsEnabled(enabled);
            
            monitor.info("Metrics state updated to: " + enabled);
            return Response.ok(Map.of(
                "success", true,
                "message", "Metrics state updated",
                "enabled", enabled
            ))
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
                .build();
        } catch (Exception e) {
            monitor.severe("Error retrieving database configuration", e);
            return Response.serverError()
            .entity("Error retrieving configuration")
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")   
            .build();
        }
    }

    @OPTIONS
    @Path("/test")
    public Response testConnectionOptions() {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type")
                .build();
    }

    @OPTIONS
    public Response optionsForUpdateConfig() {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .build();
    }
    
    @OPTIONS
    @Path("/state")
    public Response options() {
        return createCorsResponse(Response.ok());
    }

    private Response createCorsResponse(Response.ResponseBuilder responseBuilder) {
        return responseBuilder
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
            .header("Access-Control-Allow-Credentials", "true")
            .header("Access-Control-Max-Age", "3600")
            .build();
    }
}