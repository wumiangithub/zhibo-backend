package com.zhibo.viewer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ViewerRepository extends JpaRepository<ViewerEntity, Long>, JpaSpecificationExecutor<ViewerEntity> {

    Optional<ViewerEntity> findByMiniOpenid(String miniOpenid);

    Optional<ViewerEntity> findByPhone(String phone);

    Optional<ViewerEntity> findByUid(Long uid);

    boolean existsByUid(Long uid);

    List<ViewerEntity> findByPhoneIn(Collection<String> phones);

    List<ViewerEntity> findByNicknameContaining(String nickname);

    List<ViewerEntity> findByIdIn(Collection<Long> ids);

    List<ViewerEntity> findByUidIn(Collection<Long> uids);
}
