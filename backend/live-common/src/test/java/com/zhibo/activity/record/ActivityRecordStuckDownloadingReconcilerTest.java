package com.zhibo.activity.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityRecordStuckDownloadingReconcilerTest {

    @Mock
    private ActivityRecordRepository activityRecordRepository;
    @Mock
    private ActivityRecordTosIngestService activityRecordTosIngestService;

    @InjectMocks
    private ActivityRecordStuckDownloadingReconciler reconciler;

    @Test
    void reconcile_marksNotInFlightDownloadingFailed() {
        ActivityRecordEntity row = new ActivityRecordEntity();
        row.setActivityId("act1");
        row.setRecordId(99L);
        row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);

        when(activityRecordRepository.findByStatusWithoutTos(ActivityRecordEntity.STATUS_DOWNLOADING))
                .thenReturn(List.of(row));
        when(activityRecordTosIngestService.isIngestInFlight(99L)).thenReturn(false);
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        reconciler.reconcileStuckDownloading();

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        verify(activityRecordTosIngestService).clearInFlight(99L);
        verify(activityRecordRepository).save(row);
    }

    @Test
    void reconcile_skipsInFlight() {
        ActivityRecordEntity row = new ActivityRecordEntity();
        row.setActivityId("act1");
        row.setRecordId(99L);
        row.setStatus(ActivityRecordEntity.STATUS_DOWNLOADING);

        when(activityRecordRepository.findByStatusWithoutTos(ActivityRecordEntity.STATUS_DOWNLOADING))
                .thenReturn(List.of(row));
        when(activityRecordTosIngestService.isIngestInFlight(99L)).thenReturn(true);

        reconciler.reconcileStuckDownloading();

        assertEquals(ActivityRecordEntity.STATUS_DOWNLOADING, row.getStatus());
        verify(activityRecordRepository, never()).save(any());
    }
}
