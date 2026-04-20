-- v7：移除二维码“已售(Sold)”状态
-- 目的：统一二维码状态为 Active / Recalled / Expired，避免后台与 H5 出现不一致。

UPDATE qr_codes
SET status = 'Active'
WHERE status = 'Sold';
