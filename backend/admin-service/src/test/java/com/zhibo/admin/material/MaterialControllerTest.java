package com.zhibo.admin.material;

import com.zhibo.admin.material.dto.MaterialGroupResponse;
import com.zhibo.admin.material.dto.MaterialItemResponse;
import com.zhibo.admin.material.dto.MaterialListResponse;
import com.zhibo.admin.material.dto.RegisterMaterialsResponse;
import com.zhibo.admin.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import com.zhibo.admin.auth.AuthInterceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialControllerTest {

    private MockMvc mockMvc;
    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        materialService = mock(MaterialService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MaterialController(materialService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listGroups_ok() throws Exception {
        when(materialService.listGroups("image"))
                .thenReturn(List.of(new MaterialGroupResponse("g1", "默认分组", 2, true)));

        mockMvc.perform(get("/api/v1/materials/groups/list").param("type", "image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("g1"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].count").value(2));
    }

    @Test
    void list_ok() throws Exception {
        MaterialListResponse resp = new MaterialListResponse();
        resp.setTotal(1);
        resp.setPage(1);
        resp.setPageSize(20);
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("m1");
        item.setName("a.jpg");
        item.setType("image");
        resp.setList(List.of(item));
        when(materialService.list(eq("image"), isNull(), isNull(), isNull(), eq(false), eq(1), eq(20)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/v1/materials/list").param("type", "image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("m1"));
    }

    @Test
    void list_passesSelectableOnly() throws Exception {
        MaterialListResponse resp = new MaterialListResponse();
        resp.setTotal(0);
        resp.setPage(1);
        resp.setPageSize(20);
        resp.setList(List.of());
        when(materialService.list(eq("image"), isNull(), isNull(), isNull(), eq(true), eq(1), eq(20)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/v1/materials/list")
                        .param("type", "image")
                        .param("selectableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void list_passesKeywordAndAuditStatus() throws Exception {
        MaterialListResponse resp = new MaterialListResponse();
        resp.setTotal(0);
        resp.setPage(1);
        resp.setPageSize(20);
        resp.setList(List.of());
        when(materialService.list(eq("image"), eq("g1"), eq("封面"), eq("2"), eq(false), eq(1), eq(20)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/v1/materials/list")
                        .param("type", "image")
                        .param("groupId", "g1")
                        .param("keyword", "封面")
                        .param("auditStatus", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void startVhallTranscode_ok() throws Exception {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("v1");
        item.setType("video");
        item.setVhallVideoId("vid-99");
        item.setVhallTranscodeProgress(0);
        item.setVhallTranscodeStatus(0);
        when(materialService.startVhallTranscode("v1")).thenReturn(item);

        mockMvc.perform(post("/api/v1/materials/vhall-transcode/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("v1"))
                .andExpect(jsonPath("$.data.vhallVideoId").value("vid-99"))
                .andExpect(jsonPath("$.data.vhallTranscodeProgress").value(0))
                .andExpect(jsonPath("$.data.vhallTranscodeStatus").value(0));
    }

    @Test
    void createArticle_ok() throws Exception {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("a1");
        item.setName("这里是文章的标题");
        item.setType("article");
        when(materialService.createArticle(any(), eq(10001L))).thenReturn(item);

        mockMvc.perform(post("/api/v1/materials/articles/create")
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId":"g1",
                                  "title":"这里是文章的标题",
                                  "author":"作者",
                                  "content":"<p>正文</p>"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("a1"))
                .andExpect(jsonPath("$.data.type").value("article"))
                .andExpect(jsonPath("$.data.name").value("这里是文章的标题"));
    }

    @Test
    void register_ok() throws Exception {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("m1");
        item.setName("a.jpg");
        item.setUploaderName("张三");
        item.setDepartmentName("技术部");
        when(materialService.register(any(), anyLong()))
                .thenReturn(new RegisterMaterialsResponse(List.of(item)));

        mockMvc.perform(post("/api/v1/materials/create")
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"image",
                                  "groupId":"g1",
                                  "items":[{"name":"a.jpg","url":"https://x","objectKey":"material/image/1_a.jpg"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value("m1"))
                .andExpect(jsonPath("$.data.list[0].uploaderName").value("张三"))
                .andExpect(jsonPath("$.data.list[0].departmentName").value("技术部"));
    }

    @Test
    void list_validationFailure_returns400() throws Exception {
        when(materialService.list(eq("image"), isNull(), isNull(), isNull(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("page 不能小于 1"));

        mockMvc.perform(get("/api/v1/materials/list").param("type", "image").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("page 不能小于 1"));
    }

    @Test
    void rename_ok() throws Exception {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("m1");
        item.setName("新封面.jpg");
        item.setObjectKey("material/image/1_a.jpg");
        when(materialService.rename(eq("m1"), any())).thenReturn(item);

        mockMvc.perform(put("/api/v1/materials/rename/m1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新封面.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("新封面.jpg"))
                .andExpect(jsonPath("$.data.objectKey").value("material/image/1_a.jpg"));
    }

    @Test
    void updateAiAudit_ok() throws Exception {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("m1");
        item.setAiAuditStatus(1);
        when(materialService.updateAiAudit(eq("m1"), any(), eq(10001L))).thenReturn(item);

        mockMvc.perform(put("/api/v1/materials/ai-audit/m1")
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.aiAuditStatus").value(1));
    }

    @Test
    void updateRiskAudit_ok() throws Exception {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId("m1");
        item.setRiskAuditStatus(2);
        when(materialService.updateRiskAudit(eq("m1"), any(), eq(10001L))).thenReturn(item);

        mockMvc.perform(put("/api/v1/materials/risk-audit/m1")
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.riskAuditStatus").value(2));
    }

    @Test
    void softDelete_ok() throws Exception {
        when(materialService.softDelete("m1", 10001L))
                .thenReturn(new com.zhibo.admin.material.dto.DeleteIdResponse("m1"));

        mockMvc.perform(delete("/api/v1/materials/delete/m1")
                        .requestAttr(AuthInterceptor.ATTR_USER_ID, 10001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("m1"));
    }
}
