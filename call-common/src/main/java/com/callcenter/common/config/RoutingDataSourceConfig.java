package com.callcenter.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.callcenter.common.context.DbRouteContextHolder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.CollectionUtils;

@Configuration
@MapperScan("com.callcenter.common.mapper")
public class RoutingDataSourceConfig {

    @Bean
    public DataSource dataSource(CallDatasourceProperties properties) {
        if (CollectionUtils.isEmpty(properties.getNodes())) {
            throw new IllegalStateException("call.datasource.nodes must not be empty");
        }

        Map<Object, Object> targets = new LinkedHashMap<>();
        for (CallDatasourceProperties.Node node : properties.getNodes()) {
            targets.put(node.getIndex(), buildDataSource(node));
        }

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targets);
        routingDataSource.setDefaultTargetDataSource(targets.values().iterator().next());
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisPlusInterceptor mybatisPlusInterceptor
    ) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPlugins(mybatisPlusInterceptor);
        return factoryBean.getObject();
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private DataSource buildDataSource(CallDatasourceProperties.Node node) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("call-db-" + node.getIndex());
        config.setJdbcUrl("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true"
                .formatted(node.getHost(), node.getPort(), node.getDatabase()));
        config.setUsername(node.getUsername());
        config.setPassword(node.getPassword());
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setAutoCommit(false);
        config.setConnectionTimeout(3000);
        return new HikariDataSource(config);
    }

    static class RoutingDataSource extends AbstractRoutingDataSource {

        @Override
        protected Object determineCurrentLookupKey() {
            return DbRouteContextHolder.get();
        }
    }
}
