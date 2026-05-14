package com.leadfinder.dto;

import java.util.List;

public class SearchResponse {

    private List<CandidateDto> candidates;

    public SearchResponse() {
    }

    public SearchResponse(List<CandidateDto> candidates) {
        this.candidates = candidates;
    }

    public List<CandidateDto> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<CandidateDto> candidates) {
        this.candidates = candidates;
    }
}
