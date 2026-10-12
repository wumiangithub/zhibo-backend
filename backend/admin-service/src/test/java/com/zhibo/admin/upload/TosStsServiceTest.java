package com.zhibo.admin.upload;

import com.volcengine.model.response.AssumeRoleResponse;
import com.zhibo.admin.upload.dto.TosStsResponse;
import com.zhibo.admin.volc.VolcProperties;
import com.zhibo.admin.volc.VolcStsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TosStsServiceTest {

    @Mock
    private VolcStsClient volcStsClient;

    private VolcProperties properties;
    private TosStsService service;

    @BeforeEach
    void setUp() {
        properties = new VolcProperties();
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setStsRoleTrn("trn:iam::2116729154:role/sts");
        properties.setTosRegion("cn-shanghai");
        properties.setTosEndpoint("tos-cn-shanghai.volces.com");
        properties.setTosBucket("qky-live-sucai-test");
        properties.setStsDurationSeconds(3600);
        service = new TosStsService(properties, volcStsClient);
    }

    @Test
    void issueCredentials_mapsStsResult() {
        AssumeRoleResponse.Credentials credentials = new AssumeRoleResponse.Credentials();
        credentials.setAccessKeyId("tmp-ak");
        credentials.setSecretAccessKey("tmp-sk");
        credentials.setSessionToken("tmp-token");
        credentials.setExpiredTime("2026-10-10T14:00:00+08:00");
        when(volcStsClient.assumeRole(anyString(), anyInt())).thenReturn(credentials);

        TosStsResponse response = service.issueCredentials();

        assertEquals("cn-shanghai", response.getRegion());
        assertEquals("tos-cn-shanghai.volces.com", response.getEndpoint());
        assertEquals("qky-live-sucai-test", response.getBucketName());
        assertEquals("tmp-ak", response.getAccessKeyId());
        assertEquals("tmp-sk", response.getSecretAccessKey());
        assertEquals("tmp-token", response.getSessionToken());
        assertEquals("2026-10-10T06:00:00Z", response.getExpiredTime());
    }

    @Test
    void issueCredentials_missingConfig_throws400Style() {
        properties.setAccessKey("");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.issueCredentials());
        assertEquals("未配置火山 TOS 凭证", ex.getMessage());
    }

    @Test
    void toUtcIso_parsesInstantAlreadyUtc() {
        assertEquals("2026-10-10T06:00:00Z", TosStsService.toUtcIso("2026-10-10T06:00:00Z", 3600));
    }

    @Test
    void clampDuration_bounds() {
        assertEquals(900, TosStsService.clampDuration(100));
        assertEquals(3600, TosStsService.clampDuration(99999));
        assertEquals(1800, TosStsService.clampDuration(1800));
    }

    @Test
    void toUtcIso_fallbackWhenUnparseable() {
        String iso = TosStsService.toUtcIso("not-a-date", 3600);
        assertTrue(iso.endsWith("Z"));
    }
}
