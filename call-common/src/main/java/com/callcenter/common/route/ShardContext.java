package com.callcenter.common.route;

public record ShardContext(int dbIndex, int tableIndex, String yearMonth) {
}

