package com.notify.agent.tenant;

import com.notify.agent.AgentContextHolder;
import com.notify.agent.models.TenantRegistry;
import com.notify.agent.TenantRegistryRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TenantDataSourceRegistry {

    private final Map<String, DataSource> cache = new ConcurrentHashMap<>();

    private final TenantRegistryRepository repository;
    private final DataSource defaultDataSource;

    @Autowired
    public TenantDataSourceRegistry(TenantRegistryRepository repository,
            @Qualifier("dataSource") DataSource defaultDataSource) {
        this.repository = repository;
        this.defaultDataSource = defaultDataSource;
    }

    public DataSource getOrCreate(String tenantId) {
        if (tenantId == null) {
            return defaultDataSource;
        }
        return cache.computeIfAbsent(tenantId, this::load);
    }

    private DataSource load(String tenantId) {
        String originalTenantId = null;
        if (AgentContextHolder.getContext() != null) {
            originalTenantId = AgentContextHolder.getContext().getTenantId();
            AgentContextHolder.getContext().setTenantId(null);
        }

        try {
            TenantRegistry tenant = repository.findByTenantId(tenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

            if (tenant.getDbUrl() == null || tenant.getDbUrl().isEmpty()) {
                return defaultDataSource;
            }

            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(tenant.getDbUrl());
            ds.setUsername(tenant.getDbUsername());
            ds.setPassword(tenant.getDbPassword());
            ds.setDriverClassName(tenant.getDbDriverClass());

            if (tenant.getPoolMaxSize() != null) {
                ds.setMaximumPoolSize(tenant.getPoolMaxSize());
            }
            if (tenant.getPoolMinIdle() != null) {
                ds.setMinimumIdle(tenant.getPoolMinIdle());
            }

            return ds;
        } finally {
            if (AgentContextHolder.getContext() != null) {
                AgentContextHolder.getContext().setTenantId(originalTenantId);
            }
        }
    }
}
