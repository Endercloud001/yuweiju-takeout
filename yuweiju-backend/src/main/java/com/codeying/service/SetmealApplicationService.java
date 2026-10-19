package com.codeying.service;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.setmeal.SetmealDTO;
import com.codeying.dto.admin.setmeal.SetmealPageQuery;
import com.codeying.entity.Setmeal;

import java.util.List;

/**
 * 套餐应用服务。
 *
 * @author Endercloud
 */
public interface SetmealApplicationService {
    /**
     * 新建目录并校验可精确存储的非负金额、匹配分类及非空有效组成；组成菜允许停售。
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void create(Long adminId, SetmealDTO body);
    /**
     * 更新目录并校验有效值；省略金额、分类及组成沿用原值，非法输入或持久化失败整体回滚。
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void update(Long adminId, SetmealDTO body);
    /**
     * Delete resource.
     *
     * @param adminId business identifier
     * @param ids business identifier list
     */
    void delete(Long adminId, String ids);
    /**
     * Change business status.
     *
     * @param adminId business identifier
     * @param status status value
     * @param id business identifier
     */
    void setStatus(Long adminId, Integer status, Long id);
    /**
     * Get resource data.
     *
     * @param id business identifier
     * @return com.codeying.vo.admin.setmeal.SetmealVO result
     */
    com.codeying.vo.admin.setmeal.SetmealVO getById(Long id);
    /**
     * Query resource list.
     *
     * @param categoryId business identifier
     * @param status status value
     * @return list data result
     */
    List<Setmeal> list(Long categoryId, Integer status);
    /**
     * Query paged data.
     *
     * @param page paging parameter
     * @param pageSize paging parameter
     * @param name name parameter
     * @param categoryId business identifier
     * @param status status value
     * @return paged data result
     */
    PageData<com.codeying.vo.admin.setmeal.SetmealPageVO> page(Integer page, Integer pageSize, String name, Long categoryId, Integer status);
    /**
     * Query paged data.
     *
     * @param query query parameters
     * @return paged data result
     */
    PageData<com.codeying.vo.admin.setmeal.SetmealPageVO> page(SetmealPageQuery query);
    /**
     * Execute userDishes.
     *
     * @param setmealId business identifier
     * @return list data result
     */
    List<com.codeying.vo.user.setmeal.SetmealDishVO> userDishes(Long setmealId);
}
