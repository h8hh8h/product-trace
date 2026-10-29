-- ============================================================
-- v4 后台操作审计日志
-- 幂等：可重复执行
-- 执行：mysql -uroot -p product_trace < migration-v4.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  admin_id        BIGINT        NULL COMMENT '操作人ID（未登录/系统触发时为空）',
  admin_username  VARCHAR(64)   NULL COMMENT '操作人用户名（冗余快照，账号删除后仍可追溯）',
  module          VARCHAR(50)   NOT NULL COMMENT '业务模块，如 产品/二维码/站点配置/账号',
  action          VARCHAR(50)   NOT NULL COMMENT '动作，如 新增/编辑/删除/作废/批量生成',
  method          VARCHAR(150)  NULL COMMENT '完整方法签名（类名.方法名）',
  request_uri     VARCHAR(255)  NULL COMMENT '请求路径',
  http_method     VARCHAR(10)   NULL COMMENT 'GET/POST/PUT/DELETE',
  target_id       VARCHAR(64)   NULL COMMENT '操作对象ID（从路径变量或参数中提取）',
  request_params  TEXT          NULL COMMENT '请求参数（已脱敏，最多 2000 字符）',
  response_code   INT           NULL COMMENT '业务返回码，200=成功',
  error_msg       VARCHAR(500)  NULL COMMENT '失败时的异常摘要',
  ip              VARCHAR(64)   NULL COMMENT '客户端 IP',
  cost_ms         INT           NULL COMMENT '耗时（毫秒）',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_created_at (created_at),
  KEY idx_admin_id (admin_id),
  KEY idx_module_action (module, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作审计日志';

SELECT '=== admin_operation_log 已就绪 ===' AS section;
DESCRIBE admin_operation_log;
