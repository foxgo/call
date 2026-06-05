package com.callcenter.ingestion.infrastructure.postprocess.client;

import com.callcenter.ingestion.domain.postprocess.ThirdPartyPushRequest;

public interface ThirdPartyPushClient {

    void push(ThirdPartyPushRequest request);
}
