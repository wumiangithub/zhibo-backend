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
import com.zhibo.common.UuidIds;

/** 本站直播活动表 {@code activity}。 */
@Entity
@Table(name = "activity")
@Getter
@Setter
public class ActivityEntity {

    /** 本站活动主键 UUID（对外 API / 路由），不是微吼 webinar_id。 */
    @Id
    @Column(length = 32)
    private String id;

    /** 绑定的已通过直播报备 ID；空表示未绑定。 */
    @Column(name = "report_id", length = 32)
    private String reportId;

    /** 直播服务商：vhall / aliyun / tencent。 */
    @Column(nullable = false, length = 32)
    private String provider;

    /** 云侧房间号；微吼即 webinar_id。 */
    @Column(name = "provider_room_id", length = 64)
    private String providerRoomId;

    /** 口令开播唯一口令（9 位数字）；H5 链接与 App 凭此进房。 */
    @Column(name = "host_passcode", length = 16)
    private String hostPasscode;

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

    /** 开播回调首次写入的实际开始时间。 */
    @Column(name = "live_actual_start_time")
    private LocalDateTime liveActualStartTime;

    /** 结束回调首次写入的实际结束时间。 */
    @Column(name = "live_actual_end_time")
    private LocalDateTime liveActualEndTime;

    /** 1 音频 / 2 视频 / 3 互动 / 4 录播。 */
    @Column(nullable = false)
    private Integer type;

    /**
     * 活动是否就绪。默认 1。
     * 0 = 未就绪（录播视频转码中）；1 = 就绪；2 = 失败（不可重试）。
     * 库列为 TINYINT（V31），须声明以免 validate 期望 INTEGER。
     */
    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer ready = 1;

    /** 录播「素材视频」路径：本站素材 UUID；供转码回调挂钩 create-demand。 */
    @Column(name = "record_material_id", length = 32)
    private String recordMaterialId;

    /**
     * 录播是否定时 → 微吼 create-demand {@code is_timing}。
     * 仅 type=4 有意义；null 按 true。库列为 TINYINT（V33）。
     */
    @Column(name = "is_timing", columnDefinition = "TINYINT")
    private Boolean isTiming;

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
    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    /** 删除人 sys_useraccount.rs_mainkey_id。 */
    @Column(name = "deleted_by")
    private Long deletedBy;

    /** 删除人姓名快照。 */
    @Column(name = "deleted_by_name", length = 50)
    private String deletedByName;

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
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UuidIds.newId();
        }
    }
}
