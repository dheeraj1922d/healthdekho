# HealthDekho — Insurance Management System

HealthDekho is a Java 21 / Spring Boot 3.5.7 and React application implementing the current-scope features in the supplied FDE capstone assessment: customer authentication, policy discovery and registration, AI-assisted recommendations, premium installment/payment management, and customer policy search.

## Stack and architecture
- **Backend:** Spring Web, Spring Security (signed bearer tokens), Spring Data JPA, Bean Validation, Flyway, PostgreSQL, optional Spring AI OpenAI integration.
- **Frontend:** React functional components and hooks, Axios, Vite.
- **Layers:** REST controllers → services/business rules → repositories → JPA entities; request/response DTOs are records.
- **Future claims/claim approval:** intentionally excluded, as the assessment marks it as a future enhancement.

## Run locally
Requirements: Java 21, Maven 3.9+, Node.js 20+, npm, and Docker with Compose.

```bash
cp .env.example .env
docker compose up --build
```
Open http://localhost:5173. Flyway creates and seeds the PostgreSQL schema on first backend startup. The local sample users are listed in `.env.example`; they are for demonstration only and must be replaced/disabled before production. To enable model-based recommendations, add an OpenAI API key to `.env`; without it, the recommendations endpoint uses a transparent, deterministic product-ranking fallback and still persists each request/result.

For split local development, start PostgreSQL with `docker compose up db -d`, run the API from `backend` with `mvn spring-boot:run`, then run `npm install && npm run dev` from `frontend`.

## Main routes
- `POST /api/auth/login`, `GET /api/auth/me`
- `GET /api/products`, `GET /api/products/available`
- `GET /api/policies`, `POST /api/policies`, `GET /api/policies/search`
- `GET /api/policies/{policyNumber}`, `GET /api/policies/{policyNumber}/installments`
- `POST /api/policies/{policyNumber}/payments`
- `POST /api/recommendations`, `GET /api/recommendations`
- `GET /api/health`

All policy, payment, and recommendation routes require the logged-in customer's bearer token and scope customer data to that identity. See `backend/src/main/resources/db/migration/V1__initial_schema.sql` for the data model and demo seed data.

## Production notes
Deploy the API to a Java 21-capable container/runtime and provide a managed PostgreSQL database. Host the React build on a static hosting provider, set `VITE_API_URL` to the API origin, and configure `CORS_ALLOWED_ORIGINS`. Set strong `JWT_SECRET`, database credentials, and (optionally) `OPENAI_API_KEY` as hosting secrets, not in source control. Do not use the bundled demonstration credentials or compose defaults in production. This project is not compatible with the preconfigured Hono/Cloudflare Pages one-click runtime.
