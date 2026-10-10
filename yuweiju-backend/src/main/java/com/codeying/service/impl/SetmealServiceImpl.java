package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.Setmeal;
import com.codeying.mapper.SetmealMapper;
import com.codeying.service.SetmealService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 套餐业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper, Setmeal> implements SetmealService {

    private final SetmealMapper catalogMapper;

    public SetmealServiceImpl(SetmealMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Override
    public Long countByCategory(Long categoryId) {
        return catalogMapper.countByCategory(categoryId);
    }

    @Override
    public Long countSellingInIds(List<Long> ids) {
        return catalogMapper.countSellingInIds(ids);
    }

    @Override
    public List<Setmeal> listCatalog(Long categoryId, Integer status) {
        return catalogMapper.listCatalog(categoryId, status);
    }

    @Override
    public IPage<Setmeal> pageCatalog(IPage<Setmeal> page, String name, Long categoryId, Integer status) {
        return catalogMapper.pageCatalog(page, name, categoryId, status);
    }
}
