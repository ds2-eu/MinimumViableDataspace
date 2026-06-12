package org.eclipse.edc.connector.dataplane.metrics;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;

public class TokenInterceptor implements ContainerRequestFilter {

    private final ThreadLocal<String> tokenHolder = new ThreadLocal<>();
    private final Monitor monitor;

    public TokenInterceptor(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        monitor.info("TokenInterceptor.filter() fired, auth: " + requestContext.getHeaderString("Authorization"));


        monitor.info("TokenInterceptor thread: " + Thread.currentThread().getName());

        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader != null) {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            monitor.info("TokenInterceptor: captured JWT from request" + token);
            tokenHolder.set(token);
            monitor.debug("TokenInterceptor: captured JWT from request");
        }
    }

    public String getToken() {
        return tokenHolder.get();
    }

    public void clear() {
        tokenHolder.remove();
    }
}