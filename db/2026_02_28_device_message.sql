-- 用户与设备留言记录表（Web 端发给设备的消息历史，按用户+设备隔离）
CREATE TABLE IF NOT EXISTS `device_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `userId` int NOT NULL COMMENT '用户ID',
  `deviceId` varchar(255) NOT NULL COMMENT '设备ID',
  `content` text COMMENT '消息内容',
  `type` varchar(50) DEFAULT 'text' COMMENT '消息类型',
  `isUser` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否用户发送：1-用户，0-设备/系统',
  `read_status` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_device` (`userId`, `deviceId`),
  KEY `idx_create_time` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备留言记录表';
