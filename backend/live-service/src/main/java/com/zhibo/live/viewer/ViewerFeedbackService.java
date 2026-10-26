package com.zhibo.live.viewer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.live.viewer.dto.ViewerFeedbackDetailResponse;
import com.zhibo.live.viewer.dto.ViewerFeedbackImageItem;
import com.zhibo.live.viewer.dto.ViewerFeedbackListItem;
import com.zhibo.live.viewer.dto.ViewerFeedbackListResponse;
import com.zhibo.live.viewer.dto.ViewerFeedbackSubmitRequest;
import com.zhibo.live.viewer.dto.ViewerFeedbackSubmitResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerFeedbackCategory;
import com.zhibo.viewer.ViewerFeedbackEntity;
import com.zhibo.viewer.ViewerFeedbackRepository;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观众意见反馈：提交 / 我的列表 / 详情。 */
@Service
public class ViewerFeedbackService {

    static final int CONTENT_MAX = 200;
    static final int IMAGES_MAX = 4;
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    private static final String DISABLED = "账号已停用";
    private static final Pattern CN_MOBILE = Pattern.compile("^1\\d{10}$");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<Map<String, String>>> IMAGES_TYPE =
            new TypeReference<>() {};

    private final ViewerRepository viewerRepository;
    private final ViewerFeedbackRepository feedbackRepository;
    private final ViewerTosUploader tosUploader;
    private final ObjectMapper objectMapper;

