package com.callcenter.observability.logging.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "call.logging")
public class LoggingProperties {

    private boolean jsonEnabled = true;
    private String env = "local";
    private String requestIdHeader = "X-Request-Id";
    private final Request request = new Request();
    private final Masking masking = new Masking();

    public boolean isJsonEnabled() {
        return jsonEnabled;
    }

    public void setJsonEnabled(boolean jsonEnabled) {
        this.jsonEnabled = jsonEnabled;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getRequestIdHeader() {
        return requestIdHeader;
    }

    public void setRequestIdHeader(String requestIdHeader) {
        this.requestIdHeader = requestIdHeader;
    }

    public Request getRequest() {
        return request;
    }

    public Masking getMasking() {
        return masking;
    }

    public static class Request {

        private boolean includeHeaders;
        private boolean includeBody;
        private int maxBodyLength = 2048;
        private List<String> headerWhitelist = new ArrayList<>(List.of("X-Request-Id", "X-Forwarded-For"));

        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        public void setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        public boolean isIncludeBody() {
            return includeBody;
        }

        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }

        public List<String> getHeaderWhitelist() {
            return headerWhitelist;
        }

        public void setHeaderWhitelist(List<String> headerWhitelist) {
            this.headerWhitelist = headerWhitelist;
        }
    }

    public static class Masking {

        private boolean enabled = true;
        private List<String> secretFields = new ArrayList<>(List.of(
                "password",
                "pwd",
                "token",
                "accessToken",
                "refreshToken",
                "authorization",
                "cookie",
                "secret",
                "appSecret"
        ));
        private List<String> phoneFields = new ArrayList<>(List.of("phone", "mobile", "contactPhone"));
        private List<String> idCardFields = new ArrayList<>(List.of("idCard", "idNo", "identityCard"));
        private List<String> nameFields = new ArrayList<>(List.of("name", "realName", "fullName"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getSecretFields() {
            return secretFields;
        }

        public void setSecretFields(List<String> secretFields) {
            this.secretFields = secretFields;
        }

        public List<String> getPhoneFields() {
            return phoneFields;
        }

        public void setPhoneFields(List<String> phoneFields) {
            this.phoneFields = phoneFields;
        }

        public List<String> getIdCardFields() {
            return idCardFields;
        }

        public void setIdCardFields(List<String> idCardFields) {
            this.idCardFields = idCardFields;
        }

        public List<String> getNameFields() {
            return nameFields;
        }

        public void setNameFields(List<String> nameFields) {
            this.nameFields = nameFields;
        }
    }
}
