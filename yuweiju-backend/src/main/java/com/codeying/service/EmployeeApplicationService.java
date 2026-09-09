package com.codeying.service;

import com.codeying.common.page.PageData;
import com.codeying.dto.admin.employee.EditPasswordDTO;
import com.codeying.dto.admin.employee.EmployeeDTO;
import com.codeying.dto.admin.employee.EmployeePageQuery;
import com.codeying.dto.admin.employee.LoginDTO;
import com.codeying.vo.admin.employee.EmployeeVO;
import com.codeying.vo.admin.employee.LoginVO;

/**
 * Employee Application Service service interface.
 *
 * @author Endercloud
 */
public interface EmployeeApplicationService {
    /**
     * Handle login request.
     *
     * @param body request payload
     * @return LoginVO result
     */
    LoginVO login(LoginDTO body);
    /**
     * Update resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void editPassword(Long adminId, EditPasswordDTO body);
    /**
     * Change business status.
     *
     * @param adminId business identifier
     * @param status status value
     * @param id business identifier
     */
    void setStatus(Long adminId, Integer status, Long id);
    /**
     * Query paged data.
     *
     * @param query query parameters
     * @return paged data result
     */
    PageData<EmployeeVO> page(EmployeePageQuery query);
    /**
     * Create resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void create(Long adminId, EmployeeDTO body);
    /**
     * Get resource data.
     *
     * @param id business identifier
     * @return EmployeeVO result
     */
    EmployeeVO getById(Long id);
    /**
     * Update resource.
     *
     * @param adminId business identifier
     * @param body request payload
     */
    void update(Long adminId, EmployeeDTO body);
}

