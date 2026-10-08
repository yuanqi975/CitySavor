-- Run this once against an existing hmdp database before enabling Kafka consumption.
-- The first query must return no rows. If it does, clean duplicate business data first.
SELECT user_id, voucher_id, COUNT(*) AS duplicate_count
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;

ALTER TABLE tb_voucher_order
    ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
