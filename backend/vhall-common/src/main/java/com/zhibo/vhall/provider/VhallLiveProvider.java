package com.zhibo.vhall.provider;

import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.OnlinePoint;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.PushStreamInfo;
import com.zhibo.liveprovider.dto.UpdateRoomCommand;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import com.zhibo.vhall.client.VhallClient;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallSignUtil;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 微吼 {@link LiveProvider} 实现：所有微吼 HTTP 只经本类，业务 Service 勿直接调 {@link VhallClient}。
 */
public class VhallLiveProvider implements LiveProvider {

    public static final String CODE = "vhall";

    static final String PATH_CREATE = "/v3/webinars/webinar/create";
    static final String PATH_CREATE_DEMAND = "/v3/webinars/webinar/create-demand";
    /** 媒资：URL 拉取上传；返回 video_id，可作 create-demand 的 record_id */
    static final String PATH_CREATE_BY_URL = "/v3/webinars/videos/create-by-url";
    /** 媒资详情：含 transcode_status（1 成功 / 2 转码中 / 3 失败） */
    static final String PATH_VIDEO_INFO = "/v3/webinars/videos/get-info";
    static final String PATH_INFO = "/v3/webinars/webinar/info";
    static final String PATH_EDIT = "/v3/webinars/webinar/edit";
    /** 开始直播。三方推流必须 {@code start_type=4}，否则微吼侧会黑屏 */
    static final String PATH_START = "/v3/webinars/live/start";
    /** 推流方式：4 = 三方推拉流（OBS）。1 是浏览器主持人开播，不要用 */
    static final String START_TYPE_THIRD_PARTY = "4";
    static final String PATH_END = "/v3/webinars/live/end";
    static final String PATH_DELETE = "/v3/webinars/webinar/delete";
    static final String PATH_ROLE_URL = "/v3/webinars/live/get-role-url";
    static final String PATH_GET_TOKEN = "/v3/users/open-user/get-token";
    static final String PATH_ONLINE_NOW = "/v3/data-center/webinar/current-online-number";
    static final String PATH_ONLINE_TREND = "/v3/data-center/report/online";
    /** 某场活动的回放列表；{@code id} 即 create-demand 的 record_id */
    static final String PATH_RECORD_LIST = "/v3/webinars/record/get-list";
    /** 聊天审核页（中控台 iframe） */
    static final String PATH_CHAT_FILTER = "/v3/webinars/manage/chat-filter";
    /** 当前活动的 rtmp 推流地址；业务参数仅 webinar_id */
    static final String PATH_STREAM_PUSH = "/v3/webinars/live/get-stream-push-address";
    /** 当前活动是否有推流；业务参数仅 webinar_id；data.status：0 无 / 1 有 */
    static final String PATH_STREAM_STATUS = "/v3/webinars/stream/get-stream-status";
    static final String EMBED_BASE = "https://e.vhall.com/v3/embed/live/detail/";
    static final String SDK_SCRIPT_URL = "https://cnstatic01.e.vhall.com/jssdk/dist/3.9.1/VhallSDK.js";
    static final int ROLE_HOST = 1;

    private static final Pattern GUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final String EMAIL_DOMAIN = "@zhibo.local";
    private static final String DEFAULT_NICKNAME = "观众";
    private static final int NICKNAME_MAX = 50;
    /** 中控台评论审核默认身份（与助理昵称一致） */
    static final String DEFAULT_CHAT_FILTER_EMAIL = "assistant@zhibo.local";
    static final String DEFAULT_CHAT_FILTER_NAME = "助理";
    private static final int CHAT_FILTER_NAME_MAX = 30;

    private final VhallClient vhallClient;
    private final VhallProperties properties;
    private final Clock clock;

