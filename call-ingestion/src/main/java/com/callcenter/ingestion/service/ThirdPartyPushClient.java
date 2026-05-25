package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.ThirdPartyPushRequest;

public interface ThirdPartyPushClient {

    void push(ThirdPartyPushRequest request);
}
