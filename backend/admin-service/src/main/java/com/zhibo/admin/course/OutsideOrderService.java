package com.zhibo.admin.course;

import com.zhibo.activity.ActivityOrderCourseEntity;
import com.zhibo.activity.ActivityOrderCourseRepository;
import com.zhibo.activity.ActivityOrderEntity;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.admin.course.dto.OutsideOrderReceiveItem;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接收订单中心推送的订单，按 {@code orderId} 幂等写入 {@code activity_order}。
 * 整批同一事务：任一校验或写入失败全部回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsideOrderService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Map<Integer, Integer> OMS_STATUS = Map.of(
            1010, 1,
            1030, 2,
            1071, 3,
            1080, 4,
            1081, 5);

    private final ActivityOrderRepository orderRepository;
    private final ActivityOrderCourseRepository orderCourseRepository;
    private final ViewerRepository viewerRepository;

    @Transactional
    public void receive(List<OutsideOrderReceiveItem> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("请求体必须是订单数组");
        }
        List<ParsedOrder> parsed = new ArrayList<>(orders.size());
        for (OutsideOrderReceiveItem item : orders) {
            parsed.add(parse(item));
        }
        List<String> orderIds = parsed.stream().map(p -> p.orderNo).toList();
        log.info("收到订单回调 orderIds={}", orderIds);
        for (ParsedOrder parsedOrder : parsed) {
            upsert(parsedOrder);
        }
    }

    private ParsedOrder parse(OutsideOrderReceiveItem item) {
        if (item == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        String orderId = trimToNull(item.getOrderId());
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        if (orderId.length() > 64) {
            throw new IllegalArgumentException("订单ID不能超过64个字符");
        }
        String username = trimToNull(item.getUsername());
        if (username == null) {
            throw new IllegalArgumentException("下单人用户名不能为空");
        }
        if (username.length() > 64) {
            throw new IllegalArgumentException("下单人用户名不能超过64个字符");
        }
        String productName = trimToNull(item.getProductName());
        if (productName != null && productName.length() > 200) {
            throw new IllegalArgumentException("商品名称不能超过200个字符");
        }
        String productType = trimToNull(item.getProductType());
        if (productType != null && productType.length() > 50) {
            throw new IllegalArgumentException("商品类型不能超过50个字符");
        }
        String salesmanId = trimToNull(item.getSalesmanId());
        if (salesmanId != null && salesmanId.length() > 64) {
            throw new IllegalArgumentException("业务员ID不能超过64个字符");
        }
        String liveRoomId = trimToNull(item.getLiveRoomId());
        if (liveRoomId != null && liveRoomId.length() > 32) {
            throw new IllegalArgumentException("直播间ID不能超过32个字符");
        }
        LocalDateTime orderTime = parseTime(item.getOrderTime(), "下单时间格式错误");
        LocalDateTime payTime = parseTime(item.getPayTime(), "支付时间格式错误");
        int status = mapStatus(item.getOrderStatus(), payTime);
        List<String> courseIds = normalizeCourseIds(item.getCourseIds());
        ParsedOrder parsed = new ParsedOrder();
        parsed.orderNo = orderId;
        parsed.username = username;
        parsed.productId = item.getProductId();
        parsed.productName = productName;
        parsed.productType = productType;
        parsed.tradeAmount = item.getTradeAmount();
        parsed.activityId = liveRoomId;
        parsed.courseIds = courseIds;
        parsed.orderTime = orderTime;
        parsed.payTime = payTime;
        parsed.salesmanId = salesmanId;
        parsed.channel = item.getChannel();
        parsed.orderStatus = status;
        return parsed;
    }

    static int mapStatus(Integer omsStatus, LocalDateTime payTime) {
        if (omsStatus == null) {
            return payTime != null ? 2 : 1;
        }
        Integer mapped = OMS_STATUS.get(omsStatus);
        if (mapped == null) {
            throw new IllegalArgumentException("订单状态无效");
        }
        return mapped;
    }

    private static List<String> normalizeCourseIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : raw) {
            String trimmed = trimToNull(id);
            if (trimmed == null) {
                continue;
            }
            if (trimmed.length() > 32) {
                throw new IllegalArgumentException("课程ID不能超过32个字符");
            }
            unique.add(trimmed);
        }
        return List.copyOf(unique);
    }

    private static LocalDateTime parseTime(String raw, String errorMessage) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIME);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void upsert(ParsedOrder parsed) {
        ActivityOrderEntity entity = orderRepository.findByOrderNo(parsed.orderNo).orElseGet(ActivityOrderEntity::new);
        boolean isNew = entity.getId() == null;
        entity.setOrderNo(parsed.orderNo);
        entity.setUsername(parsed.username);
        entity.setUserNickname(snapshotNickname(parsed.username));
        entity.setProductId(parsed.productId);
        entity.setProductName(parsed.productName);
        entity.setProductType(parsed.productType);
        entity.setPayAmount(parsed.tradeAmount);
        entity.setActivityId(parsed.activityId);
        entity.setSalesmanId(parsed.salesmanId);
        entity.setChannel(parsed.channel);
        entity.setOrderTime(parsed.orderTime);
        entity.setPayTime(parsed.payTime);
        entity.setOrderStatus(parsed.orderStatus);
        entity = orderRepository.saveAndFlush(entity);
        replaceCourses(entity.getId(), parsed.courseIds);
        if (isNew) {
            log.info("订单已新建 orderId={} id={}", parsed.orderNo, entity.getId());
        } else {
            log.info("订单已更新 orderId={} id={}", parsed.orderNo, entity.getId());
        }
    }

    /**
     * 按 username 查 viewer，有昵称则快照；无观众或无昵称则置空，不用用户名冒充昵称。
     */
    private String snapshotNickname(String username) {
        Optional<ViewerEntity> viewer = viewerRepository.findByUsername(username);
        if (viewer.isEmpty()) {
            return null;
        }
        String nickname = trimToNull(viewer.get().getNickname());
        if (nickname == null) {
            return null;
        }
        return nickname.length() > 50 ? nickname.substring(0, 50) : nickname;
    }

    private void replaceCourses(Long orderId, List<String> courseIds) {
        orderCourseRepository.deleteByOrderId(orderId);
        orderCourseRepository.flush();
        for (String courseId : courseIds) {
            ActivityOrderCourseEntity row = new ActivityOrderCourseEntity();
            row.setOrderId(orderId);
            row.setCourseId(courseId);
            orderCourseRepository.save(row);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class ParsedOrder {
        private String orderNo;
        private String username;
        private Long productId;
        private String productName;
        private String productType;
        private java.math.BigDecimal tradeAmount;
        private String activityId;
        private List<String> courseIds;
        private LocalDateTime orderTime;
        private LocalDateTime payTime;
        private String salesmanId;
        private Integer channel;
        private int orderStatus;
    }
}
