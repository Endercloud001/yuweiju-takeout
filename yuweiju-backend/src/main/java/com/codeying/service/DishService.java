package com.codeying.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.Dish;
import java.util.List;

/**
 * 菜品业务服务。
 *
 * @author Endercloud
 */
public interface DishService extends IService<Dish> {
    /** 统计分类下的全部菜品，供分类删除关联检查。 */
    Long countByCategory(Long categoryId);

    /** 读取指定分类菜品，可选售卖状态，按更新时间和 id 降序。 */
    List<Dish> listCatalog(Long categoryId, Integer status);

    /** 按可选名称包含匹配、分类和状态分页，按更新时间和 id 降序。 */
    IPage<Dish> pageCatalog(IPage<Dish> page, String name, Long categoryId, Integer status);
}
