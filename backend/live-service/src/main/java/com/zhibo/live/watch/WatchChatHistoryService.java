package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.WatchChatHistoryItem;
import com.zhibo.live.watch.dto.WatchChatHistoryResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.ProviderNotAvailableException;
import com.zhibo.liveprovider.dto.ProviderChatHistory;
import com.zhibo.liveprovider.dto.ProviderChatMessage;
import com.zhibo.vhall.exception.VhallException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 回放页只读聊天历史（D91）：代理微吼 get-list；失败降级空列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchChatHistoryService {

    static final int PAGE_SIZE = 50;

    private final WatchService watchService;
    private final LiveProviderRegistry providerRegistry;

    public WatchChatHistoryResponse list(String activityId, int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);

        WatchChatHistoryResponse empty = emptyResponse(page);
        LiveProvider provider;
        try {
            provider = providerRegistry.require(entity.getProvider());
        } catch (ProviderNotAvailableException e) {
            throw e;
        }

        int pos = (page - 1) * PAGE_SIZE;
        ProviderChatHistory history;
        try {
            history = provider.listChatHistory(entity.getProviderRoomId(), pos, PAGE_SIZE);
        } catch (UnsupportedOperationException e) {
            throw new ProviderNotAvailableException();
        } catch (VhallException e) {
            log.warn("聊天历史 get-list 失败 activityId={} page={}: {}",
                    activityId, page, e.getMessage());
            return empty;
        } catch (RuntimeException e) {
            log.warn("聊天历史 get-list 异常 activityId={} page={}: {}",
                    activityId, page, e.getMessage());
            return empty;
        }

        if (history == null) {
            return empty;
        }

        List<ProviderChatMessage> raw = history.list() == null ? List.of() : history.list();
        // 微吼通常新→旧；展示旧→新
        List<ProviderChatMessage> ordered = new ArrayList<>(raw);
        Collections.reverse(ordered);

        WatchChatHistoryResponse response = new WatchChatHistoryResponse();
        response.setTotal(history.total());
        response.setPage(page);
        response.setPageSize(PAGE_SIZE);
        response.setList(ordered.stream().map(WatchChatHistoryService::toItem).toList());
        return response;
    }

    private static WatchChatHistoryResponse emptyResponse(int page) {
        WatchChatHistoryResponse response = new WatchChatHistoryResponse();
        response.setTotal(0);
        response.setPage(page);
        response.setPageSize(PAGE_SIZE);
        response.setList(List.of());
        return response;
    }

    private static WatchChatHistoryItem toItem(ProviderChatMessage msg) {
        WatchChatHistoryItem item = new WatchChatHistoryItem();
        item.setSenderId(msg.senderId());
        item.setSenderName(msg.senderName());
        item.setMsg(msg.msg());
        item.setMsgId(msg.msgId());
        item.setAvatar(msg.avatar());
        item.setRoleName(msg.roleName());
        item.setTimestamp(msg.timestamp());
        return item;
    }
}
