package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.live.viewer.dto.ViewerPresencePingResponse;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViewerPresenceServiceTest {

    private ViewerRepository viewerRepository;
    private Clock clock;
    private ViewerPresenceService service;
    private ViewerEntity viewer;

    @BeforeEach
    void setUp() {
        viewerRepository = mock(ViewerRepository.class);
        Instant fixed = Instant.parse("2026-10-27T06:00:00Z");
        clock = Clock.fixed(fixed, ZoneId.of("Asia/Shanghai"));
        service = new ViewerPresenceService(viewerRepository, clock);
        viewer = new ViewerEntity();
        viewer.setId(9L);
        viewer.setStatus(ViewerStatus.NORMAL);
        when(viewerRepository.findById(9L)).thenReturn(Optional.of(viewer));
        when(viewerRepository.save(any(ViewerEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void ping_writesWhenEmpty() {
        ViewerPresencePingResponse res = service.ping(9L, "miniapp");
        assertEquals("2026-10-27 14:00:00", res.getLastVisitTime());
        assertEquals(LocalDateTime.of(2026, 10, 27, 14, 0), viewer.getLastVisitTime());
        verify(viewerRepository).save(viewer);
    }

    @Test
    void ping_throttlesWithinFiveMinutes() {
        viewer.setLastVisitTime(LocalDateTime.of(2026, 10, 27, 13, 56));
        ViewerPresencePingResponse res = service.ping(9L, "live");
        assertEquals("2026-10-27 13:56:00", res.getLastVisitTime());
        verify(viewerRepository, never()).save(any());
    }

    @Test
    void ping_writesAfterThrottleWindow() {
        viewer.setLastVisitTime(LocalDateTime.of(2026, 10, 27, 13, 54));
        ViewerPresencePingResponse res = service.ping(9L, null);
        assertEquals("2026-10-27 14:00:00", res.getLastVisitTime());
        verify(viewerRepository).save(viewer);
    }

    @Test
    void ping_rejectsBadScene() {
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.ping(9L, "web"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void ping_rejectsDisabled() {
        viewer.setStatus(ViewerStatus.DISABLED);
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.ping(9L, "miniapp"));
        assertEquals(403, ex.getCode());
        verify(viewerRepository, never()).save(any());
    }

    @Test
    void ping_rejectsMissingViewer() {
        when(viewerRepository.findById(anyLong())).thenReturn(Optional.empty());
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.ping(1L, null));
        assertEquals(403, ex.getCode());
    }
}
