USE hmdp;

UPDATE tb_shop
SET images = CASE type_id
    WHEN 1 THEN '/imgs/types/ms.png'
    WHEN 2 THEN '/imgs/types/KTV.png'
    WHEN 3 THEN '/imgs/types/hpg.png'
    WHEN 4 THEN '/imgs/types/jsyd.png'
    WHEN 7 THEN '/imgs/types/qzyl.png'
    ELSE '/imgs/types/ms.png'
END
WHERE address LIKE '西安市%';
