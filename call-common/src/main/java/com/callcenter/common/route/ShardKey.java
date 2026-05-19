package com.callcenter.common.route;

public record ShardKey(long tenantId, int dbIndex, int tableIndex, String yearMonth) {

    public ShardContext toContext() {
        return new ShardContext(dbIndex, tableIndex, yearMonth);
    }
}

