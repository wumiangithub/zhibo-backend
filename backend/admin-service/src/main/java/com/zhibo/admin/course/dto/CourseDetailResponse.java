package com.zhibo.admin.course.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CourseDetailResponse extends CourseSummary {

    private String introduction;
    private String detailHtml;
    private String coverMaterialId;
    private List<CourseMaterialView> materials = new ArrayList<>();

    private Boolean sellAlone;
    private String accessValidityType;
    private Integer accessValidityDays;
    /** 仅 encrypted 时回显明文。 */
    private String password;
}
