-- ============================================================================
--  增量脚本：fault_report 增加 steps 列
--  用途：保存处置步骤（JSON 数组），使 AI 分析页回显历史结论时能还原分步方案
--  说明：01_schema.sql 已包含该列；本脚本用于已按旧版本建库的环境
-- ============================================================================

USE aiops;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'aiops'
       AND TABLE_NAME = 'fault_report'
       AND COLUMN_NAME = 'steps'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE fault_report ADD COLUMN steps TEXT DEFAULT NULL COMMENT ''处置步骤（JSON 数组）'' AFTER evidence',
    'SELECT ''steps 列已存在，跳过'' AS info');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '迁移完成' AS result;
