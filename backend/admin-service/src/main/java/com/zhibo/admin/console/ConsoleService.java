package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityReady;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.PlaybackRules;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleQuestionHistoryItem;
import com.zhibo.admin.console.dto.ConsoleQuestionHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleSdkPayload;
import com.zhibo.admin.console.dto.ConsoleSdkSignResponse;
import com.zhibo.admin.console.dto.ConsoleSummaryResponse;
import com.zhibo.common.UuidIds;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import com.zhibo.presence.PresenceUserIds;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 中控台页壳 + 员工进房签名（D110）。鉴权员工 id 由 Controller 从 admin JWT 传入。
 * 进房 email 为 {@code z{id}@qkzb.com}（与角色 {@code e{id}} 错开）。
 */
@Service
@RequiredArgsConstructor
public class ConsoleService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int NICKNAME_MAX = 30;
    private static final String DEFAULT_NICKNAME = "助理";
    /** 与观看 question-history 同上限；单场提问体量通常远小于聊天 */
    static final int MAX_QUESTION_LIST = 500;

    private final ActivityRepository activityRepository;
    private final ActivityMemberRepository memberRepository;
    private final SysUserAccountRepository userRepository;
    private final RoomCommentRepository commentRepository;
    private final LiveProviderRegistry providerRegistry;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsoleSummaryResponse summary(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
        boolean playbackEnabled = !Boolean.FALSE.equals(entity.getPlaybackEnabled());

        ConsoleSummaryResponse response = new ConsoleSummaryResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setTitle(entity.getTitle());
        response.setState(entity.getState());
        response.setType(entity.getType());
        response.setStartTime(entity.getStartTime());
        response.setEndTime(entity.getEndTime());
        response.setPlaybackEnabled(playbackEnabled);
        response.setPlaybackValidityType(
                entity.getPlaybackValidityType() != null ? entity.getPlaybackValidityType() : "forever");
        response.setPlaybackValidityDays(entity.getPlaybackValidityDays());
        response.setPlaybackExpired(PlaybackRules.isExpired(entity, now));
        response.setPlaybackSpeedAllow(!Boolean.FALSE.equals(entity.getPlaybackSpeedAllow()));
        response.setPlaybackFastForwardAllow(!Boolean.FALSE.equals(entity.getPlaybackFastForwardAllow()));
        return response;
    }

    /**
     * 中控台讨论区问答兜底：读本站 {@code room_comment} 提问行。
     * 前端先走微吼 {@code vhall_get_live_history_question_msg}；空/失败再调本接口。
     * 形状对齐观看 {@code GET /watch/question-history/{id}}（无售卖门禁）。
     */
    @Transactional(readOnly = true)
    public ConsoleQuestionHistoryResponse questionHistory(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        List<RoomCommentEntity> rows =
                commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        entity.getId(), RoomCommentType.QUESTION);

        ConsoleQuestionHistoryResponse response = new ConsoleQuestionHistoryResponse();
        response.setTotal(rows.size());
        int limit = Math.min(rows.size(), MAX_QUESTION_LIST);
        for (int i = 0; i < limit; i++) {
            ConsoleQuestionHistoryItem item = toQuestionItem(rows.get(i));
            if (item.getMsg() == null || item.getMsg().isBlank()) {
                continue;
            }
            if (item.getSenderName() == null || item.getSenderName().isBlank()) {
                continue;
            }
            response.getList().add(item);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public ConsoleSdkSignResponse sdkSign(String activityId, String nicknameQuery, long employeeId) {
        if (employeeId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        ActivityEntity entity = requireActivity(activityId);
        requireReadyForConsole(entity);
        String roomId = trimToNull(entity.getProviderRoomId());
        if (roomId == null) {
            throw new IllegalArgumentException("直播间尚未就绪");
        }

        SysUserAccountEntity employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
        ActivityMemberEntity member = memberRepository
                .findById(new ActivityMemberId(entity.getId(), employeeId))
                .orElse(null);

        String nickname = resolveConsoleNickname(nicknameQuery, member, employee);
        String head = resolveConsoleHead(member, employee);
        // 中控台用 z{id}，与主持/嘉宾/助理 e{id} 错开（微吼同邮箱会 20005）
        String email = PresenceUserIds.consoleEmail(employeeId);

        WatchSdkBundle bundle = providerRegistry.require(entity.getProvider())
                .buildWatchSdk(new WatchSdkCommand(roomId, nickname, employeeId, head, email));

        ConsoleSdkSignResponse response = new ConsoleSdkSignResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setEmployeeId(employeeId);
        String resolvedNick = bundle.getSdk() != null && bundle.getSdk().getUsername() != null
                ? bundle.getSdk().getUsername()
                : nickname;
        response.setNickname(resolvedNick);
        response.setSdk(toPayload(bundle.getSdk()));
        return response;
    }

    static String resolveConsoleNickname(
            String query, ActivityMemberEntity member, SysUserAccountEntity employee) {
        String fromQuery = trimToNull(query);
        if (fromQuery != null) {
            return truncateNick(fromQuery);
        }
        if (member != null) {
            String fromMember = trimToNull(member.getNickname());
            if (fromMember != null) {
                return truncateNick(fromMember);
            }
        }
        String fromEmployee = employee == null ? null : trimToNull(employee.getUserName());
        if (fromEmployee != null) {
            return truncateNick(fromEmployee);
        }
        return DEFAULT_NICKNAME;
    }

    static String resolveConsoleHead(ActivityMemberEntity member, SysUserAccountEntity employee) {
        if (member != null) {
            String fromMember = trimToNull(member.getAvatarUrl());
            if (fromMember != null) {
                return fromMember;
            }
        }
        if (employee != null) {
            String head = trimToNull(employee.getHeadimgurl());
            if (head != null) {
                return head;
            }
            String avatar = trimToNull(employee.getUserAvatar());
            if (avatar != null) {
                return avatar;
            }
        }
        return "";
    }

    private static String truncateNick(String nick) {
        if (nick.length() <= NICKNAME_MAX) {
            return nick;
        }
        return nick.substring(0, NICKNAME_MAX);
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ActivityEntity requireActivity(String id) {
        String uuid = UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
        ActivityEntity entity = activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (entity.getDeletedTime() != null) {
            throw new IllegalArgumentException("直播不存在");
        }
        return entity;
    }

    /** 录播未就绪时禁止挂 SDK（与观看 requireReadyForWatch 同口径，无售卖门禁）。 */
    private static void requireReadyForConsole(ActivityEntity entity) {
        Integer ready = entity.getReady();
        if (ActivityReady.isOk(ready)) {
            return;
        }
        if (ready != null && ready == ActivityReady.FAIL) {
            throw new IllegalArgumentException("视频转码失败，暂无法进房");
        }
        throw new IllegalArgumentException("视频转码中，暂无法进房");
    }

    private static ConsoleSdkPayload toPayload(WatchSdkBundle.SdkInit sdk) {
        if (sdk == null) {
            return null;
        }
        ConsoleSdkPayload payload = new ConsoleSdkPayload();
        payload.setAccount(sdk.getAccount());
        payload.setEmail(sdk.getEmail());
        payload.setUsername(sdk.getUsername());
        payload.setHead(sdk.getHead() == null ? "" : sdk.getHead());
        payload.setWebinarId(sdk.getWebinarId());
        payload.setAppKey(sdk.getAppKey());
        payload.setSignedAt(sdk.getSignedAt());
        if (sdk.getSignType() != null) {
            payload.setSignType(sdk.getSignType());
        }
        payload.setSign(sdk.getSign());
        return payload;
    }

    private static ConsoleQuestionHistoryItem toQuestionItem(RoomCommentEntity c) {
        ConsoleQuestionHistoryItem item = new ConsoleQuestionHistoryItem();
        String questionId = blankToNull(c.getQuestionId());
        item.setQuestionId(questionId);
        if (c.getViewerId() != null && c.getViewerId() > 0) {
            item.setSenderId(Long.toString(c.getViewerId()));
        }
        String nick = blankToNull(c.getNickname());
        item.setSenderName(nick != null ? nick : "观众");
        item.setMsg(c.getContent() == null ? "" : c.getContent().trim());
        item.setMsgId(blankToNull(c.getMsgId()));
        item.setRoleName(c.getRoleName());
        item.setTimestamp(toEpochMillis(c.getCommentTime()));
        String answer = blankToNull(c.getAnswerContent());
        if (answer != null) {
            item.setAnswerContent(answer);
            item.setAnswerNickname(blankToNull(c.getAnswerNickname()));
            item.setAnswerRoleName(c.getAnswerRoleName());
        }
        return item;
    }

    private static Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(SHANGHAI).toInstant().toEpochMilli();
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
