package com.codeying.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO;
import com.codeying.entity.Dish;
import com.codeying.entity.Setmeal;
import com.codeying.entity.ShoppingCart;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.DishService;
import com.codeying.service.SetmealService;
import com.codeying.service.ShoppingCartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 用户端购物车接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/shoppingCart")
public class UserShoppingCartController {

    private final ShoppingCartService shoppingCartService;
    private final DishService dishService;
    private final SetmealService setmealService;
    private final AnalysisObservationService analysisObservationService;

    public UserShoppingCartController(
            ShoppingCartService shoppingCartService,
            DishService dishService,
            SetmealService setmealService,
            AnalysisObservationService analysisObservationService
    ) {
        this.shoppingCartService = shoppingCartService;
        this.dishService = dishService;
        this.setmealService = setmealService;
        this.analysisObservationService = analysisObservationService;
    }

    /**
     * 添加购物车。
     *
     * @param body    请求体（菜品/套餐二选一）
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PostMapping("/add")
    @Transactional
    public ApiResult<Object> add(@RequestBody @Valid ShoppingCartChangeDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        Long dishId = body.getDishId();
        Long setmealId = body.getSetmealId();
        String dishFlavor = normalizeDishFlavor(body.getDishFlavor());

        ShoppingCart existing = findExisting(userId, dishId, setmealId, dishFlavor);
        if (existing != null) {
            ShoppingCart entity = new ShoppingCart();
            entity.setId(existing.getId());
            entity.setNumber(existing.getNumber() == null ? 2 : existing.getNumber() + 1);
            shoppingCartService.updateById(entity);
            if (dishId != null) {
                analysisObservationService.recordRecommendationClick(userId, dishId);
            }
            return ApiResult.success();
        }

        ShoppingCart entity = new ShoppingCart();
        entity.setUserId(userId);
        entity.setDishId(dishId);
        entity.setSetmealId(setmealId);
        entity.setDishFlavor(dishFlavor);
        entity.setNumber(1);
        entity.setCreateTime(new Date());

        if (dishId != null) {
            Dish dish = dishService.getById(dishId);
            if (dish == null) return ApiResult.badRequest("商品不存在");
            entity.setName(dish.getName());
            entity.setImage(dish.getImage());
            entity.setAmount(dish.getPrice());
        } else {
            Setmeal setmeal = setmealService.getById(setmealId);
            if (setmeal == null) return ApiResult.badRequest("商品不存在");
            entity.setName(setmeal.getName());
            entity.setImage(setmeal.getImage());
            entity.setAmount(setmeal.getPrice());
        }

        shoppingCartService.save(entity);
        if (dishId != null) {
            analysisObservationService.recordRecommendationClick(userId, dishId);
        }
        return ApiResult.success();
    }

    /**
     * 减少购物车商品数量（数量为 1 时删除该条目）。
     *
     * @param body    请求体（菜品/套餐二选一）
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PostMapping("/sub")
    @Transactional
    public ApiResult<Object> sub(@RequestBody @Valid ShoppingCartChangeDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        Long dishId = body.getDishId();
        Long setmealId = body.getSetmealId();
        String dishFlavor = normalizeDishFlavor(body.getDishFlavor());

        ShoppingCart existing = findExisting(userId, dishId, setmealId, dishFlavor);
        if (existing == null) return ApiResult.badRequest("购物车中无此商品");

        Integer number = existing.getNumber();
        if (number != null && number > 1) {
            ShoppingCart entity = new ShoppingCart();
            entity.setId(existing.getId());
            entity.setNumber(number - 1);
            shoppingCartService.updateById(entity);
        } else {
            shoppingCartService.removeById(existing.getId());
        }
        return ApiResult.success();
    }

    /**
     * 查询当前用户购物车列表。
     *
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 购物车列表
     */
    @GetMapping("/list")
    public ApiResult<List<ShoppingCart>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        QueryWrapper<ShoppingCart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByAsc("create_time").orderByAsc("id");
        return ApiResult.successData(shoppingCartService.list(wrapper));
    }

    /**
     * 清空当前用户购物车。
     *
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @DeleteMapping("/clean")
    public ApiResult<Object> clean(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        QueryWrapper<ShoppingCart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        shoppingCartService.remove(wrapper);
        return ApiResult.success();
    }

    private ShoppingCart findExisting(Long userId, Long dishId, Long setmealId, String dishFlavor) {
        QueryWrapper<ShoppingCart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (dishId != null) {
            wrapper.eq("dish_id", dishId);
        } else {
            wrapper.eq("setmeal_id", setmealId);
        }
        if (dishId != null) {
            if (dishFlavor == null) {
                wrapper.and(w -> w.isNull("dish_flavor").or().eq("dish_flavor", ""));
            } else {
                wrapper.eq("dish_flavor", dishFlavor);
            }
        }
        wrapper.last("limit 1");
        return shoppingCartService.getOne(wrapper);
    }

    private String normalizeDishFlavor(String dishFlavor) {
        if (!StringUtils.hasText(dishFlavor)) return null;
        return dishFlavor.trim();
    }

    private Long getUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        if (userIdObj instanceof Long userId) return userId;
        return null;
    }

    // 入参与校验由 DTO 保证
}
