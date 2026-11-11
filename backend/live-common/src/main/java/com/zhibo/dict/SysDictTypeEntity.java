package com.zhibo.dict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 系统字典类型（{@code sys_dict_type}）。 */
@Entity
@Table(name = "sys_dict_type")
@Getter
@Setter
public class SysDictTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rs_mainkey_id")
    private Long id;

    /** 1 正常 / -1 已删除 */
    @Column(name = "rs_status", nullable = false)
    private Integer status;

    @Column(name = "rs_dispindex", nullable = false)
    private Integer dispIndex;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "create_by", nullable = false)
    private Long createBy;

    @Column(name = "update_by", nullable = false)
    private Long updateBy;

    /** 字典代码，如 {@code pay_channel} */
    @Column(name = "dict_dm", nullable = false, length = 50)
    private String dictDm;

    @Column(name = "dict_name", nullable = false, length = 50)
    private String dictName;

    @Column(name = "dict_multiple", nullable = false)
    private Integer dictMultiple;

    @Column(length = 500)
    private String remark;
}
