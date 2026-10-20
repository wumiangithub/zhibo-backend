package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** {@code activity_member} 联合主键：一场活动里同一员工只有一行开播角色。 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ActivityMemberId implements Serializable {

    @Column(name = "activity_id", length = 36)
    private String activityId;

    @Column(name = "user_id")
    private Long userId;
}
