package com.zhibo.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityProductClickRepository extends JpaRepository<ActivityProductClickEntity, Long> {

    @Query("SELECT c.spuId, COUNT(c), COUNT(DISTINCT c.username) FROM ActivityProductClickEntity c "
            + "WHERE c.activityId = :activityId GROUP BY c.spuId")
    List<Object[]> aggregateBySpu(@Param("activityId") String activityId);
}
