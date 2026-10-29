package com.zhibo.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 流水插入单独开事务：唯一键冲突只回滚本段，不把外层名单更新弄成 500。
 */
@Service
@RequiredArgsConstructor
public class PresenceEventInserter {

    private final PresenceEventRepository eventRepository;

    /**
     * @return true 新插入；false 已存在（mid / 客户端去重键）或并发撞唯一键
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insert(
            String roomId,
            String userId,
            String type,
            LocalDateTime eventTime,
            String mid,
            String source,
            String socketId) {
        String normalizedMid = blankToNull(mid);
        if (normalizedMid != null && eventRepository.existsByMid(normalizedMid)) {
            return false;
        }
        String normalizedSocket = blankToNull(socketId);
        boolean duplicate = normalizedSocket == null
                ? eventRepository.existsByRoomIdAndUserIdAndTypeAndEventTimeAndSource(
                        roomId, userId, type, eventTime, source)
                : eventRepository.existsByRoomIdAndUserIdAndTypeAndEventTimeAndSourceAndSocketId(
                        roomId, userId, type, eventTime, source, normalizedSocket);
        if (duplicate) {
            return false;
        }
        PresenceEventEntity event = new PresenceEventEntity();
        event.setRoomId(roomId);
        event.setUserId(userId);
        event.setSocketId(normalizedSocket);
        event.setType(type);
        event.setEventTime(eventTime);
        event.setMid(normalizedMid);
        event.setSource(source);
        try {
            // 立刻 flush，才能在本方法里抓住唯一键冲突（不能拖到外层 commit）
            eventRepository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
