package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.live.viewer.dto.ViewerFeedbackDetailResponse;
import com.zhibo.live.viewer.dto.ViewerFeedbackImageItem;
import com.zhibo.live.viewer.dto.ViewerFeedbackListResponse;
import com.zhibo.live.viewer.dto.ViewerFeedbackSubmitRequest;
import com.zhibo.live.viewer.dto.ViewerFeedbackSubmitResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerFeedbackCategory;
import com.zhibo.viewer.ViewerFeedbackEntity;
import com.zhibo.viewer.ViewerFeedbackRepository;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ViewerFeedbackServiceTest {

    private ViewerRepository viewerRepository;
    private ViewerFeedbackRepository feedbackRepository;
    private ViewerTosUploader tosUploader;
    private ViewerFeedbackService service;
    private ViewerEntity viewer;

    @BeforeEach
    void setUp() {
        viewerRepository = mock(ViewerRepository.class);
        feedbackRepository = mock(ViewerFeedbackRepository.class);
        tosUploader = mock(ViewerTosUploader.class);
        service =
                new ViewerFeedbackService(
                        viewerRepository, feedbackRepository, tosUploader, new ObjectMapper());

        viewer = new ViewerEntity();
        viewer.setId(9L);
        viewer.setStatus(ViewerStatus.NORMAL);
        viewer.setAgreedTime(LocalDateTime.now());
        viewer.setLastLoginTime(LocalDateTime.now());
        when(viewerRepository.findById(9L)).thenReturn(Optional.of(viewer));
        when(feedbackRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            ViewerFeedbackEntity e = inv.getArgument(0);
                            e.setId(100L);
                            return e;
                        });
        when(tosUploader.isAllowedAvatarUrl(any())).thenReturn(true);
    }

    @Test
    void submit_ok_withoutImages() {
        ViewerFeedbackSubmitRequest req = new ViewerFeedbackSubmitRequest();
        req.setCategory(ViewerFeedbackCategory.COURSE_BENEFIT);
        req.setContent(" 课程看不了 ");
        ViewerFeedbackSubmitResponse resp = service.submit(9L, req);
        assertEquals(100L, resp.getId());
    }

    @Test
    void submit_withImageAndPhone() {
        ViewerFeedbackImageItem img = new ViewerFeedbackImageItem();
        img.setObjectKey("viewer/feedback/9_1.jpg");
        img.setUrl("https://qklive-testcos.cjs99.com/viewer/feedback/9_1.jpg");
        ViewerFeedbackSubmitRequest req = new ViewerFeedbackSubmitRequest();
        req.setCategory(ViewerFeedbackCategory.OTHER);
        req.setContent("建议");
        req.setContactPhone("13800138000");
        req.setImages(List.of(img));
        assertEquals(100L, service.submit(9L, req).getId());
    }

    @Test
    void submit_rejectEmptyContent() {
        ViewerFeedbackSubmitRequest req = new ViewerFeedbackSubmitRequest();
        req.setCategory(1);
        req.setContent("  ");
        ViewerAuthException ex =
                assertThrows(ViewerAuthException.class, () -> service.submit(9L, req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void submit_rejectBadPhone() {
        ViewerFeedbackSubmitRequest req = new ViewerFeedbackSubmitRequest();
        req.setCategory(1);
        req.setContent("有问题");
        req.setContactPhone("123");
        assertThrows(ViewerAuthException.class, () -> service.submit(9L, req));
    }

    @Test
    void submit_blankPhoneBecomesNull() {
        ViewerFeedbackSubmitRequest req = new ViewerFeedbackSubmitRequest();
        req.setCategory(4);
        req.setContent("其他");
        req.setContactPhone("  ");
        assertEquals(100L, service.submit(9L, req).getId());
        ArgumentCaptor<ViewerFeedbackEntity> captor =
                ArgumentCaptor.forClass(ViewerFeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());
        assertNull(captor.getValue().getContactPhone());
    }

    @Test
    void list_ok() {
        ViewerFeedbackEntity row = new ViewerFeedbackEntity();
        row.setId(100L);
        row.setViewerId(9L);
        row.setCategory(ViewerFeedbackCategory.COURSE_BENEFIT);
        row.setContent("课程权益无法使用");
        row.setImagesJson(
                "[{\"url\":\"https://qklive-testcos.cjs99.com/viewer/feedback/9_1.jpg\",\"objectKey\":\"viewer/feedback/9_1.jpg\"}]");
        row.setCreatedTime(LocalDateTime.of(2026, 10, 26, 18, 0, 0));
        when(feedbackRepository.findByViewerIdOrderByCreatedTimeDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        ViewerFeedbackListResponse resp = service.list(9L, 1, 20);
        assertEquals(1, resp.getTotal());
        assertEquals(1, resp.getList().size());
        assertEquals("课程权益", resp.getList().get(0).getCategoryLabel());
        assertEquals(1, resp.getList().get(0).getImageCount());
        assertEquals("2026-10-26 18:00:00", resp.getList().get(0).getCreatedTime());
    }

    @Test
    void detail_ok() {
        ViewerFeedbackEntity row = new ViewerFeedbackEntity();
        row.setId(100L);
        row.setViewerId(9L);
        row.setCategory(2);
        row.setContent("卡顿");
        row.setContactPhone("13800138000");
        row.setCreatedTime(LocalDateTime.of(2026, 10, 26, 18, 0, 0));
        when(feedbackRepository.findByIdAndViewerId(100L, 9L)).thenReturn(Optional.of(row));

        ViewerFeedbackDetailResponse d = service.detail(9L, 100L);
        assertEquals(100L, d.getId());
        assertEquals("视频播放", d.getCategoryLabel());
        assertEquals("13800138000", d.getContactPhone());
    }

    @Test
    void detail_notFound() {
        when(feedbackRepository.findByIdAndViewerId(1L, 9L)).thenReturn(Optional.empty());
        ViewerAuthException ex =
                assertThrows(ViewerAuthException.class, () -> service.detail(9L, 1L));
        assertEquals(404, ex.getCode());
    }
}
