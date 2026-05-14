package com.leadfinder.service;

import com.leadfinder.client.ProxycurlClient;
import com.leadfinder.client.SerpApiClient;
import com.leadfinder.dto.CandidateDto;
import com.leadfinder.dto.SearchRequest;
import com.leadfinder.dto.SearchResponse;
import com.leadfinder.util.SearchQueryBuilder;
import com.leadfinder.util.ScoreUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final SerpApiClient serpApiClient;
    private final ProxycurlClient proxycurlClient;

    public SearchService(SerpApiClient serpApiClient, ProxycurlClient proxycurlClient) {
        this.serpApiClient = serpApiClient;
        this.proxycurlClient = proxycurlClient;
    }

    public SearchResponse searchCandidates(SearchRequest request) {
        List<String> queries = SearchQueryBuilder.buildLinkedInQueries(
                request.getName(),
                request.getCompany(),
                request.getTitle()
        );

        // Using a Map to de-duplicate results by LinkedIn URL while preserving order of discovery
        Map<String, CandidateDto> candidateMap = new LinkedHashMap<>();

        for (String query : queries) {
            List<CandidateDto> found = serpApiClient.searchLinkedInProfiles(query);
            for (CandidateDto candidate : found) {
                if (!candidateMap.containsKey(candidate.getLinkedinUrl())) {
                    candidateMap.put(candidate.getLinkedinUrl(), candidate);
                }
            }
            // If we already have a good number of candidates from the strongest queries, we can stop
            if (candidateMap.size() >= 10) {
                break;
            }
        }

        List<CandidateDto> candidates = new ArrayList<>(candidateMap.values());

        // Enrich and Score
        int enrichmentLimit = Math.min(candidates.size(), 3);
        for (int i = 0; i < enrichmentLimit; i++) {
            proxycurlClient.enrichCandidate(candidates.get(i));
        }

        candidates.forEach(candidate -> {
            int confidence = ScoreUtil.computeConfidence(
                    request.getName(),
                    request.getCompany(),
                    candidate
            );
            candidate.setConfidence(confidence);
        });

        // Rank by confidence
        candidates.sort((left, right) -> Integer.compare(right.getConfidence(), left.getConfidence()));

        SearchResponse response = new SearchResponse();
        response.setCandidates(candidates);
        return response;
    }
}
