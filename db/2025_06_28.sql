ALTER TABLE `xiaozhi`.`sys_role` 
ADD COLUMN `avatar` varchar(255) DEFAULT NULL COMMENT '角色头像' AFTER roleName;
