package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityControllerTest {

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
        when(activityService.create(any())).thenReturn(new CreateActivityResponse(1001L));

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"活动A","startTime":"2026-10-01 20:00:00","type":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1001));
    }

    @Test
    void create_validationFailure_returns400() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","startTime":"2026-10-01 20:00:00","type":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_rejectsType4() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"录播","startTime":"2026-10-01 20:00:00","type":4}
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
                                {"title":"%s","startTime":"2026-10-01 20:00:00","type":2}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void detail_returnsOk() throws Exception {
        when(activityService.detail(9L)).thenAnswer(inv -> {
            var detail = new com.zhibo.admin.activity.dto.ActivityDetailResponse();
            detail.setId(9L);
            detail.setTitle("X");
            return detail;
        });

        mockMvc.perform(get("/api/activities/9").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.title").value("X"));
    }

    @Test
    void host_returnsOk() throws Exception {
        var host = new com.zhibo.admin.activity.dto.ActivityHostResponse();
        host.setHostUrl("https://host.example/page");
        when(hostService.host(9L)).thenReturn(host);

        mockMvc.perform(get("/api/activities/9/host").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hostUrl").value("https://host.example/page"));
    }

    @Test
    void embed_returnsOk() throws Exception {
        var embed = new com.zhibo.admin.activity.dto.ActivityEmbedResponse();
        embed.setEmbedUrl("https://e.vhall.com/v3/embed/live/detail/9?token=t");
        when(embedService.embed(9L)).thenReturn(embed);

        mockMvc.perform(get("/api/activities/9/embed").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.embedUrl")
                        .value("https://e.vhall.com/v3/embed/live/detail/9?token=t"));
    }
}
