CREATE TABLE customer (
    customer_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    date_of_birth DATE NOT NULL CHECK (date_of_birth <= CURRENT_DATE),
    phone VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_customer_status ON customer(status);

CREATE TABLE insurance_product (
    product_id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(32) NOT NULL UNIQUE,
    policy_name VARCHAR(160) NOT NULL,
    policy_type VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    term_months INTEGER NOT NULL CHECK (term_months > 0),
    premium_frequency VARCHAR(20) NOT NULL,
    premium_amount NUMERIC(19,2) NOT NULL CHECK (premium_amount > 0),
    sum_insured NUMERIC(19,2) NOT NULL CHECK (sum_insured > 0),
    min_entry_age INTEGER NOT NULL CHECK (min_entry_age >= 0),
    max_entry_age INTEGER NOT NULL CHECK (max_entry_age >= min_entry_age),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_product_active ON insurance_product(active);

CREATE TABLE customer_policy (
    policy_id BIGSERIAL PRIMARY KEY,
    policy_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer(customer_id),
    product_id BIGINT NOT NULL REFERENCES insurance_product(product_id),
    start_date DATE NOT NULL,
    maturity_date DATE NOT NULL CHECK (maturity_date > start_date),
    premium_frequency VARCHAR(20) NOT NULL,
    premium_amount NUMERIC(19,2) NOT NULL CHECK (premium_amount > 0),
    sum_insured NUMERIC(19,2) NOT NULL CHECK (sum_insured > 0),
    total_premium_payable NUMERIC(19,2) NOT NULL CHECK (total_premium_payable > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_policy_product UNIQUE(customer_id, product_id)
);
CREATE INDEX idx_policy_customer_status ON customer_policy(customer_id, status);
CREATE INDEX idx_policy_number ON customer_policy(policy_number);

CREATE TABLE premium_installment (
    installment_id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES customer_policy(policy_id) ON DELETE CASCADE,
    installment_number INTEGER NOT NULL CHECK (installment_number > 0),
    due_date DATE NOT NULL,
    grace_period_end_date DATE NOT NULL,
    scheduled_amount NUMERIC(19,2) NOT NULL CHECK (scheduled_amount > 0),
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0 AND paid_amount <= scheduled_amount),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT uq_policy_installment UNIQUE(policy_id, installment_number)
);
CREATE INDEX idx_installment_policy_status ON premium_installment(policy_id, payment_status);

CREATE TABLE premium_payment (
    payment_id BIGSERIAL PRIMARY KEY,
    installment_id BIGINT NOT NULL REFERENCES premium_installment(installment_id),
    transaction_reference VARCHAR(48) NOT NULL UNIQUE,
    payment_date DATE NOT NULL,
    amount_paid NUMERIC(19,2) NOT NULL CHECK (amount_paid > 0),
    penalty_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (penalty_amount >= 0),
    payment_method VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_installment_date ON premium_payment(installment_id, payment_date);

CREATE TABLE ai_recommendation_request (
    recommendation_request_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(customer_id),
    insurance_goals VARCHAR(1000),
    preferred_term VARCHAR(120),
    preferred_policy_type VARCHAR(32),
    calculated_age INTEGER NOT NULL CHECK (calculated_age >= 0),
    model_name VARCHAR(120) NOT NULL,
    model_version VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recommendation_customer_date ON ai_recommendation_request(customer_id, requested_at);

CREATE TABLE ai_recommendation_item (
    recommendation_item_id BIGSERIAL PRIMARY KEY,
    recommendation_request_id BIGINT NOT NULL REFERENCES ai_recommendation_request(recommendation_request_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES insurance_product(product_id),
    rank_order INTEGER NOT NULL,
    recommendation_reason VARCHAR(1000) NOT NULL,
    suitability VARCHAR(20) NOT NULL,
    selected_for_registration BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_recommendation_product UNIQUE(recommendation_request_id, product_id)
);

-- Demo users use BCrypt hashes and are for local/demo use only.
-- Hashes intentionally use the same documented sample password: HealthDekho123!
INSERT INTO customer(customer_id, email, password_hash, full_name, date_of_birth, phone, status) VALUES
(1001, 'aarav.sharma@example.com', '$2b$10$IPg9xL5sH6Qwe.W5GtJrdeSU6aUY.jiKYCYkNAiBhhxLDqZPBut1O', 'Aarav Sharma', '1990-04-15', '9876501001', 'ACTIVE'),
(1002, 'meera.iyer@example.com', '$2b$10$IPg9xL5sH6Qwe.W5GtJrdeSU6aUY.jiKYCYkNAiBhhxLDqZPBut1O', 'Meera Iyer', '1985-09-22', '9876501002', 'ACTIVE'),
(1003, 'rohan.verma@example.com', '$2b$10$IPg9xL5sH6Qwe.W5GtJrdeSU6aUY.jiKYCYkNAiBhhxLDqZPBut1O', 'Rohan Verma', '2000-01-10', '9876501003', 'ACTIVE');
INSERT INTO insurance_product(product_id, product_code, policy_name, policy_type, description, term_months, premium_frequency, premium_amount, sum_insured, min_entry_age, max_entry_age, active) VALUES
(201, 'HD-TERM-001', 'HealthDekho Secure Term', 'TERM', 'Long-term protection that supports income replacement for your family.', 240, 'MONTHLY', 1250.00, 5000000.00, 18, 60, TRUE),
(202, 'HD-HEALTH-001', 'HealthDekho Family Health', 'HEALTH', 'Flexible annual medical cover for individuals and families.', 12, 'YEARLY', 18000.00, 1000000.00, 18, 65, TRUE),
(203, 'HD-SAV-001', 'HealthDekho Smart Savings', 'SAVINGS', 'Build long-term savings alongside life protection.', 120, 'QUARTERLY', 7500.00, 1500000.00, 21, 55, TRUE),
(204, 'HD-ACC-001', 'HealthDekho Personal Accident', 'ACCIDENT', 'Accident protection with a simple affordable premium.', 12, 'YEARLY', 3200.00, 750000.00, 18, 70, TRUE);
INSERT INTO customer_policy(policy_id, policy_number, customer_id, product_id, start_date, maturity_date, premium_frequency, premium_amount, sum_insured, total_premium_payable, status) VALUES
(3001, 'HDP-2026-0001', 1001, 201, '2026-11-20', '2046-11-20', 'MONTHLY', 1250.00, 5000000.00, 300000.00, 'ACTIVE'),
(3002, 'HDP-2026-0002', 1002, 202, '2026-11-20', '2027-11-20', 'YEARLY', 18000.00, 1000000.00, 18000.00, 'ACTIVE');
INSERT INTO premium_installment(installment_id, policy_id, installment_number, due_date, grace_period_end_date, scheduled_amount, paid_amount, payment_status) VALUES
(4001, 3001, 1, '2026-11-20', '2026-12-20', 1250.00, 1250.00, 'PAID'),
(4002, 3001, 2, '2026-12-20', '2027-01-20', 1250.00, 500.00, 'PARTIALLY_PAID'),
(4003, 3001, 3, '2027-01-20', '2027-02-20', 1250.00, 0.00, 'PENDING'),
(4004, 3002, 1, '2026-11-20', '2026-12-20', 18000.00, 18000.00, 'PAID');
INSERT INTO premium_payment(payment_id, installment_id, transaction_reference, payment_date, amount_paid, penalty_amount, payment_method) VALUES
(5001, 4001, 'TXN-HD-000001', '2026-11-20', 1250.00, 0.00, 'UPI'),
(5002, 4002, 'TXN-HD-000002', '2026-12-21', 500.00, 0.00, 'NET_BANKING'),
(5003, 4004, 'TXN-HD-000003', '2026-11-20', 18000.00, 0.00, 'CARD');
SELECT setval(pg_get_serial_sequence('customer','customer_id'), (SELECT MAX(customer_id) FROM customer));
SELECT setval(pg_get_serial_sequence('insurance_product','product_id'), (SELECT MAX(product_id) FROM insurance_product));
SELECT setval(pg_get_serial_sequence('customer_policy','policy_id'), (SELECT MAX(policy_id) FROM customer_policy));
SELECT setval(pg_get_serial_sequence('premium_installment','installment_id'), (SELECT MAX(installment_id) FROM premium_installment));
SELECT setval(pg_get_serial_sequence('premium_payment','payment_id'), (SELECT MAX(payment_id) FROM premium_payment));
