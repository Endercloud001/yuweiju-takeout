package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codeying.entity.Admin;
import org.springframework.util.StringUtils;

/** 旧模板管理员筛选；保持独立 tb_admin 与 session 语义。 */
public interface AdminMapper extends BaseMapper<Admin> {
    /** 旧模板密码等值查询，无新增状态/角色条件。 */
    default Admin findByCredentials(String username, String password) {
        return selectOne(new QueryWrapper<Admin>().eq("username", username).eq("password", password));
    }

    /** 注册使用 selectOne，保留重复行失败行为。 */
    default Admin findByUsername(String username) {
        return selectOne(new QueryWrapper<Admin>().eq("username", username));
    }

    /** 新建管理员时检查用户名占用。 */
    default boolean usernameExists(String username) {
        return !selectList(new QueryWrapper<Admin>().eq("username", username)).isEmpty();
    }

    /** 旧模板用户名/姓名模糊筛选、主键降序，空字符串不筛选。 */
    default IPage<Admin> pageByUsernameAndName(IPage<Admin> page, String username, String name) {
        return selectPage(page, new QueryWrapper<Admin>()
                .like(!StringUtils.isEmpty(username), "username", username)
                .like(!StringUtils.isEmpty(name), "name", name).orderByDesc("id"));
    }
}
