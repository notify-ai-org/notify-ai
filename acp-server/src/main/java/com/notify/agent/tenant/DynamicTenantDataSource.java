package com.notify.agent.tenant;

import com.notify.agent.AgentContextHolder;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DynamicTenantDataSource extends AbstractDataSource {

    private final TenantDataSourceRegistry registry;

    public DynamicTenantDataSource(TenantDataSourceRegistry registry) {
        this.registry = registry;
    }

    private String getCurrentTenantId() {
        if (AgentContextHolder.getContext() != null) {
            return AgentContextHolder.getContext().getTenantId();
        }
        return null;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return registry.getOrCreate(getCurrentTenantId()).getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return registry.getOrCreate(getCurrentTenantId()).getConnection(username, password);
    }
}
