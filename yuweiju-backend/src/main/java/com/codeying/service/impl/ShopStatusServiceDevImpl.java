package com.codeying.service.impl;

import com.codeying.service.ShopStatusService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Shop Status Service Dev Impl.
 *
 * @author Endercloud
 */
@Service
@Profile("dev")
public class ShopStatusServiceDevImpl implements ShopStatusService {
    @Override
    public int getStatus() {
        return 1;
    }

    @Override
    public void setStatus(int status) {
    }
}
