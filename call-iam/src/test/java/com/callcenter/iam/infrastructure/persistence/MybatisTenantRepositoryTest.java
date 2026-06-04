package com.callcenter.iam.infrastructure.persistence;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.callcenter.common.config.MybatisPlusConfig;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantStatus;
import com.callcenter.iam.infrastructure.persistence.adapter.MybatisTenantRepository;
import com.callcenter.iam.infrastructure.persistence.mapper.TenantMapper;
import java.sql.Connection;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MybatisTenantRepositoryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("call_iam_repo")
            .withUsername("call")
            .withPassword("call123");

    @Test
    void shouldInsertAndReloadTenantAggregate() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_iam_schema.sql"));
        }

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory(dataSource);
        TenantMapper tenantMapper = sqlSessionFactory.openSession(true).getMapper(TenantMapper.class);
        MybatisTenantRepository repository = new MybatisTenantRepository(tenantMapper);

        Tenant tenant = Tenant.active(101L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0));
        repository.save(tenant);

        Tenant reloaded = repository.findById(101L).orElseThrow();
        assertThat(reloaded.getTenantCode()).isEqualTo("acme");
        assertThat(reloaded.getTenantName()).isEqualTo("Acme");
        assertThat(reloaded.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        factoryBean.setMapperLocations();
        factoryBean.setTypeAliasesPackage("com.callcenter.iam.infrastructure.persistence.dataobject");
        return factoryBean.getObject();
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }
}
