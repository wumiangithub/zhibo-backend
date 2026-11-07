package com.zhibo.admin.console;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.console.dto.ConsoleCouponHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleQuestionHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleSdkSignResponse;
import com.zhibo.admin.console.dto.ConsoleSendCouponRequest;
import com.zhibo.admin.console.dto.ConsoleSendCouponResponse;
import com.zhibo.admin.console.dto.ConsoleSummaryResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
