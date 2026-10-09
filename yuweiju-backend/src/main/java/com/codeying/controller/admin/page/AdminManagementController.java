package com.codeying.controller.admin.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codeying.controller.common.BaseController;
import com.codeying.entity.Admin;
import com.codeying.result.ApiResult;
import com.codeying.service.AdminService;
import com.codeying.vo.PagerFooterVO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * 管理端页面控制器：管理员管理。
 *
 * @author Endercloud
 */
@Controller
@RequestMapping("admin")
public class AdminManagementController extends BaseController {

    private final AdminService adminService;

    public AdminManagementController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 管理员列表页（分页）。
     *
     * @param model     视图模型
     * @param pageIndex 页码
     * @param size      每页大小
     * @param username  用户名关键字
     * @param name      姓名关键字
     * @return 页面模板路径
     */
    @RequestMapping("list")
    public String list(Model model, Integer pageIndex, Integer size, String username, String name) {
        IPage<Admin> pageInfo = adminService.pageLegacy(pageIndex, size, username, name);

        model.addAttribute("adminList", pageInfo.getRecords());
        model.addAttribute("pager", new PagerFooterVO(pageInfo));
        model.addAttribute("username", username);
        model.addAttribute("name", name);
        return "pages/admin-list";
    }

    /**
     * 管理员编辑页（新增/修改）。
     *
     * @param id    管理员 ID（为空表示新增）
     * @param model 视图模型
     * @return 页面模板路径
     */
    @RequestMapping("edit")
    public String edit(String id, Model model) {
        if (StringUtils.isEmpty(id)) {
            return "pages/admin-add";
        }
        Admin entity = adminService.getById(id);
        model.addAttribute("item", entity);
        return "pages/admin-edit";
    }

    /**
     * 管理员详情页。
     *
     * @param id    管理员 ID
     * @param model 视图模型
     * @return 页面模板路径
     */
    @RequestMapping("detail")
    public String detail(String id, Model model) {
        Admin entity = adminService.getById(id);
        model.addAttribute("item", entity);
        return "pages/admin-detail";
    }

    /**
     * 保存管理员信息（新增/修改）。
     *
     * @param entityTemp 管理员实体
     * @return 操作结果
     */
    @RequestMapping("save")
    @ResponseBody
    public ApiResult<Object> save(Admin entityTemp) {
        String id = entityTemp.getId();
        if (!adminService.saveLegacy(entityTemp)) return fail("用户名 已存在！");
        if (id != null && !id.isEmpty() && getCurrentUser().getId().equals(id)) {
            setSessionValue("user", adminService.getById(id));
        }
        return ApiResult.successMsg("保存成功");
    }

    /**
     * 删除管理员。
     *
     * @param id 管理员 ID
     * @return 操作结果
     */
    @RequestMapping("delete")
    @ResponseBody
    public ApiResult<Object> delete(String id) {
        boolean res = adminService.removeById(id);
        return res ? success() : fail();
    }
}
