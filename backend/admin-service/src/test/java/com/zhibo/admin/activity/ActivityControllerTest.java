package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityControllerTest {

    private static final String UUID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    private MockMvc mockMvc;
    private ActivityService activityService;
    private HostService hostService;
    private EmbedService embedService;

    @BeforeEach
    void setUp() {
        activityService = mock(ActivityService.class);
        hostService = mock(HostService.class);
        embedService = mock(EmbedService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ActivityController(activityService, hostService, embedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_returnsApiResponse() throws Exception {
        when(activityService.create(any())).thenReturn(new CreateActivityResponse(UUID));

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"vhall","title":"活动A","startTime":"2026-10-01 20:00:00","type":2,"listed":true,"sellType":"free"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(UUID));
    }

    @Test
    void create_validationFailure_returns400() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"vhall","title":"","startTime":"2026-10-01 20:00:00","type":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_requiresProvider() throws Exception {
        mockMvc.perform(post("/api/activities")
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
        mockMvc.perform(post("/api/activities")
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

        mockMvc.perform(get("/api/activities/" + UUID).accept(MediaType.APPLICATION_JSON))
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
        when(hostService.host(eq(UUID))).thenReturn(host);

        mockMvc.perform(get("/api/activities/" + UUID + "/host").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("vhall"))
                .andExpect(jsonPath("$.data.hostUrl").value("https://host.example/page"));
    }

    @Test
    void embed_returnsOk() throws Exception {
        var embed = new com.zhibo.admin.activity.dto.ActivityEmbedResponse();
        embed.setEmbedUrl("https://e.vhall.com/v3/embed/live/detail/9?token=t");
        when(embedService.embed(eq(UUID))).thenReturn(embed);

        mockMvc.perform(get("/api/activities/" + UUID + "/embed").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.embedUrl")
                        .value("https://e.vhall.com/v3/embed/live/detail/9?token=t"));
    }

    @Test
    void records_returnsList() throws Exception {
        var response = new com.zhibo.admin.activity.dto.ActivityRecordListResponse();
        response.setTotal(1);
        response.setList(java.util.List.of(
                new com.zhibo.admin.activity.dto.ActivityRecordItem(
                        "799703", "回放A", "01:02:03", "2026-09-01 10:00:00", "https://cover")));
        when(activityService.listRecords(eq(UUID))).thenReturn(response);

        mockMvc.perform(get("/api/activities/" + UUID + "/records").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("799703"))
                .andExpect(jsonPath("$.data.list[0].title").value("回放A"));
    }
}
