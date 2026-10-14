package com.codeying.mapper;

import com.codeying.entity.UserBehaviorFeatureSnapshot;
import com.codeying.entity.DishTimeseriesFeatureSnapshot;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/** Feature statistics from completed history. Half-open windows and catalog membership stay explicit. */
public interface AnalysisFeatureMapper {
    List<UserBehaviorFeatureSnapshot> buildUserSnapshots(@Param("date") LocalDate date,
            @Param("begin") Date begin, @Param("end") Date end, @Param("low") double low, @Param("mid") double mid);
    List<DishTimeseriesFeatureSnapshot> buildDishSnapshots(@Param("begin") Date begin, @Param("end") Date end,
            @Param("oneDay") Date oneDay, @Param("sevenDays") Date sevenDays,
            @Param("date") LocalDate date, @Param("lambda") double lambda);
}
