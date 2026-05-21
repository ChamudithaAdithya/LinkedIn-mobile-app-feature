package com.leadfinder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxycurlConfig {

    @Value("${proxycurl.api.key:}")
    private String apiKey;

    @Value("${proxycurl.api.url:https://nubela.co/api/v1/employee/profile}")
    private String apiUrl;

    @Value("${proxycurl.fallback.api.url:}")
    private String fallbackApiUrl;

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getFallbackApiUrl() {
        return fallbackApiUrl;
    }
}
