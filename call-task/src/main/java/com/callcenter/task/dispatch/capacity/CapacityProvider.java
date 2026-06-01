package com.callcenter.task.dispatch.capacity;

public interface CapacityProvider {

    CapacitySnapshot snapshot();

    boolean available();

    double healthScore();
}