    public VhallLiveProvider(VhallClient vhallClient, VhallProperties properties, Clock clock) {
        this.vhallClient = vhallClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public CreateRoomResult create(CreateRoomCommand command) {
        Integer type = command.type();
        if (type == null || type < 1 || type > 4) {
            throw new IllegalArgumentException("直播类型无效");
        }

        String title = requireTitle(command.title());
        String startTime = toVhallMinuteTime(command.startTime(), "开始时间");
        String introduction = trimToNull(command.introduction());
        if (introduction != null && introduction.length() > 256) {
            throw new IllegalArgumentException("简介不能超过256个字");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        String path;
        if (type == 4) {
            path = PATH_CREATE_DEMAND;
            String recordId = command.recordId() == null ? "" : command.recordId().trim();
            if (recordId.isEmpty()) {
                throw new IllegalArgumentException("录播须填写 recordId");
            }
            if (introduction == null) {
                throw new IllegalArgumentException("录播须填写简介");
            }
            params.put("subject", title);
            params.put("introduction", introduction);
            params.put("record_id", recordId);
            params.put("webinar_curr_num", 0);
            if (command.coverUrl() != null && !command.coverUrl().isBlank()) {
                params.put("img_url", command.coverUrl().trim());
            }
            params.put("is_timing", 1);
            params.put("start_time", startTime);
            putNoDelayIfPresent(params, command.ultraLowLatency());
        } else {
            path = PATH_CREATE;
            params.put("subject", title);
            params.put("start_time", startTime);
            params.put("webinar_type", type);
            if (introduction != null) {
                params.put("introduction", introduction);
            }
            if (command.coverUrl() != null && !command.coverUrl().isBlank()) {
                params.put("img_url", command.coverUrl().trim());
            }
            putNoDelayIfPresent(params, command.ultraLowLatency());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(path, params, Map.class);
        String roomId = asString(data.get("webinar_id"));
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalStateException("微吼未返回 webinar_id");
        }
        return new CreateRoomResult(roomId);
    }

    @Override
    public String createVideoByUrl(String url, String filename) {
        String videoUrl = url == null ? "" : url.trim();
        if (videoUrl.isEmpty()) {
            throw new IllegalArgumentException("视频地址不能为空");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("url", videoUrl);
        String name = trimToNull(filename);
        if (name != null) {
            params.put("filename", name);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_CREATE_BY_URL, params, Map.class);
        String videoId = asString(data == null ? null : data.get("video_id"));
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalStateException("微吼未返回 video_id");
        }
        return videoId.trim();
    }

    @Override
    public Integer getVideoTranscodeStatus(String videoId) {
        String id = videoId == null ? "" : videoId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("video_id 不能为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.getForData(
                PATH_VIDEO_INFO, Map.of("video_id", id), Map.class);
        Integer status = data == null ? null : asInteger(data.get("transcode_status"));
        if (status == null) {
            throw new IllegalStateException("微吼未返回 transcode_status");
        }
        return status;
    }

    @Override
    public String buildHostUrl(String providerRoomId) {
        requireRoomId(providerRoomId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", providerRoomId);
        params.put("type", ROLE_HOST);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.getForData(PATH_ROLE_URL, params, Map.class);
        String hostUrl = asString(data.get("page_url"));
        if (hostUrl == null || hostUrl.isBlank()) {
            throw new IllegalStateException("微吼未返回主持人地址 page_url");
        }
        return hostUrl;
    }

    @Override
    public ProviderRoomSnapshot fetchRoom(String providerRoomId) {
        requireRoomId(providerRoomId);
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = vhallClient.postForData(
                PATH_INFO, Map.of("webinar_id", providerRoomId), Map.class);
        return mapSnapshot(raw);
    }

    @Override
    public void update(String providerRoomId, UpdateRoomCommand command) {
        requireRoomId(providerRoomId);
        if (command == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        boolean hasTitle = command.title() != null && !command.title().isBlank();
        boolean hasStart = command.startTime() != null && !command.startTime().isBlank();
        boolean hasIntro = command.introduction() != null;
        boolean hasCover = command.coverUrl() != null;
        if (!hasTitle && !hasStart && !hasIntro && !hasCover) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", providerRoomId);
        if (hasTitle) {
            params.put("subject", command.title().trim());
        }
        if (hasStart) {
            params.put("start_time", toVhallMinuteTime(command.startTime(), "开始时间"));
        }
        if (hasIntro) {
            params.put("introduction", command.introduction().trim());
        }
        if (hasCover) {
            String cover = command.coverUrl().trim();
            // 空串表示清空封面时仍传空，让微吼侧可覆盖
            params.put("img_url", cover);
        }
        vhallClient.postForData(PATH_EDIT, params, Map.class);
    }

    @Override
    public int start(String providerRoomId) {
        requireRoomId(providerRoomId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", providerRoomId);
        params.put("start_type", START_TYPE_THIRD_PARTY);
        vhallClient.postForData(PATH_START, params, Map.class);
        ProviderRoomSnapshot info = fetchRoom(providerRoomId);
        return info.state() != null ? info.state() : 1;
    }

    @Override
    public int end(String providerRoomId) {
        requireRoomId(providerRoomId);
        vhallClient.postForData(PATH_END, Map.of("webinar_id", providerRoomId), Map.class);
        ProviderRoomSnapshot info = fetchRoom(providerRoomId);
        return info.state() != null ? info.state() : 3;
    }

    @Override
    public void delete(String providerRoomId) {
        requireRoomId(providerRoomId);
        vhallClient.postForData(PATH_DELETE, Map.of("webinar_ids", providerRoomId), Map.class);
    }

    @Override
    public int currentOnline(String providerRoomId) {
        requireRoomId(providerRoomId);
        @SuppressWarnings("unchecked")
        Map<String, Object> online = vhallClient.postForData(
                PATH_ONLINE_NOW, Map.of("webinar_id", providerRoomId), Map.class);
        Integer num = asInteger(online.get("num"));
        return num == null ? 0 : num;
    }

    /**
     * 微吼批量用户在线：真实开放接口尚未对接。
     * 当前 mock——传入的每个 userId 都返回 {@code true}，不打 HTTP。
     */
    @Override
    public Map<String, Boolean> batchUserOnline(String providerRoomId, List<String> userIds) {
        requireRoomId(providerRoomId);
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        for (String raw : userIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            result.put(raw.trim(), Boolean.TRUE);
        }
        return result;
    }

    @Override
    public List<OnlinePoint> onlineTrend(String providerRoomId, String startTime, String endTime) {
        requireRoomId(providerRoomId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", providerRoomId);
        params.put("start_time", startTime);
        params.put("end_time", endTime);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_ONLINE_TREND, params, Map.class);

        List<OnlinePoint> points = new ArrayList<>();
        Object rawList = data.get("list");
        if (rawList instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                points.add(new OnlinePoint(asString(map.get("time")), asInteger(map.get("total"))));
            }
        }
        return points;
    }

    @Override
    public String buildAdminEmbedUrl(String providerRoomId) {
        requireRoomId(providerRoomId);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_GET_TOKEN, Map.of(), Map.class);
        String token = asString(data.get("token"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("微吼未返回授权 token");
        }
        return UriComponentsBuilder
                .fromUriString(EMBED_BASE + providerRoomId)
                .queryParam("token", token)
                .encode()
                .build()
                .toUriString();
    }

    @Override
    public WatchSdkBundle buildWatchSdk(WatchSdkCommand command) {
        requireRoomId(command.providerRoomId());
        String appSecret = properties.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("缺少微吼 SecretKey，无法签名 SDK 载荷");
        }

        String guestId = resolveGuestId(command.guestId());
        String nickname = resolveNickname(command.nickname());
        String email = guestId + EMAIL_DOMAIN;
        String webinarId = command.providerRoomId();

        ProviderRoomSnapshot info = fetchRoom(webinarId);
        String baseEmbed = info.fullEmbedShareLink();
        if (baseEmbed == null || baseEmbed.isBlank()) {
            throw new IllegalStateException("活动缺少全屏嵌入地址 full_embed_share_link");
        }
        String embedUrl = buildEmbedUrl(baseEmbed, guestId, nickname);

        String signedAt = Long.toString(clock.instant().getEpochSecond());
        Map<String, Object> signParams = new LinkedHashMap<>();
        signParams.put("app_key", properties.getAppKey());
        signParams.put("signedat", signedAt);
        signParams.put("webinar_id", webinarId);
        signParams.put("email", email);
        signParams.put("username", nickname);
        String sign = VhallSignUtil.sign(appSecret, signParams);

        WatchSdkBundle.SdkInit sdk = new WatchSdkBundle.SdkInit();
        sdk.setScriptUrl(SDK_SCRIPT_URL);
        sdk.setJqueryUrl(null);
        sdk.setAccount(guestId);
        sdk.setEmail(email);
        sdk.setUsername(nickname);
        sdk.setWebinarId(webinarId);
        sdk.setAppKey(properties.getAppKey());
        sdk.setSignedAt(signedAt);
        sdk.setSignType(0);
        sdk.setSign(sign);

        WatchSdkBundle bundle = new WatchSdkBundle();
        bundle.setTitle(info.title());
        bundle.setState(info.state());
        bundle.setType(info.type());
        bundle.setStartTime(info.startTime());
        bundle.setEndTime(info.endTime());
        bundle.setEmbedUrl(embedUrl);
        bundle.setSdk(sdk);
        return bundle;
    }

    @Override
    public ProviderRecordList listRecords(String providerRoomId) {
        requireRoomId(providerRoomId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", providerRoomId);
        params.put("pos", 0);
        params.put("limit", 100);
        params.put("source", -1);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_RECORD_LIST, params, Map.class);
        List<ProviderRecordItem> list = mapRecordList(data == null ? null : data.get("list"));
        long total = asLong(data == null ? null : data.get("total"));
        if (total <= 0) {
            total = list.size();
        }
        return new ProviderRecordList(total, list);
    }

    @Override
    public PushStreamInfo getPushStream(String providerRoomId) {
        requireRoomId(providerRoomId);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.getForData(
                PATH_STREAM_PUSH, Map.of("webinar_id", providerRoomId), Map.class);
        if (data == null) {
            throw new IllegalStateException("微吼未返回推流地址");
        }
        String streamAddress = asString(data.get("stream_address"));
        String streamCode = asString(data.get("stream_code"));
        if (streamAddress == null || streamAddress.isBlank()
                || streamCode == null || streamCode.isBlank()) {
            throw new IllegalStateException("微吼未返回推流地址");
        }
        return new PushStreamInfo(streamAddress.trim(), streamCode.trim(), trimToNull(asString(data.get("expire_time"))));
    }

    @Override
    public int getStreamStatus(String providerRoomId) {
        requireRoomId(providerRoomId);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.getForData(
                PATH_STREAM_STATUS, Map.of("webinar_id", providerRoomId), Map.class);
        Integer status = data == null ? null : asInteger(data.get("status"));
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalStateException("微吼未返回推流状态");
        }
        return status;
    }

    @Override
    public String buildChatFilterUrl(String providerRoomId, String email, String name) {
        requireRoomId(providerRoomId);
        String resolvedEmail = resolveChatFilterEmail(email);
        String resolvedName = resolveChatFilterName(name);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("webinar_id", providerRoomId);
        params.put("email", resolvedEmail);
        params.put("name", resolvedName);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postForData(PATH_CHAT_FILTER, params, Map.class);
        String pageUrl = asString(data == null ? null : data.get("page_url"));
        if (pageUrl == null || pageUrl.isBlank()) {
            throw new IllegalStateException("微吼未返回聊天审核页 page_url");
        }
        return pageUrl;
    }

    static String resolveChatFilterEmail(String email) {
        if (email == null || email.isBlank()) {
            return DEFAULT_CHAT_FILTER_EMAIL;
        }
        return email.trim();
    }

    static String resolveChatFilterName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_CHAT_FILTER_NAME;
        }
        String trimmed = name.trim();
        if (trimmed.length() > CHAT_FILTER_NAME_MAX) {
            throw new IllegalArgumentException("name 不能超过 " + CHAT_FILTER_NAME_MAX + " 字");
        }
        return trimmed;
    }

