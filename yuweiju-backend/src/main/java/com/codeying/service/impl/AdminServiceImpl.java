package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeying.mapper.AdminMapper;
import com.codeying.entity.Admin;
import com.codeying.service.AdminService;
import com.codeying.utils.CommonUtils;
import org.springframework.stereotype.Service;
import java.util.Date;

/** 旧模板账户用例；不改变现有认证边界。 */
@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {
    public AdminServiceImpl(AdminMapper adminMapper) {
        this.baseMapper = adminMapper;
    }

    @Override
    public Admin findForLogin(String username, String password) {
        return baseMapper.findByCredentials(username, password);
    }

    @Override
    public boolean register(String username, String password) {
        if (baseMapper.findByUsername(username) != null) return false;
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPassword(password);
        admin.setId(CommonUtils.newId());
        admin.setCreatetime(new Date());
        save(admin);
        return true;
    }

    @Override
    public IPage<Admin> pageLegacy(Integer pageIndex, Integer size, String username, String name) {
        // Page(current, size) 会规范化非正页码；旧页脚需保留调用者传入的页码。
        Page<Admin> page = new Page<Admin>().setCurrent(pageIndex == null ? 1 : pageIndex)
                .setSize(size == null ? 15 : size);
        return baseMapper.pageByUsernameAndName(page, username, name);
    }

    @Override
    public boolean saveLegacy(Admin admin) {
        if (admin.getId() == null || admin.getId().isEmpty()) {
            admin.setId(CommonUtils.newId());
            admin.setCreatetime(new Date());
            if (baseMapper.usernameExists(admin.getUsername())) return false;
            save(admin);
        } else {
            updateById(admin);
        }
        return true;
    }
}
