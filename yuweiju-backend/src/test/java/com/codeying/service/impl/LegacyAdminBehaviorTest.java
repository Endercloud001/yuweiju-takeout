package com.codeying.service.impl;

import com.codeying.entity.Admin;
import com.codeying.mapper.AdminMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyAdminBehaviorTest {
    @Test void legacyFooterPreservesRequestedPageAndDefaults() {
        var mapper = mock(AdminMapper.class); var service = new AdminServiceImpl(mapper);
        when(mapper.pageByUsernameAndName(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        var defaults = new com.codeying.vo.PagerFooterVO(service.pageLegacy(null, null, "", ""));
        assertEquals(1, defaults.getPageIndex()); assertEquals(15, defaults.getPageSize());
        for (int requested : new int[]{-1, 0, 1, 3}) {
            var footer = new com.codeying.vo.PagerFooterVO(service.pageLegacy(requested, 20, "", ""));
            assertEquals(requested, footer.getPageIndex()); assertEquals(20, footer.getPageSize());
        }
    }

    @Test void registrationExistingAndNewAndSaveConflict() {
        var mapper = mock(AdminMapper.class); var service = new AdminServiceImpl(mapper);
        when(mapper.findByUsername("existing")).thenReturn(new Admin());
        assertFalse(service.register("existing", "synthetic")); verify(mapper, never()).insert(any(Admin.class));
        assertTrue(service.register("new", "synthetic"));
        verify(mapper).insert(argThat((Admin a) -> a.getId() != null && a.getCreatetime() != null && "synthetic".equals(a.getPassword())));
        Admin admin = new Admin(); admin.setUsername("conflict"); when(mapper.usernameExists("conflict")).thenReturn(true);
        assertFalse(service.saveLegacy(admin));
    }
    @Test void oldLoginRetainsNullFailureAndCredentialLookup() {
        var mapper = mock(AdminMapper.class); var service = new AdminServiceImpl(mapper);
        assertNull(service.findForLogin("fixture", "wrong"));
        Admin admin = new Admin(); admin.setId("fixture-id");
        when(mapper.findByCredentials("fixture", "synthetic")).thenReturn(admin);
        assertEquals("fixture-id", service.findForLogin("fixture", "synthetic").getId());
    }
}
