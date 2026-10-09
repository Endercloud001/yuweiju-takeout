package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Date;

/**
 * 用户 Mapper。
 *
 * @author Endercloud
 */
public interface UserMapper extends BaseMapper<User> {
    /** Inclusive new-user count for the supplied period. */
    @Select("select count(*) from user where create_time >= #{begin} and create_time <= #{end}")
    Long countCreatedInRange(@Param("begin") Date begin, @Param("end") Date end);

    /** Cumulative users through the inclusive end instant. */
    @Select("select count(*) from user where create_time <= #{end}")
    Long countCreatedThrough(@Param("end") Date end);
    /** 按微信身份查找用户；重复身份仍按 selectOne 的原有行为失败。 */
    default User findByOpenid(String openid) {
        return selectOne(new QueryWrapper<User>().eq("openid", openid));
    }
}
