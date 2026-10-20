package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 对外课程列表项。空的 introduction / coverUrl / listedTime 保持 null，
 * 由全局 Jackson {@code non_null} 省略。owners 始终是数组，空则 {@code []}。
 */
@Data
public class OutsideCourseItem {

    private String id;
    private String title;
    private String coverUrl;
    private String introduction;
    private String listedTime;
    private String updatedTime;
    private List<OutsideCourseOwner> owners = new ArrayList<>();
}
