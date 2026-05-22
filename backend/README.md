# LeadFinder Backend

Spring Boot backend for LinkedIn candidate search and NinjaPear enrichment.

## Overview

The backend exposes two main flows:

1. **Search flow** (`/api/v1/search`) - builds LinkedIn search queries, calls SerpAPI, parses LinkedIn profile hits, scores and returns candidate previews.
2. **Selection/enrichment flow** (`/api/v1/select`) - accepts a selected candidate, enriches it via NinjaPear, and returns the enriched profile plus a status message.

This separation keeps search fast and uses enrichment only after the user selects a candidate.

## API Endpoints

- `POST /api/v1/search`
  - Request: `SearchRequest` containing `name`, `company`, and optional `title`
  - Response: `SearchResponse` containing a list of `CandidateDto`
- `POST /api/v1/select`
  - Request: `SelectRequest` containing a selected `CandidateDto`
  - Response: `SelectResponse` containing the enriched candidate and a `message`

## Search flow

The search workflow is implemented by `SearchController` -> `SearchService` -> `SerpApiClient`.

- `SearchService` builds multiple LinkedIn-focused queries using `SearchQueryBuilder`.
- `SerpApiClient` calls SerpAPI with `engine=google` and parses `organic_results`.
- It keeps only results which include `linkedin.com/in` and extracts:
  - candidate name
  - candidate company
  - LinkedIn URL
  - optional profile thumbnail
- Results are deduplicated by LinkedIn URL.
- `ScoreUtil` assigns a confidence score based on name, company, and headline matches.
- Candidates are sorted by confidence before returning.

## Enrichment flow

The enrichment workflow is implemented by `SelectionController` -> `ProxycurlClient`.

`ProxycurlClient.enrichCandidate(candidate)` performs a strict, stepwise NinjaPear flow:

1. **Company website lookup**
   - calls `/api/v1/company/website` with `company_name`
   - stores `companyWebsite` on the candidate
2. **Work email lookup**
   - calls `/api/v1/employee/work-email` with `first_name`, `last_name`, and `domain`
   - stores `email` on the candidate
3. **Profile details lookup**
   - calls `/api/v1/employee/profile` with `work_email`
   - populates profile fields such as `headline`, `summary`, `location`, `phone`, `personalWebsite`, `socialProfileUrl`, and company details

If any step fails, enrichment stops gracefully and returns a message explaining the failure.

## Key backend components

- `SearchController` - handles `/api/v1/search`
- `SelectionController` - handles `/api/v1/select`
- `SearchService` - orchestrates query generation, search execution, scoring, and ranking
- `SerpApiClient` - makes SerpAPI calls and parses LinkedIn candidate hits
- `ProxycurlClient` - performs NinjaPear enrichment on a selected candidate
- `SearchQueryBuilder` - constructs multiple query variants for better LinkedIn coverage
- `ScoreUtil` - computes candidate confidence from profile match quality
- `CandidateDto` - holds candidate preview and enriched profile fields

## Config

- `backend/src/main/resources/application.properties`
  - `serpapi.api.key` – required for search
  - `proxycurl.api.key` – required for NinjaPear enrichment
  - `proxycurl.api.url` – NinjaPear host base URL

## Run locally

1. Open the backend folder:
   ```bash
   cd backend
   ```
2. Set the API keys in `src/main/resources/application.properties`
3. Start the app:
   ```bash
   mvn spring-boot:run
   ```

## Notes

- The search flow is intentionally decoupled from enrichment to avoid slowing candidate discovery.
- Enrichment only runs after selection, so the frontend can search first and enrich later.
- The backend currently uses in-memory DTO-based results and no persistent storage.
