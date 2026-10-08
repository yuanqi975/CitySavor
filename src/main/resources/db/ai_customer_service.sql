-- Run this migration once against the existing hmdp database.
-- Existing business tables and data are intentionally untouched.

CREATE TABLE IF NOT EXISTS `tb_ai_conversation` (
  `id` varchar(36) NOT NULL COMMENT 'conversation UUID',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT 'owner user id',
  `title` varchar(64) NOT NULL COMMENT 'conversation title',
  `summary` text NULL COMMENT 'reserved compressed history summary',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_user_update` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `tb_ai_chat_message` (
  `id` varchar(36) NOT NULL COMMENT 'message UUID',
  `conversation_id` varchar(36) NOT NULL COMMENT 'conversation id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT 'owner user id',
  `role` varchar(16) NOT NULL COMMENT 'user or assistant',
  `content` text NOT NULL COMMENT 'message content',
  `request_id` varchar(64) NULL COMMENT 'client retry id',
  `tool_name` varchar(128) NULL COMMENT 'tools used for this reply',
  `tool_arguments` text NULL COMMENT 'tool call audit data',
  `status` varchar(16) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED or FAILED',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_request` (`conversation_id`, `request_id`),
  KEY `idx_ai_message_conversation_time` (`conversation_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
