package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codeying.entity.Dish;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.util.StringUtils;

/**
 * 菜品 Mapper。
 *
 * @author Endercloud
 */
public interface DishMapper extends BaseMapper<Dish> {
    /** Count items in the requested sale status. */
    @Select("select count(*) from dish where status = #{status}")
    Long countByStatus(@Param("status") int status);

    /** 统计分类下的全部菜品，供分类删除关联检查。 */
    default Long countByCategory(Long categoryId) {
        return selectCount(new QueryWrapper<Dish>()
                .eq("category_id", categoryId));
    }

    /** 读取指定分类菜品，可选售卖状态，按更新时间和 id 降序。 */
    default List<Dish> listCatalog(Long categoryId, Integer status) {
        return selectList(new QueryWrapper<Dish>()
                .eq("category_id", categoryId)
                .eq(status != null, "status", status)
                .orderByDesc("update_time", "id"));
    }

    /** 按可选名称包含匹配、分类和状态分页，按更新时间和 id 降序。 */
    default IPage<Dish> pageCatalog(IPage<Dish> page, String name, Long categoryId, Integer status) {
        return selectPage(page, new QueryWrapper<Dish>()
                .like(StringUtils.hasText(name), "name", name == null ? null : name.trim())
                .eq(categoryId != null, "category_id", categoryId)
                .eq(status != null, "status", status)
                .orderByDesc("update_time", "id"));
    }
}
