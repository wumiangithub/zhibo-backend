package com.zhibo.liveaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 本站直播账号启停 {@code live_account}；无行视为已启用。 */
@Entity
@Table(name = "live_account")
@Getter
@Setter
public class LiveAccountEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 1 启用 / 0 禁用 */
    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer enabled;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "updated_by")
    private Long updatedBy;
}
