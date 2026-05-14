package com.leadfinder.util;

import com.leadfinder.dto.CandidateDto;

public final class ScoreUtil {

    private ScoreUtil() {
        // utility class
    }

    public static int computeConfidence(String targetName, String targetCompany, CandidateDto candidate) {
        int score = 0;

        if (isStrongNameMatch(targetName, candidate.getName())) {
            score += 50;
        }

        if (isCompanyMatch(targetCompany, candidate.getCompany(), candidate.getHeadline())) {
            score += 30;
        }

        if (hasTitleMatch(candidate.getHeadline())) {
            score += 20;
        }

        return Math.min(100, score);
    }

    private static boolean isStrongNameMatch(String targetName, String candidateName) {
        if (targetName == null || candidateName == null) {
            return false;
        }
        String normalizedTarget = targetName.trim().toLowerCase();
        String normalizedCandidate = candidateName.trim().toLowerCase();
        
        // Exact match or contains (for middle names/initials)
        return normalizedCandidate.contains(normalizedTarget) || normalizedTarget.contains(normalizedCandidate);
    }

    private static boolean isCompanyMatch(String targetCompany, String candidateCompany, String candidateHeadline) {
        if (targetCompany == null || targetCompany.trim().isEmpty()) {
            return false;
        }
        String normalizedCompany = targetCompany.trim().toLowerCase();
        
        if (candidateCompany != null && candidateCompany.toLowerCase().contains(normalizedCompany)) {
            return true;
        }
        
        // Sometimes the company is only in the headline
        return candidateHeadline != null && candidateHeadline.toLowerCase().contains(normalizedCompany);
    }

    private static boolean hasTitleMatch(String candidateHeadline) {
        // For now, if they have a headline, they get the score. 
        // Later this could be fuzzy similarity with targetTitle.
        return candidateHeadline != null && !candidateHeadline.trim().isEmpty();
    }
}
