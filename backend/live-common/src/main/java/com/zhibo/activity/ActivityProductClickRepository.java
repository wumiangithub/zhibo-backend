package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityProductClickRepository extends JpaRepository<ActivityProductClickEntity, Long> {

    @Query("SELECT c.spuId, COUNT(c), COUNT(DISTINCT c.username) FROM ActivityProductClickEntity c "
            + "WHERE c.activityId = :activityId GROUP BY c.spuId")
    List<Object[]> aggregateBySpu(@Param("activityId") String activityId);

    /** 本场按观众聚合点击次数（中控台观众明细）。 */
    @Query("SELECT c.username, COUNT(c) FROM ActivityProductClickEntity c "
            + "WHERE c.activityId = :activityId AND c.username IN :usernames GROUP BY c.username")
    List<Object[]> countByActivityAndUsernames(
            @Param("activityId") String activityId, @Param("usernames") Collection<String> usernames);
}
