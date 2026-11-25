-- Local-only sample accounts and policies. This location is enabled by the local profile only.
INSERT INTO customer(customer_id, email, password_hash, full_name, date_of_birth, phone, status) VALUES
(1001, 'aarav.sharma@example.com', '$2b$10$IPg9xL5sH6Qwe.W5GtJrdeSU6aUY.jiKYCYkNAiBhhxLDqZPBut1O', 'Aarav Sharma', '1990-04-15', '9876501001', 'ACTIVE'),
(1002, 'meera.iyer@example.com', '$2b$10$IPg9xL5sH6Qwe.W5GtJrdeSU6aUY.jiKYCYkNAiBhhxLDqZPBut1O', 'Meera Iyer', '1985-09-22', '9876501002', 'ACTIVE'),
(1003, 'rohan.verma@example.com', '$2b$10$IPg9xL5sH6Qwe.W5GtJrdeSU6aUY.jiKYCYkNAiBhhxLDqZPBut1O', 'Rohan Verma', '2000-01-10', '9876501003', 'ACTIVE');
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
SELECT setval(pg_get_serial_sequence('customer_policy','policy_id'), (SELECT MAX(policy_id) FROM customer_policy));
SELECT setval(pg_get_serial_sequence('premium_installment','installment_id'), (SELECT MAX(installment_id) FROM premium_installment));
SELECT setval(pg_get_serial_sequence('premium_payment','payment_id'), (SELECT MAX(payment_id) FROM premium_payment));
