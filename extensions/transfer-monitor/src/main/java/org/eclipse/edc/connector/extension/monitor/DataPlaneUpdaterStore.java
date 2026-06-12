package org.eclipse.edc.connector.extension.monitor;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.List;
import java.util.stream.Stream;

public class DataPlaneUpdaterStore implements TransferProcessStore {
    private final TransferProcessStore delegate;
    private final Monitor monitor;

    public DataPlaneUpdaterStore(TransferProcessStore delegate, Monitor monitor) {
        this.delegate = delegate;
        this.monitor = monitor;
    }

    public StoreResult<Void> updateDataPlaneId(String dataPlaneId, String processId) {
        var result = delegate.findByIdAndLease(processId);
        if (result.succeeded()) {
            var process = result.getContent();
            monitor.debug("Updating data plane ID for process: " + processId);
            var updated = TransferProcess.Builder.newInstance()                    
                    .dataPlaneId(dataPlaneId)
                    .build();

            delegate.save(updated);
            return StoreResult.success();
        }
        return StoreResult.success();
    }

    @Override
    public StoreResult<TransferProcess> findByIdAndLease(String id) {
        return delegate.findByIdAndLease(id);
    }

    @Override
    public TransferProcess findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public void save(TransferProcess entity) {
        delegate.save(entity);
    }

    @Override
    public Stream<TransferProcess> findAll(QuerySpec querySpec) {
        return delegate.findAll(querySpec);
    }

    @Override
    public TransferProcess findForCorrelationId(String correlationId) {
        return delegate.findForCorrelationId(correlationId);
    }

    @Override
    public void delete(String processId) {
        delegate.delete(processId);
    }

    @Override
    public List<TransferProcess> nextNotLeased(int max, Criterion... criteria) {
        return delegate.nextNotLeased(max, criteria);
    }
}