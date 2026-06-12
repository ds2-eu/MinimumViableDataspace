package org.eclipse.edc.connector.dataplane.metrics.store;

//import com.fasterxml.jackson.databind.JsonNode;  
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.monitor.Monitor;

// 1. Java standard imports (java., javax.)
//import java.io.IOException;  // Add this
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;


/**
 * PostgreSQL implementation of ContractUsageStore.
 */
public class PostgresContractUsageStore {
    private static final String ALERTS_DATABASE = "alerts";
    private static final String IDENTITY_DATABASE = "identity";
    private  String dbUrl;
    private  String dbUser;
    private  String dbPassword;
    private String controlPlaneDbUrl;
    private String controlPlaneDbUser;
    private String controlPlaneDbPassword;
    private String alertDbUrl;
    private String identityDbUrl;
    private final Monitor monitor;    
    private final ReentrantLock connectionLock = new ReentrantLock();


    public PostgresContractUsageStore(String dbUrl, String dbUser, String dbPassword, Monitor monitor) {
        this(dbUrl, dbUser, dbPassword, dbUrl, dbUser, dbPassword, monitor);
    }

    public PostgresContractUsageStore(String dbUrl, String dbUser, String dbPassword,
            String controlPlaneDbUrl, String controlPlaneDbUser, String controlPlaneDbPassword, Monitor monitor) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.controlPlaneDbUrl = controlPlaneDbUrl;
        this.controlPlaneDbUser = controlPlaneDbUser;
        this.controlPlaneDbPassword = controlPlaneDbPassword;
        this.monitor = monitor;
        initializeAuxiliaryDatabases();
    }

    public void updateConfiguration(String newDbUrl, String newDbUser, String newDbPassword) {
        updateConfiguration(newDbUrl, newDbUser, newDbPassword, newDbUrl, newDbUser, newDbPassword);
    }

    public void updateConfiguration(String newDbUrl, String newDbUser, String newDbPassword,
            String newControlPlaneDbUrl, String newControlPlaneDbUser, String newControlPlaneDbPassword) {
        connectionLock.lock();
        try {
            this.dbUrl = newDbUrl;
            this.dbUser = newDbUser;
            this.dbPassword = newDbPassword;
            this.controlPlaneDbUrl = newControlPlaneDbUrl;
            this.controlPlaneDbUser = newControlPlaneDbUser;
            this.controlPlaneDbPassword = newControlPlaneDbPassword;
            monitor.info("Updated database configuration for metrics store");
            initializeAuxiliaryDatabases();
        } finally {
            connectionLock.unlock();
        }
    }

    private Connection getConnection() throws SQLException {
        connectionLock.lock();
        try {
            monitor.debug("Getting connection to: " + dbUrl);
            return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } finally {
            connectionLock.unlock();
        }
    }

    private Connection getAlertConnection() throws SQLException {
        return DriverManager.getConnection(alertDbUrl, dbUser, dbPassword);
    }

    private Connection getProviderConnection() throws SQLException {
        return DriverManager.getConnection(identityDbUrl, dbUser, dbPassword);
    }

    private Connection getControlPlaneConnection() throws SQLException {
        monitor.debug("Getting control plane connection to: " + controlPlaneDbUrl);
        return DriverManager.getConnection(controlPlaneDbUrl, controlPlaneDbUser, controlPlaneDbPassword);
    }

    private void initializeAuxiliaryDatabases() {
        alertDbUrl = initializeAuxiliaryDatabase(ALERTS_DATABASE, """
                CREATE TABLE IF NOT EXISTS alerts (
                    contract_id    VARCHAR NOT NULL,
                    alert_type     VARCHAR NOT NULL,
                    alert_message  TEXT    NOT NULL,
                    consumer_id    VARCHAR NOT NULL,
                    created_at     TIMESTAMP DEFAULT now()
                )
                """);

        identityDbUrl = initializeAuxiliaryDatabase(IDENTITY_DATABASE, """
                CREATE TABLE IF NOT EXISTS participant_context (
                    participant_context_id VARCHAR PRIMARY KEY,
                    created_date           VARCHAR,
                    last_modified_date     VARCHAR,
                    state                  VARCHAR,
                    did                    VARCHAR
                )
                """);

        initializeMainDatabaseSchema();
    }

    private String initializeAuxiliaryDatabase(String databaseName, String schemaSql) {
        var auxiliaryDbUrl = databaseUrl(databaseName);
        try {
            ensureDatabaseExists(databaseName);
            try (var conn = DriverManager.getConnection(auxiliaryDbUrl, dbUser, dbPassword);
                    var stmt = conn.prepareStatement(schemaSql)) {
                stmt.executeUpdate();
            }
            monitor.info("Initialized metrics database: " + databaseName);
            return auxiliaryDbUrl;
        } catch (SQLException e) {
            monitor.warning("Could not initialize database '" + databaseName + "'. Falling back to main connector database: " + e.getMessage());
            try (var conn = getConnection();
                    var stmt = conn.prepareStatement(schemaSql)) {
                stmt.executeUpdate();
            } catch (SQLException fallbackException) {
                monitor.severe("Could not initialize fallback schema for '" + databaseName + "'", fallbackException);
            }
            return dbUrl;
        }
    }

    private void initializeMainDatabaseSchema() {
        String transferMetadataSql = """
                CREATE TABLE IF NOT EXISTS transfer_metadata (
                    contract_id     VARCHAR,
                    asset_id        VARCHAR,
                    id              VARCHAR,
                    transfer_limit  INTEGER DEFAULT 0
                )
                """;

        try (var conn = getConnection();
                var stmt = conn.prepareStatement(transferMetadataSql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            monitor.warning("Could not initialize transfer_metadata table in main connector database: " + e.getMessage());
        }
    }

    private void ensureDatabaseExists(String databaseName) throws SQLException {
        try (var rootConn = getRootConnection()) {
            if (databaseExists(rootConn, databaseName)) {
                return;
            }
            rootConn.setAutoCommit(true);
            try (var stmt = rootConn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE " + databaseName);
            }
        }
    }

    private boolean databaseExists(Connection conn, String databaseName) throws SQLException {
        String query = "SELECT 1 FROM pg_database WHERE datname = ?";
        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, databaseName);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String databaseUrl(String databaseName) {
        return dbUrl.replaceFirst("/[^/?]+(\\?.*)?$", "/" + databaseName + "$1");
    }
    
    public void recordUsage(String contractId, String assetId, String transferProcessId, long bytes) {
        monitor.debug("Recording usage in PostgresContractUsageStore");
        monitor.debug("Contract ID: " + contractId);
        monitor.debug("Asset ID: " + assetId);
        monitor.debug("Transfer Process ID: " + transferProcessId);
        monitor.debug("Bytes: " + bytes);

        try (Connection conn = getConnection()) {
            processDatabase(contractId, assetId, transferProcessId, bytes, conn, false);
        } catch (SQLException e) {
            monitor.severe("Failed to process primary database", e);
            throw new RuntimeException("Failed to record usage in primary database", e);
        }
    }

    private void processDatabase(String contractId, String assetId, String transferProcessId, long bytes, 
                            Connection conn, boolean isConsumerDb) {
        try {
            double currentTransferredBytes = 0.0;
            long limit = 100;
            long threshold = 70;
            String processId = "";            

            // Step 1: Read the existing bytes_transferred from additional_properties
            String selectSql = isConsumerDb ? 
                    """
                SELECT COALESCE((etp.private_properties->>'bytes_transferred')::numeric, 0.0) AS bytes_transferred,
                    COALESCE((etp.private_properties->>'limit')::bigint, 100) AS limit,
                    COALESCE((etp.private_properties->>'threshold')::bigint, 70) AS threshold                    
                FROM edc_transfer_process etp                  
                WHERE etp.contract_id = ?
                LIMIT 1
                    """ : 
                    // Same query for both databases in this case
                    """
                SELECT COALESCE((etp.private_properties->>'bytes_transferred')::numeric, 0.0) AS bytes_transferred,
                    COALESCE((etp.private_properties->>'limit')::bigint, 100) AS limit,
                    COALESCE((etp.private_properties->>'threshold')::bigint, 70) AS threshold                   
                FROM edc_transfer_process etp                  
                WHERE etp.contract_id = ?
                LIMIT 1
                    """;

            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, contractId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        currentTransferredBytes = rs.getDouble("bytes_transferred");
                        limit = rs.getLong("limit");
                        threshold = rs.getLong("threshold");                                          
                    }
                }
            }
            monitor.info("Contract id is: " + contractId);
            monitor.info("Asset id is: " + assetId);
            monitor.info("Transfer process id is: " + transferProcessId);
            monitor.info("Bytes log: " + bytes);            
            double newTransferKilobytes = (double) bytes / 1000;   
            monitor.info("current transfer kbs: " + currentTransferredBytes);
            monitor.info("transfer kbs: " + newTransferKilobytes);                  
            double newTransferredBytes = currentTransferredBytes + newTransferKilobytes;  
            monitor.info("new transfer logs for provider: " + newTransferredBytes); 
                
            // Step 3: Update bytes_transferred in private_properties
            String updateSql = isConsumerDb ?
                    """
                UPDATE edc_transfer_process
                SET private_properties = 
                    CASE
                        WHEN private_properties IS NULL THEN 
                            jsonb_build_object('bytes_transferred', ?, 'limit', ?, 'threshold', ?)
                        ELSE 
                            private_properties::jsonb 
                            || jsonb_build_object('bytes_transferred', ?, 'limit', ?, 'threshold', ?)
                    END
                WHERE contract_id = ?
                    """ :
                    """
                UPDATE edc_transfer_process
                SET private_properties = 
                    CASE
                        WHEN private_properties IS NULL THEN 
                            jsonb_build_object('bytes_transferred', ?, 'limit', ?, 'threshold', ?)
                        ELSE 
                            private_properties::jsonb 
                            || jsonb_build_object('bytes_transferred', ?, 'limit', ?, 'threshold', ?)
                    END
                WHERE contract_id = ?
                    """;

            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {                
                updateStmt.setDouble(1, newTransferredBytes); 
                updateStmt.setLong(2, limit); 
                updateStmt.setLong(3, threshold); 
                updateStmt.setDouble(4, newTransferredBytes);                    
                updateStmt.setLong(5, limit); 
                updateStmt.setLong(6, threshold);                
                updateStmt.setString(7, contractId);  

                int updated = updateStmt.executeUpdate();
                monitor.info("Updated " + updated + " rows for contract " + contractId +
                            " in " + (isConsumerDb ? "consumer" : "primary") + 
                            " DB. Added " + bytes + " bytes (total now: " + newTransferredBytes + ")");
            }

            // Skip the contract negotiation part for consumer DB to avoid circular dependency
            if (!isConsumerDb) {
                String accessTokenSql = """
                    SELECT 
                        claim_token->'claims'->>'iss' AS assigner,
                        claim_token->'claims'->>'aud' AS assignee
                    FROM edc_accesstokendata
                    WHERE additional_properties->>'agreement_id' = ?
                    LIMIT 1
                            """;

                try (Connection controlPlaneConn = getControlPlaneConnection();
                        PreparedStatement stmt = controlPlaneConn.prepareStatement(accessTokenSql)) {
                    stmt.setString(1, contractId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String assigner = rs.getString("assigner");
                            String assignee = rs.getString("assignee");
                            if (assigner != null && !assigner.isEmpty() && assignee != null && !assignee.isEmpty()) {
                                updateAssignerInfo(contractId, assigner, assignee, conn, isConsumerDb);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            monitor.severe("Failed to record usage in " + (isConsumerDb ? "consumer" : "primary") + " database", e);
            throw new RuntimeException("Failed to record usage", e); 
        }
    }

    private void updateAssignerInfo(String contractId, String assigner, String assignee, 
                                Connection conn, boolean isConsumerDb) throws SQLException {
        String updateAssignerSql = """
            UPDATE edc_transfer_process
            SET private_properties =
                CASE
                    WHEN private_properties IS NULL THEN 
                        jsonb_build_object('assigner', ?, 'assignee', ?)
                    ELSE 
                        private_properties::jsonb 
                        || jsonb_build_object('assigner', ?, 'assignee', ?)
                END
            WHERE contract_id = ?
                """;
        
        try (PreparedStatement updateAssignerStmt = conn.prepareStatement(updateAssignerSql)) {
            updateAssignerStmt.setString(1, assigner);
            updateAssignerStmt.setString(2, assignee);
            updateAssignerStmt.setString(3, assigner);
            updateAssignerStmt.setString(4, assignee);
            updateAssignerStmt.setString(5, contractId);

            int updated = updateAssignerStmt.executeUpdate();
            monitor.info("Updated " + updated + " transfer process records with assigner and assignee in " + 
                        (isConsumerDb ? "consumer" : "primary") + " DB");
        }
        String updateNegotiationSql = """
            UPDATE edc_contract_negotiation
            SET contract_offers = (
                SELECT jsonb_agg(
                    jsonb_set(
                        jsonb_set(offer, '{policy,assigner}', to_jsonb(?::text)),
                        '{policy,assignee}', to_jsonb(?::text)
                    )
                )
                FROM jsonb_array_elements(contract_offers::jsonb) AS offer
            )
            WHERE agreement_id = ?
                """;

        try (PreparedStatement updateNegotiationStmt = conn.prepareStatement(updateNegotiationSql)) {
            updateNegotiationStmt.setString(1, assigner);
            updateNegotiationStmt.setString(2, assignee);
            updateNegotiationStmt.setString(3, contractId);

            int updated = updateNegotiationStmt.executeUpdate();
            monitor.info("Updated " + updated + " contract negotiation records with assigner/assignee");
        }
    }
        
    public List<Map<String, Object>> getAllTransfersWithUsers() {
        String query = """
                    SELECT                     
                    etp.state,
                    etp.transfer_type,
                    etp.created_at,
                    etp.updated_at,
                    etp.protocol,
                    etp.counter_party_address,
                    etp.data_plane_id,
                    etp.correlation_id as transfer_id,
                    etp.private_properties->>'consumer_id' as user_id,
                    etp.private_properties->>'provider_id' as provider,
                    etp.private_properties->>'assigner' as assigner,
                    etp.private_properties->>'assignee' as assignee,
                    etp.private_properties->>'transfer_time' as transfer_time,
                    COALESCE((etp.private_properties->>'limit')::bigint, 100) as limit,
                    COALESCE((etp.private_properties->>'threshold')::bigint, 70) as threshold,
                    etp.content_data_address->'properties'->>'https://w3id.org/edc/v0.0.1/ns/baseUrl' as data_endpoint,   
                    etp.contract_id,
                    etp.asset_id,                                     
                    COALESCE((etp.private_properties->>'bytes_transferred')::numeric, 0.0) as bytes_transferred                    
                FROM edc_transfer_process etp                                
                WHERE etp.private_properties->>'assignee' IS NOT NULL
                    """;

        List<Map<String, Object>> transfers = new ArrayList<>();
        
        try (
                Connection conn = getConnection();
                var stmt = conn.prepareStatement(query)
        ) {
            var rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> transfer = new HashMap<>();
                transfer.put("transfer_id", rs.getString("transfer_id"));
                transfer.put("user_id", rs.getString("user_id"));
                transfer.put("provider", rs.getString("provider"));
                transfer.put("contract_id", rs.getString("contract_id"));
                transfer.put("asset_id", rs.getString("asset_id"));
                transfer.put("bytes_transferred", rs.getDouble("bytes_transferred"));  
                transfer.put("assigner", rs.getString("assigner"));  // New field
                transfer.put("assignee", rs.getString("assignee"));  // New field              
                
                // Add new fields from edc_transfer_process
                transfer.put("state", rs.getInt("state"));
                transfer.put("limit", rs.getInt("limit"));
                transfer.put("threshold", rs.getInt("threshold"));
                transfer.put("transfer_type", rs.getString("transfer_type"));
                transfer.put("created_at", rs.getLong("created_at"));
                transfer.put("updated_at", rs.getLong("updated_at"));
                transfer.put("transfer_time", rs.getLong("transfer_time"));
                transfer.put("protocol", rs.getString("protocol"));
                transfer.put("data_plane_id", rs.getString("data_plane_id"));
                transfer.put("counter_party_address", rs.getString("counter_party_address"));
                transfer.put("data_endpoint", rs.getString("data_endpoint"));
                
                transfers.add(transfer);
            }
        } catch (SQLException e) {
            monitor.severe("Error getting transfers", e);
        }
        return transfers;
    }


    

    public List<Map<String, Object>> getUsersWithId(String id) {        
        String query = """
                    SELECT                     
                    etp.state,
                    etp.transfer_type,
                    etp.created_at,
                    etp.updated_at,
                    etp.protocol,
                    etp.counter_party_address,
                    etp.data_plane_id,
                    etp.correlation_id as transfer_id,
                    etp.private_properties->>'consumer_id' as user_id,  
                    etp.private_properties->>'assigner' as assigner,
                    etp.private_properties->>'assignee' as assignee,                  
                    COALESCE((etp.private_properties->>'limit')::bigint, 100) as limit,
                    COALESCE((etp.private_properties->>'threshold')::bigint, 70) as threshold,
                    etp.content_data_address->'properties'->>'https://w3id.org/edc/v0.0.1/ns/baseUrl' as data_endpoint,   
                    etp.contract_id,
                    etp.asset_id,                                     
                    COALESCE((etp.private_properties->>'bytes_transferred')::numeric, 0.0) as bytes_transferred                    
                FROM edc_transfer_process etp   
                WHERE REPLACE(etp.private_properties->>'assignee', '%3A', ':') = ?
                        """;

        List<Map<String, Object>> transfers = new ArrayList<>();
        
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, id); 
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> transfer = new HashMap<>();
                transfer.put("transfer_id", rs.getString("transfer_id"));
                transfer.put("user_id", rs.getString("user_id"));                
                transfer.put("contract_id", rs.getString("contract_id"));
                transfer.put("asset_id", rs.getString("asset_id"));
                transfer.put("bytes_transferred", rs.getDouble("bytes_transferred"));  
                transfer.put("assigner", rs.getString("assigner"));  // New field
                transfer.put("assignee", rs.getString("assignee"));  // New field                

                // Add new fields from edc_transfer_process
                transfer.put("state", rs.getInt("state"));
                transfer.put("threshold", rs.getInt("threshold"));
                transfer.put("limit", rs.getInt("limit"));
                transfer.put("transfer_type", rs.getString("transfer_type"));
                transfer.put("created_at", rs.getLong("created_at"));
                transfer.put("updated_at", rs.getLong("updated_at"));
                transfer.put("protocol", rs.getString("protocol"));
                transfer.put("data_plane_id", rs.getString("data_plane_id"));
                transfer.put("counter_party_address", rs.getString("counter_party_address"));
                transfer.put("data_endpoint", rs.getString("data_endpoint"));              
                transfers.add(transfer);
            }
        } catch (SQLException e) {
            monitor.severe("Error getting transfers", e);
        }
        return transfers;
    }


    public List<Map<String, Object>> getConsumerUsersWithId(String id) {        
        String query = """
                    SELECT                     
                    etp.state,
                    etp.transfer_type,
                    etp.created_at,
                    etp.updated_at,
                    etp.protocol,
                    etp.counter_party_address,
                    etp.data_plane_id,
                    etp.correlation_id as transfer_id,
                    etp.private_properties->>'consumer_id' as user_id,  
                    etp.private_properties->>'assigner' as assigner,
                    etp.private_properties->>'assignee' as assignee,                  
                    COALESCE((etp.private_properties->>'limit')::bigint, 100) as limit,
                    COALESCE((etp.private_properties->>'threshold')::bigint, 70) as threshold,
                    etp.content_data_address->'properties'->>'https://w3id.org/edc/v0.0.1/ns/baseUrl' as data_endpoint,   
                    etp.contract_id,
                    etp.asset_id,                                     
                    COALESCE((etp.private_properties->>'bytes_transferred')::numeric, 0.0) as bytes_transferred                    
                FROM edc_transfer_process etp   
                WHERE REPLACE(etp.private_properties->>'assignee', '%3A', ':') = ?
                        """;

        List<Map<String, Object>> transfers = new ArrayList<>();
        
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, id); 
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> transfer = new HashMap<>();
                transfer.put("transfer_id", rs.getString("transfer_id"));
                transfer.put("user_id", rs.getString("user_id"));                
                transfer.put("contract_id", rs.getString("contract_id"));
                transfer.put("asset_id", rs.getString("asset_id"));
                transfer.put("bytes_transferred", rs.getDouble("bytes_transferred"));  
                transfer.put("assigner", rs.getString("assigner"));  // New field
                transfer.put("assignee", rs.getString("assignee"));  // New field                

                // Add new fields from edc_transfer_process
                transfer.put("state", rs.getInt("state"));
                transfer.put("threshold", rs.getInt("threshold"));
                transfer.put("limit", rs.getInt("limit"));
                transfer.put("transfer_type", rs.getString("transfer_type"));
                transfer.put("created_at", rs.getLong("created_at"));
                transfer.put("updated_at", rs.getLong("updated_at"));
                transfer.put("protocol", rs.getString("protocol"));
                transfer.put("data_plane_id", rs.getString("data_plane_id"));
                transfer.put("counter_party_address", rs.getString("counter_party_address"));
                transfer.put("data_endpoint", rs.getString("data_endpoint"));              
                transfers.add(transfer);
            }
        } catch (SQLException e) {
            monitor.severe("Error getting transfers", e);
        }
        return transfers;
    }

    public Map<String, String> getTransferMetadata(String transferId) {
        String query = """
            SELECT contract_id, asset_id FROM transfer_metadata
            WHERE id = ?
                """;

        Map<String, String> metadata = new HashMap<>();

        try (Connection conn = getConnection();
                var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, transferId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    metadata.put("contractId", rs.getString("contract_id"));
                    metadata.put("assetId", rs.getString("asset_id"));
                }
            }
        } catch (SQLException e) {
            monitor.warning("Error fetching transfer metadata from main database: " + e.getMessage());
        }

        return metadata;
    }

    public int deleteAccessTokenDataByContractId(String contractId, String transferProcessId) {
        // Use transaction to ensure both operations succeed or fail together
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Delete from access token table
            String deleteTokenQuery = "DELETE FROM edc_accesstokendata WHERE additional_properties->>'agreement_id' = ?";
            try (var tokenStatement = conn.prepareStatement(deleteTokenQuery)) {
                tokenStatement.setString(1, contractId);
                tokenStatement.executeUpdate();
            }
            
            // 2. Delete from transfer process table
            String deleteTransferQuery = "DELETE FROM edc_transfer_process WHERE transferprocess_id = ?";
            try (var transferStatement = conn.prepareStatement(deleteTransferQuery)) {
                transferStatement.setString(1, transferProcessId);
                int transferDeletions = transferStatement.executeUpdate();
                conn.commit(); // Commit if both succeed
                return transferDeletions;
            }
            
        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback(); // Rollback if anything fails
                }
            } catch (SQLException rollbackEx) {
                // Log rollback failure but throw original exception
                monitor.severe("Failed to rollback transaction", rollbackEx);
            }
            throw new RuntimeException(String.format(
                "Failed to delete data for contract %s and transfer process %s", 
                contractId, transferProcessId), e);
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true); // Reset autocommit
                    conn.close();
                }
            } catch (SQLException closeEx) {
                monitor.warning("Failed to clean up connection", closeEx);
            }
        }
    }


    public void updateContractLimit(String contractId, Integer limit) {
        // Use transaction to ensure both operations succeed or fail together
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Delete from access token table
            String updateLimit = """
                    UPDATE edc_transfer_process
                    SET private_properties = 
                        CASE
                            WHEN private_properties IS NULL THEN 
                                jsonb_build_object('limit', ?)
                            ELSE 
                                private_properties::jsonb
                                || jsonb_build_object('limit', ?)
                        END
                    WHERE contract_id = ?
                        """;
            try (var tokenStatement = conn.prepareStatement(updateLimit)) {

                tokenStatement.setLong(1, limit);
                tokenStatement.setLong(2, limit);
                tokenStatement.setString(3, contractId);
                tokenStatement.executeUpdate();
                conn.commit();
            }                       
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete data for contract %s and transfer process %s", e);
        } 
    }

    public void updateThreshold(String contractId, Integer threshold) {
        // Use transaction to ensure both operations succeed or fail together
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Delete from access token table
            String updateLimit = """
                    UPDATE edc_transfer_process
                    SET private_properties = 
                        CASE
                            WHEN private_properties IS NULL THEN 
                                jsonb_build_object('threshold', ?)
                            ELSE 
                                private_properties::jsonb
                                || jsonb_build_object('threshold', ?)
                        END
                    WHERE contract_id = ?
                        """;
            try (var tokenStatement = conn.prepareStatement(updateLimit)) {

                tokenStatement.setLong(1, threshold);
                tokenStatement.setLong(2, threshold);
                tokenStatement.setString(3, contractId);
                tokenStatement.executeUpdate();
                conn.commit();
            }                       
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete data for contract %s and transfer process %s", e);
        } 
    }

    public void insertAlerts(String contractId, String alertType, String alertMessage, String consumerId) {
        Connection conn = null;
        try {
            conn = getAlertConnection();
            conn.setAutoCommit(false); // Start transaction

            String selectQuery = """
                SELECT * FROM alerts
                WHERE contract_id = ? AND alert_type = ? AND alert_message = ? AND consumer_id = ?
                    """;

            try (var selectStmt = conn.prepareStatement(selectQuery)) {
                selectStmt.setString(1, contractId);
                selectStmt.setString(2, alertType);
                selectStmt.setString(3, alertMessage);
                selectStmt.setString(4, consumerId);

                try (var rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        // If alert exists, update the created_at timestamp
                        String updateQuery = """
                            UPDATE alerts
                            SET created_at = CURRENT_TIMESTAMP
                            WHERE contract_id = ? AND alert_type = ? AND alert_message = ? AND consumer_id = ?
                                """;
                        try (var updateStmt = conn.prepareStatement(updateQuery)) {
                            updateStmt.setString(1, contractId);
                            updateStmt.setString(2, alertType);
                            updateStmt.setString(3, alertMessage);
                            updateStmt.setString(4, consumerId);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // Otherwise, insert a new alert
                        String insertQuery = """
                            INSERT INTO alerts (contract_id, alert_type, alert_message, consumer_id)
                            VALUES (?, ?, ?, ?)
                                """;
                        try (var insertStmt = conn.prepareStatement(insertQuery)) {
                            insertStmt.setString(1, contractId);
                            insertStmt.setString(2, alertType);
                            insertStmt.setString(3, alertMessage);
                            insertStmt.setString(4, consumerId);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            throw new RuntimeException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }


    public void completeTransfer(String transferId, String contractId) {
        Connection conn = null;
        try {
            conn = getConnection();        
            conn.setAutoCommit(false);
            // Otherwise, insert a new alert
            String insertQuery = """
                UPDATE edc_transfer_process
                SET state = 800
                WHERE contract_id = ?
                    """;                            
            try (var insertStmt = conn.prepareStatement(insertQuery)) {
                insertStmt.setString(1, contractId);                            
                insertStmt.executeUpdate();
            }                   
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            throw new RuntimeException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }


    public List<Map<String, Object>> getAllAlerts() {
        String query = """
                    SELECT * FROM alerts                    
                    """;
        
        List<Map<String, Object>> alerts = new ArrayList<>();

        try (
                Connection conn = getAlertConnection();
                var stmt = conn.prepareStatement(query)
        ) {
            var rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("contract_id", rs.getString("contract_id"));
                alert.put("alert_type", rs.getString("alert_type"));
                alert.put("alert_message", rs.getString("alert_message"));
                alert.put("consumer_id", rs.getString("consumer_id"));
                alert.put("created_at", rs.getTimestamp("created_at").getTime());
                
                alerts.add(alert);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return alerts;

    }

    public List<Map<String, Object>> getAllProviders() {
        String query = """
                    SELECT * FROM participant_context                    
                    """;
        
        List<Map<String, Object>> providers = new ArrayList<>();

        try (
                Connection conn = getProviderConnection();
                var stmt = conn.prepareStatement(query)
        ) {
            var rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> provider = new HashMap<>();
                provider.put("participant_context_id", rs.getString("participant_context_id"));
                provider.put("created_date", rs.getString("created_date"));
                provider.put("last_modified_date", rs.getString("last_modified_date"));
                provider.put("state", rs.getString("state"));
                provider.put("did", rs.getString("did"));
                
                providers.add(provider);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return providers;

    }

    public List<Map<String, Object>> getAllAssets() {
        String query = """
                    SELECT * FROM edc_asset                    
                    """;
        
        List<Map<String, Object>> assets = new ArrayList<>();

        try (
                Connection conn = getConnection();
                var stmt = conn.prepareStatement(query)
        ) {
            var rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> asset = new HashMap<>();
                asset.put("asset_id", rs.getString("asset_id"));
                asset.put("properties", rs.getString("properties"));
                asset.put("data_address", rs.getString("data_address"));                
                
                assets.add(asset);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return assets;

    }

    public List<Map<String, Object>> getAllConsumers() {
        String query = """
                    SELECT * FROM edc_contract_negotiation                    
                    """;
        
        List<Map<String, Object>> consumers = new ArrayList<>();

        try (
                Connection conn = getControlPlaneConnection();
                var stmt = conn.prepareStatement(query)
        ) {
            var rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> consumer = new HashMap<>();
                consumer.put("id", rs.getString("id"));
                consumer.put("created_at", rs.getString("created_at"));
                consumer.put("correlation_id", rs.getString("correlation_id"));  
                consumer.put("counterparty_id", rs.getString("counterparty_id"));
                consumer.put("counterparty_address", rs.getString("counterparty_address"));
                consumer.put("type", rs.getString("type"));         
                consumer.put("state", rs.getString("state"));         
                consumer.put("agreement_id", rs.getString("agreement_id"));         
                consumer.put("contract_offers", rs.getString("contract_offers"));         

                
                consumers.add(consumer);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return consumers;

    }
    
    public List<Map<String, Object>> getAllDataPlaneInstances() {
        List<Map<String, Object>> allInstances = new ArrayList<>();
        
        // First connect to a root database to get the list
        try (Connection rootConn = getRootConnection()) {
            // Get list of all databases
            List<String> databases = getDatabaseList(rootConn);
            
            // Now scan each database
            for (String dbName : databases) {
                if (shouldSkipDatabase(dbName)) {
                    continue;
                }
                
                try (Connection dbConn = getConnectionToDatabase(dbName)) {
                    if (tableExists(dbConn, "edc_data_plane_instance")) {
                        List<Map<String, Object>> instances = getDataPlaneInstances(dbConn);
                        allInstances.addAll(instances);
                    }
                } catch (SQLException e) {
                    monitor.warning("Error accessing database " + dbName + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            monitor.severe("Error getting database list", e);
            throw new RuntimeException("Failed to get data plane instances", e);
        }
        
        return allInstances;
    }

    public List<Map<String, Object>> getAllDataPlaneAssets(String dbName) {
        List<Map<String, Object>> allAssets = new ArrayList<>();
        try (Connection dbConn = getConnectionToDatabase(dbName)) {
            if (tableExists(dbConn, "edc_asset")) {
                List<Map<String, Object>> instances = getDataPlaneAssets(dbConn);
                allAssets.addAll(instances);
            }
        } catch (SQLException e) {
            monitor.warning("Error accessing database " + dbName + ": " + e.getMessage());
        }
        
        
        return allAssets;
    }

    private List<String> getDatabaseList(Connection conn) throws SQLException {
        List<String> databases = new ArrayList<>();
        String query = "SELECT datname FROM pg_database WHERE datistemplate = false";
        
        try (PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                databases.add(rs.getString("datname"));
            }
        }
        return databases;
    }

    private Connection getRootConnection() throws SQLException {
        connectionLock.lock();
        try {
            // Modify the URL to connect to a known database (like 'postgres')
            String rootDbUrl = dbUrl.replaceFirst("/[^/]+$", "/postgres");
            monitor.debug("Getting root connection to: " + rootDbUrl);
            return DriverManager.getConnection(rootDbUrl, dbUser, dbPassword);
        } finally {
            connectionLock.unlock();
        }
    }

    private boolean shouldSkipDatabase(String dbName) {
        // Skip system databases
        return dbName.equals("postgres") || dbName.equals("template0") || dbName.equals("template1");
    }

    private Connection getConnectionToDatabase(String dbName) throws SQLException {
        connectionLock.lock();
        try {
            String specificDbUrl = dbUrl.replaceFirst("/[^/]+$", "/" + dbName);
            monitor.debug("Getting connection to database: " + dbName);
            return DriverManager.getConnection(specificDbUrl, dbUser, dbPassword);
        } finally {
            connectionLock.unlock();
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String query = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private List<Map<String, Object>> getDataPlaneInstances(Connection conn) throws SQLException {
        List<Map<String, Object>> instances = new ArrayList<>();
        String query = "SELECT * FROM edc_data_plane_instance";
        
        try (PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> instance = new HashMap<>();
                instance.put("id", rs.getString("id"));
                instance.put("data", rs.getString("data"));
                instance.put("lease_id", rs.getString("lease_id"));
                
                // Parse JSON data if needed
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> dataMap = mapper.readValue(rs.getString("data"), Map.class);
                    instance.putAll(dataMap);
                } catch (Exception e) {
                    monitor.warning("Failed to parse data plane instance JSON", e);
                }
                
                instances.add(instance);
            }
        }
        return instances;
    }

    private List<Map<String, Object>> getDataPlaneAssets(Connection conn) throws SQLException {
        List<Map<String, Object>> instances = new ArrayList<>();
        String query = "SELECT * FROM edc_asset";
        
        try (PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> instance = new HashMap<>();
                instance.put("asset_id", rs.getString("asset_id"));
                instance.put("created_at", rs.getLong("created_at"));
                instance.put("properties", rs.getString("properties"));
                instance.put("data_address", rs.getString("data_address"));
                instance.put("private_properties", rs.getString("private_properties"));
                
                // Parse JSON data if needed
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> dataMap = mapper.readValue(rs.getString("data"), Map.class);
                    instance.putAll(dataMap);
                } catch (Exception e) {
                    monitor.warning("Failed to parse data plane instance JSON", e);
                }
                
                instances.add(instance);
            }
        }
        return instances;
    }    

    public List<Map<String, Object>> getPolicies() {
        String query = "SELECT * from edc_policydefinitions";

        List<Map<String, Object>> policies = new ArrayList<>();
        
        try (
                Connection conn = getConnection();
                var stmt = conn.prepareStatement(query)
        ) {
            var rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> policy = new HashMap<>();
                policy.put("policy_id", rs.getString("policy_id"));  
                policy.put("created_at", rs.getString("created_at"));   
                policy.put("profiles", rs.getString("profiles"));       
                policy.put("extensible_properties", rs.getString("extensible_properties"));       
                policy.put("inherits_from", rs.getString("inherits_from")); 
                policy.put("duties", rs.getString("duties"));     
                policy.put("assigner", rs.getString("assigner"));     
                policy.put("assignee", rs.getString("assignee"));    
                policy.put("target", rs.getString("target"));     
                policy.put("policy_type", rs.getString("policy_type"));  
                policy.put("prohibitions", rs.getString("prohibitions"));   
                policy.put("private_properties", rs.getString("private_properties")); 
                policy.put("permissions", rs.getString("permissions"));       

                policies.add(policy);
            }
        } catch (SQLException e) {
            monitor.severe("Error getting policies", e);
        }
        return policies;
    }
}
