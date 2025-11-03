ALTER TABLE `xiaozhi`.`sys_config` 
ADD COLUMN `modelType` varchar(30) DEFAULT NULL COMMENT 'LLM模型类型(chat, vision, intent, embedding等)' AFTER configType;
