package com.callcenter.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

@Component
public class FailureClassifier {

    public boolean isRetryable(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JsonProcessingException || current instanceof IllegalArgumentException) {
                return false;
            }
            current = current.getCause();
        }
        return true;
    }
}
