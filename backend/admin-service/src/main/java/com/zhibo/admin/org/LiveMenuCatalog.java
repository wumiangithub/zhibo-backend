package com.zhibo.admin.org;

import com.zhibo.admin.org.dto.RoleMenuNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 直播子系统菜单。与主系统 {@code sys_uimsmenu} 无关。 */
public final class LiveMenuCatalog {

    private LiveMenuCatalog() {
    }

    public static List<RoleMenuNode> tree() {
        return List.of(
                node("workbench", "工作台", leaf("overview", "概况")),
                node("live", "直播",
                        leaf("live-manage", "直播管理"),
                        leaf("live-create", "新建直播"),
                        leaf("live-console", "直播中控台")),
                node("settings", "设置",
                        leaf("materials", "素材中心"),
                        leaf("employees", "员工管理"),
                        leaf("roles", "角色管理"))
        );
    }

    public static boolean known(String code) {
        return codes().containsKey(code);
    }

    private static Map<String, String> codes() {
        Map<String, String> map = new LinkedHashMap<>();
        collect(tree(), map);
        return map;
    }

    private static void collect(List<RoleMenuNode> nodes, Map<String, String> map) {
        for (RoleMenuNode node : nodes) {
            map.put(node.getCode(), node.getName());
            collect(node.getChildren(), map);
        }
    }

    private static RoleMenuNode node(String code, String name, RoleMenuNode... children) {
        RoleMenuNode node = new RoleMenuNode();
        node.setCode(code);
        node.setName(name);
        node.setChildren(new ArrayList<>(List.of(children)));
        return node;
    }

    private static RoleMenuNode leaf(String code, String name) {
        return node(code, name);
    }
}
