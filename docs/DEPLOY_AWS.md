# Deploying HealthDekho on AWS (Elastic Beanstalk + RDS + Bedrock)

You will do four things: **1)** create the database, **2)** build the jar, **3)** create the Elastic Beanstalk app with the jar, **4)** allow Bedrock. Use one AWS region for everything (examples use `us-east-1`).

```
Browser -> Elastic Beanstalk (jar) -> RDS PostgreSQL
                                   -> Amazon Bedrock
```

## 1. Create the database (RDS)

Create it **separately from Elastic Beanstalk**, so deleting or rebuilding the EB environment never deletes your data.

1. AWS Console -> **RDS** -> *Create database*
   - Engine: **PostgreSQL** (version 15 or newer)
   - Template: *Free tier* / *Dev/Test*
   - DB instance identifier: `healthdekho-db`
   - Master username: `healthdekho`, and a strong password (save it)
   - Instance: `db.t4g.micro` (or similar small size)
   - Public access: **No**
   - Initial database name (under *Additional configuration*): `healthdekho`
2. Wait until the status is *Available*, then copy the **Endpoint** (like `healthdekho-db.abc123.us-east-1.rds.amazonaws.com`).

Your database URL is:

```
jdbc:postgresql://<endpoint>:5432/healthdekho
```

The tables and the 4 insurance plans are created automatically the first time the app starts (Flyway).

## 2. Build the jar

On your computer (needs Java 21 JDK, Maven, Node 20):

```bash
./build.sh
```

The file to upload is **`backend/target/healthdekho.jar`**.

> **Demo customers:** the jar includes 3 demo customers (password `HealthDekho123!`) so you can log in immediately. That is fine for a demo. For real use, set `SPRING_FLYWAY_LOCATIONS=classpath:db/migration` in step 3 **before the first start** and add your own customers (see "Adding a customer" below).

## 3. Create the Elastic Beanstalk application

1. AWS Console -> **Elastic Beanstalk** -> *Create application*
   - Name: `healthdekho`
   - Platform: **Java** -> branch **Corretto 21 running on 64bit Amazon Linux 2023**
   - Application code: **Upload your code** -> choose `healthdekho.jar`
   - Preset: *Single instance (free tier eligible)* is enough to start
2. On the **Configure service access** page pick (or create) the service role and the EC2 instance profile (usually `aws-elasticbeanstalk-ec2-role`). **Remember the instance profile name** - step 4 needs it.
3. Finish the wizard. Before submitting, or later via *Configuration -> Updates, monitoring, and logging -> Environment properties*, add:

| Name | Value |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<endpoint>:5432/healthdekho` |
| `DATABASE_USERNAME` | `healthdekho` |
| `DATABASE_PASSWORD` | your RDS password |
| `JWT_SECRET` | a long random text, 32+ characters (e.g. `openssl rand -base64 48`) |
| `AWS_REGION` | `us-east-1` |
| `BEDROCK_MODEL_ID` | `amazon.nova-lite-v1:0` |
| `AI_ENABLED` | `true` |
| `SPRING_FLYWAY_LOCATIONS` | *(optional)* `classpath:db/migration` to skip demo customers |

4. *Configuration -> Instance traffic and scaling*  (or *Load balancer -> Processes* if you use a load balancer): set the **health check path** to `/api/health`.

Elastic Beanstalk runs the jar for you and sends traffic to port **5000**, which is what the app uses by default. You do not need a Procfile.

### Let Elastic Beanstalk reach RDS

The EB instance must be allowed to connect to the database on port 5432:

1. EC2 Console -> *Security Groups* -> find the group used by your EB environment (named like `awseb-e-xxxx-stack-AWSEBSecurityGroup-...`). Copy its ID.
2. RDS -> `healthdekho-db` -> *VPC security groups* -> open the group -> *Edit inbound rules* -> add: **Type** PostgreSQL (5432), **Source** the EB security group ID from step 1.

(Both should be in the same VPC - the default VPC is fine.)

## 4. Allow Bedrock

1. **Bedrock access to the model:** Console -> *Amazon Bedrock* -> *Model catalog*, select your region and check the model is available to your account. (Amazon Nova models work without extra setup; some other providers ask you to accept terms or fill a short use-case form first.)
2. **IAM permission:** IAM -> *Roles* -> the EC2 instance profile role from step 3 (`aws-elasticbeanstalk-ec2-role`) -> *Add permissions* -> *Create inline policy* -> JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["bedrock:InvokeModel"],
      "Resource": "*"
    }
  ]
}
```

   For tighter security replace `"*"` with the ARN of your model, e.g. `arn:aws:bedrock:us-east-1::foundation-model/amazon.nova-lite-v1:0`. If you use an *inference profile* ID as `BEDROCK_MODEL_ID`, allow that profile's ARN too.

No AWS access keys are needed in the app - it uses the instance role automatically.

## 5. Check that it works

Open the environment URL shown in Elastic Beanstalk (like `http://healthdekho.eba-xxxx.us-east-1.elasticbeanstalk.com`).

```bash
curl http://<your-eb-url>/api/health          # {"status":"UP",...}
```

Then log in on the web page. Open the **Recommended (AI)** tab and press *Get recommendations*. The line under the form says `source: bedrock-converse` when Bedrock answered, or `source: healthdekho-ranking` if it used the simple fallback.

## Updating the app later

1. `./build.sh`
2. Elastic Beanstalk -> your environment -> **Upload and deploy** -> choose the new `healthdekho.jar`.

Database changes: add a new file `backend/src/main/resources/db/migration/V3__something.sql` (never edit V1). It runs automatically on the next start.

## Adding a customer (there is no sign-up page)

Create a BCrypt hash of the password, then insert a row using any PostgreSQL client that can reach RDS (for example from an EC2 instance, or temporarily via a bastion/CloudShell in the same VPC):

```bash
# makes a bcrypt hash of "ChangeMe123!"
htpasswd -bnBC 10 "" 'ChangeMe123!' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
```

```sql
INSERT INTO customer (email, password_hash, full_name, date_of_birth, phone)
VALUES ('jane@example.com', '<paste hash here>', 'Jane Doe', '1992-05-20', '9876543210');
```

## Troubleshooting

| Problem | Look at |
|---|---|
| Environment is red / "Degraded" | EB -> *Logs* -> *Request logs* -> *Last 100 lines*. Look for `web.stdout.log`. |
| `Connection refused` / timeout to database | Step 3 security-group rule; check the endpoint and password in `DATABASE_URL` etc. |
| `JWT_SECRET must be at least 32 characters` | Fix the `JWT_SECRET` environment property. |
| App starts but health check fails | Health check path must be `/api/health`. |
| Recommendations say `healthdekho-ranking` although `AI_ENABLED=true` | Bedrock call failed and the app fell back. Look in `web.stdout.log` for `Bedrock recommendation failed` - usually a missing IAM permission, wrong `AWS_REGION`, or a model not available in that region. |
| Nothing to log in with | You skipped the demo data - add a customer (above). |
| 502 right after deploy | The Java app needs ~20 seconds to start; wait and refresh. |

## Costs to remember

RDS, the EB EC2 instance (and a load balancer if you choose that preset) are billed while they run. Bedrock is billed per request. Delete the EB environment and the RDS instance when you are finished practising.
