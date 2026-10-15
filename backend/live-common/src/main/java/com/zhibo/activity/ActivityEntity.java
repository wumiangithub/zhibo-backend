package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** 本站直播活动表 {@code activity}。 */
@Entity
@Table(name = "activity")
@Getter
@Setter
public class ActivityEntity {

    /** 本站活动主键 UUID（对外 API / 路由），不是微吼 webinar_id。 */
    @Id
    @Column(length = 36)
    private String id;

    /** 直播服务商：vhall / aliyun / tencent。 */
    @Column(nullable = false, length = 32)
    private String provider;

    /** 云侧房间号；微吼即 webinar_id。 */
    @Column(name = "provider_room_id", length = 64)
    private String providerRoomId;

    /** 直播名称。 */
    @Column(nullable = false, length = 64)
    private String title;

    /** 直播简介。 */
    @Column(length = 256)
    private String introduction;

    /** 开始时间 yyyy-MM-dd HH:mm:ss。 */
    @Column(name = "start_time", nullable = false, length = 32)
    private String startTime;

    /** 结束时间；只落本站。 */
    @Column(name = "end_time", length = 32)
    private String endTime;

    /** 1 音频 / 2 视频 / 3 互动 / 4 录播。 */
    @Column(nullable = false)
    private Integer type;

    /** 1 直播 / 2 预告 / 3 结束 / 4 点播 / 5 回放。 */
    @Column(nullable = false)
    private Integer state;

    /** traditional / immersive。 */
    @Column(name = "viewer_style", length = 32)
    private String viewerStyle;

    /** 超低延迟；映射微吼 no_delay_webinar。 */
    @Column(name = "ultra_low_latency")
    private Boolean ultraLowLatency;

    /** 虚拟形象（本站配置）。 */
    @Column(name = "virtual_avatar")
    private Boolean virtualAvatar;

    /** 详情封面 URL。 */
    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

    /** image / video。 */
    @Column(name = "warmup_type", length = 16)
    private String warmupType;

    /** 暖场素材 URL。 */
    @Column(name = "warmup_url", length = 1024)
    private String warmupUrl;

    /** 宣传封面 URL。 */
    @Column(name = "promo_cover_url", length = 1024)
    private String promoCoverUrl;

    /** 详情富文本 HTML。 */
    @Column(name = "detail_html", columnDefinition = "MEDIUMTEXT")
    private String detailHtml;

    /** 云侧分享链接。 */
    @Column(name = "share_link", length = 1024)
    private String shareLink;

    /** pending 待上架 / on 已上架 / off 已下架 / banned 强制封禁。 */
    @Column(name = "shelf_status", nullable = false, length = 16)
    private String shelfStatus;

    /** pending 待审核 / passed 已通过 / rejected 已驳回。 */
    @Column(name = "risk_status", nullable = false, length = 16)
    private String riskStatus;

    /** free / paid / encrypted / specified。 */
    @Column(name = "sell_type", nullable = false, length = 16)
    private String sellType;

    /** 有效期文案；本版固定「长期有效」。 */
    @Column(name = "validity_label", nullable = false, length = 32)
    private String validityLabel;

    /** 软删时间；非空则对外视为不存在。 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 风控通过后是否自动上架。 */
    @Column(name = "auto_list", nullable = false)
    private Boolean autoList = Boolean.FALSE;

    /** 是否单独售卖。 */
    @Column(name = "sell_alone", nullable = false)
    private Boolean sellAlone = Boolean.TRUE;

    /** 付费价格（元）。 */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    /** 划线价。 */
    @Column(name = "line_price", precision = 12, scale = 2)
    private BigDecimal linePrice;

    /** 加密售卖密码 AES 密文。 */
    @Column(name = "sell_password_cipher", length = 512)
    private String sellPasswordCipher;

    /** long / days。 */
    @Column(name = "access_validity_type", nullable = false, length = 16)
    private String accessValidityType = "long";

    /** 购买后 N 天。 */
    @Column(name = "access_validity_days")
    private Integer accessValidityDays;

    /** 是否开启回放。 */
    @Column(name = "playback_enabled", nullable = false)
    private Boolean playbackEnabled = Boolean.TRUE;

    /** forever / days。 */
    @Column(name = "playback_validity_type", nullable = false, length = 16)
    private String playbackValidityType = "forever";

    /** 直播结束后 N 天。 */
    @Column(name = "playback_validity_days")
    private Integer playbackValidityDays;

    /** 是否允许倍速。 */
    @Column(name = "playback_speed_allow", nullable = false)
    private Boolean playbackSpeedAllow = Boolean.TRUE;

    /** 是否允许快进。 */
    @Column(name = "playback_fast_forward_allow", nullable = false)
    private Boolean playbackFastForwardAllow = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
