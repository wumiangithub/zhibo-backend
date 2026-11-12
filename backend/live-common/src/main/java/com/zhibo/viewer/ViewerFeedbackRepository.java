package com.zhibo.viewer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewerFeedbackRepository extends JpaRepository<ViewerFeedbackEntity, Long> {

    Page<ViewerFeedbackEntity> findByUsernameOrderByCreatedTimeDesc(String username, Pageable pageable);

    Optional<ViewerFeedbackEntity> findByIdAndUsername(Long id, String username);
}
