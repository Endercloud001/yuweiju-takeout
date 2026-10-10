package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codeying.entity.Setmeal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.util.StringUtils;

/**
 * 套餐 Mapper。
 *
 * @author Endercloud
 */
public interface SetmealMapper extends BaseMapper<Setmeal> {
    /** Count items in the requested sale status. */
    @Select("select count(*) from setmeal where status = #{status}")
    Long countByStatus(@Param("status") int status);

    /** 统计分类下的全部套餐，供分类删除关联检查。 */
    default Long countByCategory(Long categoryId) {
        return selectCount(new QueryWrapper<Setmeal>()
                .eq("category_id", categoryId));
    }

    /** 统计指定 ID 中售卖的套餐，供整个删除批次的拒绝判断。 */
    default Long countSellingInIds(List<Long> ids) {
        if (ids.isEmpty()) return 0L;
        return selectCount(new QueryWrapper<Setmeal>()
                .in("id", ids)
                .eq("status", 1));
    }

    /** 按可选分类和售卖状态读取套餐，按更新时间和 id 降序。 */
    default List<Setmeal> listCatalog(Long categoryId, Integer status) {
        return selectList(new QueryWrapper<Setmeal>()
                .eq(categoryId != null, "category_id", categoryId)
                .eq(status != null, "status", status)
                .orderByDesc("update_time", "id"));
    }

    /** 按可选名称包含匹配、分类和状态分页，按更新时间和 id 降序。 */
    default IPage<Setmeal> pageCatalog(IPage<Setmeal> page, String name, Long categoryId, Integer status) {
        return selectPage(page, new QueryWrapper<Setmeal>()
                .like(StringUtils.hasText(name), "name", name == null ? null : name.trim())
                .eq(categoryId != null, "category_id", categoryId)
                .eq(status != null, "status", status)
                .orderByDesc("update_time", "id"));
    }
}