    public ViewerFeedbackService(
            ViewerRepository viewerRepository,
            ViewerFeedbackRepository feedbackRepository,
            ViewerTosUploader tosUploader,
            ObjectMapper objectMapper) {
        this.viewerRepository = viewerRepository;
        this.feedbackRepository = feedbackRepository;
        this.tosUploader = tosUploader;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ViewerFeedbackSubmitResponse submit(long viewerId, ViewerFeedbackSubmitRequest request) {
        requireViewer(viewerId);
        if (request == null) {
            throw new ViewerAuthException(400, "请填写反馈内容");
        }
        if (request.getCategory() == null || !ViewerFeedbackCategory.isValid(request.getCategory())) {
            throw new ViewerAuthException(400, "请选择意见类型");
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new ViewerAuthException(400, "请描述你的问题或建议");
        }
        if (content.length() > CONTENT_MAX) {
            throw new ViewerAuthException(400, "描述最多" + CONTENT_MAX + "字");
        }
        String phone = normalizePhone(request.getContactPhone());
        List<ViewerFeedbackImageItem> images = normalizeImages(request.getImages());

        ViewerFeedbackEntity row = new ViewerFeedbackEntity();
        row.setViewerId(viewerId);
        row.setCategory(request.getCategory());
        row.setContent(content);
        row.setContactPhone(phone);
        row.setImagesJson(toImagesJson(images));
        feedbackRepository.save(row);
        return new ViewerFeedbackSubmitResponse(row.getId());
    }

    @Transactional(readOnly = true)
    public ViewerFeedbackListResponse list(long viewerId, Integer page, Integer pageSize) {
        requireViewer(viewerId);
        int p = page == null ? DEFAULT_PAGE : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (p < 1) {
            throw new ViewerAuthException(400, "页码无效");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ViewerAuthException(400, "每页条数无效");
        }
        Page<ViewerFeedbackEntity> result =
                feedbackRepository.findByViewerIdOrderByCreatedTimeDesc(
                        viewerId, PageRequest.of(p - 1, size));
        List<ViewerFeedbackListItem> list = new ArrayList<>(result.getContent().size());
        for (ViewerFeedbackEntity e : result.getContent()) {
            list.add(toListItem(e));
        }
        return new ViewerFeedbackListResponse(result.getTotalElements(), p, size, list);
    }

    @Transactional(readOnly = true)
    public ViewerFeedbackDetailResponse detail(long viewerId, long id) {
        requireViewer(viewerId);
        ViewerFeedbackEntity row =
                feedbackRepository
                        .findByIdAndViewerId(id, viewerId)
                        .orElseThrow(() -> new ViewerAuthException(404, "反馈不存在"));
        return toDetail(row);
    }

    private ViewerEntity requireViewer(long viewerId) {
        ViewerEntity viewer = viewerRepository.findById(viewerId).orElse(null);
        if (viewer == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        if (!ViewerStatus.isNormal(viewer.getStatus())) {
            throw new ViewerAuthException(403, DISABLED);
        }
        return viewer;
    }

    private static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String phone = raw.trim();
        if (phone.isEmpty()) {
            return null;
        }
        if (!CN_MOBILE.matcher(phone).matches()) {
            throw new ViewerAuthException(400, "请输入正确的手机号");
        }
        return phone;
    }

    private List<ViewerFeedbackImageItem> normalizeImages(List<ViewerFeedbackImageItem> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        if (raw.size() > IMAGES_MAX) {
            throw new ViewerAuthException(400, "截图最多" + IMAGES_MAX + "张");
        }
        List<ViewerFeedbackImageItem> out = new ArrayList<>(raw.size());
        for (ViewerFeedbackImageItem item : raw) {
            if (item == null) {
                throw new ViewerAuthException(400, "截图地址无效");
            }
            String url = item.getUrl() == null ? "" : item.getUrl().trim();
            String key = item.getObjectKey() == null ? "" : item.getObjectKey().trim();
            if (url.isEmpty() || key.isEmpty() || url.length() > 512 || key.length() > 1024) {
                throw new ViewerAuthException(400, "截图地址无效");
            }
            if (!key.startsWith(ViewerUploadBiz.FEEDBACK_PREFIX) || key.contains("..")) {
                throw new ViewerAuthException(400, "截图地址无效");
            }
            if (!url.endsWith("/" + key)) {
                throw new ViewerAuthException(400, "截图地址无效");
            }
            if (!tosUploader.isAllowedAvatarUrl(url)) {
                throw new ViewerAuthException(400, "截图地址无效");
            }
            ViewerFeedbackImageItem ok = new ViewerFeedbackImageItem();
            ok.setUrl(url);
            ok.setObjectKey(key);
            out.add(ok);
        }
        return out;
    }

    private String toImagesJson(List<ViewerFeedbackImageItem> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<Map<String, String>> list = new ArrayList<>(images.size());
        for (ViewerFeedbackImageItem item : images) {
            Map<String, String> m = new LinkedHashMap<>(2);
            m.put("url", item.getUrl());
            m.put("objectKey", item.getObjectKey());
            list.add(m);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new ViewerAuthException(500, "反馈保存失败");
        }
    }

    private List<ViewerFeedbackImageItem> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, String>> raw = objectMapper.readValue(json, IMAGES_TYPE);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<ViewerFeedbackImageItem> out = new ArrayList<>(raw.size());
            for (Map<String, String> m : raw) {
                if (m == null) continue;
                ViewerFeedbackImageItem item = new ViewerFeedbackImageItem();
                item.setUrl(m.get("url"));
                item.setObjectKey(m.get("objectKey"));
                out.add(item);
            }
            return out;
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private ViewerFeedbackListItem toListItem(ViewerFeedbackEntity e) {
        ViewerFeedbackListItem item = new ViewerFeedbackListItem();
        item.setId(e.getId());
        item.setCategory(e.getCategory());
        item.setCategoryLabel(ViewerFeedbackCategory.label(e.getCategory()));
        item.setContent(e.getContent());
        item.setImageCount(parseImages(e.getImagesJson()).size());
        item.setCreatedTime(formatTime(e.getCreatedTime()));
        return item;
    }

    private ViewerFeedbackDetailResponse toDetail(ViewerFeedbackEntity e) {
        ViewerFeedbackDetailResponse d = new ViewerFeedbackDetailResponse();
        d.setId(e.getId());
        d.setCategory(e.getCategory());
        d.setCategoryLabel(ViewerFeedbackCategory.label(e.getCategory()));
        d.setContent(e.getContent());
        d.setContactPhone(e.getContactPhone());
        d.setImages(parseImages(e.getImagesJson()));
        d.setCreatedTime(formatTime(e.getCreatedTime()));
        return d;
    }

    private static String formatTime(LocalDateTime t) {
        return t == null ? "" : TIME_FMT.format(t);
    }
}
