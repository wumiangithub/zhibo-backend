package com.zhibo.live.viewer;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.WatchActivityItem;
import com.zhibo.live.watch.dto.WatchActivityListResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 小程序「我的学习」：只回当前观众有观看权益的直播。 */
@Service
@RequiredArgsConstructor
public class ViewerLearningService {

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public WatchActivityListResponse listEntitledLives(long viewerId, int page, int pageSize, Integer state) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        boolean statesEmpty = state == null || state == 0;
        List<Integer> statesParam = statesEmpty ? List.of(0) : List.of(state);
        PageRequest pageable = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("createdTime")));
        Page<ActivityEntity> result =
                activityRepository.searchEntitledForViewer(viewerId, statesEmpty, statesParam, pageable);

        WatchActivityListResponse response = new WatchActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream().map(this::toItem).toList());
        return response;
    }

    private WatchActivityItem toItem(ActivityEntity entity) {
        WatchActivityItem item = new WatchActivityItem();
        item.setId(entity.getId());
        item.setTitle(entity.getTitle());
        item.setState(entity.getState());
        item.setStartTime(entity.getStartTime());
        item.setCoverUrl(entity.getCoverUrl());
        return item;
    }
}
