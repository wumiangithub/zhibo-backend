package com.zhibo.admin.auth;

import java.util.Collection;
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

    /** 在职且 yhid 落在给定集合；可选姓名模糊。 */
    @Query("SELECT u FROM SysUserAccountEntity u WHERE u.rsStatus = 1 "
            + "AND u.yhid IN :yhids "
            + "AND (:userName IS NULL OR u.userName LIKE CONCAT('%', :userName, '%')) "
            + "ORDER BY u.userName ASC, u.id ASC")
    List<SysUserAccountEntity> findActiveByYhidIn(
            @Param("yhids") Collection<Long> yhids,
            @Param("userName") String userName);
}
