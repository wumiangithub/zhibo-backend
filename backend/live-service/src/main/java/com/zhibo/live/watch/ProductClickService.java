package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityProductClickEntity;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.live.watch.dto.WatchProductClickResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 小黄车「去抢购」点击埋点。 */
@Service
@RequiredArgsConstructor
public class ProductClickService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final WatchService watchService;
    private final ActivityProductRepository productRepository;
    private final ActivityProductClickRepository clickRepository;
    private final Clock clock;

    @Transactional
    public WatchProductClickResponse click(String activityId, String username, String rawSpuId) {
        String spuId = rawSpuId == null ? "" : rawSpuId.trim();
        if (spuId.isEmpty()) {
            throw new IllegalArgumentException("spuId 不能为空");
        }
        ActivityEntity activity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(activity);
        if (!productRepository.existsById(new ActivityProductId(activity.getId(), spuId))) {
            throw new IllegalArgumentException("商品未绑定本场");
        }
        ActivityProductClickEntity row = new ActivityProductClickEntity();
        row.setActivityId(activity.getId());
        row.setSpuId(spuId);
        row.setUsername(username);
        row.setClickedAt(LocalDateTime.ofInstant(clock.instant(), SHANGHAI));
        clickRepository.save(row);
        return new WatchProductClickResponse(true);
    }
}
