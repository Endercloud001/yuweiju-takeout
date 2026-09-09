package com.codeying.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codeying.result.ApiResult;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.entity.AddressBook;
import com.codeying.service.AddressBookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端地址簿接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/user/addressBook")
public class UserAddressBookController {

    private final AddressBookService addressBookService;

    public UserAddressBookController(AddressBookService addressBookService) {
        this.addressBookService = addressBookService;
    }

    /**
     * 新增收货地址。
     *
     * @param body    地址信息
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PostMapping
    @Transactional
    public ApiResult<Object> create(@RequestBody @Valid com.codeying.dto.user.addressbook.AddressBookDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        AddressBook entity = new AddressBook();
        entity.setUserId(userId);
        entity.setConsignee(body.getConsignee());
        entity.setSex(body.getSex());
        entity.setPhone(body.getPhone().trim());
        entity.setProvinceCode(body.getProvinceCode());
        entity.setProvinceName(body.getProvinceName());
        entity.setCityCode(body.getCityCode());
        entity.setCityName(body.getCityName());
        entity.setDistrictCode(body.getDistrictCode());
        entity.setDistrictName(body.getDistrictName());
        entity.setDetail(body.getDetail().trim());
        entity.setLabel(body.getLabel());

        boolean isFirst = addressBookService.count(new QueryWrapper<AddressBook>().eq("user_id", userId)) == 0;
        Integer isDefault = body.getIsDefault();
        if (isFirst) {
            entity.setIsDefault(1);
        } else {
            entity.setIsDefault(isDefault != null && isDefault == 1 ? 1 : 0);
        }

        addressBookService.save(entity);
        if (!isFirst && entity.getIsDefault() != null && entity.getIsDefault() == 1) {
            clearDefaultExcept(userId, entity.getId());
        }
        return ApiResult.success();
    }

    /**
     * 查询当前用户地址列表。
     *
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 地址列表
     */
    @GetMapping("/list")
    public ApiResult<List<AddressBook>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("is_default").orderByDesc("id");
        return ApiResult.successData(addressBookService.list(wrapper));
    }

    /**
     * 获取当前用户默认地址。
     *
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 默认地址
     */
    @GetMapping("/default")
    public ApiResult<AddressBook> getDefault(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("is_default", 1);
        wrapper.orderByDesc("id");
        wrapper.last("limit 1");
        return ApiResult.successData(addressBookService.getOne(wrapper));
    }

    /**
     * 修改地址信息。
     *
     * @param body    地址信息（需包含 ID）
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PutMapping
    public ApiResult<Object> update(@RequestBody @Valid com.codeying.dto.user.addressbook.AddressBookDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (body.getId() == null) return ApiResult.badRequest("参数错误");

        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("id", body.getId());
        wrapper.eq("user_id", userId);
        AddressBook existing = addressBookService.getOne(wrapper);
        if (existing == null) return ApiResult.badRequest("地址不存在");

        AddressBook entity = new AddressBook();
        entity.setId(body.getId());
        entity.setConsignee(body.getConsignee());
        entity.setSex(body.getSex());
        entity.setPhone(body.getPhone().trim());
        entity.setProvinceCode(body.getProvinceCode());
        entity.setProvinceName(body.getProvinceName());
        entity.setCityCode(body.getCityCode());
        entity.setCityName(body.getCityName());
        entity.setDistrictCode(body.getDistrictCode());
        entity.setDistrictName(body.getDistrictName());
        entity.setDetail(body.getDetail().trim());
        entity.setLabel(body.getLabel());

        addressBookService.update(entity, wrapper);
        return ApiResult.success();
    }

    /**
     * 删除地址。
     *
     * @param id      地址 ID
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @DeleteMapping
    @Transactional
    public ApiResult<Object> delete(@RequestParam("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (id == null) return ApiResult.badRequest("参数错误");

        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        wrapper.eq("user_id", userId);
        AddressBook existing = addressBookService.getOne(wrapper);
        if (existing == null) return ApiResult.badRequest("地址不存在");

        addressBookService.remove(wrapper);

        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            QueryWrapper<AddressBook> pickWrapper = new QueryWrapper<>();
            pickWrapper.eq("user_id", userId);
            pickWrapper.orderByDesc("id");
            pickWrapper.last("limit 1");
            AddressBook next = addressBookService.getOne(pickWrapper);
            if (next != null) {
                AddressBook setDefault = new AddressBook();
                setDefault.setId(next.getId());
                setDefault.setIsDefault(1);
                QueryWrapper<AddressBook> updateWrapper = new QueryWrapper<>();
                updateWrapper.eq("id", next.getId());
                updateWrapper.eq("user_id", userId);
                addressBookService.update(setDefault, updateWrapper);
            }
        }

        return ApiResult.success();
    }

    /**
     * 根据 ID 查询地址详情。
     *
     * @param id      地址 ID
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 地址详情
     */
    @GetMapping("/{id}")
    public ApiResult<AddressBook> getById(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        if (id == null) return ApiResult.badRequest("参数错误");

        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        wrapper.eq("user_id", userId);
        AddressBook entity = addressBookService.getOne(wrapper);
        if (entity == null) return ApiResult.badRequest("地址不存在");
        return ApiResult.successData(entity);
    }

    /**
     * 设置默认地址。
     *
     * @param body    请求体（包含地址 ID）
     * @param request HTTP 请求（用于获取用户 ID）
     * @return 操作结果
     */
    @PutMapping("/default")
    @Transactional
    public ApiResult<Object> setDefault(@RequestBody @Valid com.codeying.dto.user.common.IdDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");

        QueryWrapper<AddressBook> existsWrapper = new QueryWrapper<>();
        existsWrapper.eq("id", body.getId());
        existsWrapper.eq("user_id", userId);
        AddressBook exists = addressBookService.getOne(existsWrapper);
        if (exists == null) return ApiResult.badRequest("地址不存在");

        clearDefaultExcept(userId, body.getId());

        AddressBook entity = new AddressBook();
        entity.setId(body.getId());
        entity.setIsDefault(1);
        addressBookService.update(entity, existsWrapper);
        return ApiResult.success();
    }

    private void clearDefaultExcept(Long userId, Long keepId) {
        AddressBook clear = new AddressBook();
        clear.setIsDefault(0);
        QueryWrapper<AddressBook> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (keepId != null) wrapper.ne("id", keepId);
        wrapper.eq("is_default", 1);
        addressBookService.update(clear, wrapper);
    }

    private Long getUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        if (userIdObj instanceof Long userId) return userId;
        return null;
    }

    // 入参与校验已由 DTO 承担
}
