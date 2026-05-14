package com.leadfinder.dto;

public class SelectRequest {

    private CandidateDto selectedCandidate;

    public SelectRequest() {
    }

    public SelectRequest(CandidateDto selectedCandidate) {
        this.selectedCandidate = selectedCandidate;
    }

    public CandidateDto getSelectedCandidate() {
        return selectedCandidate;
    }

    public void setSelectedCandidate(CandidateDto selectedCandidate) {
        this.selectedCandidate = selectedCandidate;
    }
}
