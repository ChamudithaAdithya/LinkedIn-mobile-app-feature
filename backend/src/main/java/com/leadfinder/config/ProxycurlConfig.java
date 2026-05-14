package com.leadfinder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxycurlConfig {

    @Value("${proxycurl.api.key:}")
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }
}
