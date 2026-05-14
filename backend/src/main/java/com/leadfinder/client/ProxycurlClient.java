package com.leadfinder.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadfinder.config.ProxycurlConfig;
import com.leadfinder.dto.CandidateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ProxycurlClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxycurlClient.class);
    private final RestTemplate restTemplate;
    private final ProxycurlConfig proxycurlConfig;

    public ProxycurlClient(ProxycurlConfig proxycurlConfig) {
        this.proxycurlConfig = proxycurlConfig;
        this.restTemplate = new RestTemplate();
    }

    public void enrichCandidate(CandidateDto candidate) {
        String apiKey = proxycurlConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.debug("Proxycurl API key not configured; skipping enrichment for {}", candidate.getLinkedinUrl());
            return;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://nubela.co/proxycurl/api/v1/linkedin")
                    .queryParam("url", candidate.getLinkedinUrl())
                    .queryParam("fallback_to_cache", "on-error")
                    .queryParam("use_cache", "if-present")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();

            if (body != null) {
                candidate.setProfilePicUrl(body.path("profile_pic_url").asText(null));
                candidate.setHeadline(body.path("headline").asText(candidate.getTitle()));
                candidate.setSummary(body.path("summary").asText(null));
                
                String city = body.path("city").asText("");
                String country = body.path("country").asText("");
                if (!city.isEmpty() || !country.isEmpty()) {
                    candidate.setLocation(city + (city.isEmpty() || country.isEmpty() ? "" : ", ") + country);
                }
                
                // If Proxycurl has a more accurate name, use it
                String fullName = body.path("full_name").asText(null);
                if (fullName != null && !fullName.isBlank()) {
                    candidate.setName(fullName);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to enrich candidate from Proxycurl: {}", e.getMessage());
        }
    }
}
