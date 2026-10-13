package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.DishHeatPredictionResult;

/**
 * 菜品热度预测结果 Mapper。
 *
 * @author Endercloud
 */
public interface DishHeatPredictionResultMapper extends BaseMapper<DishHeatPredictionResult> {
    /** Active heat recommendations in the existing score order for one window. */
    default java.util.List<DishHeatPredictionResult> listActiveByHeat(java.time.LocalDate date, int limit) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", date).eq("status", 1).orderByDesc("heat_score").last("limit " + limit));
    }

}
