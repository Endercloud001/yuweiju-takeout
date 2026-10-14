package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.OrderRiskStandardizerMeta;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * Order risk standardizer metadata mapper.
 *
 * @author Endercloud
 */
@Mapper
public interface OrderRiskStandardizerMetaMapper extends BaseMapper<OrderRiskStandardizerMeta> {
    /** Replace the specified feature standardizer in the Service transaction. */
    default int deleteVersion(String version) {
        return delete(new QueryWrapper<OrderRiskStandardizerMeta>().eq("feature_version",version));
    }
}

