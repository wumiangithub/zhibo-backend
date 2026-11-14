package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityAudienceRepository
        extends JpaRepository<ActivityAudienceEntity, Long>, JpaSpecificationExecutor<ActivityAudienceEntity> {

    Optional<ActivityAudienceEntity> findByActivityIdAndUsername(String activityId, String username);

    List<ActivityAudienceEntity> findByActivityIdAndUsernameIn(String activityId, Collection<String> usernames);

    List<ActivityAudienceEntity> findByActivityId(String activityId);

    long countByActivityId(String activityId);

    /** 本场出现过的员工邀请人 id（>0 去重；底栏跟进人下拉）。 */
    @Query("SELECT DISTINCT a.employeeInviterId FROM ActivityAudienceEntity a "
            + "WHERE a.activityId = :activityId AND a.employeeInviterId IS NOT NULL "
            + "AND a.employeeInviterId > 0")
    List<Long> findDistinctEmployeeInviterIds(@Param("activityId") String activityId);
}
