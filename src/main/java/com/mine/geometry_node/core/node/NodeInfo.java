package com.mine.geometry_node.core.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 节点自动注册注解
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeInfo {
    /**
     * 节点所属的菜单路径，例如 "geometry_node.menu.events/block"
     */
    String category();
}