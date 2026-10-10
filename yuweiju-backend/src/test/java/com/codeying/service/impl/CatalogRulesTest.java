package com.codeying.service.impl;

import com.codeying.entity.Category;
import com.codeying.entity.Dish;
import com.codeying.entity.Setmeal;
import com.codeying.exception.CategoryBusinessException;
import com.codeying.exception.DishBusinessException;
import com.codeying.exception.SetmealBusinessException;
import com.codeying.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.codeying.dto.admin.category.CategoryDTO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Rule checks only; actual SQL and proxy rollback are asserted by Issue8Probe. */
class CatalogRulesTest {
    @Test
    void categoryRefusesDeletionForItsApplicableAssociation() {
        var categories = mock(CategoryService.class);
        var dishes = mock(DishService.class);
        var setmeals = mock(SetmealService.class);
        var app = new CategoryApplicationServiceImpl(categories, dishes, setmeals);
        var c = new Category(); c.setId(10L); c.setType(1);
        when(categories.getById(10L)).thenReturn(c);
        when(dishes.countByCategory(10L)).thenReturn(1L);
        assertThrows(CategoryBusinessException.class, () -> app.delete(1L, 10L));
        verify(categories, never()).removeById(10L);
        c.setType(2);
        when(setmeals.countByCategory(10L)).thenReturn(1L);
        assertThrows(CategoryBusinessException.class, () -> app.delete(1L, 10L));
    }

    @Test
    void oneAssociatedDishRefusesEntireMixedBatchBeforeAnyDeletion() {
        var dishes = mock(DishService.class);
        var flavors = mock(DishFlavorService.class);
        var details = mock(SetmealDishService.class);
        var app = new DishApplicationServiceImpl(dishes, flavors, mock(CategoryService.class), details);
        when(details.countByDishes(java.util.List.of(10L, 20L))).thenReturn(1L);
        assertThrows(DishBusinessException.class, () -> app.delete(1L, "10,20"));
        verifyNoInteractions(flavors, dishes);
    }

    @Test
    void oneSellingSetmealRefusesEntireMixedBatchBeforeAnyDeletion() {
        var setmeals = mock(SetmealService.class);
        var details = mock(SetmealDishService.class);
        var app = new SetmealApplicationServiceImpl(setmeals, details, mock(CategoryService.class), mock(DishService.class));
        when(setmeals.countSellingInIds(java.util.List.of(10L, 20L))).thenReturn(1L);
        assertThrows(SetmealBusinessException.class, () -> app.delete(1L, "10,20"));
        verifyNoInteractions(details);
        verify(setmeals, never()).removeByIds(anyCollection());
    }

    @Test
    void invalidPageIsRefusedAndDuplicateNameRetainsCause() {
        var categories = mock(CategoryService.class);
        var app = new CategoryApplicationServiceImpl(categories, mock(DishService.class), mock(SetmealService.class));
        assertThrows(CategoryBusinessException.class, () -> app.page(0, 10, null, null));
        verifyNoInteractions(categories);
        var dto = new CategoryDTO(); dto.setName("duplicate"); dto.setType(1); dto.setSort(1);
        var cause = new DuplicateKeyException("isolated duplicate");
        when(categories.save(any(Category.class))).thenThrow(cause);
        assertSame(cause, assertThrows(CategoryBusinessException.class, () -> app.create(1L, dto)).getCause());
    }
}
