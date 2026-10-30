package com.zhibo.activity.record;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 回放成片服务端 PutObject（D88）。admin / live 各自用本服务 TOS 凭证实现。
 */
public interface ActivityRecordTosClient {

    /** 对象 key 目录前缀（已登记，D60/D88）。 */
    String RECORD_PREFIX = "activity/record/";

    /**
     * @param objectKey 完整 key，须以 {@link #RECORD_PREFIX} 开头
     * @return 可访问 URL（优先 CDN）
     */
    String putFile(String objectKey, Path file, String contentType) throws IOException;
}
