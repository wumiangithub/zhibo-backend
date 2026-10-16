package com.zhibo.liveaccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LiveAccountRepository extends JpaRepository<LiveAccountEntity, Long> {

    List<LiveAccountEntity> findByUserIdIn(Collection<Long> userIds);
}
