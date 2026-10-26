package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.volcengine.model.response.AssumeRoleResponse;
import com.zhibo.live.viewer.dto.ViewerTosStsResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.live.volc.ViewerVolcStsClient;
import com.zhibo.live.volc.VolcTosProperties;
import org.junit.jupiter.api.Test;

class ViewerTosStsServiceTest {

    @Test
    void issueCredentials_mapsFields() {
        VolcTosProperties props = new VolcTosProperties();
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        props.setStsRoleTrn("trn:iam::1:role/sts");
        props.setTosRegion("cn-shanghai");
        props.setTosEndpoint("tos-cn-shanghai.volces.com");
        props.setTosBucket("qky-live-sucai-test");
        props.setTosCdnUrl("https://qklive-testcos.cjs99.com/");
        props.setStsDurationSeconds(1800);

        AssumeRoleResponse.Credentials cred = new AssumeRoleResponse.Credentials();
        cred.setAccessKeyId("tmp-ak");
        cred.setSecretAccessKey("tmp-sk");
        cred.setSessionToken("tmp-token");
        cred.setExpiredTime("2026-10-26T12:00:00Z");

        ViewerVolcStsClient client = mock(ViewerVolcStsClient.class);
        when(client.assumeRole(anyString(), anyInt())).thenReturn(cred);

        ViewerTosStsResponse resp = new ViewerTosStsService(props, client).issueCredentials();
        assertEquals("cn-shanghai", resp.getRegion());
        assertEquals("tos-cn-shanghai.volces.com", resp.getEndpoint());
        assertEquals("qky-live-sucai-test", resp.getBucketName());
        assertEquals("tmp-ak", resp.getAccessKeyId());
        assertEquals(ViewerTosUploader.KEY_PREFIX, resp.getKeyPrefix());
        assertEquals("https://qklive-testcos.cjs99.com", resp.getCdnUrl());
        assertEquals("2026-10-26T12:00:00Z", resp.getExpiredTime());
    }

    @Test
    void issueCredentials_rejectsMissingConfig() {
        VolcTosProperties props = new VolcTosProperties();
        ViewerVolcStsClient client = mock(ViewerVolcStsClient.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ViewerTosStsService(props, client).issueCredentials());
    }
}
