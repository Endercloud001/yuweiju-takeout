package com.codeying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeying.entity.CustomerServiceMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人工客服消息 Mapper。
 *
 * @author Endercloud
 */
@Mapper
public interface CustomerServiceMessageMapper extends BaseMapper<CustomerServiceMessage> {}
