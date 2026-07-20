package org.eclipse.edc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogRequest {
    private String userId;
    private String organization;
    private String id;
    private ConnectorLog connectorLog;
    private String logType = "connector";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConnectorLog {
        private String type;
        private Payload payload;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Payload {
        private String text;
    }
}
