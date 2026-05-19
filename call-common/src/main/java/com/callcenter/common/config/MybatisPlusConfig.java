package com.callcenter.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.route.ShardContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        DynamicTableNameInnerInterceptor interceptor = new DynamicTableNameInnerInterceptor();
        interceptor.setTableNameHandler((sql, tableName) -> {
            if ("call_record".equals(tableName)) {
                return resolveTableName("call_record");
            }
            if ("call_round".equals(tableName)) {
                return resolveTableName("call_round");
            }
            return tableName;
        });

        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(interceptor);
        return mybatisPlusInterceptor;
    }

    private String resolveTableName(String baseName) {
        ShardContext context = ShardContextHolder.getRequired();
        return "%s_%s_%02d".formatted(baseName, context.yearMonth(), context.tableIndex());
    }
}
