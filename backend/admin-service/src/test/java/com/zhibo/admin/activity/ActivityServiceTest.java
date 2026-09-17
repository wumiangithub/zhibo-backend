package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private VhallClient vhallClient;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void create_mapsFieldsAndReturnsId() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setTitle("  测试活动  ");
        request.setStartTime("2026-10-01 20:00:00");
        request.setType(2);

        when(vhallClient.postForData(eq(ActivityService.PATH_CREATE), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_id", 123456L));

        CreateActivityResponse response = activityService.create(request);
        assertEquals(123456L, response.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(ActivityService.PATH_CREATE), captor.capture(), eq(Map.class));
        assertEquals("测试活动", captor.getValue().get("subject"));
        assertEquals("2026-10-01 20:00", captor.getValue().get("start_time"));
        assertEquals(2, captor.getValue().get("webinar_type"));
    }

    @Test
    void list_mapsVhallListToActivitySummary() {
        when(vhallClient.postForData(eq(ActivityService.PATH_LIST), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "total", 1,
                        "list", List.of(Map.of(
                                "webinar_id", 99,
                                "subject", "标题",
                                "start_time", "2026-01-01 10:00:00",
                                "img_url", "https://cover",
                                "webinar_state", 2,
                                "webinar_type", 2,
                                "share_link", "https://watch",
                                "created_at", "2026-01-01 09:00:00"
                        ))
                ));

        ActivityListResponse response = activityService.list(0, 20, null, null);
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getList().size());
        assertEquals(99L, response.getList().get(0).getId());
        assertEquals("标题", response.getList().get(0).getTitle());
        assertEquals(2, response.getList().get(0).getState());
    }

    @Test
    void list_rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class,
                () -> activityService.list(0, 101, null, null));
    }

    @Test
    void detail_mapsInfoResponse() {
        when(vhallClient.postForData(eq(ActivityService.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "id", 88,
                        "subject", "详情标题",
                        "start_time", "2026-02-01 12:00:00",
                        "introduction", "简介",
                        "webinar_state", 1,
                        "webinar_type", 3,
                        "pv", 10
                ));

        ActivityDetailResponse detail = activityService.detail(88);
        assertEquals(88L, detail.getId());
        assertEquals("详情标题", detail.getTitle());
        assertEquals("简介", detail.getIntroduction());
        assertEquals(1, detail.getState());
        assertEquals(10, detail.getPv());
    }
}
