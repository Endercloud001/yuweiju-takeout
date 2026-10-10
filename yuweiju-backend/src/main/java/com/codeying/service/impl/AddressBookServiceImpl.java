package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.dto.user.addressbook.AddressBookDTO;
import com.codeying.entity.AddressBook;
import com.codeying.exception.OrderBusinessException;
import com.codeying.mapper.AddressBookMapper;
import com.codeying.service.AddressBookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;

/** Address rules and transaction boundaries shared by HTTP and other callers. */
@Service
public class AddressBookServiceImpl extends ServiceImpl<AddressBookMapper, AddressBook> implements AddressBookService {
    private final AddressBookMapper addresses;

    public AddressBookServiceImpl(AddressBookMapper addresses) {
        this.addresses = addresses;
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new OrderBusinessException("未登录");
    }

    private AddressBook fields(AddressBookDTO body) {
        if (body == null) throw new OrderBusinessException("参数错误");
        if (!StringUtils.hasText(body.getPhone())) throw new OrderBusinessException("手机号不能为空");
        if (!StringUtils.hasText(body.getDetail())) throw new OrderBusinessException("详细地址不能为空");
        AddressBook address = new AddressBook();
        address.setConsignee(body.getConsignee());
        address.setSex(body.getSex());
        address.setPhone(body.getPhone().trim());
        address.setProvinceCode(body.getProvinceCode());
        address.setProvinceName(body.getProvinceName());
        address.setCityCode(body.getCityCode());
        address.setCityName(body.getCityName());
        address.setDistrictCode(body.getDistrictCode());
        address.setDistrictName(body.getDistrictName());
        address.setDetail(body.getDetail().trim());
        address.setLabel(body.getLabel());
        return address;
    }

    @Override
    @Transactional
    public void createForUser(Long userId, AddressBookDTO body) {
        requireUser(userId);
        AddressBook address = fields(body);
        address.setUserId(userId);
        boolean first = addresses.countByUser(userId) == 0;
        address.setIsDefault(first || Integer.valueOf(1).equals(body.getIsDefault()) ? 1 : 0);
        if (addresses.insert(address) != 1) throw new OrderBusinessException("地址写入失败");
        if (!first && Integer.valueOf(1).equals(address.getIsDefault())) {
            addresses.clearDefaultExcept(userId, address.getId());
        }
    }

    @Override
    public List<AddressBook> listForUser(Long userId) {
        requireUser(userId);
        return addresses.findByUser(userId);
    }

    @Override
    public AddressBook defaultForUser(Long userId) {
        requireUser(userId);
        return addresses.findDefaultByUser(userId);
    }

    @Override
    public AddressBook findOwned(Long userId, Long id) {
        requireUser(userId);
        if (id == null) throw new OrderBusinessException("参数错误");
        AddressBook address = addresses.findOwned(userId, id);
        if (address == null) throw new OrderBusinessException("地址不存在");
        return address;
    }

    @Override
    @Transactional
    public void updateForUser(Long userId, AddressBookDTO body) {
        requireUser(userId);
        if (body == null || body.getId() == null) throw new OrderBusinessException("参数错误");
        findOwned(userId, body.getId());
        AddressBook address = fields(body);
        address.setId(body.getId());
        // Existing update API ignores isDefault; only create/default-switch may change it.
        if (addresses.updateOwned(userId, address) != 1) throw new OrderBusinessException("地址写入失败");
    }

    @Override
    @Transactional
    public void deleteForUser(Long userId, Long id) {
        AddressBook existing = findOwned(userId, id);
        if (addresses.deleteOwned(userId, id) != 1) throw new OrderBusinessException("地址写入失败");
        if (Integer.valueOf(1).equals(existing.getIsDefault())) {
            AddressBook next = addresses.findLatestByUser(userId);
            if (next != null && addresses.setDefaultOwned(userId, next.getId()) != 1) {
                throw new OrderBusinessException("地址写入失败");
            }
        }
    }

    @Override
    @Transactional
    public void setDefaultForUser(Long userId, Long id) {
        findOwned(userId, id);
        addresses.clearDefaultExcept(userId, id);
        if (addresses.setDefaultOwned(userId, id) != 1) throw new OrderBusinessException("地址写入失败");
    }
}
