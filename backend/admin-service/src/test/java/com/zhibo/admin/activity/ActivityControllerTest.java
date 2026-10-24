package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityPasscodeResponse;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.zhibo.admin.auth.AuthInterceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityControllerTest {

    private static final String UUID = "a1b2c3d4e5f67890abcdef1234567890";

    private MockMvc mockMvc;
    private ActivityService activityService;
    private HostService hostService;
    private HostPasscodeService hostPasscodeService;

    @BeforeEach
    void setUp() {
        activityService = mock(ActivityService.class);
        hostService = mock(HostService.class);
        hostPasscodeService = mock(HostPasscodeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ActivityController(activityService, hostService, hostPasscodeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_returnsApiResponse() throws Exception {
        when(activityService.create(any(), anyLong())).thenReturn(new CreateActivityResponse(UUID));

        mockMvc.perform(post("/api/v1/activities/create")
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"vhall","title":"活动A","startTime":"2026-10-01 20:00:00","type":2,"autoList":true,"hostIds":[10001]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(UUID));
    }

    @Test
    void create_validationFailure_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/activities/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"vhall","title":"","startTime":"2026-10-01 20:00:00","type":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_requiresProvider() throws Exception {
        mockMvc.perform(post("/api/v1/activities/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"活动","startTime":"2026-10-01 20:00:00","type":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_rejectsTitleOver64() throws Exception {
        String title = "a".repeat(65);
        mockMvc.perform(post("/api/v1/activities/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"vhall","title":"%s","startTime":"2026-10-01 20:00:00","type":2}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void detail_returnsOk() throws Exception {
        when(activityService.detail(eq(UUID))).thenAnswer(inv -> {
            var detail = new com.zhibo.admin.activity.dto.ActivityDetailResponse();
            detail.setId(UUID);
            detail.setProvider("vhall");
            detail.setTitle("X");
            return detail;
        });

        mockMvc.perform(get("/api/v1/activities/detail/" + UUID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(UUID))
                .andExpect(jsonPath("$.data.title").value("X"));
    }

    @Test
    void host_returnsOk() throws Exception {
        var host = new com.zhibo.admin.activity.dto.ActivityHostResponse();
        host.setProvider("vhall");
        host.setHostUrl("https://host.example/page");
        when(hostService.host(eq(UUID), isNull(), eq(10001L))).thenReturn(host);

        mockMvc.perform(get("/api/v1/activities/host/" + UUID).accept(MediaType.APPLICATION_JSON)
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("vhall"))
                .andExpect(jsonPath("$.data.hostUrl").value("https://host.example/page"));
    }

    @Test
    void passcode_returnsOk() throws Exception {
        ActivityPasscodeResponse body = new ActivityPasscodeResponse();
        body.setPasscode("274803129");
        when(hostPasscodeService.getPasscode(eq(UUID))).thenReturn(body);

        mockMvc.perform(get("/api/v1/activities/passcode/" + UUID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.passcode").value("274803129"));
    }

    @Test
    void resetPasscode_returnsOk() throws Exception {
        ActivityPasscodeResponse body = new ActivityPasscodeResponse();
        body.setPasscode("111222333");
        when(hostPasscodeService.resetPasscode(eq(UUID))).thenReturn(body);

        mockMvc.perform(post("/api/v1/activities/passcode/reset/" + UUID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.passcode").value("111222333"));
    }

    @Test
    void records_returnsList() throws Exception {
        var response = new com.zhibo.admin.activity.dto.ActivityRecordListResponse();
        response.setTotal(1);
        response.setList(java.util.List.of(
                new com.zhibo.admin.activity.dto.ActivityRecordItem(
                        799703L, "回放A", "01:02:03", "2026-09-01 10:00:00", "https://cover",
                        "1024", "2027-09-01 10:00:00")));
        when(activityService.listRecords(eq(UUID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/activities/records/" + UUID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(799703))
                .andExpect(jsonPath("$.data.list[0].title").value("回放A"))
                .andExpect(jsonPath("$.data.list[0].storage").value("1024"))
                .andExpect(jsonPath("$.data.list[0].saveTime").value("2027-09-01 10:00:00"));
    }

    @Test
    void recordDownload_returnsUrl() throws Exception {
        when(activityService.recordDownload(eq(UUID), eq(5541984L))).thenReturn(
                new com.zhibo.admin.activity.dto.ActivityRecordDownloadResponse(
                        5541984L, "https://vod-download.vhall.com/a.zip", 1, 100));

        mockMvc.perform(get("/api/v1/activities/records/" + UUID + "/download/5541984")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(5541984))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://vod-download.vhall.com/a.zip"))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.progress").value(100));
    }
}
