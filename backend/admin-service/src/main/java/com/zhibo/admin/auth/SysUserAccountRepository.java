package com.zhibo.admin.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserAccountRepository extends JpaRepository<SysUserAccountEntity, Long> {

    Optional<SysUserAccountEntity> findByUserAccount(String userAccount);

    Optional<SysUserAccountEntity> findByUnionid(String unionid);

    Optional<SysUserAccountEntity> findByOpenid(String openid);
}
