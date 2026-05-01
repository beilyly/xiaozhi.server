SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'xiaozhi'
    AND TABLE_NAME = 'sys_device'
    AND COLUMN_NAME = 'location'
);

SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE `xiaozhi`.`sys_device` ADD COLUMN `location` varchar(255) DEFAULT NULL COMMENT ''地理位置'' AFTER `ip`',
  'SELECT ''sys_device.location already exists'''
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
