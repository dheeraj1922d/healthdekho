# HealthDekho — Insurance Management System

HealthDekho is a Java 21 / Spring Boot 3.5.7 and React application implementing the current-scope features in the supplied FDE capstone assessment: customer authentication, policy discovery and registration, AI-assisted recommendations, premium installment/payment management, and customer policy search.

## Stack and architecture
- **Backend:** Spring Web, Spring Security (signed bearer tokens), Spring Data JPA, Bean Validation, Flyway, PostgreSQL, Spring AI Amazon Bedrock Converse.
- **Frontend:** React functional components and hooks, Axios, Vite.
- **Layers:** REST controllers → services/business rules → repositories → JPA entities; request/response DTOs are records.
- **Future claims/claim approval:** intentionally excluded, as the assessment marks it as a future enhancement.

## Run locally
Requirements: Java 21, Maven 3.9+, Node.js 20+, npm, and Docker with Compose.

```bash
cp .env.example .env
docker compose up --build
```
Open http://localhost:5173. Flyway creates the schema and loads local demo data using the `local` Spring profile. Local recommendations default to deterministic catalog ranking. To test Bedrock locally, use an AWS CLI profile or role on the host and run the API with `AI_ENABLED=true`, `AWS_REGION`, and `BEDROCK_MODEL_ID`; never paste credentials into source control. Production migrations remove the bundled demo customers.

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

## AWS deployment and complete documentation
The recommended AWS shape is ECS on AWS Fargate for the Java API, RDS PostgreSQL for persistence, and S3 + CloudFront for the React build. Attach an ECS task role with narrowly scoped Bedrock inference access; do not use long-lived AWS keys in the container. AWS App Runner stopped accepting new customers on April 30, 2026, so this guide targets ECS/Fargate. AWS resources are not provisioned by this repository.

Start with [`docs/PROJECT_GUIDE.md`](docs/PROJECT_GUIDE.md) for functionality, architecture, APIs, schema, local development, tests, security and operations. Follow [`docs/AWS_DEPLOYMENT.md`](docs/AWS_DEPLOYMENT.md) for the AWS deployment runbook. Set `VITE_API_URL`, `CORS_ALLOWED_ORIGINS`, `AWS_REGION`, and `BEDROCK_MODEL_ID` for the chosen environment. Store JWT and database credentials in AWS Secrets Manager/SSM and keep demo credentials out of production. This project is not compatible with the preconfigured Hono/Cloudflare Pages one-click runtime.
