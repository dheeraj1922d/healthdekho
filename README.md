# HealthDekho

A small insurance web app: customers log in, browse plans, get AI plan recommendations, register policies, record premium payments and search their policies.

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Flyway |
| AI | Spring AI + **Amazon Bedrock** (falls back to simple ranking if Bedrock is off or fails) |
| Database | **Amazon RDS** PostgreSQL |
| Frontend | React 19 + Vite (compiled into the same jar) |
| Hosting | **AWS Elastic Beanstalk** - you upload one `.jar` file |

No Docker. No separate frontend hosting. One build gives you one jar.

```
 Browser ──► Elastic Beanstalk (healthdekho.jar)
               ├── serves the React pages  (/)
               ├── serves the REST API     (/api/...)
               ├── ──► RDS PostgreSQL
               └── ──► Amazon Bedrock
```

## Project layout

```
build.sh                  builds frontend + backend into ONE jar
backend/                  Spring Boot app
  src/main/java/com/healthdekho/
    config/               security + JWT setup
    controller/           REST endpoints
    service/              business rules (PolicyService, PaymentService, RecommendationService, BedrockRecommender ...)
    entity/ repository/   database classes
    dto/ exception/       request/response records, error handling
  src/main/resources/
    application.yml       all settings (read from environment variables)
    db/migration/         V1: tables + insurance products
    db/demo/              V2: 3 demo customers (optional)
  src/test/               unit tests
frontend/                 React app (src/pages, src/components)
docs/                     DEPLOY_AWS.md, TESTING.md
```

## Run it on your computer

You need: **Java 21 JDK**, **Maven 3.9+**, **Node 20+**, and a local **PostgreSQL**.

```bash
# 1. create the database (once)
psql -U postgres -c "CREATE USER healthdekho WITH PASSWORD 'healthdekho';"
psql -U postgres -c "CREATE DATABASE healthdekho OWNER healthdekho;"

# 2. build everything into one jar (also runs the unit tests)
./build.sh

# 3. start it
JWT_SECRET=any-random-text-at-least-32-characters-long \
AI_ENABLED=false \
java -jar backend/target/healthdekho.jar
```

Open <http://localhost:5000> and log in with `aarav.sharma@example.com` / `HealthDekho123!`
(other demo users: `meera.iyer@example.com`, `rohan.verma@example.com`, same password).

`AI_ENABLED=false` skips Bedrock so you do not need AWS credentials on your laptop. To try real Bedrock locally, run `aws configure` (or `export AWS_PROFILE=...`), set `AI_ENABLED=true`, and make sure your IAM user can call `bedrock:InvokeModel`.

### Editing the frontend with live reload (optional)

```bash
# terminal 1: the backend (step 3 above)
# terminal 2:
cd frontend && npm install && npm run dev      # http://localhost:5173, /api is proxied to port 5000
```

## Settings (environment variables)

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `JWT_SECRET` | **yes** | - | Random text, 32+ characters. Signs login tokens. |
| `DATABASE_URL` | on AWS | `jdbc:postgresql://localhost:5432/healthdekho` | RDS: `jdbc:postgresql://<rds-endpoint>:5432/<db>` |
| `DATABASE_USERNAME` | on AWS | `healthdekho` | |
| `DATABASE_PASSWORD` | on AWS | `healthdekho` | |
| `AWS_REGION` | no | `us-east-1` | Region used for Bedrock |
| `BEDROCK_MODEL_ID` | no | `amazon.nova-lite-v1:0` | Bedrock model to use |
| `AI_ENABLED` | no | `true` | `false` = never call Bedrock |
| `SPRING_FLYWAY_LOCATIONS` | no | migration + demo | Set to `classpath:db/migration` to **skip the demo customers** |
| `PORT` | no | `5000` | Elastic Beanstalk already expects 5000 |
| `TOKEN_HOURS` | no | `12` | Login lifetime |

## Deploy to AWS

Follow **[docs/DEPLOY_AWS.md](docs/DEPLOY_AWS.md)** - RDS, Bedrock permission, Elastic Beanstalk, step by step.

## Tests

Follow **[docs/TESTING.md](docs/TESTING.md)**. Short version:

```bash
cd backend  && mvn test        # 39 backend unit tests (no database or AWS needed)
cd frontend && npm test        # 6 frontend tests
```

## API

All routes need `Authorization: Bearer <token>` except health and login.

| Method | Route | What it does |
|---|---|---|
| GET | `/api/health` | Is the app up? (used by Elastic Beanstalk) |
| POST | `/api/auth/login` | Email + password, returns a token |
| GET | `/api/auth/me` | Logged-in customer |
| GET | `/api/products` | All active plans |
| GET | `/api/products/available` | Plans you have not registered |
| GET / POST | `/api/policies` | List / register your policies |
| GET | `/api/policies/search` | Search by `policyNumber`, `customerName`, `dateOfBirth` |
| GET | `/api/policies/{number}` | One policy with installments and payment history |
| POST | `/api/policies/{number}/payments` | Record payments |
| POST / GET | `/api/recommendations` | Get AI recommendations / recent ones |

## Business rules (in one place)

- **Registration:** age at start date must be inside the plan's entry-age range; one policy per plan per customer.
- **Installments:** one per interval (monthly 1, quarterly 3, half-yearly 6, yearly 12 months), count = term / interval. Grace period = 1 month after the due date.
- **Payments:** partial payments allowed, cannot exceed the balance. After the grace period a **10% penalty** is added on the outstanding amount. Payments are only *recorded* - no real money moves.
- **Recommendations:** only plans you are old enough for and do not already own are shown to Bedrock; any product code Bedrock invents is thrown away.
- Not included: claims, self-signup, password reset.
