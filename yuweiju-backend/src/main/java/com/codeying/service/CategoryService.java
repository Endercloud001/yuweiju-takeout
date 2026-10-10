package com.codeying.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.Category;
import java.util.List;

/**
 * 分类业务服务。
 *
 * @author Endercloud
 */
public interface CategoryService extends IService<Category> {
    /** 按可选类型读取管理端分类，包含禁用分类，按 sort 升序、id 降序。 */
    List<Category> listByType(Integer type);

    /** 读取用户可见的启用分类，按 type、sort 升序、id 降序。 */
    List<Category> listEnabled(Integer type);

    /** 按可选名称包含匹配和类型分页，按 sort 升序、更新时间和 id 降序。 */
    IPage<Category> pageCatalog(IPage<Category> page, String name, Integer type);
}
