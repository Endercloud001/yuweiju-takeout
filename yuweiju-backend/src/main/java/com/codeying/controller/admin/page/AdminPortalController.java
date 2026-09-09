package com.codeying.controller.admin.page;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.controller.common.BaseController;
import com.codeying.entity.Admin;
import com.codeying.entity.LoginUser;
import com.codeying.service.AdminService;
import com.codeying.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Date;

/**
 * 管理端门户页面控制器：登录、注册、首页跳转等。
 *
 * @author Endercloud
 */
@Controller
public class AdminPortalController extends BaseController {

    @Autowired
    protected AdminService adminService;

    /**
     * 根路径入口：未登录跳转登录页，已登录跳转工作台入口。
     *
     * @return 页面跳转
     */
    @RequestMapping("/")
    public String index() {
        LoginUser user = getCurrentUser();
        if (user == null) return "login";
        return "redirect:/hello";
    }

    /**
     * 示例页入口。
     *
     * @return 页面模板路径
     */
    @RequestMapping("hello")
    public String hello() {
        return "hello";
    }

    /**
     * 注册页（GET）。
     *
     * @return 页面模板路径
     */
    @GetMapping("register")
    public String register() {
        return "register";
    }

    /**
     * 登录页（GET）。
     *
     * @return 页面模板路径
     */
    @GetMapping("login")
    public String login() {
        return "login";
    }

    /**
     * 登录（POST）。
     *
     * @param captcha  验证码
     * @param username 用户名
     * @param password 密码
     * @param usertype 用户类型（当前支持 admin）
     * @return 页面跳转
     * @throws Exception 编码设置等异常
     */
    @PostMapping("login")
    public String login(String captcha, String username, String password, String usertype) throws Exception {
        req.setCharacterEncoding("utf-8");

        String captchaOrigin = (String) req.getSession().getAttribute("captcha");
        if (captcha == null || !captcha.equalsIgnoreCase(captchaOrigin)) {
            req.setAttribute("message", "验证码错误！");
            return "login";
        }

        LoginUser loginUser;
        if ("admin".equals(usertype)) {
            QueryWrapper<Admin> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username);
            wrapper.eq("password", password);
            loginUser = adminService.getOne(wrapper);
            if (loginUser != null) {
                req.getSession().setAttribute("user", loginUser);
                req.getSession().setAttribute("role", "admin");
                return "redirect:/hello";
            }
        }

        req.setAttribute("message", "账号密码有误，登陆失败");
        return "login";
    }

    /**
     * 注册（POST）。
     *
     * @param username 用户名
     * @param password 密码
     * @param usertype 用户类型（当前支持 admin）
     * @return 页面跳转
     * @throws Exception 编码设置等异常
     */
    @PostMapping("register")
    public String register(String username, String password, String usertype) throws Exception {
        req.setCharacterEncoding("utf-8");
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            req.setAttribute("message", "账号密码不可为空！");
            return "register";
        }
        if ("admin".equals(usertype)) {
            QueryWrapper<Admin> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username);
            Admin temp = adminService.getOne(wrapper);
            if (temp != null) {
                req.setAttribute("message", "账号已存在！");
                return "register";
            }
            Admin admin = new Admin();
            admin.setUsername(username);
            admin.setPassword(password);
            admin.setId(CommonUtils.newId());
            admin.setCreatetime(new Date());
            adminService.save(admin);
            req.setAttribute("message", "注册成功，请登陆");
            return "login";
        }
        req.setAttribute("message", "请选择角色类型");
        return "register";
    }

    /**
     * 退出登录。
     *
     * @return 登录页
     */
    @RequestMapping("logout")
    public String logout() {
        req.getSession().removeAttribute("user");
        return "login";
    }
}
