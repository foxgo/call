package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.postprocess.ThirdPartyPushRequest;

public interface ThirdPartyPushGateway {

    void push(ThirdPartyPushRequest request);
}
