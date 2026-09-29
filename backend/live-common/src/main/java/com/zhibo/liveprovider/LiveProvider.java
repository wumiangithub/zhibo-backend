package com.zhibo.liveprovider;

import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.OnlinePoint;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.UpdateRoomCommand;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;

import java.util.List;

/**
 * 直播服务商 SPI：各平台（微吼等）实现此接口并注册为 Spring Bean。
 */
public interface LiveProvider {

    /** 服务商代码，如 {@code "vhall"}。 */
    String code();

    CreateRoomResult create(CreateRoomCommand command);

    String buildHostUrl(String providerRoomId);

    ProviderRoomSnapshot fetchRoom(String providerRoomId);

    void update(String providerRoomId, UpdateRoomCommand command);

    /** @return 结束后的新 state */
    int end(String providerRoomId);

    void delete(String providerRoomId);

    int currentOnline(String providerRoomId);

    List<OnlinePoint> onlineTrend(String providerRoomId, String startTime, String endTime);

    String buildAdminEmbedUrl(String providerRoomId);

    WatchSdkBundle buildWatchSdk(WatchSdkCommand command);

    /**
     * 某场直播下的回放/录制列表；{@code list[].id} 即 create-demand 的 {@code record_id}。
     */
    ProviderRecordList listRecords(String providerRoomId);
}
