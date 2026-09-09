package com.codeying.assembler;

import com.codeying.entity.Dish;
import com.codeying.entity.DishFlavor;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜品装配器：将 Entity 装配为 VO。
 *
 * @author Endercloud
 */
public final class DishAssembler {
    private DishAssembler() {}

    /**
     * 
     *
     * @param dish dish 
     * @param categoryName categoryName 
     * @param flavors flavors 
     * @return com.codeying.vo.admin.dish.DishVO 
     */
    public static com.codeying.vo.admin.dish.DishVO toAdminVO(Dish dish, String categoryName, List<DishFlavor> flavors) {
        com.codeying.vo.admin.dish.DishVO vo = new com.codeying.vo.admin.dish.DishVO();
        vo.setId(dish.getId());
        vo.setCategoryId(dish.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setDescription(dish.getDescription());
        vo.setImage(dish.getImage());
        vo.setName(dish.getName());
        vo.setPrice(dish.getPrice());
        vo.setStatus(dish.getStatus());
        vo.setUpdateTime(dish.getUpdateTime());
        vo.setFlavors(flavors == null ? List.of() : flavors);
        return vo;
    }

    /**
     * 
     *
     * @param dish dish 
     * @param categoryName categoryName 
     * @return com.codeying.vo.admin.dish.DishPageVO 
     */
    public static com.codeying.vo.admin.dish.DishPageVO toAdminPageVO(Dish dish, String categoryName) {
        com.codeying.vo.admin.dish.DishPageVO vo = new com.codeying.vo.admin.dish.DishPageVO();
        vo.setId(dish.getId());
        vo.setCategoryId(dish.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setDescription(dish.getDescription());
        vo.setImage(dish.getImage());
        vo.setName(dish.getName());
        vo.setPrice(dish.getPrice());
        vo.setStatus(dish.getStatus());
        vo.setUpdateTime(dish.getUpdateTime());
        return vo;
    }

    /**
     * Convert data structure.
     *
     * @param dish dish parameter
     * @param flavors flavors parameter
     * @return com.codeying.vo.user.dish.DishVO result
     */
    public static com.codeying.vo.user.dish.DishVO toUserVO(Dish dish, List<DishFlavor> flavors) {
        com.codeying.vo.user.dish.DishVO vo = new com.codeying.vo.user.dish.DishVO();
        vo.setId(dish.getId());
        vo.setCategoryId(dish.getCategoryId());
        vo.setDescription(dish.getDescription());
        vo.setImage(dish.getImage());
        vo.setName(dish.getName());
        vo.setPrice(dish.getPrice());
        vo.setStatus(dish.getStatus());
        vo.setUpdateTime(dish.getUpdateTime());
        vo.setFlavors(normalizeFlavors(flavors));
        return vo;
    }

    private static List<DishFlavor> normalizeFlavors(List<DishFlavor> flavors) {
        if (flavors == null || flavors.isEmpty()) return List.of();
        List<DishFlavor> normalized = new ArrayList<>(flavors.size());
        for (DishFlavor f : flavors) {
            if (f == null) continue;
            DishFlavor copy = new DishFlavor();
            copy.setId(f.getId());
            copy.setDishId(f.getDishId());
            copy.setName(f.getName());
            copy.setValue(normalizeFlavorValue(f.getValue()));
            normalized.add(copy);
        }
        return normalized;
    }

    private static String normalizeFlavorValue(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("[") && v.endsWith("]")) return v;
        if (v.isEmpty()) return "[]";
        String[] parts = v.split(",");
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (String p : parts) {
            String item = p == null ? "" : p.trim();
            if (item.isEmpty()) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(item)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\\\"");
            else sb.append(c);
        }
        return sb.toString();
    }
}
