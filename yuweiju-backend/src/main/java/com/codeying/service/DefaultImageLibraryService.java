package com.codeying.service;

import com.codeying.vo.admin.common.DefaultImageVO;

import java.util.List;

/**
 * 默认图片库查询服务。
 *
 * @author Endercloud
 */
public interface DefaultImageLibraryService {

    /**
     * 查询默认图片列表。
     *
     * @param scene 图片场景
     * @param limit 返回上限
     * @return 图片列表
     */
    List<DefaultImageVO> listDefaultImages(String scene, Integer limit);
}