package com.leadfinder.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadfinder.config.ProxycurlConfig;
import com.leadfinder.dto.CandidateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Component
public class ProxycurlClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxycurlClient.class);
    private static final int MAX_ERROR_BODY_LOG_LENGTH = 1000;
    private final RestTemplate restTemplate;
    private final ProxycurlConfig proxycurlConfig;
    private final String apiUrl;
    
    @Autowired
    public ProxycurlClient(ProxycurlConfig proxycurlConfig) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000); 
        requestFactory.setReadTimeout(30000); 
        
        this.proxycurlConfig = proxycurlConfig;
        this.apiUrl = proxycurlConfig.getApiUrl();
        this.restTemplate = new RestTemplate(requestFactory);
    }

    // Retries network timeouts and 5xx errors automatically
    @Retryable(
            retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 1.5)
    )
    public String enrichCandidate(CandidateDto candidate) {
        String apiKey = proxycurlConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            String msg = "Proxycurl API key not configured; skipping enrichment";
            LOGGER.debug("{} for {}", msg, candidate.getLinkedinUrl());
            return msg;
        }

        String normalizedName = normalizeMissingValue(candidate.getName());
        String firstName = getFirstName(normalizedName);
        String lastName = getLastName(normalizedName);
        String companyName = normalizeMissingValue(candidate.getCompany());

        if (firstName == null || firstName.isBlank()) {
            String msg = "Skipping NinjaPear enrichment: no valid first name for " + candidate.getLinkedinUrl();
            LOGGER.debug(msg);
            return msg;
        }

        if (companyName == null || !isLikelyCompanyName(companyName)) {
            String msg = String.format("Skipping NinjaPear enrichment: invalid or missing company '%s' for %s", candidate.getCompany(), candidate.getLinkedinUrl());
            LOGGER.debug(msg);
            return msg;
        }

        try {
            String website = getDomain(companyName, apiKey);
            if (website == null || website.isBlank()) {
                String msg = "Company website not found for " + companyName;
                LOGGER.debug(msg);
                return msg;
            }
            candidate.setCompanyWebsite(website);

            String domain = extractDomain(website);
            if (domain == null || domain.isBlank()) {
                String msg = "Could not extract domain from company website " + website;
                LOGGER.debug(msg);
                return msg;
            }

            String workEmail = getWorkEmail(firstName, lastName, domain, apiKey);
            if (workEmail == null || workEmail.isBlank()) {
                String msg = "Email not found for " + firstName + " at " + domain;
                LOGGER.debug(msg);
                return msg;
            }
            candidate.setEmail(workEmail);

            boolean profileFilled = populateProfileDetails(candidate, workEmail, apiKey);
            if (profileFilled) {
                return "Enriched with NinjaPear profile details";
            }
            return "Profile details not found for " + workEmail;
        } catch (HttpClientErrorException.TooManyRequests e) {
            String msg = "Enrichment API Rate Limit Exceeded (HTTP 429). Please wait before enriching more contacts.";
            LOGGER.error(msg);
            return msg;
        } catch (RestClientResponseException e) {
            String msg = String.format("Failed to enrich candidate from NinjaPear: status=%d response=%s", e.getRawStatusCode(), getSafeResponseBody(e));
            LOGGER.error(msg, e);
            if (e.getStatusCode().is5xxServerError()) {
                throw new HttpServerErrorException(e.getStatusCode(), e.getStatusText(), e.getResponseHeaders(), e.getResponseBodyAsByteArray(), null);
            }
            return msg;
        } catch (ResourceAccessException e) {
            LOGGER.error("Network timeout connecting to NinjaPear API", e);
            throw e;
        } catch (Exception e) {
            String msg = "Failed to enrich candidate from NinjaPear: " + e.getMessage();
            LOGGER.error(msg, e);
            return msg;
        }
    }
    
    private String getDomain(String companyName, String apiKey) {
        String url = UriComponentsBuilder.fromHttpUrl(getApiHost())
                .path("/api/v1/company/website")
                .queryParam("company_name", companyName)
                .queryParam("use_cache", "if-present")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();
            String website = firstNonEmpty(body, "website", "domain", "company_website", "company_url");
            return website;
        } catch (RestClientResponseException e) {
            if (e.getRawStatusCode() == 403) {
                LOGGER.debug("Company website lookup failed for '{}': Out of credits. {}", companyName, getSafeResponseBody(e));
            } else if (e.getRawStatusCode() == 404) {
                LOGGER.debug("Company website lookup failed for '{}': Company not found. {}", companyName, getSafeResponseBody(e));
            } else {
                LOGGER.debug("Company website lookup failed for '{}': status={} response={}", companyName, e.getRawStatusCode(), getSafeResponseBody(e));
            }
            return null;
        }
    }

    private String getWorkEmail(String firstName, String lastName, String domain, String apiKey) {
        if (firstName == null || firstName.isBlank() || domain == null || domain.isBlank()) {
            return null;
        }

        String url = UriComponentsBuilder.fromHttpUrl(getApiHost())
                .path("/api/v1/employee/work-email")
                .queryParam("first_name", firstName)
                .queryParamIfPresent("last_name", lastName == null || lastName.isBlank() ? Optional.empty() : Optional.of(lastName))
                .queryParam("domain", domain)
                .queryParam("use_cache", "if-present")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null) {
                return null;
            }
            String email = firstNonEmpty(body, "work_email", "email");
            return email;
        } catch (RestClientResponseException e) {
            LOGGER.debug("Work email lookup failed for '{} {}' domain='{}': status={} response={}", firstName, lastName, domain, e.getRawStatusCode(), getSafeResponseBody(e));
            return null;
        }
    }

    private boolean populateProfileDetails(CandidateDto candidate, String workEmail, String apiKey) {
        String url = UriComponentsBuilder.fromHttpUrl(getApiHost())
                .path("/api/v1/employee/profile")
                .queryParam("work_email", workEmail)
                .queryParam("use_cache", "if-present")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode profile = unwrapProfileNode(response.getBody());
            if (profile == null || profile.isEmpty()) {
                LOGGER.debug("Profile details not found for work_email={}", workEmail);
                return false;
            }

            candidate.setProfilePicUrl(firstNonEmpty(profile, "profile_pic_url", "profile_pic", "image", "avatar", "photo"));
            candidate.setHeadline(firstNonEmpty(profile, "job_title", "headline", "title", "position", "role", "current_title", "current_position"));
            String description = firstNonEmpty(profile, "bio", "summary", "description", "about", "profile_summary");
            if (description != null && !description.isBlank()) {
                candidate.setSummary(description);
                candidate.setBio(description);
            }
            candidate.setPersonalWebsite(firstNonEmpty(profile, "personal_website", "website", "url", "personal_url"));
            candidate.setSocialProfileUrl(firstNonEmpty(profile, "x_profile_url", "social_profile_url", "linkedin_url", "profile_url"));

            String companyWebsite = firstNonEmpty(profile, "employer_website", "company_website", "company_url");
            if (companyWebsite != null && !companyWebsite.isBlank()) {
                candidate.setCompanyWebsite(companyWebsite);
            }

            String companyName = firstNonEmpty(profile, "company", "employer", "current_employer", "current_company", "organization", "employer_name", "company_name");
            if (companyName != null && !companyName.isBlank()) {
                candidate.setCompany(companyName);
            }

            String city = firstNonEmpty(profile, "city", "location_city", "locality");
            String state = firstNonEmpty(profile, "state", "region", "province");
            String country = firstNonEmpty(profile, "country", "country_code", "country_name");
            String location = buildLocation(city, state, country);
            if (!location.isBlank()) {
                candidate.setLocation(location);
            }

            String fullName = firstNonEmpty(profile, "full_name", "name", "display_name");
            if (fullName != null && !fullName.isBlank()) {
                candidate.setName(fullName);
            }

            String email = firstNonEmpty(profile, "work_email", "email", "personal_email", "contact_email");
            if (email != null && !email.isBlank()) {
                candidate.setEmail(email);
            }
            String phone = firstNonEmpty(profile, "phone", "phone_number", "mobile", "mobile_phone");
            if (phone != null && !phone.isBlank()) {
                candidate.setPhone(phone);
            }

            return true;
        } catch (RestClientResponseException e) {
            LOGGER.debug("Profile enrichment failed for work_email={} status={} response={}", workEmail, e.getRawStatusCode(), getSafeResponseBody(e));
            return false;
        }
    }

    private JsonNode unwrapProfileNode(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        if (body.has("data") && body.get("data").isObject()) {
            return body.get("data");
        }
        if (body.has("profile") && body.get("profile").isObject()) {
            return body.get("profile");
        }
        return body;
    }

    private String firstNonEmpty(JsonNode node, String... fieldNames) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isLikelyCompanyName(String company) {
        if (company == null) {
            return false;
        }
        String normalized = company.trim().toLowerCase();
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("linkedin") || normalized.contains(" at ") || normalized.contains(" bei ") || normalized.contains("student") || normalized.contains("intern") || normalized.contains("freelance") || normalized.contains("contractor") || normalized.contains("agency") || normalized.contains("department") || normalized.contains(" team") || normalized.contains(" team ")) {
            return false;
        }
        String[] roleKeywords = {
                "developer", "engineer", "designer", "manager", "consultant", "director", "founder", "owner", "lead", "analyst", "architect",
                "president", "chief", "cto", "ceo", "cfo", "coo", "vp", "vice", "principal", "teacher", "speaker", "coach",
                "assistant", "support", "specialist", "sales", "marketing", "service", "shipping", "logistics", "operations",
                "human resources", "researcher", "trainer", "technician", "administrator", "representative", "account", "auditor", "executive"
        };
        for (String keyword : roleKeywords) {
            if (normalized.contains(" " + keyword) || normalized.startsWith(keyword) || normalized.endsWith(" " + keyword) || normalized.contains(keyword + " ")) {
                return false;
            }
        }
        return true;
    }

    private String getApiHost() {
        if (apiUrl == null || apiUrl.isBlank()) {
            return "https://nubela.co";
        }

        try {
            URI uri = new URI(apiUrl);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return scheme + "://nubela.co";
            }
            return scheme + "://" + host;
        } catch (URISyntaxException e) {
            return "https://nubela.co";
        }
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return url.trim();
            }
            return host;
        } catch (URISyntaxException e) {
            return url.replaceFirst("https?://", "").split("/", 2)[0].trim();
        }
    }

    private String normalizeMissingValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String lower = trimmed.toLowerCase();
        if (lower.equals("null") || lower.equals("none") || lower.equals("unknown") || lower.equals("n/a")) {
            return null;
        }
        return trimmed;
    }

    private String getFirstName(String fullName) {
        fullName = normalizeMissingValue(fullName);
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }

    private String getLastName(String fullName) {
        fullName = normalizeMissingValue(fullName);
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)) : null;
    }

    private String buildLocation(String city, String state, String country) {
        StringBuilder location = new StringBuilder();
        if (city != null && !city.isBlank()) {
            location.append(city.trim());
        }
        if (state != null && !state.isBlank()) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(state.trim());
        }
        if (country != null && !country.isBlank()) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(country.trim());
        }
        return location.toString();
    }

    private String getSafeResponseBody(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_ERROR_BODY_LOG_LENGTH ? body : body.substring(0, MAX_ERROR_BODY_LOG_LENGTH) + "...";
    }
}
