package com.leadfinder.util;

import java.util.ArrayList;
import java.util.List;

public final class SearchQueryBuilder {

    private SearchQueryBuilder() {
        // utility class
    }

    public static List<String> buildLinkedInQueries(String fullName, String company, String title) {
        List<String> queries = new ArrayList<>();

        String cleanName = normalize(fullName);
        String cleanCompany = normalize(company);
        String cleanTitle = normalize(title);

        if (cleanName.isBlank()) {
            return queries;
        }

        // Base site constraint for both personal profile formats
        String siteConstraint = "(site:linkedin.com/in OR site:linkedin.com/pub)";

        // 1. Strongest: Exact Name + Exact Company
        if (!cleanCompany.isBlank()) {
            queries.add(String.format("%s \"%s\" \"%s\"", siteConstraint, cleanName, cleanCompany));
        }

        // 2. Relaxed: Exact Name + Company Keyword
        if (!cleanCompany.isBlank()) {
            queries.add(String.format("%s \"%s\" %s", siteConstraint, cleanName, cleanCompany));
        }

        // 3. Role-based: Exact Name + Exact Title
        if (!cleanTitle.isBlank()) {
            queries.add(String.format("%s \"%s\" \"%s\"", siteConstraint, cleanName, cleanTitle));
        }

        // 4. Broad: Exact Name only
        queries.add(String.format("%s \"%s\"", siteConstraint, cleanName));

        // 5. Very Broad: Split Name (Handle middle names or OCR errors)
        queries.add(String.format("%s %s", siteConstraint, cleanName));

        return queries;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        // Remove quotes and collapse extra whitespace
        return value.replace("\"", "").replaceAll("\\s+", " ").trim();
    }
}
