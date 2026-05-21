package com.leadfinder.service;

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

    // ProxycurlClient is removed from this service entirely 
    // to prevent synchronous bottlenecks during the search phase.
    public SearchService(SerpApiClient serpApiClient) {
        this.serpApiClient = serpApiClient;
    }

    public SearchResponse searchCandidates(SearchRequest request) {
        List<String> queries = SearchQueryBuilder.buildLinkedInQueries(
                request.getName(),
                request.getCompany(),
                request.getTitle()
        );

        Map<String, CandidateDto> candidateMap = new LinkedHashMap<>();

        for (String query : queries) {
            List<CandidateDto> found = serpApiClient.searchLinkedInProfiles(query);
            for (CandidateDto candidate : found) {
                if (!candidateMap.containsKey(candidate.getLinkedinUrl())) {
                    candidateMap.put(candidate.getLinkedinUrl(), candidate);
                }
            }
            if (candidateMap.size() >= 10) {
                break;
            }
        }

        List<CandidateDto> candidates = new ArrayList<>(candidateMap.values());

        // Score based on SerpAPI preview text only
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