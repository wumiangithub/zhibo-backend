package com.zhibo.live.watch;

import com.zhibo.activity.ActivityCouponEntity;
import com.zhibo.activity.ActivityCouponRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.activity.RoomCouponContent;
import com.zhibo.live.watch.dto.WatchCouponHistoryItem;
import com.zhibo.live.watch.dto.WatchCouponHistoryResponse;
import com.zhibo.live.watch.dto.WatchCouponItem;
import com.zhibo.live.watch.dto.WatchCouponListResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观看端本场已发券 + 券聊天历史（D113）。 */
@Service
@RequiredArgsConstructor
public class WatchCouponService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SENT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final int MAX_HISTORY = 500;

    private final WatchService watchService;
    private final ActivityCouponRepository activityCouponRepository;
    private final RoomCommentRepository commentRepository;

    @Transactional(readOnly = true)
    public WatchCouponListResponse listCoupons(String activityId) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        List<ActivityCouponEntity> rows =
                activityCouponRepository.findByActivityIdOrderByLastSentTimeDesc(entity.getId());
        WatchCouponListResponse response = new WatchCouponListResponse();
        for (ActivityCouponEntity row : rows) {
            response.getList().add(toCouponItem(row));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public WatchCouponHistoryResponse history(String activityId) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        entity.getId(), RoomCommentType.COUPON);
        WatchCouponHistoryResponse response = new WatchCouponHistoryResponse();
        response.setTotal(rows.size());
        int limit = Math.min(rows.size(), MAX_HISTORY);
        for (int i = 0; i < limit; i++) {
            WatchCouponHistoryItem item = toHistoryItem(rows.get(i));
            if (item != null) {
                response.getList().add(item);
            }
        }
        return response;
    }

    private static WatchCouponItem toCouponItem(ActivityCouponEntity row) {
        WatchCouponItem item = new WatchCouponItem();
        item.setCouponId(row.getCouponId());
        item.setCouponNo(row.getCouponNo());
        item.setCouponName(row.getCouponName());
        item.setCouponAmount(row.getCouponAmount());
        item.setCouponStartDate(row.getCouponStartDate());
        item.setCouponEndDate(row.getCouponEndDate());
        item.setAllSku(row.isAllSku());
        item.setDeptNames(row.getDeptNames() == null ? "" : row.getDeptNames());
        item.setSkuNames(row.getSkuNames() == null ? "" : row.getSkuNames());
        item.setLastSentTime(
                row.getLastSentTime() == null ? null : SENT_TIME.format(row.getLastSentTime()));
        item.setLastSenderEmployeeId(row.getLastSenderEmployeeId());
        item.setLastSenderNickname(row.getLastSenderNickname());
        item.setLastSenderDeptId(row.getLastSenderDeptId());
        item.setLastSenderDeptName(row.getLastSenderDeptName());
        return item;
    }

    private static WatchCouponHistoryItem toHistoryItem(RoomCommentEntity c) {
        RoomCouponContent parsed = RoomCouponContent.parse(c.getContent());
        if (parsed == null) {
            return null;
        }
        String nick = c.getNickname() == null ? null : c.getNickname().trim();
        if (nick == null || nick.isEmpty()) {
            return null;
        }
        WatchCouponHistoryItem item = new WatchCouponHistoryItem();
        item.setId(c.getId());
        item.setSenderName(nick);
        item.setTimestamp(toEpochMillis(c.getCommentTime()));
        item.setCouponId(parsed.couponId());
        item.setCouponNo(parsed.couponNo());
        item.setCouponName(parsed.couponName());
        item.setCouponAmount(parsed.couponAmount());
        item.setCouponStartDate(parsed.couponStartDate());
        item.setCouponEndDate(parsed.couponEndDate());
        item.setEmployeeId(parsed.employeeId());
        item.setDeptId(parsed.deptId());
        item.setDeptName(parsed.deptName());
        return item;
    }

    private static Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(SHANGHAI).toInstant().toEpochMilli();
    }
}
