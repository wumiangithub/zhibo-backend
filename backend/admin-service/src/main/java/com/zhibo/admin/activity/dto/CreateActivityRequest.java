package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建活动请求（本站落库 + 按 provider 调云侧）。
 * <p>
 * 第十一迭代：{@code autoList} 替代 {@code listed}；必选主播；可选课件；不要求 sellType。
 */
@Data
public class CreateActivityRequest {

    /** 直播服务商：vhall / aliyun / tencent */
    @NotBlank(message = "provider 不能为空")
    private String provider;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题不能超过64个字")
    private String title;

    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /** 结束时间（可选）；只落本站，须晚于 startTime */
    private String endTime;

    @Size(max = 256, message = "简介不能超过256个字")
    private String introduction;

    /**
     * 1 音频 / 2 视频 / 3 互动 / 4 录播（微吼走 create-demand）。
     */
    @NotNull(message = "直播类型不能为空")
    @Min(value = 1, message = "直播类型无效")
    @Max(value = 4, message = "直播类型无效")
    private Integer type;

    /**
     * type=4 且 provider=vhall：微吼回放列表 ID（微吼 integer / JSON number），直接作 create-demand 的 record_id。
     * 与 {@link #recordMaterialId} 二选一，不可同时传。
     */
    private Long recordId;

    /**
     * type=4：本站录播视频素材 UUID（库列 {@code record_material_id}；请求/响应 JSON 统一 {@code recordMaterialId}）。
     * 与 {@link #recordId} 二选一，不可同时传。
     */
    private String recordMaterialId;

    /**
     * type=4：是否定时直播 → 微吼 {@code is_timing}（1/0）。
     * 省略或 null 按 {@code true}；非录播忽略。
     */
    private Boolean isTiming;

    private String viewerStyle;

    /**
     * 是否超低延迟。true 时创建微吼传 {@code no_delay_webinar=1}，false 传 0；不传则不带该参。
     * 落本站库。创建后不可改。
     */
    private Boolean ultraLowLatency;
    private Boolean virtualAvatar;
    private String coverUrl;
    private String warmupType;
    private String warmupUrl;
    private String promoCoverUrl;

    @Size(max = 100000, message = "详情过长")
    private String detailHtml;

    /**
     * 是否自动上架；创建时 shelfStatus 一律 pending。
     * 旧字段 {@code listed} 若传入则业务 400。
     */
    @NotNull(message = "autoList 不能为空")
    private Boolean autoList;

    /**
     * 兼容探测：旧客户端若仍传 listed，服务端拒绝并提示改用 autoList。
     * 无校验注解，由 Service 显式拒绝。
     */
    private Boolean listed;

    /** 主播员工 id；去重后 1～20。 */
    @NotNull(message = "hostIds 不能为空")
    private List<Long> hostIds;

    /** 归属人员工 id。 */
    private List<Long> ownerIds;

    /** 关联商品快照。 */
    private List<ProductBinding> products;

    /** 课件素材 UUID 有序数组；最多 100。 */
    private List<String> coursewareMaterialIds;

    /** 绑定的已通过直播报备 UUID；创建必填。 */
    @NotBlank(message = "reportId 不能为空")
    @Size(max = 36, message = "reportId 无效")
    private String reportId;

    @Data
    public static class ProductBinding {
        private String spuId;
        private String productName;
    }
}
