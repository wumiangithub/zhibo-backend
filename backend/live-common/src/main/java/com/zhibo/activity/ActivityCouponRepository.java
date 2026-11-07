package com.zhibo.activity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityCouponRepository extends JpaRepository<ActivityCouponEntity, Long> {

    Optional<ActivityCouponEntity> findByActivityIdAndCouponId(String activityId, Long couponId);

    List<ActivityCouponEntity> findByActivityIdOrderByLastSentTimeDesc(String activityId);
}
