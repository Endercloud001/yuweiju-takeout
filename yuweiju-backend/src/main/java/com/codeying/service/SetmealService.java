package com.codeying.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.Setmeal;
import java.util.List;

/**
 * 套餐业务服务。
 *
 * @author Endercloud
 */
public interface SetmealService extends IService<Setmeal> {
    /** 统计分类下的全部套餐，供分类删除关联检查。 */
    Long countByCategory(Long categoryId);

    /** 统计指定 ID 中售卖的套餐，供整个删除批次的拒绝判断。 */
    Long countSellingInIds(List<Long> ids);

    /** 按可选分类和售卖状态读取套餐，按更新时间和 id 降序。 */
    List<Setmeal> listCatalog(Long categoryId, Integer status);

    /** 按可选名称包含匹配、分类和状态分页，按更新时间和 id 降序。 */
    IPage<Setmeal> pageCatalog(IPage<Setmeal> page, String name, Long categoryId, Integer status);
}
