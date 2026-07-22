package org.eclipse.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetCreated;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetDeleted;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetUpdated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionCreated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionDeleted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractdefinition.ContractDefinitionUpdated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAccepted;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationAgreed;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationFinalized;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationInitiated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationOffered;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationRequested;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationTerminated;
import org.eclipse.edc.connector.controlplane.contract.spi.event.contractnegotiation.ContractNegotiationVerified;
import org.eclipse.edc.connector.controlplane.policy.spi.event.PolicyDefinitionCreated;
import org.eclipse.edc.connector.controlplane.policy.spi.event.PolicyDefinitionDeleted;
import org.eclipse.edc.connector.controlplane.policy.spi.event.PolicyDefinitionUpdated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessCompleted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessDeprovisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessInitiated;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioned;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessProvisioningRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessRequested;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessSuspended;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessTerminated;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.event.EventSubscriber;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.logging.Logger;

@Extension("DRM Event Extension")
public class DrmEventExtension implements ServiceExtension {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(DrmEventExtension.class.getName());
    @Setting(
            key = "edc.controlplane.drm.monitor.endpoint",
            description = "URL to post connector logs to DRM",
            defaultValue = "http://10.151.64.56:8001/receiveConnectorLog"
    )
    private static final String DRM_MONITOR_ENDPOINT = "edc.controlplane.drm.monitor.endpoint";

    @Setting(
            key = "edc.controlplane.drm.monitor.enabled",
            description = "Enable or disable the DRM monitoring extension",
            defaultValue = "false"
    )
    private static final String DRM_MONITOR_ENABLED = "edc.controlplane.drm.monitor.enabled";

    @Override
    public void initialize(ServiceExtensionContext context) {

        Monitor monitor = context.getMonitor();
        EventRouter eventRouter = context.getService(EventRouter.class);

        PostLog postLog = new PostLog();

        String endpoint = context.getSetting(DRM_MONITOR_ENDPOINT, "");
        boolean enabled = context.getSetting(DRM_MONITOR_ENABLED, false);

        if (!enabled) {
            monitor.info(() -> "DRM Event Extension is DISABLED - events from the connector will NOT be sent to the DRM module.");
            LOGGER.info("DRM Event Extension is DISABLED - events from the connector will NOT be sent to the DRM module.");
        } else {

            monitor.info(() -> "DRM Event Extension initialized");

            // ASSET EVENTS
            eventRouter.register(AssetCreated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("asset.created", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(AssetUpdated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("asset.updated", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(AssetDeleted.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("asset.deleted", envelope, monitor, postLog, endpoint);
                }
            });


            // CONTRACT DEFINITION EVENTS
            eventRouter.register(ContractDefinitionCreated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.definition.created", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractDefinitionUpdated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.definition.updated", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractDefinitionDeleted.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.definition.deleted", envelope, monitor, postLog, endpoint);
                }
            });


            // CONTRACT NEGOTIATION EVENTS
            eventRouter.register(ContractNegotiationInitiated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.initiated", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationRequested.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.requested", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationOffered.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.offered", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationAccepted.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.accepted", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationAgreed.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.agreed", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationVerified.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.verified", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationFinalized.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.finalized", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(ContractNegotiationTerminated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("contract.negotiation.terminated", envelope, monitor, postLog, endpoint);
                }
            });


            // POLICY EVENTS
            eventRouter.register(PolicyDefinitionCreated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("policy.definition.created", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(PolicyDefinitionUpdated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("policy.definition.updated", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(PolicyDefinitionDeleted.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("policy.definition.deleted", envelope, monitor, postLog, endpoint);
                }
            });


            // TRANSFER PROCESS EVENTS
            eventRouter.register(TransferProcessInitiated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.initiated", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessRequested.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.requested", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessStarted.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.started", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessProvisioningRequested.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.provisioningRequested", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessProvisioned.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.provisioned", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessDeprovisioningRequested.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.deprovisioningRequested", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessDeprovisioned.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.deprovisioned", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessSuspended.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.suspended", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessTerminated.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.terminated", envelope, monitor, postLog, endpoint);
                }
            });

            eventRouter.register(TransferProcessCompleted.class, new EventSubscriber() {
                @Override
                public <E extends Event> void on(EventEnvelope<E> envelope) {
                    sendFullEvent("transfer.process.completed", envelope, monitor, postLog, endpoint);
                }
            });

        }
    }

    private void sendFullEvent(String type, Object event, Monitor monitor, PostLog postLog, String endpoint) {
        try {
            String json = objectMapper.writeValueAsString(event);
            monitor.info(() -> "Sending event to DRM: " + json);
            postLog.postLog(type, json.replace("{", "").replace("}", "").replace("\"", "").replace(",", ", \n"), endpoint);
        } catch (Exception e) {
            monitor.severe(() -> "Failed to serialize event", e);
        }
    }
}