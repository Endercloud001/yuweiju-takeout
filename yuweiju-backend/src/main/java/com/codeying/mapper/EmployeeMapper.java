package com.codeying.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codeying.entity.Employee;
import org.springframework.util.StringUtils;

/** 员工业务查询；保留标准主键 CRUD。 */
public interface EmployeeMapper extends BaseMapper<Employee> {
    /** 兼容既有密码等值与启用状态筛选，不改变密码策略。 */
    default Employee findEnabledByCredentials(String username, String password) {
        return selectOne(new QueryWrapper<Employee>().eq("username", username)
                .eq("password", password).eq("status", 1));
    }

    /** 姓名模糊筛选，更新时间和主键均降序；空白姓名不筛选。 */
    default IPage<Employee> pageByName(IPage<Employee> page, String name) {
        QueryWrapper<Employee> query = new QueryWrapper<>();
        if (StringUtils.hasText(name)) query.like("name", name.trim());
        return selectPage(page, query.orderByDesc("update_time").orderByDesc("id"));
    }

    /** 用户名冲突计数；编辑时排除当前主键。 */
    default long countByUsernameExcludingId(String username, Long excludedId) {
        QueryWrapper<Employee> query = new QueryWrapper<Employee>().eq("username", username);
        if (excludedId != null) query.ne("id", excludedId);
        return selectCount(query);
    }
}
