-- 录播 create-demand：是否定时（微吼 is_timing）；非录播可空，缺省按 true

ALTER TABLE activity
  ADD COLUMN is_timing TINYINT NULL
    COMMENT '录播是否定时：1是/0否；null按1；非录播可空' AFTER record_material_id;
