package com.zhibo.material;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 文章正文，主键与 {@link MaterialEntity} 相同。列表不加载此表。 */
@Entity
@Table(name = "material_article")
@Getter
@Setter
public class MaterialArticleEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 16)
    private String author;

    /** 与 Flyway MEDIUMTEXT 对齐，供 ddl-auto=validate。 */
    @Column(nullable = false, columnDefinition = "mediumtext")
    private String content;
}
