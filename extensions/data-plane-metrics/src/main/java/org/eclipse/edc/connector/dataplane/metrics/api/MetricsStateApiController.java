/*
 *  Copyright (c) 2024 Contributor to Eclipse Foundation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.edc.connector.dataplane.metrics.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.dataplane.metrics.DataPlaneMetricsExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;

import java.util.Map;



@Path("/metrics/state")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MetricsStateApiController {
    private final DataPlaneMetricsExtension extension;
    private final Monitor monitor;

    public MetricsStateApiController(DataPlaneMetricsExtension extension, Monitor monitor) {
        this.extension = extension;
        this.monitor = monitor;
    }

    @POST
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
    @Path("/metrics/state")
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