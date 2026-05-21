package com.leadfinder.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadfinder.config.SerpApiConfig;
import com.leadfinder.dto.CandidateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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

        // Configure RestTemplate with timeouts
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000); // 10 seconds connection timeout
        requestFactory.setReadTimeout(30000);    // 30 seconds read timeout

        this.restTemplate = new RestTemplate(requestFactory);
    }

    public List<CandidateDto> searchLinkedInProfiles(String query) {
        String apiKey = serpApiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("SerpAPI key is not configured; set serpapi.api.key in application.properties or environment.");
            throw new IllegalStateException("SerpAPI API key is required. Configure serpapi.api.key in application.properties.");
        }

        String url = UriComponentsBuilder.fromHttpUrl("https://serpapi.com/search.json")
                .queryParam("engine", "google")
                .queryParam("q", query)
                .queryParam("api_key", apiKey)
                .build()
                .toUriString();

        LOGGER.debug("SERP API URL: {}", url);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode body = response.getBody();
        if (body == null || !body.has("organic_results")) {
            LOGGER.debug("SERP API response has no organic_results or is empty: {}", body);
            return List.of();
        }

        JsonNode results = body.get("organic_results");
        List<CandidateDto> candidates = new ArrayList<>();
        for (JsonNode result : results) {
            String title = result.path("title").asText();
            String link = result.path("link").asText();
            LOGGER.debug("SERP API result link: {} title: {}", link, title);
            if (link.contains("linkedin.com/in")) {
                String candidateName = parseCandidateName(title);
                String candidateCompany = parseCandidateCompany(title);
                String thumbnail = result.path("thumbnail").asText(null);
                
                CandidateDto candidate = new CandidateDto(
                        candidateName,
                        title,
                        candidateCompany,
                        link,
                        0);
                candidate.setProfilePicUrl(thumbnail);
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private String parseCandidateName(String title) {
        if (title == null) {
            return null;
        }
        // Split by common separators: |, -, – (en dash)
        String[] segments = title.split("[|\\-\u2013]");
        return segments.length > 0 ? segments[0].trim() : title.trim();
    }

    private String parseCandidateCompany(String title) {
        if (title == null) {
            return null;
        }
        String[] segments = title.split("[|\\-\u2013]");
        for (int i = 1; i < segments.length; i++) {
            String candidateCompany = segments[i].trim();
            if (candidateCompany.isEmpty()) {
                continue;
            }
            String normalized = candidateCompany.toLowerCase();
            if (normalized.contains("linkedin") || !isLikelyCompany(candidateCompany)) {
                continue;
            }
            return candidateCompany;
        }
        return null;
    }

    private boolean isLikelyCompany(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        String lower = segment.toLowerCase();
        if (lower.contains("linkedin") || lower.contains(" at ") || lower.contains("student") || lower.contains("intern")) {
            return false;
        }
        if (lower.contains(",")) {
            return false;
        }
        String[] roleKeywords = {
                "developer", "engineer", "designer", "manager", "consultant", "director", "founder",
                "owner", "lead", "analyst", "architect", "president", "chief", "cto", "ceo", "cfo", "coo",
                "vp", "vice", "principal", "teacher", "speaker", "coach", "freelance", "contractor"
        };
        for (String keyword : roleKeywords) {
            if (lower.contains(keyword)) {
                return false;
            }
        }
        return true;
    }
}
