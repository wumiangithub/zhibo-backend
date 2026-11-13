package com.zhibo.admin.dashboard;

import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.dashboard.dto.BusinessMetricsResponse;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台「经营数据」：全站概况，只读 zhibo 库，不查 OMS，不按业务员过滤。
 */
@Service
public class DashboardBusinessService {

    /** 已支付：2已付款 / 3退款中 / 4部分退款 / 5全部退款；不含 1待付款。 */
    static final List<Integer> PAID_STATUSES = List.of(2, 3, 4, 5);

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter STATS_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ViewerRepository viewerRepository;
    private final ActivityOrderRepository activityOrderRepository;
    private final Clock clock;

    public DashboardBusinessService(
            ViewerRepository viewerRepository,
            ActivityOrderRepository activityOrderRepository,
            Clock clock) {
        this.viewerRepository = viewerRepository;
        this.activityOrderRepository = activityOrderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessMetricsResponse metrics() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SHANGHAI);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);

        BusinessMetricsResponse response = new BusinessMetricsResponse();
        response.setTodayVisitorCount(
                viewerRepository.countByLastVisitTimeGreaterThanEqualAndLastVisitTimeLessThan(
                        start, end));
        response.setTodayNewUserCount(
                viewerRepository.countByCreatedTimeGreaterThanEqualAndCreatedTimeLessThan(
                        start, end));
        response.setTodayPayAmount(formatAmount(
                activityOrderRepository.sumPayAmountByStatusesAndPayTimeBetween(
                        PAID_STATUSES, start, end)));
        response.setTodayOrderCount(
                activityOrderRepository.countByStatusesAndPayTimeBetween(
                        PAID_STATUSES, start, end));
        response.setTotalPayUserCount(
                activityOrderRepository.countDistinctUsernameByStatuses(PAID_STATUSES));
        response.setTotalUserCount(viewerRepository.count());
        response.setTotalPayAmount(formatAmount(
                activityOrderRepository.sumPayAmountByStatuses(PAID_STATUSES)));
        response.setStatsTime(now.format(STATS_TIME));
        return response;
    }

    /** 空金额按 0；固定两位小数、直接进制，不走科学计数法。 */
    static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
