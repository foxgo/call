package com.callcenter.common.initializer;

import com.callcenter.common.config.CallElasticsearchProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final RestClient restClient;
    private final CallElasticsearchProperties properties;

    public ElasticsearchIndexInitializer(RestClient restClient, CallElasticsearchProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.isAutoInit()) {
            return;
        }
        putIfNeeded("/_ilm/policy/call_record_ilm", "es/call-record-ilm.json");
        putIfNeeded("/_ilm/policy/call_round_ilm", "es/call-round-ilm.json");
        putIfNeeded("/_index_template/call_record_template", "es/call-record-template.json");
        putIfNeeded("/_index_template/call_round_template", "es/call-round-template.json");
        putIfNeeded("/call_record_000001", "es/call-record-bootstrap.json");
        putIfNeeded("/call_round_000001", "es/call-round-bootstrap.json");
    }

    private void putIfNeeded(String endpoint, String resourcePath) throws IOException {
        Request request = new Request("PUT", endpoint);
        request.setOptions(RequestOptions.DEFAULT);
        request.setEntity(new StringEntity(readResource(resourcePath), ContentType.APPLICATION_JSON));
        try {
            restClient.performRequest(request);
            log.info("Initialized Elasticsearch resource: {}", endpoint);
        } catch (ResponseException exception) {
            int statusCode = exception.getResponse().getStatusLine().getStatusCode();
            if (statusCode == 400 || statusCode == 404 || statusCode == 409) {
                log.info("Elasticsearch resource already exists or is partially initialized: {}", endpoint);
                return;
            }
            throw exception;
        }
    }

    private String readResource(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
