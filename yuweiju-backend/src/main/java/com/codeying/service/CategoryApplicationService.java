package com.codeying.service;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.category.CategoryDTO;
import com.codeying.dto.admin.category.CategoryPageQuery;
import com.codeying.entity.Category;

import java.util.List;

/**
 * 分类应用服务。
 *
 * @author Endercloud
 */
public interface CategoryApplicationService {
    /**
     * Create resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void create(Long adminId, CategoryDTO body);
    /**
     * Update resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void update(Long adminId, CategoryDTO body);
    /**
     * Change business status.
     *
     * @param adminId business identifier
     * @param status status value
     * @param id business identifier
     */
    void setStatus(Long adminId, Integer status, Long id);
    /**
     * Delete resource.
     *
     * @param adminId business identifier
     * @param id business identifier
     */
    void delete(Long adminId, Long id);
    /**
     * Query resource list.
     *
     * @param type type parameter
     * @return list data result
     */
    List<Category> list(Integer type);
    /**
     * Query paged data.
     *
     * @param page paging parameter
     * @param pageSize paging parameter
     * @param name name parameter
     * @param type type parameter
     * @return paged data result
     */
    PageData<Category> page(Integer page, Integer pageSize, String name, Integer type);
    /**
     * Query paged data.
     *
     * @param query query parameters
     * @return paged data result
     */
    PageData<Category> page(CategoryPageQuery query);
}
