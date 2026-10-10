package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codeying.entity.Category;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 分类 Mapper。
 *
 * @author Endercloud
 */
public interface CategoryMapper extends BaseMapper<Category> {
    /** 按可选类型读取管理端分类，包含禁用分类，按 sort 升序、id 降序。 */
    default List<Category> listByType(Integer type) {
        return selectList(new QueryWrapper<Category>()
                .eq(type != null, "type", type)
                .orderByAsc("sort")
                .orderByDesc("id"));
    }

    /** 读取用户可见的启用分类，按 type、sort 升序、id 降序。 */
    default List<Category> listEnabled(Integer type) {
        return selectList(new QueryWrapper<Category>()
                .eq("status", 1)
                .eq(type != null, "type", type)
                .orderByAsc("type", "sort")
                .orderByDesc("id"));
    }

    /** 按可选名称包含匹配和类型分页，按 sort 升序、更新时间和 id 降序。 */
    default IPage<Category> pageCatalog(IPage<Category> page, String name, Integer type) {
        return selectPage(page, new QueryWrapper<Category>()
                .like(StringUtils.hasText(name), "name", name == null ? null : name.trim())
                .eq(type != null, "type", type)
                .orderByAsc("sort")
                .orderByDesc("update_time", "id"));
    }
}