    private static List<ProviderRecordItem> mapRecordList(Object rawList) {
        List<ProviderRecordItem> result = new ArrayList<>();
        if (!(rawList instanceof List<?> items)) {
            return result;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) map;
            String id = asString(raw.get("id"));
            if (id == null || id.isBlank()) {
                continue;
            }
            result.add(new ProviderRecordItem(
                    id,
                    asString(raw.get("name")),
                    asString(raw.get("duration")),
                    asString(raw.get("created_at")),
                    asString(raw.get("img_url"))
            ));
        }
        return result;
    }

    private static ProviderRoomSnapshot mapSnapshot(Map<String, Object> raw) {
        return new ProviderRoomSnapshot(
                asString(raw.get("subject")),
                asInteger(raw.get("webinar_state")),
                asInteger(raw.get("webinar_type")),
                asInteger(raw.get("pv")),
                asString(raw.get("full_embed_share_link")),
                asString(raw.get("share_link")),
                asString(raw.get("start_time")),
                resolveEndTime(raw.get("end_time")),
                asString(raw.get("introduction")),
                asInteger(raw.get("verify")),
                asString(raw.get("img_url"))
        );
    }

    public static String toVhallMinuteTime(String startTime, String field) {
        if (startTime == null || startTime.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        String value = startTime.trim();
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return value.substring(0, 16);
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
            return value;
        }
        throw new IllegalArgumentException(field + "格式须为 yyyy-MM-dd HH:mm:ss");
    }

    public static String resolveGuestId(String guestId) {
        if (guestId == null || guestId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String trimmed = guestId.trim();
        if (!GUEST_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("guestId 只能包含字母和数字");
        }
        return trimmed;
    }

    public static String resolveNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > NICKNAME_MAX) {
            throw new IllegalArgumentException("nickname 不能超过 " + NICKNAME_MAX + " 字");
        }
        return trimmed;
    }

    public static String buildEmbedUrl(String baseEmbed, String guestId, String nickname) {
        return UriComponentsBuilder.fromUriString(baseEmbed)
                .replaceQueryParam("email", guestId + EMAIL_DOMAIN)
                .replaceQueryParam("nickname", nickname)
                .encode()
                .build()
                .toUriString();
    }

    static String resolveEndTime(Object rawEndTime) {
        if (rawEndTime == null) {
            return null;
        }
        String endTime = rawEndTime.toString().trim();
        if (endTime.isEmpty() || endTime.startsWith("0000")) {
            return null;
        }
        return endTime;
    }

    private static void putNoDelayIfPresent(Map<String, Object> params, Boolean ultraLowLatency) {
        if (ultraLowLatency == null) {
            return;
        }
        // 微吼 create / create-demand：0 普通 / 1 无延迟
        params.put("no_delay_webinar", Boolean.TRUE.equals(ultraLowLatency) ? 1 : 0);
    }

    private static String requireTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("标题不能超过64个字");
        }
        return value;
    }

    private static void requireRoomId(String providerRoomId) {
        if (providerRoomId == null || providerRoomId.isBlank()) {
            throw new IllegalArgumentException("活动云侧房间号无效");
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
