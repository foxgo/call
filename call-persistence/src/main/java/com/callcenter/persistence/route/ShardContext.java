package com.callcenter.persistence.route;

public record ShardContext(int dbIndex, int tableIndex, String yearMonth) {
}
