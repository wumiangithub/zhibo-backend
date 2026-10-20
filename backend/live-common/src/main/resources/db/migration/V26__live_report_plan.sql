-- 计划时间拆子表：列表按「任一段与筛选区间相交」精确筛选，避免多段空洞被外包区间误命中
-- COLLATE 必须与 live_report 一致（V16 未写 COLLATE，MySQL 8 落库 utf8mb4_0900_ai_ci）。
-- 若用 utf8mb4_unicode_ci，FK report_id→live_report.id 会报 ERROR 3780 incompatible。

CREATE TABLE live_report_plan (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  report_id VARCHAR(36) NOT NULL COMMENT '报备 UUID，live_report.id',
  start_time DATETIME(3) NOT NULL COMMENT '本段开始',
  end_time DATETIME(3) NOT NULL COMMENT '本段结束',
  sort_order INT NOT NULL COMMENT '段序，从 0 起，与 plan_times JSON 下标一致',
  PRIMARY KEY (id),
  KEY idx_live_report_plan_report (report_id),
  KEY idx_live_report_plan_range (start_time, end_time),
  CONSTRAINT fk_live_report_plan_report FOREIGN KEY (report_id) REFERENCES live_report (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='直播报备计划时间段；与 live_report.plan_times JSON 同步，供列表相交筛选';

INSERT INTO live_report_plan (report_id, start_time, end_time, sort_order)
SELECT
  r.id,
  STR_TO_DATE(jt.start_time, '%Y-%m-%d %H:%i:%s'),
  STR_TO_DATE(jt.end_time, '%Y-%m-%d %H:%i:%s'),
  jt.ord - 1
FROM live_report r
JOIN JSON_TABLE(
  r.plan_times,
  '$[*]' COLUMNS (
    ord FOR ORDINALITY,
    start_time VARCHAR(32) PATH '$.startTime',
    end_time VARCHAR(32) PATH '$.endTime'
  )
) AS jt;
