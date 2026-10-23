package com.zhibo.viewer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewerIdentityRepository extends JpaRepository<ViewerIdentityEntity, Long> {

    Optional<ViewerIdentityEntity> findByChannelAndOpenid(int channel, String openid);
}
