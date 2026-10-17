package com.zhibo.livereport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LiveReportAuditRepository extends JpaRepository<LiveReportAuditEntity, String> {
    List<LiveReportAuditEntity> findByReportIdOrderByCreatedAtDesc(String reportId);
}
