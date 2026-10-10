package com.codeying.controller.user;

import com.codeying.dto.user.addressbook.AddressBookDTO;
import com.codeying.dto.user.common.IdDTO;
import com.codeying.entity.AddressBook;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.service.AddressBookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Authenticated address HTTP entry points; rules and transactions live in Service. */
@RestController
@RequestMapping("/user/addressBook")
public class UserAddressBookController {
    private final AddressBookService addressBookService;

    public UserAddressBookController(AddressBookService addressBookService) {
        this.addressBookService = addressBookService;
    }

    @PostMapping
    public ApiResult<Object> create(@RequestBody @Valid AddressBookDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        addressBookService.createForUser(userId, body);
        return ApiResult.success();
    }

    @GetMapping("/list")
    public ApiResult<List<AddressBook>> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(addressBookService.listForUser(userId));
    }

    @GetMapping("/default")
    public ApiResult<AddressBook> getDefault(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(addressBookService.defaultForUser(userId));
    }

    @PutMapping
    public ApiResult<Object> update(@RequestBody @Valid AddressBookDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        addressBookService.updateForUser(userId, body);
        return ApiResult.success();
    }

    @DeleteMapping
    public ApiResult<Object> delete(@RequestParam("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        addressBookService.deleteForUser(userId, id);
        return ApiResult.success();
    }

    @GetMapping("/{id}")
    public ApiResult<AddressBook> getById(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        return ApiResult.successData(addressBookService.findOwned(userId, id));
    }

    @PutMapping("/default")
    public ApiResult<Object> setDefault(@RequestBody @Valid IdDTO body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ApiResult.unauthorized("未登录");
        addressBookService.setDefaultForUser(userId, body.getId());
        return ApiResult.success();
    }

    private Long getUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        return value instanceof Long userId ? userId : null;
    }
}
