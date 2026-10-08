USE hmdp;

INSERT INTO tb_voucher (shop_id, title, sub_title, rules, pay_value, actual_value, type, status)
SELECT s.id,
       CONCAT('西安专享 · ', s.name, '限时秒杀'),
       '限时抢购，数量有限',
       '每位用户限购一张，秒杀成功后请在有效期内使用',
       CASE WHEN s.type_id = 1 THEN 3900 ELSE 2900 END,
       CASE WHEN s.type_id = 1 THEN 5000 ELSE 4000 END,
       1, 1
FROM tb_shop s
WHERE s.address LIKE '西安市%'
  AND NOT EXISTS (
      SELECT 1 FROM tb_voucher v
      WHERE v.shop_id = s.id AND v.type = 1 AND v.title = CONCAT('西安专享 · ', s.name, '限时秒杀')
  );

INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time)
SELECT v.id, 30,
       DATE_SUB(NOW(), INTERVAL 5 MINUTE),
       DATE_ADD(NOW(), INTERVAL 7 DAY)
FROM tb_voucher v
WHERE v.type = 1 AND v.status = 1 AND v.title LIKE '西安专享 · %限时秒杀'
  AND NOT EXISTS (SELECT 1 FROM tb_seckill_voucher sv WHERE sv.voucher_id = v.id);
