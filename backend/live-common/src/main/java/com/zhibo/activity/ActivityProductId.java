package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** {@code activity_product} 联合主键。 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ActivityProductId implements Serializable {

    @Column(name = "activity_id", length = 32)
    private String activityId;

    /** 十进制字符串，避免雪花 id 超出 JS 安全整数。 */
    @Column(name = "spu_id", length = 32)
    private String spuId;
}
