package com.zhibo.liveprovider;

import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.OnlinePoint;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.PushStreamInfo;
import com.zhibo.liveprovider.dto.UpdateRoomCommand;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;

import java.util.List;
import java.util.Map;

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

    /**
     * 第三方推流开播（OBS）。浏览器主持人开播不走本方法。
     *
     * @return 开播后的新 state（与 {@link #end} 一样，以云侧 info 的 webinar_state 为准）
     */
    int start(String providerRoomId);

    /** @return 结束后的新 state */
    int end(String providerRoomId);

    void delete(String providerRoomId);

    int currentOnline(String providerRoomId);

    List<OnlinePoint> onlineTrend(String providerRoomId, String startTime, String endTime);

    /**
     * 批量查询指定用户当前是否在直播间在线。
     * 返回 map 的 key 为请求里的 userId；未出现的 id 视为未知，调用方勿当成在线。
     * 微吼真实接口未就绪时实现可先 mock（例如一律返回在线）。
     */
    Map<String, Boolean> batchUserOnline(String providerRoomId, List<String> userIds);

    String buildAdminEmbedUrl(String providerRoomId);

    WatchSdkBundle buildWatchSdk(WatchSdkCommand command);

    /**
     * 某场直播下的回放/录制列表；{@code list[].id} 即 create-demand 的 {@code record_id}。
     */
    ProviderRecordList listRecords(String providerRoomId);

    /**
     * 聊天审核页 URL（供中控台 iframe）。
     *
     * @param email 审核人标识；空则由实现填默认
     * @param name  审核人昵称；空则由实现填默认
     * @return 可嵌入的审核页地址
     */
    String buildChatFilterUrl(String providerRoomId, String email, String name);

    /**
     * 第三方 rtmp 推到本场直播间的服务器与串流密钥。
     * srt、口令开播不走本方法。
     */
    PushStreamInfo getPushStream(String providerRoomId);

    /**
     * 第三方推流是否已到达本场直播间。
     *
     * @return {@code 0} 无流，{@code 1} 有流
     */
    int getStreamStatus(String providerRoomId);
}
