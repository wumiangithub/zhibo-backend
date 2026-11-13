package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityOrderCourseRepository extends JpaRepository<ActivityOrderCourseEntity, Long> {

    void deleteByOrderId(Long orderId);

    List<ActivityOrderCourseEntity> findByOrderIdOrderByIdAsc(Long orderId);

    List<ActivityOrderCourseEntity> findByOrderIdIn(Collection<Long> orderIds);
}
