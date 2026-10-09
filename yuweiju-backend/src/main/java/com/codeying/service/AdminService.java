package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.entity.Admin;

/**
 * Admin Service interface.
 *
 * @author Endercloud
 */
public interface AdminService extends IService<Admin> {
    /** 旧模板登录查找；失败返回 null，captcha/session 由入口处理。 */
    Admin findForLogin(String username, String password);
    /** 旧模板注册；已存在返回 false，保留既有明文赋值。 */
    boolean register(String username, String password);
    /** 旧模板管理员分页；保留默认分页和筛选排序口径。 */
    com.baomidou.mybatisplus.core.metadata.IPage<Admin> pageLegacy(Integer pageIndex, Integer size, String username, String name);
    /** 旧模板保存；新建重名返回 false，编辑沿用主键 CRUD。 */
    boolean saveLegacy(Admin admin);
}
