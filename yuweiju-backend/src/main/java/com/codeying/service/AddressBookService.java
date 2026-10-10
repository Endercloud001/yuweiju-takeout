package com.codeying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codeying.dto.user.addressbook.AddressBookDTO;
import com.codeying.entity.AddressBook;
import java.util.List;

/** User-owned address use cases. Invalid identity, payload or ownership raises a business error. */
public interface AddressBookService extends IService<AddressBook> {
    /** Create an address; the first is default, an explicit default replaces the former one atomically. */
    void createForUser(Long userId, AddressBookDTO body);
    /** List only this user's addresses, default first then newest ID. */
    List<AddressBook> listForUser(Long userId);
    /** Return the newest default or null when absent. */
    AddressBook defaultForUser(Long userId);
    /** Return an owned address, refusing missing/foreign IDs. */
    AddressBook findOwned(Long userId, Long id);
    /** Update address fields while preserving the stored default flag. */
    void updateForUser(Long userId, AddressBookDTO body);
    /** Delete an owned address, replacing a deleted default with the latest remaining ID. */
    void deleteForUser(Long userId, Long id);
    /** Atomically clear the former default and select the owned address. */
    void setDefaultForUser(Long userId, Long id);
}
