package com.codeying.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 常用工具类。
 *
 * @author Endercloud
 */
public class CommonUtils {

    private static final AtomicInteger n = new AtomicInteger(100000);

    /**
     * 生成随机 ID（时间戳 + 自增序列）。
     *
     * @return 随机 ID
     */
    public static String getRandomIdByTime() {
        SimpleDateFormat simpleDateFormat;
        simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date();
        String str = simpleDateFormat.format(date);
        int num = n.getAndIncrement();
        return str+num;
    }

    /**
     * 生成新 ID。
     *
     * @return 新 ID
     */
    public static String newId(){
        return getRandomIdByTime();
    }

}

