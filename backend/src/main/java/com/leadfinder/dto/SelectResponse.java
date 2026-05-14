package com.leadfinder.dto;

public class SelectResponse {

    private CandidateDto candidate;
    private String message;

    public SelectResponse() {
    }

    public SelectResponse(CandidateDto candidate, String message) {
        this.candidate = candidate;
        this.message = message;
    }

    public CandidateDto getCandidate() {
        return candidate;
    }

    public void setCandidate(CandidateDto candidate) {
        this.candidate = candidate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
