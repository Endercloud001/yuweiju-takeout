package com.codeying.controller.user;

import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO;
import com.codeying.entity.ShoppingCart;
import com.codeying.service.ShoppingCartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Authenticated cart HTTP entry points; business rules live in Service. */
@RestController
@RequestMapping("/user/shoppingCart")
public class UserShoppingCartController {
    private final ShoppingCartService shoppingCartService;
    public UserShoppingCartController(ShoppingCartService shoppingCartService) { this.shoppingCartService = shoppingCartService; }

    @PostMapping("/add")
    public ApiResult<Object> add(@RequestBody @Valid ShoppingCartChangeDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        shoppingCartService.addItem(userId, body);
        return ApiResult.success();
    }
    @PostMapping("/sub")
    public ApiResult<Object> sub(@RequestBody @Valid ShoppingCartChangeDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        shoppingCartService.subtractItem(userId, body);
        return ApiResult.success();
    }
    @GetMapping("/list")
    public ApiResult<List<ShoppingCart>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(shoppingCartService.listForUser(userId));
    }
    @DeleteMapping("/clean")
    public ApiResult<Object> clean(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        shoppingCartService.clearForUser(userId);
        return ApiResult.success();
    }
    private Long getUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        return value instanceof Long userId ? userId : null;
    }
}
