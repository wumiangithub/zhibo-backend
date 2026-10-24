package com.zhibo.course;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** {@code course_material} 联合主键。 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CourseMaterialId implements Serializable {

    @Column(name = "course_id", length = 32)
    private String courseId;

    @Column(name = "material_id", length = 32)
    private String materialId;
}
