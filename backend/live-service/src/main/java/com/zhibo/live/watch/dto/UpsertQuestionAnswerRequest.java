package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 观看页提问回复 upsert（D107）。按云提问 id（questionId）对齐同一 {@code room_comment} 行。
 */
@Data
public class UpsertQuestionAnswerRequest {

    /** 云提问 id（如 question_answer_commit data.id）。 */
    @NotBlank(message = "questionId 不能为空")
    @Size(max = 64, message = "questionId 最长 64")
    private String questionId;

    /**
     * 云消息 id（如 data.msg_id）；可选。行上 msg 为空时补写一次。
     */
    @Size(max = 64)
    private String msgId;

    /** 提问正文；行不存在时必填，用于补建提问行。 */
    @Size(max = 500, message = "content 最长 500")
    private String content;

    /** 提问人昵称快照；补建行时可选。 */
    @Size(max = 64)
    private String nickname;

    /** 提问人微吼角色数字；补建行时可选。 */
    private Integer roleName;

    /** 回复正文。 */
    @NotBlank(message = "answerContent 不能为空")
    @Size(max = 500, message = "answerContent 最长 500")
    private String answerContent;

    @Size(max = 64)
    private String answerNickname;

    /** 回复人微吼角色数字（1 主持 / 3 助理 / 4 嘉宾等）。 */
    private Integer answerRoleName;
}
