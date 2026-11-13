package com.zhibo.admin.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 对外课程列表项，字段名对齐已发布合同《课程列表_订单回调接口文档》。
 * {@code deptId}/{@code deptName} 取自归属人（{@code sort_order} 最小的第一人）的员工部门
 * （{@link com.zhibo.admin.org.EmployeeDeptResolver}，禁止读 {@code qw_bmxx}）；
 * 无归属人或无部门时为 null，由全局 Jackson {@code non_null} 省略。
 * {@code teacherName} 本站无圈子，恒为 null，但须输出该键。
 */
@Data
public class OutsideCourseItem {

    /** 课程 ID = 本站 {@code course.id}（32 位无横线 UUID，不发明 KC 编码）。 */
    private String courseId;
    private String courseName;
    private Long deptId;
    private String deptName;
    /** 1 启用（已上架且风控通过） / 2 已下架。 */
    private int status;
    private String updaterName;
    /** {@code course.updated_time}，格式 {@code yyyy-MM-dd HH:mm:ss}（Asia/Shanghai）。 */
    private String updateTime;
    private int replayCount;
    private int liveCount;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String teacherName;
}
