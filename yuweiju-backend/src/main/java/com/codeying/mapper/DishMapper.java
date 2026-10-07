package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.Dish;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 菜品 Mapper。
 *
 * @author Endercloud
 */
public interface DishMapper extends BaseMapper<Dish> {
    /** Count items in the requested sale status. */
    @Select("select count(*) from dish where status = #{status}")
    Long countByStatus(@Param("status") int status);
}
