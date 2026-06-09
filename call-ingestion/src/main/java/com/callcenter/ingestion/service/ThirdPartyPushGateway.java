package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.ThirdPartyPushRequest;

public interface ThirdPartyPushGateway {

    void push(ThirdPartyPushRequest request);
}
