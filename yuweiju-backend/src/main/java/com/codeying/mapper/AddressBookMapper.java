package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.codeying.entity.AddressBook;
import java.util.List;

/** Bound user conditions for address persistence; ordinary primary-key CRUD remains BaseMapper. */
public interface AddressBookMapper extends BaseMapper<AddressBook> {
    /** Owned addresses in the public default/newest order. */
    default List<AddressBook> findByUser(Long userId) {
        return selectList(new QueryWrapper<AddressBook>().eq("user_id", userId)
                .orderByDesc("is_default").orderByDesc("id"));
    }
    /** Count all addresses owned by a user. */
    default long countByUser(Long userId) {
        return selectCount(new QueryWrapper<AddressBook>().eq("user_id", userId));
    }
    /** Find an ID within the owner's scope, or null. */
    default AddressBook findOwned(Long userId, Long id) {
        return selectOne(new QueryWrapper<AddressBook>().eq("user_id", userId).eq("id", id));
    }
    /** Newest default within the owner's scope, or null. */
    default AddressBook findDefaultByUser(Long userId) {
        return selectOne(new QueryWrapper<AddressBook>().eq("user_id", userId).eq("is_default", 1)
                .orderByDesc("id").last("limit 1"));
    }
    /** Latest remaining address used by the existing default replacement rule. */
    default AddressBook findLatestByUser(Long userId) {
        return selectOne(new QueryWrapper<AddressBook>().eq("user_id", userId).orderByDesc("id").last("limit 1"));
    }
    /** Clear defaults other than the selected owned ID; no cross-user update. */
    default int clearDefaultExcept(Long userId, Long keepId) {
        return update(null, new UpdateWrapper<AddressBook>().eq("user_id", userId).ne("id", keepId)
                .eq("is_default", 1).set("is_default", 0));
    }
    /** Set the default flag only for an owned ID. */
    default int setDefaultOwned(Long userId, Long id) {
        return update(null, new UpdateWrapper<AddressBook>().eq("user_id", userId).eq("id", id).set("is_default", 1));
    }
    /** Update the supplied address fields under a bound owner/ID condition. */
    default int updateOwned(Long userId, AddressBook address) {
        return update(address, new UpdateWrapper<AddressBook>().eq("user_id", userId).eq("id", address.getId()));
    }
    /** Delete a single owned ID. */
    default int deleteOwned(Long userId, Long id) {
        return delete(new QueryWrapper<AddressBook>().eq("user_id", userId).eq("id", id));
    }
}
