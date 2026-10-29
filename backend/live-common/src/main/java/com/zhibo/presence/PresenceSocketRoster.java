package com.zhibo.presence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@code socket_id} 计算某人当前是否在房。没有 socket 的历史行不参与。
 */
public final class PresenceSocketRoster {

    private PresenceSocketRoster() {
    }

    public record State(boolean online, LocalDateTime latestOpenJoin, LocalDateTime latestClosedLeave) {
    }

    public static State compute(List<PresenceEventEntity> events) {
        Map<String, LocalDateTime> openJoin = new LinkedHashMap<>();
        Map<String, LocalDateTime> closedLeave = new LinkedHashMap<>();
        for (PresenceEventEntity event : ordered(events)) {
            String socketId = socketId(event);
            if (socketId == null) {
                continue;
            }
            LocalDateTime time = event.getEventTime();
            if (time == null) {
                continue;
            }
            if (PresenceEventEntity.TYPE_JOIN.equals(event.getType())) {
                if (!openJoin.containsKey(socketId)) {
                    openJoin.put(socketId, time);
                }
            } else if (PresenceEventEntity.TYPE_LEAVE.equals(event.getType())) {
                if (openJoin.containsKey(socketId)) {
                    openJoin.remove(socketId);
                    closedLeave.put(socketId, time);
                }
            }
        }
        if (!openJoin.isEmpty()) {
            LocalDateTime latestJoin = openJoin.values().stream().max(LocalDateTime::compareTo).orElse(null);
            return new State(true, latestJoin, null);
        }
        LocalDateTime latestLeave = closedLeave.values().stream().max(LocalDateTime::compareTo).orElse(null);
        return new State(false, null, latestLeave);
    }

    private static List<PresenceEventEntity> ordered(List<PresenceEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<PresenceEventEntity> ordered = new ArrayList<>(events);
        ordered.sort(Comparator
                .comparing(PresenceEventEntity::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PresenceEventEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ordered;
    }

    private static String socketId(PresenceEventEntity event) {
        if (event == null || event.getSocketId() == null || event.getSocketId().isBlank()) {
            return null;
        }
        return event.getSocketId().trim();
    }
}
