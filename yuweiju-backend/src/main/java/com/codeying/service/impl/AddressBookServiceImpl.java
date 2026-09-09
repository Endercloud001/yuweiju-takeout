package com.codeying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codeying.entity.AddressBook;
import com.codeying.mapper.AddressBookMapper;
import com.codeying.service.AddressBookService;
import org.springframework.stereotype.Service;

/**
 * 地址簿业务服务实现类。
 *
 * @author Endercloud
 */
@Service
public class AddressBookServiceImpl extends ServiceImpl<AddressBookMapper, AddressBook> implements AddressBookService {}
