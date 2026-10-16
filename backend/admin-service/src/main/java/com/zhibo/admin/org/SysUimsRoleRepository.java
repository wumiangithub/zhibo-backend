package com.zhibo.admin.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SysUimsRoleRepository extends JpaRepository<SysUimsRoleEntity, Long> {

    @Query("SELECT r FROM SysUimsRoleEntity r WHERE r.rsStatus = 1 ORDER BY r.rsDispindex ASC, r.id ASC")
    List<SysUimsRoleEntity> findActive();
}
