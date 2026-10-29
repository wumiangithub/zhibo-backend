package com.zhibo.admin.livereport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.AuthService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.common.UuidIds;
import com.zhibo.livereport.*;
import com.zhibo.material.MaterialAudit;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class LiveReportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MB = 1024L * 1024L;
    private final LiveReportRepository reports;
    private final LiveReportPlanRepository planRows;
    private final LiveReportAuditRepository audits;
    private final SysUserAccountRepository users;
    private final MaterialRepository materials;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * 一次提交 1～10 段计划时间。校验通过后按段各插入一条报备：各自 UUID、各自一段计划时间、
     * 各自一条 submit 审核记录，状态均为 pending。名称、内容、封面、主播、材料相同。
     */
    @Transactional
    public LiveReportDtos.CreateResult create(LiveReportDtos.SaveRequest req, long userId) {
        SysUserAccountEntity operator = user(userId, "当前员工不存在");
        SysUserAccountEntity host = user(requireHostId(req.hostId()), "主播不存在");
        // 新建封面走素材中心，校验用的 id 不会落库；客户端无法事先拼出 legacy 封面键。
        validateFull(UuidIds.newId(), req.title(), req.content(), req.planTimes(), req.coverUrl(),
                req.coverObjectKey(), req.coverFileSize(), req.coverMimeType(), req.coverMaterialId(),
                req.attachments(), true);
        LocalDateTime submitted = LocalDateTime.now(clock);
        List<LiveReportDtos.Result> created = new ArrayList<>();
        for (LiveReportDtos.PlanTime slot : req.planTimes()) {
            String id = UuidIds.newId();
            List<LiveReportDtos.PlanTime> one = List.of(slot);
            LiveReportEntity e = new LiveReportEntity();
            e.setId(id);
            e.setTitle(req.title().trim());
            e.setContent(req.content().trim());
            setPlans(e, one);
            applyCover(e, id, req.coverUrl(), req.coverObjectKey(), req.coverFileSize(),
                    req.coverMimeType(), req.coverMaterialId());
            e.setHostId(host.getId());
            e.setHostName(host.getUserName());
            e.setHostAvatar(AuthService.resolveAvatar(host));
            e.setAttachments(json(req.attachments()));
            e.setStatus("pending");
            e.setBoundLiveCount(0);
            e.setSubmitterId(operator.getId());
            e.setSubmitterName(operator.getUserName());
            e.setDepartmentName(AuthService.resolveDepartmentName(operator));
            e.setSubmittedTime(submitted);
            reports.save(e);
            replacePlanRows(e.getId(), one);
            addAudit(e.getId(), "submit", null, null, operator);
            created.add(new LiveReportDtos.Result(e.getId(), e.getStatus()));
        }
        return new LiveReportDtos.CreateResult(created);
    }

    @Transactional(readOnly = true)
    public LiveReportDtos.ListResult list(int page, int pageSize, String title, String hostName, String status,
            String startFrom, String startTo) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数错误");
        if (status != null && !status.isBlank() && !Set.of("pending","passed","rejected").contains(status))
            throw new IllegalArgumentException("审核状态错误");
        LocalDateTime from = blank(startFrom) ? null : parse(startFrom);
        LocalDateTime to = blank(startTo) ? null : parse(startTo);
        var spec = (org.springframework.data.jpa.domain.Specification<LiveReportEntity>) (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedTime")));
            if (title != null && !title.isBlank()) ps.add(cb.like(root.get("title"), "%"+title.trim()+"%"));
            if (hostName != null && !hostName.isBlank()) ps.add(cb.like(root.get("hostName"), "%"+hostName.trim()+"%"));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            // 任一段与 [startFrom, startTo] 相交；不用 planStartMin/planEndMax，避免多段空洞误命中
            if (from != null || to != null) {
                Subquery<Integer> sq = q.subquery(Integer.class);
                Root<LiveReportPlanEntity> plan = sq.from(LiveReportPlanEntity.class);
                List<Predicate> planPs = new ArrayList<>();
                planPs.add(cb.equal(plan.get("reportId"), root.get("id")));
                if (from != null) planPs.add(cb.greaterThanOrEqualTo(plan.get("endTime"), from));
                if (to != null) planPs.add(cb.lessThanOrEqualTo(plan.get("startTime"), to));
                sq.select(cb.literal(1)).where(planPs.toArray(Predicate[]::new));
                ps.add(cb.exists(sq));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var p = reports.findAll(spec, PageRequest.of(page-1,pageSize,Sort.by(Sort.Direction.DESC,"submittedTime")));
        List<LiveReportEntity> rows = p.getContent();
        Map<Long, String> hostDept = hostDepartmentById(rows);
        return new LiveReportDtos.ListResult(page, pageSize, p.getTotalElements(),
                rows.stream().map(e -> detailOf(e, hostDept.get(e.getHostId()))).toList());
    }

    @Transactional(readOnly = true)
    public LiveReportDtos.Detail detail(String id) {
        LiveReportEntity e = report(id);
        Map<Long, String> hostDept = hostDepartmentById(List.of(e));
        return detailOf(e, hostDept.get(e.getHostId()));
    }

    /** 创建之后任何状态都不可再改。不存在或已软删仍返回「报备不存在」。 */
    @Transactional
    public LiveReportDtos.Result update(String id, LiveReportDtos.UpdateRequest req, long userId) {
        report(id);
        throw new IllegalArgumentException("报备不可编辑");
    }

    @Transactional
    public LiveReportDtos.Result audit(String id, LiveReportDtos.AuditRequest req, long userId) {
        LiveReportEntity e=report(id);
        if (!Set.of("pending","passed","rejected").contains(e.getStatus())) throw new IllegalArgumentException("报备状态不可审核");
        if (!Set.of("passed","rejected").contains(req.result())) throw new IllegalArgumentException("审核结果错误");
        String reason=req.reason()==null?null:req.reason().trim();
        if ("rejected".equals(req.result()) && (reason==null || reason.isEmpty())) throw new IllegalArgumentException("驳回原因不能为空");
        SysUserAccountEntity operator=user(userId,"当前员工不存在"); e.setStatus(req.result()); reports.save(e);
        addAudit(id,"audit",req.result(),reason,operator); return new LiveReportDtos.Result(id,e.getStatus());
    }

    /**
     * 软删报备：写 {@code deleted_time} / {@code deleted_by} / {@code deleted_by_name}。
     * 已绑定直播仍允许删，不级联软删活动；已软删再删 → 400。
     */
    @Transactional
    public LiveReportDtos.DeleteResult softDelete(String id, long userId) {
        LiveReportEntity e = report(id);
        SysUserAccountEntity operator = user(userId, "当前员工不存在");
        e.setDeletedTime(LocalDateTime.now(clock));
        e.setDeletedBy(operator.getId());
        e.setDeletedByName(operator.getUserName() == null ? "" : operator.getUserName());
        reports.save(e);
        return new LiveReportDtos.DeleteResult(e.getId());
    }

    @Transactional(readOnly=true)
    public LiveReportDtos.AuditList audits(String id) {
        report(id);
        List<LiveReportAuditEntity> rows = audits.findByReportIdOrderByCreatedTimeDesc(id);
        Set<Long> opIds = rows.stream()
                .map(LiveReportAuditEntity::getOperatorId)
                .filter(Objects::nonNull)
                .filter(opId -> opId > 0)
                .collect(Collectors.toSet());
        Map<Long, String> deptByUser = new HashMap<>();
        if (!opIds.isEmpty()) {
            for (SysUserAccountEntity u : users.findAllById(opIds)) {
                String dept = AuthService.resolveDepartmentName(u);
                if (dept != null && !dept.isBlank()) {
                    deptByUser.put(u.getId(), dept);
                }
            }
        }
        return new LiveReportDtos.AuditList(rows.stream()
                .map(a -> new LiveReportDtos.AuditItem(
                        a.getId(),
                        a.getAction(),
                        a.getResult(),
                        a.getReason(),
                        a.getOperatorId(),
                        a.getOperatorName(),
                        deptByUser.get(a.getOperatorId()),
                        formatTime(a.getCreatedTime())))
                .toList());
    }

    private void validateFull(String id,String title,String content,List<LiveReportDtos.PlanTime> times,
            String coverUrl,String coverKey,Long coverSize,String coverMime,String coverMaterialId,
            List<LiveReportDtos.Attachment> files, boolean create) {
        if(title==null||title.trim().isEmpty()||title.trim().length()>64) throw new IllegalArgumentException("报备名称需为 1~64 字");
        if(content==null||content.trim().isEmpty()||content.trim().length()>500) throw new IllegalArgumentException("报备内容需为 1~500 字");
        validatePlans(times);
        validateCover(id, coverUrl, coverKey, coverSize, coverMime, coverMaterialId);
        // 新建材料只接受 live-report/attachment/。历史键 live-report/{id}/attachment/ 仅 create=false 时放行；更新接口已关闭。
        String shared = "live-report/attachment/";
        String scoped = "live-report/" + id + "/attachment/";
        if(files==null||files.size()!=1) throw new IllegalArgumentException("材料只能上传 1 份");
        for(var f:files){
            if(f.name()==null||f.objectKey()==null) throw new IllegalArgumentException("材料仅支持 doc、docx、pdf、txt，且大小不超过 10MB");
            String n=f.name().toLowerCase(Locale.ROOT);
            boolean prefixOk = f.objectKey().startsWith(shared) || (!create && f.objectKey().startsWith(scoped));
            boolean typeOk = n.endsWith(".doc")||n.endsWith(".docx")||n.endsWith(".pdf")||n.endsWith(".txt");
            if(!prefixOk||!typeOk||f.fileSize()==null||f.fileSize()<=0||f.fileSize()>10*MB)
                throw new IllegalArgumentException("材料仅支持 doc、docx、pdf、txt，且大小不超过 10MB");
        }
    }

    /** 素材中心封面必须存在、未删、图片、两审通过；旧 live-report/{id}/cover/ 仅校验格式。 */
    private void validateCover(String id, String coverUrl, String coverKey, Long coverSize, String coverMime, String coverMaterialId) {
        if (coverUrl == null || coverUrl.isBlank()) throw new IllegalArgumentException("请选择报备封面");
        if (coverKey == null || coverKey.isBlank()) throw new IllegalArgumentException("封面须来自素材中心图片或报备封面目录");
        String legacyCover = "live-report/" + id + "/cover/";
        if (coverKey.startsWith("material/image/")) {
            if (coverMaterialId == null || coverMaterialId.isBlank()) throw new IllegalArgumentException("封面须来自素材中心已审核通过的图片");
            MaterialEntity m = materials.findById(coverMaterialId)
                    .filter(x -> x.getDeletedTime() == null)
                    .orElseThrow(() -> new IllegalArgumentException("封面素材不存在"));
            if (!"image".equals(m.getType())) throw new IllegalArgumentException("封面须为图片素材");
            if (!MaterialAudit.bothPassed(m))
                throw new IllegalArgumentException("封面须来自素材中心已审核通过的图片");
            if (m.getObjectKey() == null || !coverKey.equals(m.getObjectKey()))
                throw new IllegalArgumentException("封面素材与对象键不一致");
            if (m.getFileSize() == null || m.getFileSize() <= 0) throw new IllegalArgumentException("封面素材缺少大小");
            coverSize = m.getFileSize();
            coverMime = resolveCoverMime(m);
        } else if (!coverKey.startsWith(legacyCover)) {
            throw new IllegalArgumentException("封面须来自素材中心图片或报备封面目录");
        }
        if (coverSize == null || coverSize <= 0 || coverSize > 5 * MB) throw new IllegalArgumentException("封面大小须不超过 5MB");
        if (coverMime == null || !Set.of("image/jpeg", "image/png").contains(coverMime))
            throw new IllegalArgumentException("封面只支持 JPG/PNG");
    }

    private void applyCover(LiveReportEntity e, String id, String coverUrl, String coverKey, Long coverSize,
            String coverMime, String coverMaterialId) {
        if (coverKey != null && coverKey.startsWith("material/image/") && coverMaterialId != null && !coverMaterialId.isBlank()) {
            MaterialEntity m = materials.findById(coverMaterialId).orElseThrow(() -> new IllegalArgumentException("封面素材不存在"));
            e.setCoverUrl(m.getUrl());
            e.setCoverObjectKey(m.getObjectKey());
            e.setCoverFileSize(m.getFileSize());
            e.setCoverMimeType(resolveCoverMime(m));
            e.setCoverMaterialId(m.getId());
            return;
        }
        e.setCoverUrl(coverUrl);
        e.setCoverObjectKey(coverKey);
        e.setCoverFileSize(coverSize);
        e.setCoverMimeType(coverMime);
        e.setCoverMaterialId(null);
    }

    private String resolveCoverMime(MaterialEntity m) {
        String mime = m.getMimeType();
        if (mime != null && Set.of("image/jpeg", "image/png", "image/jpg").contains(mime.toLowerCase(Locale.ROOT))) {
            return mime.equalsIgnoreCase("image/jpg") ? "image/jpeg" : mime.toLowerCase(Locale.ROOT);
        }
        String name = m.getName() == null ? "" : m.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        throw new IllegalArgumentException("封面只支持 JPG/PNG");
    }
    private void validatePlans(List<LiveReportDtos.PlanTime> xs){if(xs==null||xs.isEmpty()||xs.size()>10)throw new IllegalArgumentException("计划时间需为 1~10 段"); LocalDate max=LocalDate.now(clock).plusDays(7); for(var x:xs){LocalDateTime s=parse(x.startTime()),e=parse(x.endTime());if(!e.isAfter(s))throw new IllegalArgumentException("计划结束时间须晚于开始时间");if(e.toLocalDate().isAfter(max))throw new IllegalArgumentException("报备计划截止日期不能超过未来第 7 天");}}
    private LocalDateTime parse(String s){try{return LocalDateTime.parse(s,TIME);}catch(DateTimeParseException|NullPointerException ex){throw new IllegalArgumentException("时间格式须为 yyyy-MM-dd HH:mm:ss");}}
    private LiveReportEntity report(String id) {
        String uuid = UuidIds.require(id, "报备不存在", "报备不存在");
        LiveReportEntity e = reports.findById(uuid).orElseThrow(() -> new IllegalArgumentException("报备不存在"));
        if (e.getDeletedTime() != null) {
            throw new IllegalArgumentException("报备不存在");
        }
        return e;
    }
    private SysUserAccountEntity user(long id,String msg){return users.findById(id).filter(u->Integer.valueOf(1).equals(u.getRsStatus())).orElseThrow(()->new IllegalArgumentException(msg));}
    private long requireHostId(Long hostId){if(hostId==null||hostId<=0L)throw new IllegalArgumentException("主播不存在");return hostId;}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalArgumentException("数据格式错误");}}
    private List<LiveReportDtos.PlanTime> plans(LiveReportEntity e){return read(e.getPlanTimes(),new TypeReference<>(){});}
    private void setPlans(LiveReportEntity e, List<LiveReportDtos.PlanTime> xs) {
        e.setPlanTimes(json(xs));
        e.setPlanStartMin(xs.stream().map(x -> parse(x.startTime())).min(LocalDateTime::compareTo).orElseThrow());
        e.setPlanEndMax(xs.stream().map(x -> parse(x.endTime())).max(LocalDateTime::compareTo).orElseThrow());
    }

    /** 全量替换子表行，与 plan_times JSON 保持一致。须在 live_report 行已落库后调用。 */
    private void replacePlanRows(String reportId, List<LiveReportDtos.PlanTime> xs) {
        planRows.deleteByReportId(reportId);
        List<LiveReportPlanEntity> rows = new ArrayList<>(xs.size());
        for (int i = 0; i < xs.size(); i++) {
            LiveReportDtos.PlanTime t = xs.get(i);
            LiveReportPlanEntity row = new LiveReportPlanEntity();
            row.setReportId(reportId);
            row.setStartTime(parse(t.startTime()));
            row.setEndTime(parse(t.endTime()));
            row.setSortOrder(i);
            rows.add(row);
        }
        planRows.saveAll(rows);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private List<LiveReportDtos.Attachment> attachments(LiveReportEntity e){return read(e.getAttachments(),new TypeReference<>(){});}
    private <T>T read(String s,TypeReference<T> t){try{return mapper.readValue(s,t);}catch(JsonProcessingException e){throw new IllegalStateException("报备数据损坏",e);}}

    /** 批量现查主播部门，避免列表 N+1。 */
    private Map<Long, String> hostDepartmentById(List<LiveReportEntity> rows) {
        Set<Long> hostIds = rows.stream()
                .map(LiveReportEntity::getHostId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> out = new HashMap<>();
        if (hostIds.isEmpty()) return out;
        for (SysUserAccountEntity u : users.findAllById(hostIds)) {
            String dept = AuthService.resolveDepartmentName(u);
            if (dept != null && !dept.isBlank()) {
                out.put(u.getId(), dept);
            }
        }
        return out;
    }

    private LiveReportDtos.Detail detailOf(LiveReportEntity e, String hostDepartmentName) {
        return new LiveReportDtos.Detail(
                e.getId(), e.getTitle(), e.getContent(), plans(e),
                e.getCoverUrl(), e.getCoverObjectKey(), e.getCoverFileSize(), e.getCoverMimeType(),
                e.getCoverMaterialId(),
                new LiveReportDtos.Host(e.getHostId(), e.getHostName(), e.getHostAvatar(), hostDepartmentName),
                attachments(e), e.getStatus(), e.getBoundLiveCount(),
                e.getSubmitterId(), e.getSubmitterName(), e.getDepartmentName(),
                formatTime(e.getSubmittedTime()), formatTime(e.getUpdatedTime()));
    }

    private String formatTime(LocalDateTime t){return t==null?null:t.format(TIME);}
    private void addAudit(String reportId,String action,String result,String reason,SysUserAccountEntity u){LiveReportAuditEntity a=new LiveReportAuditEntity();a.setReportId(reportId);a.setAction(action);a.setResult(result);a.setReason(reason);a.setOperatorId(u.getId());a.setOperatorName(u.getUserName());audits.save(a);}
}
