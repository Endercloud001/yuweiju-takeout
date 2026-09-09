package com.codeying.controller.admin;

import com.codeying.result.ApiResult;
import com.codeying.service.WorkspaceApplicationService;
import com.codeying.vo.admin.workspace.BusinessDataVO;
import com.codeying.vo.admin.workspace.OverviewOrdersVO;
import com.codeying.vo.admin.workspace.OverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端工作台数据接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/workspace")
public class AdminWorkspaceController {

    private final WorkspaceApplicationService workspaceApplicationService;

    public AdminWorkspaceController(WorkspaceApplicationService workspaceApplicationService) {
        this.workspaceApplicationService = workspaceApplicationService;
    }

    /**
     * 获取今日经营数据（营业额、有效订单、订单完成率、客单价、新增用户）。
     *
     * @return 今日经营数据
     */
    @GetMapping("/businessData")
    public ApiResult<BusinessDataVO> businessData() {
        return ApiResult.successData(workspaceApplicationService.businessData());
    }

    /**
     * 获取套餐概览数据（起售/停售数量）。
     *
     * @return 概览数据
     */
    @GetMapping("/overviewSetmeals")
    public ApiResult<OverviewVO> overviewSetmeals() {
        return ApiResult.successData(workspaceApplicationService.overviewSetmeals());
    }

    /**
     * 获取菜品概览数据（起售/停售数量）。
     *
     * @return 概览数据
     */
    @GetMapping("/overviewDishes")
    public ApiResult<OverviewVO> overviewDishes() {
        return ApiResult.successData(workspaceApplicationService.overviewDishes());
    }

    /**
     * 获取订单概览数据（总单量、已取消、已完成、派送中、待接单）。
     *
     * @return 概览数据
     */
    @GetMapping("/overviewOrders")
    public ApiResult<OverviewOrdersVO> overviewOrders() {
        return ApiResult.successData(workspaceApplicationService.overviewOrders());
    }
}
