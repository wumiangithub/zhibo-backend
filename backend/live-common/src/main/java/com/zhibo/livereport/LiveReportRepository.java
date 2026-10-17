package com.zhibo.livereport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface LiveReportRepository extends JpaRepository<LiveReportEntity, String>, JpaSpecificationExecutor<LiveReportEntity> {}
