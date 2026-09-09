package com.codeying.controller.admin;

import com.codeying.result.ApiResult;
import com.codeying.service.ShopStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端店铺营业状态接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/shop")
public class AdminShopController {

    private final ShopStatusService shopStatusService;

    public AdminShopController(ShopStatusService shopStatusService) {
        this.shopStatusService = shopStatusService;
    }

    /**
     * 获取店铺营业状态。
     *
     * @return 1-营业 0-打烊
     */
    @GetMapping("/status")
    public ApiResult<Integer> status() {
        return ApiResult.successData(shopStatusService.getStatus());
    }

    /**
     * 设置店铺营业状态。
     *
     * @param status 1-营业 0-打烊
     * @return 操作结果
     */
    @PutMapping("/{status}")
    public ApiResult<Object> setStatus(@PathVariable("status") Integer status) {
        if (status == null) return ApiResult.badRequest("参数错误");
        shopStatusService.setStatus(status);
        return ApiResult.success();
    }
}
