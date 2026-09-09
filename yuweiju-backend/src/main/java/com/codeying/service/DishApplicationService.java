package com.codeying.service;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.dish.DishDTO;
import com.codeying.dto.admin.dish.DishPageQuery;
import com.codeying.entity.Dish;

import java.util.List;

/**
 * 菜品应用服务。
 *
 * @author Endercloud
 */
public interface DishApplicationService {
    /**
     * Create resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void create(Long adminId, DishDTO body);
    /**
     * Update resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void update(Long adminId, DishDTO body);
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
     * @return com.codeying.vo.admin.dish.DishVO result
     */
    com.codeying.vo.admin.dish.DishVO getById(Long id);
    /**
     * Query resource list.
     *
     * @param categoryId business identifier
     * @return list data result
     */
    List<Dish> listByCategory(Long categoryId);
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
    PageData<com.codeying.vo.admin.dish.DishPageVO> page(Integer page, Integer pageSize, String name, Long categoryId, Integer status);
    /**
     * Query paged data.
     *
     * @param query query parameters
     * @return paged data result
     */
    PageData<com.codeying.vo.admin.dish.DishPageVO> page(DishPageQuery query);
    /**
     * Execute userList.
     *
     * @param categoryId business identifier
     * @return list data result
     */
    List<com.codeying.vo.user.dish.DishVO> userList(Long categoryId);
}
