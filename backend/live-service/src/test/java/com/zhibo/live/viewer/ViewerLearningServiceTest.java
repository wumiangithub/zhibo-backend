package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ViewerLearningServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ViewerLearningService learningService;

    @Test
    void listEntitledLives_rejectsPageBelowOne() {
        assertThrows(
                IllegalArgumentException.class, () -> learningService.listEntitledLives(9L, 0, 20, null));
    }

    @Test
    void listEntitledLives_mapsRows() {
        ActivityEntity row = new ActivityEntity();
        row.setId(ID);
        row.setTitle("权益课");
        row.setState(1);
        row.setStartTime("2026-10-28 20:00:00");
        row.setCoverUrl("https://cover");

        when(activityRepository.searchEntitledForViewer(eq(9L), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        var resp = learningService.listEntitledLives(9L, 1, 20, null);
        assertEquals(1, resp.getTotal());
        assertEquals(ID, resp.getList().get(0).getId());
        assertEquals("权益课", resp.getList().get(0).getTitle());

        ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository)
                .searchEntitledForViewer(eq(9L), eq(true), eq(List.of(0)), pageableCap.capture());
        assertEquals(0, pageableCap.getValue().getPageNumber());
        assertEquals(20, pageableCap.getValue().getPageSize());
    }
}
