package org.eclipse.edc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PostLog {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void postLog(String type, String text, String endpoint) {

        LogRequest logRequest = new LogRequest();
        logRequest.setUserId("user2Org1TEST");
        logRequest.setOrganization("Org1MSP");
        // Auto-generate a hex-based ID using UUID
        String generatedId = UUID.randomUUID().toString();
        logRequest.setId(generatedId);

        LogRequest.Payload payload = new LogRequest.Payload();
        payload.setText(text);

        LogRequest.ConnectorLog connectorLog = new LogRequest.ConnectorLog();
        connectorLog.setType(type);
        connectorLog.setPayload(payload);

        logRequest.setConnectorLog(connectorLog);

        String json = null;
        try {
            json = objectMapper.writeValueAsString(logRequest);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to writeValueAsString JsonProcessingException: " + e.getMessage());
        }

        try {
            sendPostRequest(json, endpoint);
        } catch (Exception e) {
            System.err.println("Failed to sendPostRequest Exception: " + e.getMessage());
        }
    }

    private void sendPostRequest(String payload, String url) throws Exception {
        try {
            URL requestUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                System.err.println("Warning: log endpoint returned " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("Failed to send log: " + e.getMessage());
        }
    }
}
