package com.zhibo.viewer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewerFeedbackRepository extends JpaRepository<ViewerFeedbackEntity, Long> {

    /** 当前观众的反馈，按提交时间倒序。 */
    Page<ViewerFeedbackEntity> findByViewerIdOrderByCreatedTimeDesc(Long viewerId, Pageable pageable);

    /** 详情：仅本人可见。 */
    Optional<ViewerFeedbackEntity> findByIdAndViewerId(Long id, Long viewerId);
}
