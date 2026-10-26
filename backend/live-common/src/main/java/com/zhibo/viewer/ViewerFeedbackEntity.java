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

/** 观众意见反馈。 */
@Entity
@Table(name = "viewer_feedback")
@Getter
@Setter
public class ViewerFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    /** {@link ViewerFeedbackCategory}。 */
    @Column(nullable = false)
    private int category;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /** JSON 数组：[{url,objectKey},...]，最多 4。 */
    @Column(name = "images_json", length = 4000)
    private String imagesJson;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
