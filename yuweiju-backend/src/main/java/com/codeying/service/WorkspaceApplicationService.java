package com.codeying.service;

import com.codeying.vo.admin.workspace.BusinessDataVO;
import com.codeying.vo.admin.workspace.OverviewOrdersVO;
import com.codeying.vo.admin.workspace.OverviewVO;

/**
 * Workspace Application Service service interface.
 *
 * @author Endercloud
 */
public interface WorkspaceApplicationService {
    /**
     * Execute businessData.
     *
     * @return BusinessDataVO result
     */
    BusinessDataVO businessData();
    /**
     * Execute overviewDishes.
     *
     * @return OverviewVO result
     */
    OverviewVO overviewDishes();
    /**
     * Execute overviewSetmeals.
     *
     * @return OverviewVO result
     */
    OverviewVO overviewSetmeals();
    /**
     * Execute overviewOrders.
     *
     * @return OverviewOrdersVO result
     */
    OverviewOrdersVO overviewOrders();
}

