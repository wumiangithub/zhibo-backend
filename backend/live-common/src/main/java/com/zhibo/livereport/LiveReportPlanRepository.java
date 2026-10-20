package com.zhibo.livereport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveReportPlanRepository extends JpaRepository<LiveReportPlanEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LiveReportPlanEntity p where p.reportId = :reportId")
    void deleteByReportId(@Param("reportId") String reportId);
}
