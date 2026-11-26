# Testing guide

The tests are plain **unit tests**: fast, and they need **no database, no internet and no AWS account**. Database classes and the Bedrock chat client are replaced with mocks (fake objects from Mockito).

## Run them

```bash
# Backend (Java) - 39 tests, about 15 seconds
cd backend
mvn test

# Frontend (React helpers) - 6 tests, about 1 second
cd frontend
npm install
npm test
```

`./build.sh` also runs the backend tests before it builds the jar, and stops if one fails. Use `./build.sh skip-tests` to skip them.

Success looks like:

```
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Run just one test class or one test:

```bash
mvn test -Dtest=PaymentServiceTest
mvn test -Dtest=PaymentServiceTest#latePaymentAddsTenPercentPenaltyOnTheOutstandingAmount
```

Reports are written to `backend/target/surefire-reports/`.

## What is tested

All files are in `backend/src/test/java/com/healthdekho/service/`.

| Test class | Tests | What it proves |
|---|---:|---|
| `PaymentServiceTest` | 7 | No penalty inside the grace period; 10% penalty after it; partial payments; cannot overpay; cannot pay a paid installment; same installment twice is rejected; other customer's installment is "not found" |
| `PolicyServiceTest` | 9 | Monthly policy -> 24 installments for 24 months; quarterly -> 40 for 120 months; maturity date and total premium; too young / too old rejected; duplicate policy rejected; unknown product rejected; search needs a criterion; page-size limit; other customer's policy is "not found" |
| `RecommendationServiceTest` | 10 | AI off -> Bedrock is never called; Bedrock answer is used; Bedrock error -> fallback; empty Bedrock answer -> fallback; only age-eligible plans; owned plans hidden; nothing eligible -> no call; keyword ranking rules |
| `BedrockRecommenderTest` | 8 | Parses the model's JSON; handles ```` ```json ```` fences; **drops invented product codes**; drops duplicates; bad suitability -> `CONSIDER`; empty / non-array answers; non-JSON text throws (so the service falls back); the prompt contains no email addresses |
| `AuthServiceTest` | 4 | Good login returns a valid token whose subject is the email; wrong password, unknown email and inactive customer are all rejected |
| `ProductServiceTest` | 1 | "Available plans" hides plans the customer already owns |
| `frontend/src/format.test.js` | 6 | Money, date, frequency, term and name display helpers |

## How a test is built (copy this pattern)

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PremiumInstallmentRepository installments;   // 1. fake database access
    @Mock PremiumPaymentRepository payments;

    @Test
    void latePaymentAddsTenPercentPenaltyOnTheOutstandingAmount() {
        var service = new PaymentService(installments, payments);              // 2. real service, fake repositories
        var installment = installment(42L, 60, "100.00");                      //    data: due 60 days ago
        when(installments.findLockedForCustomer(42L, POLICY, EMAIL))           // 3. tell the fake what to return
                .thenReturn(Optional.of(installment));

        var result = service.pay(EMAIL, POLICY, pay(42L, "25.00"));            // 4. do the action

        assertThat(result.getFirst().penaltyAmount()).isEqualByComparingTo("10.00");   // 5. check the outcome
    }
}
```

Steps: **arrange** (fake data) -> **act** (call the service) -> **assert** (check the result).

Helpers you can reuse:
- `TestData.customer(...)`, `TestData.healthPlan()`, `termPlan()`, `savingsPlan()` - sample customers and plans (`TestData.java`).
- Money is compared with `isEqualByComparingTo("10.00")` so `10.0` and `10.00` both match.

### Testing Bedrock without AWS

`BedrockRecommenderTest` replaces Spring AI's `ChatClient` with a mock that returns whatever text you choose:

```java
ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class, Mockito.RETURNS_DEEP_STUBS);
when(builder.build().prompt().user(Mockito.anyString()).call().content()).thenReturn("[{\"code\":\"HD-HEALTH\", ...}]");
var recommender = new BedrockRecommender(builder, new ObjectMapper());
```

To add a case, just change the returned text, e.g. a model reply with a made-up product code.

## Adding a test for a new rule

1. Open the matching `*ServiceTest` (or create `<Name>ServiceTest` next to the service).
2. Add a `@Test` method with a sentence-style name that says what should happen.
3. Arrange -> act -> assert, as above.
4. Run `mvn test -Dtest=<Name>ServiceTest`.

## Manual check on a running app (optional)

After starting the app (see README) you can check the real thing with `curl`:

```bash
curl localhost:5000/api/health
curl -i localhost:5000/api/policies                       # 401 without a token
TOKEN=$(curl -s -X POST localhost:5000/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"aarav.sharma@example.com","password":"HealthDekho123!"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -H "Authorization: Bearer $TOKEN" localhost:5000/api/policies
```

Real Bedrock is the only part the unit tests cannot cover. Test it on AWS (see `docs/DEPLOY_AWS.md`, section 5): the Recommended tab shows `source: bedrock-converse` when it worked.
