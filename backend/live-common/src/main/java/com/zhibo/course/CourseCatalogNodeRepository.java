package com.zhibo.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseCatalogNodeRepository extends JpaRepository<CourseCatalogNodeEntity, Long> {

    /**
     * 未软删节点（无业务排序）。调用方须按 {@code CatalogSiblingOrder} 规则排序。
     */
    @Query("SELECT n FROM CourseCatalogNodeEntity n WHERE n.courseId = :courseId AND n.deletedTime IS NULL")
    List<CourseCatalogNodeEntity> findActiveByCourseId(@Param("courseId") String courseId);

    /** 兼容旧名；等价 {@link #findActiveByCourseId}，须在 service 层排序 */
    @Deprecated
    default List<CourseCatalogNodeEntity> findActiveByCourseIdOrdered(String courseId) {
        return findActiveByCourseId(courseId);
    }

    @Query("SELECT n FROM CourseCatalogNodeEntity n WHERE n.courseId = :courseId AND n.deletedTime IS NULL "
            + "AND n.parentId = :parentId")
    List<CourseCatalogNodeEntity> findActiveByCourseIdAndParentId(
            @Param("courseId") String courseId,
            @Param("parentId") long parentId);

    /** 兼容旧名；等价 {@link #findActiveByCourseIdAndParentId}，须在 service 层排序 */
    @Deprecated
    default List<CourseCatalogNodeEntity> findActiveByCourseIdAndParentIdOrdered(
            String courseId, long parentId) {
        return findActiveByCourseIdAndParentId(courseId, parentId);
    }

    Optional<CourseCatalogNodeEntity> findByIdAndCourseIdAndDeletedTimeIsNull(Long id, String courseId);

    @Query("SELECT COUNT(n) FROM CourseCatalogNodeEntity n WHERE n.courseId = :courseId "
            + "AND n.deletedTime IS NULL AND n.nodeType = :nodeType")
    long countActiveByCourseIdAndNodeType(
            @Param("courseId") String courseId,
            @Param("nodeType") int nodeType);

    @Query("SELECT COUNT(n) FROM CourseCatalogNodeEntity n WHERE n.courseId = :courseId "
            + "AND n.deletedTime IS NULL AND n.parentId = 0 "
            + "AND n.nodeType IN :nodeTypes")
    long countActiveRootByCourseIdAndNodeTypes(
            @Param("courseId") String courseId,
            @Param("nodeTypes") Collection<Integer> nodeTypes);

    @Query("SELECT COALESCE(MAX(n.sortOrder), -1) FROM CourseCatalogNodeEntity n "
            + "WHERE n.courseId = :courseId AND n.parentId = :parentId "
            + "AND n.nodeType = :nodeType AND n.deletedTime IS NULL")
    int maxSortOrder(
            @Param("courseId") String courseId,
            @Param("parentId") long parentId,
            @Param("nodeType") int nodeType);

    @Query("SELECT n.contentId FROM CourseCatalogNodeEntity n WHERE n.courseId = :courseId "
            + "AND n.deletedTime IS NULL AND n.nodeType = 3 AND n.contentKind = :contentKind "
            + "AND n.contentId IS NOT NULL")
    List<String> findUsedContentIds(
            @Param("courseId") String courseId,
            @Param("contentKind") int contentKind);

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM CourseCatalogNodeEntity n "
            + "WHERE n.courseId = :courseId AND n.deletedTime IS NULL AND n.nodeType = 3 "
            + "AND n.contentKind = :contentKind AND n.contentId = :contentId")
    boolean existsActiveContent(
            @Param("courseId") String courseId,
            @Param("contentKind") int contentKind,
            @Param("contentId") String contentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CourseCatalogNodeEntity n SET n.parentId = :newParentId "
            + "WHERE n.courseId = :courseId AND n.parentId = 0 AND n.deletedTime IS NULL "
            + "AND n.nodeType IN :nodeTypes")
    int reparentRootNodes(
            @Param("courseId") String courseId,
            @Param("newParentId") long newParentId,
            @Param("nodeTypes") Collection<Integer> nodeTypes);
}
