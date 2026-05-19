package com.callcenter.common.route;

import com.callcenter.common.config.ShardProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ShardingRouter {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final ShardProperties properties;

    public ShardingRouter(ShardProperties properties) {
        this.properties = properties;
    }

    public int dbIndex(long tenantId) {
        return Math.floorMod(Long.hashCode(tenantId), properties.getDbCount());
    }

    public int tableIndexByPhone(String phone) {
        return Math.floorMod(Objects.requireNonNullElse(phone, "").hashCode(), properties.getTableCount());
    }

    public int tableIndexByCallId(long callId) {
        return (int) ((callId >> 8) & 0x0F);
    }

    public ShardKey routeRecord(long tenantId, String phone, LocalDateTime startTime) {
        return new ShardKey(tenantId, dbIndex(tenantId), tableIndexByPhone(phone), formatYearMonth(startTime));
    }

    public ShardKey routeRound(long tenantId, long callId, LocalDateTime startTime) {
        return new ShardKey(tenantId, dbIndex(tenantId), tableIndexByCallId(callId), formatYearMonth(startTime));
    }

    public LocalDateTime toDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private String formatYearMonth(LocalDateTime time) {
        LocalDateTime value = time == null ? LocalDateTime.now(ZoneOffset.UTC) : time;
        return YEAR_MONTH.format(value);
    }
}

