package com.zhibo.admin.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SysUimsYhidRoleRepository extends JpaRepository<SysUimsYhidRoleEntity, Long> {

    @Query("SELECT r.accountId FROM SysUimsYhidRoleEntity r WHERE r.rsStatus = 1 AND r.roleId = :roleId")
    List<Long> findActiveAccountIds(@Param("roleId") Long roleId);

    List<SysUimsYhidRoleEntity> findByRsStatusAndAccountIdIn(Integer rsStatus, Collection<Long> accountIds);
}
