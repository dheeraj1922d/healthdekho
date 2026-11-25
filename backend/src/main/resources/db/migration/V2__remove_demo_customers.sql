-- V1 contained assessment/demo customer accounts. Keep catalog products, but remove
-- demo identities and their transactions before running outside a local profile.
DELETE FROM premium_payment
WHERE installment_id IN (
    SELECT i.installment_id
    FROM premium_installment i
    JOIN customer_policy p ON p.policy_id = i.policy_id
    JOIN customer c ON c.customer_id = p.customer_id
    WHERE c.email IN ('aarav.sharma@example.com', 'meera.iyer@example.com', 'rohan.verma@example.com')
);
DELETE FROM premium_installment
WHERE policy_id IN (
    SELECT p.policy_id
    FROM customer_policy p
    JOIN customer c ON c.customer_id = p.customer_id
    WHERE c.email IN ('aarav.sharma@example.com', 'meera.iyer@example.com', 'rohan.verma@example.com')
);
DELETE FROM ai_recommendation_item
WHERE recommendation_request_id IN (
    SELECT r.recommendation_request_id
    FROM ai_recommendation_request r
    JOIN customer c ON c.customer_id = r.customer_id
    WHERE c.email IN ('aarav.sharma@example.com', 'meera.iyer@example.com', 'rohan.verma@example.com')
);
DELETE FROM ai_recommendation_request
WHERE customer_id IN (
    SELECT customer_id FROM customer
    WHERE email IN ('aarav.sharma@example.com', 'meera.iyer@example.com', 'rohan.verma@example.com')
);
DELETE FROM customer_policy
WHERE customer_id IN (
    SELECT customer_id FROM customer
    WHERE email IN ('aarav.sharma@example.com', 'meera.iyer@example.com', 'rohan.verma@example.com')
);
DELETE FROM customer
WHERE email IN ('aarav.sharma@example.com', 'meera.iyer@example.com', 'rohan.verma@example.com');
