# LeadFinder Backend

This is the Spring Boot backend scaffold for the lead intelligence orchestration system.

## Phase 1: Backend Foundation

- `/api/v1/search` accepts `{ "name": "John Doe", "company": "IBM" }`
- builds search query: `site:linkedin.com/in "John Doe" "IBM"`
- queries SerpAPI
- returns `candidates` list

## Run locally

1. Set `serpapi.api.key` in `backend/src/main/resources/application.properties`
2. Run with Maven:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

## Next phases

- Add ranking / confidence scoring
- Add `/api/v1/select` endpoint
- Add Proxycurl enrichment
- Add email generation
- Add PostgreSQL persistence
