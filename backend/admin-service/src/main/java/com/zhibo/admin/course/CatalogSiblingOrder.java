package com.zhibo.admin.course;

import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 同父兄弟节点展示顺序：
 * <ul>
 *   <li>同父下全部 {@code sort_order == 0} → 默认：子课程 → 章节 → 小节，同类型再按 {@code createdTime ASC, id ASC}</li>
 *   <li>仅章节有自定义序、小节仍全为 0（章排序）→ 仍按类型分组，组内按 {@code sort_order}</li>
 *   <li>存在小节 {@code sort_order != 0}（小节排序后）→ 按 {@code sort_order ASC, id ASC}，可章节/小节交错</li>
 * </ul>
 * 自定义排序写入时用 {@code 1..n}，保留 {@code 0} 表示未排序。
 */
final class CatalogSiblingOrder {

    private CatalogSiblingOrder() {
    }

    static boolean isDefaultOrder(List<CourseCatalogNodeEntity> siblings) {
        if (siblings == null || siblings.isEmpty()) {
            return true;
        }
        for (CourseCatalogNodeEntity n : siblings) {
            if (n.getSortOrder() != 0) {
                return false;
            }
        }
        return true;
    }

    /** 小节排序落库后才会出现小节非 0；此时允许交错。 */
    static boolean usesInterleavedOrder(List<CourseCatalogNodeEntity> siblings) {
        if (siblings == null || siblings.isEmpty()) {
            return false;
        }
        for (CourseCatalogNodeEntity n : siblings) {
            if (n.getNodeType() == CourseCatalogNodeType.LESSON && n.getSortOrder() != 0) {
                return true;
            }
        }
        return false;
    }

    static int typeRank(int nodeType) {
        if (nodeType == CourseCatalogNodeType.SUBCOURSE) {
            return 0;
        }
        if (nodeType == CourseCatalogNodeType.CHAPTER) {
            return 1;
        }
        if (nodeType == CourseCatalogNodeType.LESSON) {
            return 2;
        }
        return 9;
    }

    static Comparator<CourseCatalogNodeEntity> comparator(List<CourseCatalogNodeEntity> siblings) {
        if (isDefaultOrder(siblings)) {
            return Comparator
                    .comparingInt((CourseCatalogNodeEntity n) -> typeRank(n.getNodeType()))
                    .thenComparing(CatalogSiblingOrder::createdTimeOrMin)
                    .thenComparing(n -> n.getId() == null ? 0L : n.getId());
        }
        if (usesInterleavedOrder(siblings)) {
            return Comparator
                    .comparingInt(CourseCatalogNodeEntity::getSortOrder)
                    .thenComparing(n -> n.getId() == null ? 0L : n.getId());
        }
        // 仅章排序：类型分组后组内 sort_order（小节仍 0 时按 createdTime）
        return Comparator
                .comparingInt((CourseCatalogNodeEntity n) -> typeRank(n.getNodeType()))
                .thenComparingInt(CourseCatalogNodeEntity::getSortOrder)
                .thenComparing(CatalogSiblingOrder::createdTimeOrMin)
                .thenComparing(n -> n.getId() == null ? 0L : n.getId());
    }

    private static LocalDateTime createdTimeOrMin(CourseCatalogNodeEntity n) {
        LocalDateTime t = n.getCreatedTime();
        return t == null ? LocalDateTime.MIN : t;
    }

    /** 就地按同父规则排序；返回同一列表。 */
    static List<CourseCatalogNodeEntity> sortSiblings(List<CourseCatalogNodeEntity> siblings) {
        if (siblings == null || siblings.size() <= 1) {
            return siblings;
        }
        siblings.sort(comparator(siblings));
        return siblings;
    }

    /**
     * 将整课扁平列表整理为：按 parentId 分组排序后，再按 parentId、组内序拼接。
     * 列表接口依赖「同父相对序」正确；前端按 parent 过滤时保留相对序。
     */
    static List<CourseCatalogNodeEntity> orderFlat(List<CourseCatalogNodeEntity> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes == null ? List.of() : nodes;
        }
        Map<Long, List<CourseCatalogNodeEntity>> byParent = new LinkedHashMap<>();
        for (CourseCatalogNodeEntity n : nodes) {
            byParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        List<CourseCatalogNodeEntity> out = new ArrayList<>(nodes.size());
        List<Long> parentKeys = new ArrayList<>(byParent.keySet());
        parentKeys.sort(Long::compareTo);
        for (Long parentId : parentKeys) {
            List<CourseCatalogNodeEntity> group = byParent.get(parentId);
            sortSiblings(group);
            out.addAll(group);
        }
        return out;
    }

    /**
     * 新建节点的 {@code sort_order}：同父仍全是默认 0 → 仍写 0；否则接在最大值后（跨类型）。
     */
    static int nextSortOrder(List<CourseCatalogNodeEntity> siblings) {
        if (siblings == null || siblings.isEmpty() || isDefaultOrder(siblings)) {
            return 0;
        }
        int max = 0;
        for (CourseCatalogNodeEntity n : siblings) {
            if (n.getSortOrder() > max) {
                max = n.getSortOrder();
            }
        }
        return max + 1;
    }

    /** 自定义排序落库：从 1 起连续编号。 */
    static void applyExplicitOrder(List<CourseCatalogNodeEntity> ordered) {
        int sort = 1;
        for (CourseCatalogNodeEntity n : ordered) {
            n.setSortOrder(sort++);
        }
    }

    static boolean sameIdSet(List<Long> expected, List<Long> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        return expected.stream().filter(Objects::nonNull).sorted().toList()
                .equals(actual.stream().filter(Objects::nonNull).sorted().toList());
    }
}
