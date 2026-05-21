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

@Component
public class ProxycurlClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxycurlClient.class);
    private static final int MAX_ERROR_BODY_LOG_LENGTH = 1000;
    private final RestTemplate restTemplate;
    private final ProxycurlConfig proxycurlConfig;
    private final String apiUrl;
    private final String fallbackApiUrl;
    
    @Autowired
    public ProxycurlClient(ProxycurlConfig proxycurlConfig) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000); 
        requestFactory.setReadTimeout(30000); 
        
        this.proxycurlConfig = proxycurlConfig;
        this.apiUrl = proxycurlConfig.getApiUrl();
        this.fallbackApiUrl = proxycurlConfig.getFallbackApiUrl();
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
            return "Proxycurl API key not configured; skipping enrichment";
        }

        try {
            if (isLegacyProxycurlUrl()) {
                boolean ok = enrichWithProxycurl(candidate, apiKey);
                return ok ? "Enriched with legacy Proxycurl endpoint" : "Proxycurl enrichment returned no data";
            } else {
                boolean ok = enrichWithNinjaPear(candidate, apiKey);
                if (ok) return "Enriched with NinjaPear";
                
                if (fallbackApiUrl != null && !fallbackApiUrl.isBlank()) {
                    String url = UriComponentsBuilder.fromHttpUrl(fallbackApiUrl)
                            .queryParam("linkedin_url", candidate.getLinkedinUrl())
                            .queryParam("use_cache", "if-present")
                            .build().toUriString();
                    boolean ok2 = enrichCandidateFromUrl(candidate, apiKey, url, null);
                    return ok2 ? "Enriched with fallback provider" : "Fallback returned no data";
                }
                return "NinjaPear enrichment did not return details";
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            String msg = "Enrichment API Rate Limit Exceeded (HTTP 429). Please wait before enriching more contacts.";
            LOGGER.error(msg);
            return msg;
        } catch (RestClientResponseException e) {
            String msg = String.format("Failed to enrich candidate from Proxycurl: status=%d response=%s", e.getRawStatusCode(), getSafeResponseBody(e));
            LOGGER.error(msg, e);
            // Re-throw 5xx errors so Spring Retry catches them
            if (e.getStatusCode().is5xxServerError()) {
                throw new HttpServerErrorException(e.getStatusCode(), e.getStatusText(), e.getResponseHeaders(), e.getResponseBodyAsByteArray(), null);
            }
            return msg;
        } catch (ResourceAccessException e) {
            LOGGER.error("Network timeout connecting to Enrichment API", e);
            throw e; // Triggers @Retryable
        } catch (Exception e) {
            String msg = "Failed to enrich candidate from Proxycurl: " + e.getMessage();
            LOGGER.error(msg, e);
            return msg;
        }
    }
    
    private boolean isLegacyProxycurlUrl() {
        return apiUrl != null && apiUrl.contains("/proxycurl/");
    }

    private boolean enrichWithProxycurl(CandidateDto candidate, String apiKey) {
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
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

        if (body != null && !body.isEmpty()) {
            candidate.setProfilePicUrl(body.path("profile_pic_url").asText(null));
            candidate.setHeadline(body.path("headline").asText(candidate.getTitle()));
            candidate.setSummary(body.path("summary").asText(null));

            String city = body.path("city").asText("");
            String country = body.path("country").asText("");
            if (!city.isEmpty() || !country.isEmpty()) {
                candidate.setLocation(city + (city.isEmpty() || country.isEmpty() ? "" : ", ") + country);
            }

            String fullName = body.path("full_name").asText(null);
            if (fullName != null && !fullName.isBlank()) {
                candidate.setName(fullName);
            }
            return true;
        }
        return false;
    }

    private boolean enrichWithNinjaPear(CandidateDto candidate, String apiKey) {
        String normalizedName = normalizeMissingValue(candidate.getName());
        String firstName = getFirstName(normalizedName);
        String lastName = getLastName(normalizedName);
        String company = normalizeMissingValue(candidate.getCompany());

        LOGGER.debug("Starting NinjaPear enrichment for candidate: name='{}', firstName='{}', company='{}'", normalizedName, firstName, company);

        if (firstName == null || firstName.isBlank()) {
            LOGGER.debug("Insufficient data to enrich candidate; name='{}'", candidate.getName());
            return false;
        }

        boolean enriched = false;

        // Prefer employer website flow which the API accepts (first_name + employer_website)
        if (company != null && !company.isBlank()) {
            String employerWebsite = resolveCompanyWebsite(company, apiKey);
            if (employerWebsite != null && !employerWebsite.isBlank()) {
                enriched = enrichWithNinjaPearByEmployerWebsite(candidate, apiKey, firstName, lastName, employerWebsite);
            } else {
                // If we could not resolve the employer website (for example insufficient credits),
                // the provider requires employer_website or work_email. Do not attempt the linkedin_url
                // fallback because the API returns 400 for linkedin-only requests.
                LOGGER.debug("Could not resolve website for company '{}'; skipping LinkedIn fallback for {}", company, candidate.getLinkedinUrl());
                LOGGER.debug("NinjaPear enrichment did not return details for {}", candidate.getLinkedinUrl());
                return false;
            }
        } else {
            // No company provided. The API requires work_email or employer_website+first_name.
            // We don't have a work_email at this point, so skip attempting a linkedin-only request.
            LOGGER.debug("No company or work email available for {}; skipping NinjaPear enrichment", candidate.getLinkedinUrl());
            return false;
        }

        if (!enriched) {
            LOGGER.debug("NinjaPear enrichment did not return details for {}", candidate.getLinkedinUrl());
        }

        return enriched;
    }

    private boolean enrichWithNinjaPearByEmployerWebsite(CandidateDto candidate, String apiKey, String firstName, String lastName, String employerWebsite) {
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("first_name", firstName)
                .queryParam("employer_website", employerWebsite)
                .queryParam("use_cache", "if-present")
                .queryParamIfPresent("last_name", lastName == null || lastName.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(lastName))
                .build()
                .toUriString();

        return enrichCandidateFromUrl(candidate, apiKey, url, employerWebsite);
    }

    private boolean enrichWithNinjaPearByLinkedIn(CandidateDto candidate, String apiKey) {
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("linkedin_url", candidate.getLinkedinUrl())
                .queryParam("use_cache", "if-present")
                .build()
                .toUriString();

        return enrichCandidateFromUrl(candidate, apiKey, url, null);
    }

    private boolean enrichCandidateFromUrl(CandidateDto candidate, String apiKey, String url, String employerWebsite) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();
            if (body != null) {
                LOGGER.debug("Raw Proxycurl response: {}", body.toString());
            }
            JsonNode profile = unwrapProfileNode(body);
            if (profile == null || profile.isEmpty()) {
                LOGGER.debug("Profile node is null or empty after unwrapping for {}", candidate.getLinkedinUrl());
                return false;
            }
            LOGGER.debug("Unwrapped profile for {}: {}", candidate.getLinkedinUrl(), profile.toString());

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
            if (employerWebsite != null && !employerWebsite.isBlank()) {
                candidate.setCompanyWebsite(employerWebsite);
            } else if (companyWebsite != null && !companyWebsite.isBlank()) {
                candidate.setCompanyWebsite(companyWebsite);
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

            if ((candidate.getEmail() == null || candidate.getEmail().isBlank()) && candidate.getCompanyWebsite() != null) {
                String resolvedDomain = extractDomain(candidate.getCompanyWebsite());
                if (resolvedDomain != null && !resolvedDomain.isBlank()) {
                    String workEmail = resolveWorkEmail(getFirstName(candidate.getName()), getLastName(candidate.getName()), resolvedDomain, apiKey);
                    if (workEmail != null && !workEmail.isBlank()) {
                        candidate.setEmail(workEmail);
                    }
                }
            }

            return true;
        } catch (RestClientResponseException e) {
            LOGGER.debug("NinjaPear request failed for {}: status={} response={}", candidate.getLinkedinUrl(), e.getRawStatusCode(), getSafeResponseBody(e));
            return false;
        }
    }

    private JsonNode unwrapProfileNode(JsonNode body) {
        if (body == null || body.isNull()) {
            return body;
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

    private String resolveCompanyWebsite(String company, String apiKey) {
        String websiteLookupUrl = UriComponentsBuilder.fromHttpUrl(getApiHost())
                .path("/api/v1/company/website")
                .queryParam("company_name", company)
                .queryParam("use_cache", "if-present")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(websiteLookupUrl, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();
            String website = body != null ? body.path("website").asText(null) : null;
            return website == null || website.isBlank() ? null : website;
        } catch (RestClientResponseException e) {
            LOGGER.debug("Company website lookup failed for '{}': status={} response={} ", company, e.getRawStatusCode(), getSafeResponseBody(e));
            return null;
        }
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

    private String resolveWorkEmail(String firstName, String lastName, String domain, String apiKey) {
        if (firstName == null || firstName.isBlank() || domain == null || domain.isBlank()) {
            return null;
        }

        String url = UriComponentsBuilder.fromHttpUrl(getApiHost())
                .path("/api/v1/employee/work-email")
                .queryParam("first_name", firstName)
                .queryParam("domain", domain)
                .queryParamIfPresent("last_name", lastName == null || lastName.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(lastName))
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
            String email = body.path("work_email").asText(null);
            if (email == null || email.isBlank()) {
                email = body.path("email").asText(null);
            }
            return email;
        } catch (RestClientResponseException e) {
            LOGGER.debug("Work email lookup failed for '{} {}' domain='{}': status={} response={}", firstName, lastName, domain, e.getRawStatusCode(), getSafeResponseBody(e));
            return null;
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
