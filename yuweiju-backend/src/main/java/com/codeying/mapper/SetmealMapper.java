package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.Setmeal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 套餐 Mapper。
 *
 * @author Endercloud
 */
public interface SetmealMapper extends BaseMapper<Setmeal> {
    /** Count items in the requested sale status. */
    @Select("select count(*) from setmeal where status = #{status}")
    Long countByStatus(@Param("status") int status);
}
