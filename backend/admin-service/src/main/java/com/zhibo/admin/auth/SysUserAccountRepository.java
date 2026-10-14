package com.zhibo.admin.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysUserAccountRepository extends JpaRepository<SysUserAccountEntity, Long> {

    Optional<SysUserAccountEntity> findByUserAccount(String userAccount);

    Optional<SysUserAccountEntity> findByUnionid(String unionid);

    Optional<SysUserAccountEntity> findByOpenid(String openid);

    /** 在职员工（rs_status=1），按姓名包含匹配；keyword 为空则不过滤。 */
    @Query("SELECT u FROM SysUserAccountEntity u WHERE u.rsStatus = 1 "
            + "AND (:keyword IS NULL OR u.userName LIKE CONCAT('%', :keyword, '%')) "
            + "ORDER BY u.userName ASC")
    List<SysUserAccountEntity> searchActive(@Param("keyword") String keyword, Pageable pageable);
}
