# HealthDekho AWS Deployment Runbook

This is a manual, account-owner-operated deployment guide. It does **not** create AWS resources automatically. The current agent environment has no AWS CLI, account, or credentials, so no AWS resource has been created or billed.

## 1. Recommended AWS layout

```text
Browser
  ├── HTTPS → CloudFront → private S3 bucket (React static files)
  └── HTTPS → public Application Load Balancer → ECS Fargate service (Spring Boot :8080)
                                                    ├── private RDS PostgreSQL
                                                    ├── Amazon Bedrock Runtime (Converse)
                                                    ├── Secrets Manager (DB/JWT secrets)
                                                    └── CloudWatch Logs and metrics
```

- **API:** ECS on AWS Fargate, deployed from the included `backend/Dockerfile`.
- **Database:** Amazon RDS for PostgreSQL, in private subnets, encrypted, automated backups enabled.
- **Web:** S3 private origin + CloudFront Origin Access Control (OAC). Build React with `VITE_API_URL` pointing at the API base URL.
- **AI:** Amazon Bedrock Converse. The ECS task role receives narrowly scoped model invocation permissions; no AWS access key is stored in the app.
- **Secrets/observability:** Secrets Manager for runtime secrets; CloudWatch logs/alarms.

**Do not base a new deployment on App Runner:** AWS App Runner stopped accepting new customers on April 30, 2026. This runbook therefore targets ECS/Fargate, which remains a supported container path. See official links in [References](#13-references).

## 2. Before you begin

You or your AWS administrator must have:

- An AWS account and a selected AWS Region. Use a single Region for ECS, RDS, ECR, and Bedrock where the chosen Bedrock model is available.
- An IAM identity that can create/update the resources below. Prefer a dedicated deployment role; do not use root credentials.
- Docker with Buildx, AWS CLI v2, Git, Node.js 20+, and Java 21/Maven if building locally.
- A public domain name only if a custom domain is desired. CloudFront's default hostname is sufficient for a first demonstration.
- Approval for ongoing AWS spend. RDS, NAT gateways, ALB, Fargate, CloudFront, S3, Secrets Manager, CloudWatch, and Bedrock inference are usage/capacity billed. Review current pricing for your Region with the AWS Pricing Calculator before provisioning.

No AWS account ID, domain, or region was supplied in the request, so placeholders below must be replaced by you. Never send access keys, passwords, JWT secrets, or full customer data in chat or commit them to GitHub.

## 3. Pick and verify a Bedrock model

1. In the AWS console, select your deployment Region and open Amazon Bedrock.
2. Confirm the desired foundation model is available and enabled for the account/Region. Check its throughput/quota and any model access or use-case requirements.
3. The app's default model identifier is `amazon.nova-lite-v1:0`; change `BEDROCK_MODEL_ID` if a different supported model is selected.
4. Some model families/regions require an inference profile rather than a direct foundation-model ID. In that case configure the profile ID in `BEDROCK_MODEL_ID` and allow its inference-profile ARN and any required source/target Region model ARNs in the task-role policy.
5. Confirm the selected model accepts the request size, latency, and data-handling rules for your application.

The app calls the Converse API via Spring AI. Set `AWS_REGION` to the runtime Region and `AI_ENABLED=true`. Spring AI uses the AWS SDK credential chain. In ECS, the source of credentials must be the ECS **task role**.

## 4. Create the network and database

Use your organization's approved VPC/IP plan. For a typical production deployment:

1. Create or select a VPC spanning at least two Availability Zones.
2. Place the public Application Load Balancer in public subnets. Place ECS tasks and RDS in private subnets.
3. Configure ECS tasks to reach Bedrock Runtime over HTTPS (`443`) using either:
   - a NAT gateway for controlled outbound egress, or
   - an AWS PrivateLink interface VPC endpoint for Bedrock Runtime with private DNS, plus required endpoints/routing for ECR image pulls, CloudWatch Logs, and Secrets Manager.
   Choose after comparing network reachability, operational needs, and cost. A private task subnet without NAT or the required VPC endpoints cannot call Bedrock.
4. Create an RDS PostgreSQL database in private subnets. Select a currently supported PostgreSQL engine version compatible with the JDBC driver. Enable encryption, automated backups, deletion protection for production, and maintenance windows.
5. Use security groups rather than broad CIDR rules:
   - ALB SG: inbound HTTPS `443` from intended public clients; optionally redirect HTTP `80` to HTTPS.
   - ECS task SG: inbound TCP `8080` **only from the ALB security group**; outbound `5432` to the RDS SG and `443` to the selected egress/Bedrock path.
   - RDS SG: inbound TCP `5432` **only from the ECS task SG**.
6. Create a database and a dedicated application database user. Store the password in Secrets Manager. On initial deployment, Flyway needs permission to create/alter the application schema. For a higher-assurance setup, run migrations as a controlled one-off deployment task using a migration user, then run application tasks with a restricted DML-only user; because Flyway is enabled at application startup in this project, configure that separation deliberately before using a restricted runtime user.

Construct `DATABASE_URL` with the RDS endpoint, database name, and TLS. Example:

```text
jdbc:postgresql://<rds-endpoint>:5432/healthdekho?sslmode=require
```

Use the RDS certificate validation approach required by your organization (for stricter certificate verification, include the current AWS RDS CA bundle and JDBC `verify-full` configuration).

## 5. Create secrets

Create secrets in AWS Secrets Manager, for example:

- `healthdekho/prod/database-password`
- `healthdekho/prod/jwt-secret`
- optionally `healthdekho/prod/database-username` if your platform stores username as a secret too

Generate `JWT_SECRET` from a cryptographically secure random source (at least 32 bytes). Keep it stable across routine releases or users will be signed out. Define a rotation process that intentionally invalidates existing tokens. Do not use `.env.example` values in AWS.

The app does not need a Bedrock API key. IAM task-role permissions authorize the SDK call. Do not create/store `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY` for the running service.

## 6. IAM roles and Bedrock permissions

Create separate roles for separate jobs:

### ECS task execution role

Trust `ecs-tasks.amazonaws.com`. Attach the standard ECS task execution policy for ECR image download and CloudWatch log delivery. Add only the necessary `secretsmanager:GetSecretValue` permission on the specific secret ARNs (and `kms:Decrypt` only for the customer-managed KMS key, if used).

### ECS task role (application permissions)

Trust `ecs-tasks.amazonaws.com`. Grant only model inference on the specific Bedrock model/profile selected. For synchronous Converse calls, Bedrock authorizes with `bedrock:InvokeModel`; if streaming is introduced, add `bedrock:InvokeModelWithResponseStream` only when needed. Restrict `Resource` to the applicable model ARN(s) / inference-profile ARN(s), after confirming the exact resource format in the selected Region. Do not attach broad `AdministratorAccess` or all-model permissions.

Also review: Bedrock model-access/account controls, CloudTrail, KMS key policy, VPC endpoint policy (if used), and organization SCPs.

## 7. Build and push the backend container to ECR

Create an ECR repository in the deployment Region (example name: `healthdekho-api`) and retain the repository URI. Authenticate using an IAM identity configured locally; the command below pipes a short-lived login token and does not print it:

```bash
export AWS_PROFILE=<your-deployment-profile>
export AWS_REGION=<your-region>
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/healthdekho-api"

aws ecr create-repository --repository-name healthdekho-api --image-scanning-configuration scanOnPush=true --region "$AWS_REGION"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
```

From the repository root, build for the architecture selected in the ECS task definition (this example is Linux AMD64):

```bash
docker buildx build --platform linux/amd64 --provenance=false \
  -f backend/Dockerfile -t "$ECR_URI:release-1" backend --load
docker push "$ECR_URI:release-1"
```

Use a unique immutable release tag in production and keep ECR image scanning/retention enabled. For ARM64/Graviton, build `--platform linux/arm64` and set the ECS task architecture to ARM64; they must match.

## 8. Configure ECS/Fargate API service

1. Create an ECS cluster with Fargate capacity.
2. Register a Fargate task definition:
   - Network mode: `awsvpc`; CPU/memory sized for the expected load and Bedrock latency.
   - Container image: the ECR URI and immutable tag.
   - Container port: TCP `8080`.
   - Log driver: `awslogs` to a dedicated CloudWatch log group with retention.
   - Task execution role: the execution role above.
   - Task role: the narrowly scoped application role above.
   - Environment: `PORT=8080`, `DATABASE_URL`, `AWS_REGION`, `BEDROCK_MODEL_ID`, `AI_ENABLED=true`, `CORS_ALLOWED_ORIGINS=https://<frontend-host>`, `TOKEN_HOURS=12` (if desired), and `SPRING_PROFILES_ACTIVE` unset or a non-local production profile.
   - Secret references: database password, JWT secret, and database username if stored as a secret. Do not put secret values in plain-text task environment settings.
3. Create an internet-facing Application Load Balancer in public subnets with an HTTPS listener and ACM certificate. Configure the target group for IP targets on port `8080` and health-check path `/api/health`.
4. Create an ECS service across private subnets, attached to the target group. Set ECS service security groups per the previous section and desired minimum task count. Configure deployment circuit breaker/rollback and autoscaling after observing metrics.
5. Validate target health and CloudWatch logs. The first application start runs Flyway migrations. If it fails, inspect Flyway and database connectivity logs before sending traffic.
6. Record the ALB DNS name or bind an API custom domain (ACM certificate must be in the same Region as the ALB). Use this HTTPS API URL in the React build below.

## 9. Publish React to S3 + CloudFront

1. Create a private S3 bucket in the desired Region for frontend assets. Do not enable public ACLs or public bucket policy.
2. Create a CloudFront distribution with the S3 bucket as origin and Origin Access Control (OAC), granting CloudFront read access only.
3. Configure TLS with the CloudFront default certificate or a custom domain certificate in `us-east-1` (CloudFront requirement). Set the default root object to `index.html`.
4. Build the Vite application from the `frontend` directory. Vite embeds `VITE_API_URL` at build time:

```bash
cd frontend
npm ci
VITE_API_URL="https://<api-domain>/api" npm run build
aws s3 sync dist/ "s3://<frontend-bucket>/" --delete
aws cloudfront create-invalidation --distribution-id <distribution-id> --paths '/*'
```

5. Add the exact CloudFront/custom frontend origin (scheme + host only) to the API's `CORS_ALLOWED_ORIGINS`, e.g. `https://app.example.com`. Update the ECS service after changing environment configuration.
6. For a custom domain, create/validate an ACM certificate in `us-east-1` for CloudFront, associate the alias and DNS records, and separately configure an API domain/certificate at the ALB Region.

Never upload `.env`, source maps containing private data, test fixtures with real personal information, or credentials to S3.

## 10. Smoke test after deployment

Use the API URL and a sample/demo account only if a local-only database has been intentionally loaded. Production migrations remove the shipped demo customer accounts; production has **no seeded customer logins**. Create real accounts using a secure, reviewed provisioning process before customer testing.

```bash
export API_BASE='https://<api-domain>/api'
curl -fsS "$API_BASE/health"

# Login request should only be run with an intentionally provisioned test customer.
curl -fsS -X POST "$API_BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"<test-customer-email>","password":"<test-password>"}'
```

Verify:

- CloudFront serves the app over HTTPS and API calls are not blocked by CORS.
- Unauthenticated API requests receive `401`; authenticated users only see their own policies.
- `/api/products/available` excludes the customer's existing product(s).
- Policy registration enforces age/duplicate rules and creates installments.
- Payments reject a paid installment or amount above balance and apply the penalty after grace.
- `/api/recommendations` uses Bedrock only when enabled and authorized; model outage uses deterministic fallback.
- Logs do not contain credentials, JWTs, or sensitive prompt content.

## 11. CI/CD suggestions

For a later automation step, use GitHub Actions with GitHub OIDC federation to assume a narrowly scoped AWS deployment role. Avoid static AWS keys in GitHub secrets. A secure pipeline can:

1. Run `mvn test package` and `npm ci && npm run build`.
2. Build and scan the immutable container image; push it to ECR.
3. Apply migrations using a gated deployment task/step.
4. Update the ECS service to the immutable image tag and wait for service stability.
5. Build frontend with the correct `VITE_API_URL`, sync to S3, and invalidate CloudFront.
6. Require environment protection/approval before production deployment and keep rollback artifacts.

The workflow is not included in this repository yet because the AWS account, role ARNs, repository, domain, and approval policy are unknown.

## 12. Cost management and cleanup

Before launch, estimate each resource in the AWS Pricing Calculator for your Region and expected hours/traffic. Particular cost drivers include RDS uptime/storage/backups, Fargate vCPU/memory, ALB hours/LCUs, NAT gateway hours/data processing, interface VPC endpoints, CloudFront transfer/requests, CloudWatch ingestion/retention, Secrets Manager secrets, and Bedrock input/output tokens. Set AWS Budgets and usage alerts. Use small non-production capacity only for short demos; do not assume a free tier covers the whole system.

To shut down a temporary test environment, follow your organization's retention policy, export/snapshot any data that must be kept, then scale ECS to zero/delete the ECS service, remove ALB/listeners/target groups, delete ECR images/repository if no longer needed, delete or snapshot RDS, remove NAT/VPC endpoints only if unused, remove CloudFront/S3 only after confirming no other consumers, and clean secrets/log groups after retention requirements are met. Resource deletion is destructive; protect production RDS and backups.

## 13. References

- [Amazon ECS task IAM roles](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html)
- [Amazon ECS task execution role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html)
- [AWS Fargate task networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html)
- [Amazon RDS for PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html)
- [Amazon Bedrock security and IAM](https://docs.aws.amazon.com/bedrock/latest/userguide/security-iam.html)
- [Spring AI Bedrock Converse](https://docs.spring.io/spring-ai/reference/api/chat/bedrock-converse.html)
- [Spring AI Bedrock connection properties](https://docs.spring.io/spring-ai/reference/api/bedrock.html)
- [AWS App Runner notice / service guide](https://docs.aws.amazon.com/apprunner/latest/dg/what-is-apprunner.html)
