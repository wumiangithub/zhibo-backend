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

    /**
     * 用可访问的视频 URL 在媒资中心登记视频，返回 {@code video_id}。
     * 录播「素材视频」路径：该 id 可作为 create-demand 的 {@code record_id}。
     *
     * @param url      可公网拉取的视频地址（如本站 TOS URL）
     * @param filename 可选展示名；空则由实现省略该参
     * @return 云侧 video_id（字符串形式的数字）
     */
    String createVideoByUrl(String url, String filename);

    /**
     * 查询云侧媒资转码状态（防回调丢失时详情页主动拉取）。
     * 微吼 {@code GET /v3/webinars/videos/get-info} 的 {@code transcode_status}：
     * {@code 1} 成功 / {@code 2} 转码中 / {@code 3} 失败。
     *
     * @param videoId 云侧 video_id（本站 {@code material.vhall_video_id}）
     * @return 转码状态；查不到或未实现可抛业务异常，由调用方吞掉
     */
    Integer getVideoTranscodeStatus(String videoId);

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
