package com.zhibo.admin.livereport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.AuthService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.livereport.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service @RequiredArgsConstructor
public class LiveReportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MB = 1024L * 1024L;
    private final LiveReportRepository reports;
    private final LiveReportAuditRepository audits;
    private final SysUserAccountRepository users;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional
    public LiveReportDtos.Result create(LiveReportDtos.SaveRequest req, long userId) {
        validateUuid(req.id());
        if (reports.existsById(req.id())) throw new IllegalArgumentException("报备 ID 已存在");
        SysUserAccountEntity operator = user(userId, "当前员工不存在");
        SysUserAccountEntity host = user(parseId(req.hostId(), "主播不存在"), "主播不存在");
        validateFull(req.id(), req.title(), req.content(), req.planTimes(), req.coverObjectKey(),
                req.coverFileSize(), req.coverMimeType(), req.attachments());
        LiveReportEntity e = new LiveReportEntity();
        e.setId(req.id()); e.setTitle(req.title().trim()); e.setContent(req.content().trim());
        setPlans(e, req.planTimes()); e.setCoverUrl(req.coverUrl()); e.setCoverObjectKey(req.coverObjectKey());
        e.setCoverFileSize(req.coverFileSize()); e.setCoverMimeType(req.coverMimeType());
        e.setHostId(host.getId()); e.setHostName(host.getUserName()); e.setHostAvatar(host.getUserAvatar());
        e.setAttachments(json(req.attachments())); e.setStatus("pending"); e.setBoundLiveCount(0);
        e.setSubmitterId(operator.getId()); e.setSubmitterName(operator.getUserName());
        e.setDepartmentName(AuthService.resolveDepartmentName(operator)); e.setSubmittedAt(LocalDateTime.now(clock));
        reports.save(e); addAudit(e.getId(), "submit", null, null, operator);
        return new LiveReportDtos.Result(e.getId(), e.getStatus());
    }

    @Transactional(readOnly = true)
    public LiveReportDtos.ListResult list(int page, int pageSize, String title, String hostName, String status,
            String startFrom, String startTo) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数错误");
        if (status != null && !status.isBlank() && !Set.of("pending","passed","rejected").contains(status))
            throw new IllegalArgumentException("审核状态错误");
        var spec = (org.springframework.data.jpa.domain.Specification<LiveReportEntity>) (root,q,cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (title != null && !title.isBlank()) ps.add(cb.like(root.get("title"), "%"+title.trim()+"%"));
            if (hostName != null && !hostName.isBlank()) ps.add(cb.like(root.get("hostName"), "%"+hostName.trim()+"%"));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (startFrom != null && !startFrom.isBlank()) ps.add(cb.greaterThanOrEqualTo(root.get("planEndMax"), parse(startFrom)));
            if (startTo != null && !startTo.isBlank()) ps.add(cb.lessThanOrEqualTo(root.get("planStartMin"), parse(startTo)));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var p = reports.findAll(spec, PageRequest.of(page-1,pageSize,Sort.by(Sort.Direction.DESC,"submittedAt")));
        return new LiveReportDtos.ListResult(page,pageSize,p.getTotalElements(),p.stream().map(this::detailOf).toList());
    }

    @Transactional(readOnly = true) public LiveReportDtos.Detail detail(String id) { return detailOf(report(id)); }

    @Transactional
    public LiveReportDtos.Result update(String id, LiveReportDtos.UpdateRequest req, long userId) {
        LiveReportEntity e = report(id);
        SysUserAccountEntity operator = user(userId, "当前员工不存在");
        if ("pending".equals(e.getStatus())) throw new IllegalArgumentException("待审核报备不可编辑");
        if ("passed".equals(e.getStatus())) {
            if (e.getBoundLiveCount() > 0) throw new IllegalArgumentException("已绑定直播的报备不可编辑");
            if (req.planTimes() == null) throw new IllegalArgumentException("请提交计划时间");
            List<LiveReportDtos.PlanTime> old = plans(e);
            if (req.planTimes().size() <= old.size() || !req.planTimes().subList(0,old.size()).equals(old))
                throw new IllegalArgumentException("已通过报备只能在末尾增加计划时间");
            validatePlans(req.planTimes()); setPlans(e, req.planTimes());
            reports.save(e); return new LiveReportDtos.Result(id,e.getStatus());
        }
        if (!"rejected".equals(e.getStatus())) throw new IllegalArgumentException("当前状态不可编辑");
        requireFull(req);
        SysUserAccountEntity host = user(parseId(req.hostId(), "主播不存在"), "主播不存在");
        validateFull(id,req.title(),req.content(),req.planTimes(),req.coverObjectKey(),req.coverFileSize(),req.coverMimeType(),req.attachments());
        e.setTitle(req.title().trim()); e.setContent(req.content().trim()); setPlans(e, req.planTimes());
        e.setCoverUrl(req.coverUrl()); e.setCoverObjectKey(req.coverObjectKey()); e.setHostId(host.getId());
        e.setCoverFileSize(req.coverFileSize()); e.setCoverMimeType(req.coverMimeType());
        e.setHostName(host.getUserName()); e.setHostAvatar(host.getUserAvatar()); e.setAttachments(json(req.attachments()));
        e.setStatus("pending"); e.setSubmittedAt(LocalDateTime.now(clock)); reports.save(e);
        addAudit(id,"resubmit",null,null,operator); return new LiveReportDtos.Result(id,e.getStatus());
    }

    @Transactional
    public LiveReportDtos.Result audit(String id, LiveReportDtos.AuditRequest req, long userId) {
        LiveReportEntity e=report(id); if (!"pending".equals(e.getStatus())) throw new IllegalArgumentException("仅待审核报备可审核");
        if (!Set.of("passed","rejected").contains(req.result())) throw new IllegalArgumentException("审核结果错误");
        String reason=req.reason()==null?null:req.reason().trim();
        if ("rejected".equals(req.result()) && (reason==null || reason.isEmpty())) throw new IllegalArgumentException("驳回原因不能为空");
        SysUserAccountEntity operator=user(userId,"当前员工不存在"); e.setStatus(req.result()); reports.save(e);
        addAudit(id,"audit",req.result(),reason,operator); return new LiveReportDtos.Result(id,e.getStatus());
    }

    @Transactional(readOnly=true)
    public LiveReportDtos.AuditList audits(String id) {
        report(id); return new LiveReportDtos.AuditList(audits.findByReportIdOrderByCreatedAtDesc(id).stream()
            .map(a->new LiveReportDtos.AuditItem(a.getId(),a.getAction(),a.getResult(),a.getReason(),String.valueOf(a.getOperatorId()),a.getOperatorName(),a.getCreatedAt())).toList());
    }

    private void validateFull(String id,String title,String content,List<LiveReportDtos.PlanTime> times,String coverKey,Long coverSize,String coverMime,List<LiveReportDtos.Attachment> files) {
        if(title==null||title.trim().isEmpty()||title.trim().length()>64) throw new IllegalArgumentException("报备名称需为 1~64 字");
        if(content==null||content.trim().isEmpty()||content.trim().length()>500) throw new IllegalArgumentException("报备内容需为 1~500 字");
        validatePlans(times); String base="live-report/"+id+"/";
        if(coverKey==null||!(coverKey.startsWith(base+"cover/")||coverKey.startsWith("material/image/"))) throw new IllegalArgumentException("封面须来自素材中心图片或报备封面目录");
        if(coverSize==null||coverSize<=0||(coverKey.startsWith(base+"cover/")&&coverSize>=2*MB)) throw new IllegalArgumentException("封面大小错误");
        if(coverMime==null||!Set.of("image/jpeg","image/png").contains(coverMime)) throw new IllegalArgumentException("封面只支持 JPG/PNG");
        if(files==null||files.isEmpty()||files.size()>20) throw new IllegalArgumentException("材料需为 1~20 份");
        for(var f:files){String n=f.name().toLowerCase(Locale.ROOT); if(!f.objectKey().startsWith(base+"attachment/")||!(n.endsWith(".doc")||n.endsWith(".docx")||n.endsWith(".pdf"))||f.fileSize()==null||f.fileSize()<=0||f.fileSize()>10*MB) throw new IllegalArgumentException("材料仅支持 doc/docx/pdf，单份不超过 10MB");}
    }
    private void validatePlans(List<LiveReportDtos.PlanTime> xs){if(xs==null||xs.isEmpty()||xs.size()>10)throw new IllegalArgumentException("计划时间需为 1~10 段"); LocalDate max=LocalDate.now(clock).plusDays(7); for(var x:xs){LocalDateTime s=parse(x.startTime()),e=parse(x.endTime());if(!e.isAfter(s))throw new IllegalArgumentException("计划结束时间须晚于开始时间");if(e.toLocalDate().isAfter(max))throw new IllegalArgumentException("报备计划截止日期不能超过未来第 7 天");}}
    private LocalDateTime parse(String s){try{return LocalDateTime.parse(s,TIME);}catch(DateTimeParseException|NullPointerException ex){throw new IllegalArgumentException("时间格式须为 yyyy-MM-dd HH:mm:ss");}}
    private void requireFull(LiveReportDtos.UpdateRequest r){if(r.title()==null||r.content()==null||r.planTimes()==null||r.coverUrl()==null||r.coverObjectKey()==null||r.hostId()==null||r.attachments()==null)throw new IllegalArgumentException("驳回后重新提交需包含完整字段");}
    private LiveReportEntity report(String id){return reports.findById(id).orElseThrow(()->new IllegalArgumentException("报备不存在"));}
    private SysUserAccountEntity user(long id,String msg){return users.findById(id).filter(u->Integer.valueOf(1).equals(u.getRsStatus())).orElseThrow(()->new IllegalArgumentException(msg));}
    private long parseId(String id,String msg){try{return Long.parseLong(id);}catch(Exception e){throw new IllegalArgumentException(msg);}}
    private void validateUuid(String id){try{UUID.fromString(id);}catch(Exception e){throw new IllegalArgumentException("报备 ID 格式错误");}}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalArgumentException("数据格式错误");}}
    private List<LiveReportDtos.PlanTime> plans(LiveReportEntity e){return read(e.getPlanTimes(),new TypeReference<>(){});}
    private void setPlans(LiveReportEntity e,List<LiveReportDtos.PlanTime> xs){e.setPlanTimes(json(xs));e.setPlanStartMin(xs.stream().map(x->parse(x.startTime())).min(LocalDateTime::compareTo).orElseThrow());e.setPlanEndMax(xs.stream().map(x->parse(x.endTime())).max(LocalDateTime::compareTo).orElseThrow());}
    private List<LiveReportDtos.Attachment> attachments(LiveReportEntity e){return read(e.getAttachments(),new TypeReference<>(){});}
    private <T>T read(String s,TypeReference<T> t){try{return mapper.readValue(s,t);}catch(JsonProcessingException e){throw new IllegalStateException("报备数据损坏",e);}}
    private LiveReportDtos.Detail detailOf(LiveReportEntity e){return new LiveReportDtos.Detail(e.getId(),e.getTitle(),e.getContent(),plans(e),e.getCoverUrl(),e.getCoverObjectKey(),e.getCoverFileSize(),e.getCoverMimeType(),new LiveReportDtos.Host(String.valueOf(e.getHostId()),e.getHostName(),e.getHostAvatar()),attachments(e),e.getStatus(),e.getBoundLiveCount(),String.valueOf(e.getSubmitterId()),e.getSubmitterName(),e.getDepartmentName(),e.getSubmittedAt(),e.getUpdatedAt());}
    private void addAudit(String reportId,String action,String result,String reason,SysUserAccountEntity u){LiveReportAuditEntity a=new LiveReportAuditEntity();a.setReportId(reportId);a.setAction(action);a.setResult(result);a.setReason(reason);a.setOperatorId(u.getId());a.setOperatorName(u.getUserName());audits.save(a);}
}
