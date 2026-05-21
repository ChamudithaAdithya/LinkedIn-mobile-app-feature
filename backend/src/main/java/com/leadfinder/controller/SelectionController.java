package com.leadfinder.controller;

import com.leadfinder.client.ProxycurlClient;
import com.leadfinder.dto.SelectRequest;
import com.leadfinder.dto.SelectResponse;
import com.leadfinder.dto.CandidateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class SelectionController {

    private final ProxycurlClient proxycurlClient;

    public SelectionController(ProxycurlClient proxycurlClient) {
        this.proxycurlClient = proxycurlClient;
    }

    @PostMapping("/select")
    public ResponseEntity<SelectResponse> selectCandidate(@RequestBody SelectRequest request) {
        CandidateDto selected = request.getSelectedCandidate();
        String enrichMessage = proxycurlClient.enrichCandidate(selected);
        String message = enrichMessage != null && !enrichMessage.isBlank() ? enrichMessage : "Profile selected successfully.";
        SelectResponse response = new SelectResponse(selected, message);
        return ResponseEntity.ok(response);
    }
}
