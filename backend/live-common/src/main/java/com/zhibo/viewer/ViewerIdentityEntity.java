package com.zhibo.viewer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 某个渠道的登录凭证。同一观众以后可以有多条（小程序、公众号等）。 */
@Entity
@Table(name = "viewer_identity")
@Getter
@Setter
public class ViewerIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    /** {@link ViewerChannel}。本轮只写微信小程序。 */
    @Column(nullable = false)
    private int channel;

    /** 该渠道 openid。不在这里存 session_key。 */
    @Column(length = 64)
    private String openid;

    /** 有则写入；微信没带回时不要清空已有值。 */
    @Column(length = 64)
    private String unionid;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
