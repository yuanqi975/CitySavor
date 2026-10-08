USE hmdp;

SET @old_group_concat_max_len = @@group_concat_max_len;
SET SESSION group_concat_max_len = 1024;

CREATE TABLE IF NOT EXISTS tb_shop_comment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  content VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  is_mock TINYINT UNSIGNED NOT NULL DEFAULT 0,
  status TINYINT UNSIGNED NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_shop_comment_shop_time (shop_id, create_time),
  UNIQUE KEY uk_shop_comment_user_shop (user_id, shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO tb_shop_comment (shop_id, user_id, rating, content, is_mock, status, create_time)
WITH RECURSIVE nums(n) AS (
    SELECT 1
    UNION ALL SELECT n + 1 FROM nums WHERE n < 100
),
xian_shops AS (
    SELECT id, type_id, ROW_NUMBER() OVER (ORDER BY id) AS shop_no
    FROM tb_shop WHERE address LIKE '西安市%'
),
users AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS user_no
    FROM tb_user
)
SELECT s.id,
       u.id,
       CASE
           WHEN nums.n <= 55 THEN 5
           WHEN nums.n <= 80 THEN 4
           WHEN nums.n <= 90 THEN 3
           WHEN nums.n <= 97 THEN 2
           ELSE 1
       END,
       CASE s.type_id
           WHEN 1 THEN CASE nums.n % 5
               WHEN 0 THEN '味道在线，分量足，西安本地菜很有特色。'
               WHEN 1 THEN '肉夹馍和凉皮都不错，出餐速度也很快。'
               WHEN 2 THEN '环境干净，服务热情，适合朋友一起吃。'
               WHEN 3 THEN '口味比较家常，人均价格合理，整体满意。'
               ELSE '位置好找，菜品选择多，下次还会再来。' END
           WHEN 2 THEN CASE nums.n % 5
               WHEN 0 THEN '音响效果不错，包间干净，适合聚会。'
               WHEN 1 THEN '曲库更新及时，服务响应很快，体验不错。'
               WHEN 2 THEN '灯光氛围很好，整体价格比较实惠。'
               WHEN 3 THEN '朋友聚会来这里很方便，时段选择也多。'
               ELSE '包间环境舒适，工作人员态度很好。' END
           WHEN 3 THEN CASE nums.n % 5
               WHEN 0 THEN '咖啡口感顺滑，店里安静，适合办公。'
               WHEN 1 THEN '甜品和饮品搭配不错，环境很有氛围。'
               WHEN 2 THEN '座位舒适，服务自然，适合周末放松。'
               WHEN 3 THEN '店内采光很好，饮品价格也比较合理。'
               ELSE '路过进来坐了一会儿，整体体验很舒服。' END
           WHEN 4 THEN CASE nums.n % 5
               WHEN 0 THEN '教练指导细致，器械齐全，环境保持得很好。'
               WHEN 1 THEN '课程安排合理，工作人员服务比较周到。'
               WHEN 2 THEN '场地宽敞，设备新，适合规律锻炼。'
               WHEN 3 THEN '第一次体验不错，氛围轻松，交通也方便。'
               ELSE '设施维护得很好，整体性价比不错。' END
           ELSE CASE nums.n % 5
               WHEN 0 THEN '观影厅座椅舒适，画面和声音效果都不错。'
               WHEN 1 THEN '影院环境整洁，取票入场流程很顺畅。'
               WHEN 2 THEN '屏幕清晰，音效有沉浸感，服务也很快。'
               WHEN 3 THEN '周边交通方便，整体观影体验满意。'
               ELSE '影厅环境不错，活动期间价格比较划算。' END
       END,
       1, 0,
       DATE_SUB(NOW(), INTERVAL (s.shop_no * 100 + nums.n) HOUR)
FROM nums
JOIN xian_shops s
JOIN users u ON u.user_no = nums.n;

UPDATE tb_shop s
LEFT JOIN (
    SELECT shop_id, COUNT(*) AS total, ROUND(AVG(rating) * 10) AS score
    FROM tb_shop_comment WHERE status = 0 GROUP BY shop_id
) c ON c.shop_id = s.id
SET s.comments = COALESCE(c.total, 0), s.score = COALESCE(c.score, s.score)
WHERE s.address LIKE '西安市%';

SET SESSION group_concat_max_len = @old_group_concat_max_len;
