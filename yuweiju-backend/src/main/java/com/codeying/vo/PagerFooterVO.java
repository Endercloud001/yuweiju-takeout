package com.codeying.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Arrays;
import java.util.List;

/**
 * 分页页脚数据。
 *
 * @author Endercloud
 */
public class PagerFooterVO {

    /** pageIndex field. */
    private long pageIndex;
    /** pageSize field. */
    private long pageSize;
    /** itemCount field. */
    private long itemCount;
    /** pageCount field. */
    private long pageCount;
    /** isFirst field. */
    private boolean isFirst;
    /** isLast field. */
    private boolean isLast;
    /** pageStart field. */
    private long pageStart;
    /** pageEnd field. */
    private long pageEnd;
    /** hasLessStart field. */
    private boolean hasLessStart;
    /** hasGreaterEnd field. */
    private boolean hasGreaterEnd;
/**
 * Arrays.asList
 * @return 
 */

    private List<Integer> pageSizeOptions = Arrays.asList(20, 50, 100);

    public PagerFooterVO(long pageIndex, long itemCount) {
        this(pageIndex, 20, itemCount);
    }

    public PagerFooterVO(long pageIndex, long pageSize, long itemCount) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.itemCount = itemCount;
        init();
    }

    public PagerFooterVO(IPage<?> iPage) {
        this(iPage.getCurrent(), iPage.getSize(), iPage.getTotal());
    }

    private void init() {
        double pc = itemCount * 1d / pageSize;
        pageCount = (long) Math.ceil(pc);

        isFirst = pageIndex <= 1;
        isLast = pageIndex >= pageCount;

        pageStart = pageIndex / 10 * 10;
        if (pageIndex % 10 == 0) {
            pageStart -= 10;
        }
        pageStart = pageStart + 1;

        pageEnd = pageStart + 9;
        if (pageEnd > pageCount) {
            pageEnd = pageCount;
        }
        if (pageCount == 0) {
            pageEnd = 1;
        }

        hasLessStart = (pageStart - 1) > 0;
        hasGreaterEnd = (pageEnd + 1) < pageCount;
    }

    public List<Integer> getPageSizeOptions() {
        return pageSizeOptions;
    }

    public void setPageSizeOptions(List<Integer> pageSizeOptions) {
        this.pageSizeOptions = pageSizeOptions;
    }

    public long getItemCount() {
        return itemCount;
    }

    public void setItemCount(long itemCount) {
        this.itemCount = itemCount;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(long pageIndex) {
        this.pageIndex = pageIndex;
    }

    public long getPageCount() {
        return pageCount;
    }

    public boolean getIsFirst() {
        return isFirst;
    }

    public boolean getIsLast() {
        return isLast;
    }

    public long getPageStart() {
        return pageStart;
    }

    public long getPageEnd() {
        return pageEnd;
    }

    public boolean getHasLessStart() {
        return hasLessStart;
    }

    public boolean getHasGreaterEnd() {
        return hasGreaterEnd;
    }
}
