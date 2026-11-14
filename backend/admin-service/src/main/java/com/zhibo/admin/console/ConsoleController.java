package com.zhibo.admin.console;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.console.dto.ConsoleAudienceDetailListResponse;
import com.zhibo.admin.console.dto.ConsoleCartEnabledRequest;
import com.zhibo.admin.console.dto.ConsoleCartEnabledResponse;
import com.zhibo.admin.console.dto.ConsoleCouponHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleProductListResponse;
import com.zhibo.admin.console.dto.ConsoleProductVisibleRequest;
import com.zhibo.admin.console.dto.ConsoleProductVisibleResponse;
import com.zhibo.admin.console.dto.ConsoleQuestionHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleSdkSignResponse;
import com.zhibo.admin.console.dto.ConsoleSendCouponRequest;
import com.zhibo.admin.console.dto.ConsoleSendCouponResponse;
import com.zhibo.admin.console.dto.ConsoleSummaryResponse;
import com.zhibo.admin.console.dto.ConsoleTradeStatsResponse;
import com.zhibo.admin.console.dto.ConsoleTradeTrendResponse;
import com.zhibo.admin.console.dto.ConsoleWatchTradeResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中控台页壳与员工进房签名（D110）。须 admin JWT。
 * 讨论区普通聊天历史走微吼观看 SDK（同 live-web）；问答历史优先微吼，空/失败再 {@code question-history}。
 * 发券落库见 D113。
 */
@RestController
@RequestMapping("/api/v1/activities/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final ConsoleService consoleService;
    private final ConsoleCouponService consoleCouponService;
    private final ConsoleAudienceDetailService audienceDetailService;
    private final ConsoleWatchTradeService watchTradeService;
    private final ConsoleTradeStatsService tradeStatsService;
    private final ConsoleTradeTrendService tradeTrendService;
    private final ConsoleProductService consoleProductService;

    @GetMapping("/summary/{id}")
    public ApiResponse<ConsoleSummaryResponse> summary(@PathVariable String id) {
        return ApiResponse.ok(consoleService.summary(id));
    }

    /**
     * 活动 UUID 在 Query {@code id}（与观看 sdk-sign 同属 D92 例外）。
     */
    @GetMapping("/sdk-sign")
    public ApiResponse<ConsoleSdkSignResponse> sdkSign(
            @RequestParam String id,
            @RequestParam(required = false) String nickname,
            HttpServletRequest request) {
        return ApiResponse.ok(consoleService.sdkSign(id, nickname, currentUserId(request)));
    }

    /** 讨论区问答兜底：本站 room_comment 提问行；形状对齐观看 question-history。 */
    @GetMapping("/question-history/{id}")
    public ApiResponse<ConsoleQuestionHistoryResponse> questionHistory(@PathVariable String id) {
        return ApiResponse.ok(consoleService.questionHistory(id));
    }

    /** 中控台发券：upsert activity_coupon + insert room_comment type=3；成功后再 sendCustomEvent。 */
    @PostMapping("/coupons/send/{id}")
    public ApiResponse<ConsoleSendCouponResponse> sendCoupon(
            @PathVariable String id,
            @RequestBody ConsoleSendCouponRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(consoleCouponService.send(id, currentUserId(request), body));
    }

    /** 讨论区优惠券卡片历史；形状对齐观看 coupon-history。 */
    @GetMapping("/coupon-history/{id}")
    public ApiResponse<ConsoleCouponHistoryResponse> couponHistory(@PathVariable String id) {
        return ApiResponse.ok(consoleCouponService.history(id));
    }

    /**
     * 中控台「观看数据」成交三项：直播间成交金额 / 成交人数 / 成交商品数。
     * 只读本站 {@code activity_order}；与带货明细表 {@code commerce-data} 不同（本接口是场级汇总）。
     */
    @GetMapping("/watch-trade/{id}")
    public ApiResponse<ConsoleWatchTradeResponse> watchTrade(@PathVariable String id) {
        return ApiResponse.ok(watchTradeService.watchTrade(id));
    }

    /**
     * 中控台「交易数据」左侧 KPI：成交人数 / 商品数 / 点击人数 / 转化率 / 客单价 / 未付款人数。
     * 只读本站 {@code activity_order} 与 {@code activity_product_click}。
     */
    @GetMapping("/trade-stats/{id}")
    public ApiResponse<ConsoleTradeStatsResponse> tradeStats(@PathVariable String id) {
        return ApiResponse.ok(tradeStatsService.stats(id));
    }

    /**
     * 中控台「交易数据」成交趋势：按支付时间桶聚合成交金额与销量。
     * Query 口径对齐 presence-trend（跨度 ≤1 天，桶 60s）。
     */
    @GetMapping("/trade-trend/{id}")
    public ApiResponse<ConsoleTradeTrendResponse> tradeTrend(
            @PathVariable String id,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) Integer bucketSeconds) {
        return ApiResponse.ok(tradeTrendService.trend(id, startTime, endTime, bucketSeconds));
    }

    /** 中控台「商品」：本场绑定全量 + 小黄车总开关；展示字段由前端批量查订单中心。 */
    @GetMapping("/products/{id}")
    public ApiResponse<ConsoleProductListResponse> products(@PathVariable String id) {
        return ApiResponse.ok(consoleProductService.list(id));
    }

    /** 中控台单品显示/隐藏；写 {@code activity_product.visible}。 */
    @PutMapping("/products/visible/{id}")
    public ApiResponse<ConsoleProductVisibleResponse> setProductVisible(
            @PathVariable String id, @RequestBody ConsoleProductVisibleRequest body) {
        return ApiResponse.ok(consoleProductService.setVisible(id, body));
    }

    /** 中控台小黄车总开关；写 {@code activity.cart_enabled}。 */
    @PutMapping("/cart/{id}")
    public ApiResponse<ConsoleCartEnabledResponse> setCartEnabled(
            @PathVariable String id, @RequestBody ConsoleCartEnabledRequest body) {
        return ApiResponse.ok(consoleProductService.setCartEnabled(id, body));
    }

    /** 中控台底部观众明细列表（D123）。 */
    @GetMapping("/audience-details/{id}")
    public ApiResponse<ConsoleAudienceDetailListResponse> audienceDetails(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String onlineStatus,
            @RequestParam(required = false) String followerKeyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        return ApiResponse.ok(audienceDetailService.list(
                id,
                page,
                pageSize,
                keywordType,
                keyword,
                onlineStatus,
                followerKeyword,
                sort,
                order));
    }

    /** 观众明细按筛选导出 CSV（D123）。 */
    @GetMapping("/audience-details/export/{id}")
    public ResponseEntity<byte[]> audienceDetailsExport(
            @PathVariable String id,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String onlineStatus,
            @RequestParam(required = false) String followerKeyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        byte[] body = audienceDetailService.exportCsv(
                id, keywordType, keyword, onlineStatus, followerKeyword, sort, order);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audience-details.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
