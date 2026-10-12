package com.zhibo.admin.volc;

import com.volcengine.model.request.AssumeRoleRequest;
import com.volcengine.model.response.AssumeRoleResponse;
import com.volcengine.model.response.ResponseMetadata;
import com.volcengine.service.sts.ISTSService;
import com.volcengine.service.sts.impl.STSServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 封装火山 {@code AssumeRole}，便于单测 Mock。
 */
@Slf4j
@Component
public class VolcStsClient {

    private final VolcProperties properties;
    private final ISTSService stsService;

    @Autowired
    public VolcStsClient(VolcProperties properties) {
        this(properties, STSServiceImpl.getInstance());
    }

    /** 测试可注入假 STS。 */
    VolcStsClient(VolcProperties properties, ISTSService stsService) {
        this.properties = properties;
        this.stsService = stsService;
        if (!properties.credentialsMissing()) {
            stsService.setAccessKey(properties.getAccessKey());
            stsService.setSecretKey(properties.getSecretKey());
        }
    }

    public AssumeRoleResponse.Credentials assumeRole(String roleSessionName, int durationSeconds) {
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setRoleTrn(properties.getStsRoleTrn());
        request.setRoleSessionName(roleSessionName);
        request.setDurationSeconds(durationSeconds);

        AssumeRoleResponse response;
        try {
            response = stsService.assumeRole(request);
        } catch (Exception e) {
            throw new VolcStsException(502, "火山 STS 调用失败: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new VolcStsException(502, "火山 STS 响应为空");
        }

        ResponseMetadata metadata = response.getResponseMetadata();
        if (metadata != null && metadata.getError() != null) {
            ResponseMetadata.Error error = metadata.getError();
            String msg = error.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = "火山 STS 失败, code=" + error.getCode();
            }
            throw new VolcStsException(502, msg);
        }

        AssumeRoleResponse.ResultBean result = response.getResult();
        if (result == null || result.getCredentials() == null) {
            throw new VolcStsException(502, "火山 STS 未返回临时凭证");
        }
        return result.getCredentials();
    }
}
