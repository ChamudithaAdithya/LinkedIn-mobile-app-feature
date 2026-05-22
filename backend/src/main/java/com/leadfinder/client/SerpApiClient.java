package com.leadfinder.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadfinder.config.SerpApiConfig;
import com.leadfinder.dto.CandidateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
public class SerpApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SerpApiClient.class);
    private final RestTemplate restTemplate;
    private final SerpApiConfig serpApiConfig;

    public SerpApiClient(SerpApiConfig serpApiConfig) {
        this.serpApiConfig = serpApiConfig;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000); 
        requestFactory.setReadTimeout(30000);    

        this.restTemplate = new RestTemplate(requestFactory);
    }

    // Automatically retry 3 times if we get a 5xx server error or a network timeout/drop
    @Retryable(
            retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 1.5)
    )
    public List<CandidateDto> searchLinkedInProfiles(String query) {
        String apiKey = serpApiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("SerpAPI key is missing.");
            throw new IllegalStateException("SerpAPI API key is required.");
        }

        String url = UriComponentsBuilder.fromHttpUrl("https://serpapi.com/search.json")
                .queryParam("engine", "google")
                .queryParam("q", query)
                .queryParam("api_key", apiKey)
                .build()
                .toUriString();

        LOGGER.debug("SERP API URL: {}", url);
        
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = response.getBody();
            
            if (body == null || !body.has("organic_results")) {
                return List.of();
            }

            JsonNode results = body.get("organic_results");
            List<CandidateDto> candidates = new ArrayList<>();
            for (JsonNode result : results) {
                String title = result.path("title").asText();
                String link = result.path("link").asText();
                if (link.contains("linkedin.com/in")) {
                    String candidateName = parseCandidateName(title);
                    String candidateCompany = parseCandidateCompany(title);
                    String thumbnail = result.path("thumbnail").asText(null);
                    
                    CandidateDto candidate = new CandidateDto(candidateName, title, candidateCompany, link, 0);
                    candidate.setProfilePicUrl(thumbnail);
                    candidates.add(candidate);
                }
            }
            return candidates;
            
        } catch (HttpClientErrorException.TooManyRequests e) {
            LOGGER.error("SerpAPI Rate Limit Exceeded (HTTP 429).");
            throw new RuntimeException("Too many scans requested. Please wait 30 seconds before scanning again.");
        }
    }

    private String parseCandidateName(String title) {
        if (title == null) return null;
        String[] segments = title.split("[|\\-\u2013]");
        return segments.length > 0 ? segments[0].trim() : title.trim();
    }

    private String parseCandidateCompany(String title) {
        if (title == null) return null;
        String[] segments = title.split("[|\\-\u2013]");
        for (int i = 1; i < segments.length; i++) {
            String candidateCompany = segments[i].trim();
            if (candidateCompany.isEmpty()) continue;
            String normalized = candidateCompany.toLowerCase();
            if (normalized.contains("linkedin") || !isLikelyCompany(candidateCompany)) continue;
            return candidateCompany;
        }
        return null;
    }

    private boolean isLikelyCompany(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }

        String lower = segment.toLowerCase();
        if (lower.contains("linkedin") || lower.contains(" at ") || lower.contains(" bei ") || lower.contains("student") || lower.contains("intern") || lower.contains("freelance") || lower.contains("contractor") || lower.contains("department") || lower.contains("shipping") || lower.contains("logistics") || lower.contains("operations") || lower.contains("manager") || lower.contains("assistant") || lower.contains("support") || lower.contains(",")) {
            return false;
        }

        String[] roleKeywords = {
                "developer", "engineer", "designer", "manager", "consultant", "director", "founder", "owner", "lead", "analyst", "architect",
                "president", "chief", "cto", "ceo", "cfo", "coo", "vp", "vice", "principal", "teacher", "speaker", "coach",
                "assistant", "support", "specialist", "sales", "marketing", "service", "shipping", "logistics", "operations",
                "human resources", "researcher", "trainer", "technician", "administrator", "representative", "account", "auditor", "executive"
        };
        for (String keyword : roleKeywords) {
            if (lower.contains(" " + keyword) || lower.startsWith(keyword) || lower.endsWith(" " + keyword) || lower.contains(keyword + " ")) {
                return false;
            }
        }
        return true;
    }
}