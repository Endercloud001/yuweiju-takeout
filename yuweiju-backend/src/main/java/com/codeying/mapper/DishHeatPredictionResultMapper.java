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

    /** Replace only this prediction window; model files/cache are outside SQL rollback. */
    default int deleteWindow(java.time.LocalDate start, java.time.LocalDate end) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", start).eq("window_end", end));
    }
    default long countWindow(java.time.LocalDate start, java.time.LocalDate end) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", start).eq("window_end", end));
    }
    /** Latest persisted active model, without claiming this run trained it. */
    default DishHeatPredictionResult findLatestActive(java.time.LocalDate date) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", date).eq("status", 1).orderByDesc("id").last("limit 1"));
    }
}
