package com.codeying.controller.user;

import com.codeying.properties.ShopProperties;
import com.codeying.result.ApiResult;
import com.codeying.service.ShopStatusService;
import com.codeying.vo.user.shop.ShopInfoVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端店铺信息接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/shop")
public class UserShopController {

    private final ShopStatusService shopStatusService;
    private final ShopProperties shopProperties;

    public UserShopController(ShopStatusService shopStatusService, ShopProperties shopProperties) {
        this.shopStatusService = shopStatusService;
        this.shopProperties = shopProperties;
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
     * Execute info.
     *
     * @return api response payload
     */
    @GetMapping("/info")
    public ApiResult<ShopInfoVO> info() {
        ShopInfoVO vo = new ShopInfoVO();
        vo.setStatus(shopStatusService.getStatus());
        vo.setAddress(shopProperties.getAddress());
        return ApiResult.successData(vo);
    }
}
