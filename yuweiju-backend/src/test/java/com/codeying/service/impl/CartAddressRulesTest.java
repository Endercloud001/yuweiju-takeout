package com.codeying.service.impl;

import com.codeying.dto.user.addressbook.AddressBookDTO;
import com.codeying.dto.user.shoppingcart.ShoppingCartChangeDTO;
import com.codeying.entity.AddressBook;
import com.codeying.entity.Dish;
import com.codeying.entity.ShoppingCart;
import com.codeying.exception.BusinessException;
import com.codeying.mapper.AddressBookMapper;
import com.codeying.mapper.ShoppingCartMapper;
import com.codeying.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Rule checks only. Real proxy/SQL rollback and cleanup evidence comes from Issue9Probe. */
class CartAddressRulesTest {
    private AddressBookDTO address() {
        var body = new AddressBookDTO(); body.setPhone(" 00000000000 "); body.setDetail(" door ");
        return body;
    }

    @Test
    void reusableAddressUseCasesRefuseMissingIdentityAndBlankFieldsBeforeWrites() {
        var mapper = mock(AddressBookMapper.class);
        var service = new AddressBookServiceImpl(mapper);
        assertThrows(BusinessException.class, () -> service.createForUser(null, address()));
        var body = address(); body.setPhone(" ");
        assertThrows(BusinessException.class, () -> service.createForUser(1L, body));
        body.setPhone("00000000000"); body.setDetail(null);
        assertThrows(BusinessException.class, () -> service.createForUser(1L, body));
        assertThrows(BusinessException.class, () -> service.setDefaultForUser(1L, null));
        verifyNoInteractions(mapper);
    }

    @Test
    void firstAddressIsDefaultAndNormalizesOnlyPhoneAndDetail() {
        var mapper = mock(AddressBookMapper.class);
        when(mapper.insert(any(AddressBook.class))).thenReturn(1);
        var service = new AddressBookServiceImpl(mapper);
        var body = address(); body.setId(999L); body.setIsDefault(0); body.setConsignee(" person ");
        service.createForUser(1L, body);
        var saved = ArgumentCaptor.forClass(AddressBook.class); verify(mapper).insert(saved.capture());
        assertEquals(1L, saved.getValue().getUserId()); assertNull(saved.getValue().getId());
        assertEquals(1, saved.getValue().getIsDefault()); assertEquals("00000000000", saved.getValue().getPhone());
        assertEquals("door", saved.getValue().getDetail()); assertEquals(" person ", saved.getValue().getConsignee());
        verify(mapper, never()).clearDefaultExcept(any(), any());
    }

    @Test
    void foreignAddressRefusedForEveryMutationAndDetail() {
        var mapper = mock(AddressBookMapper.class);
        var service = new AddressBookServiceImpl(mapper);
        var body = address(); body.setId(20L);
        assertThrows(BusinessException.class, () -> service.findOwned(1L, 20L));
        assertThrows(BusinessException.class, () -> service.updateForUser(1L, body));
        assertThrows(BusinessException.class, () -> service.deleteForUser(1L, 20L));
        assertThrows(BusinessException.class, () -> service.setDefaultForUser(1L, 20L));
        verify(mapper, never()).deleteOwned(any(), any());
        verify(mapper, never()).updateOwned(any(), any());
        verify(mapper, never()).clearDefaultExcept(any(), any());
        verify(mapper, never()).setDefaultOwned(any(), any());
    }

    @Test
    void updateCannotChangeOwnerOrDefaultAndCoreFailureRetainsCause() {
        var mapper = mock(AddressBookMapper.class);
        when(mapper.findOwned(1L, 20L)).thenReturn(new AddressBook());
        when(mapper.updateOwned(eq(1L), any())).thenReturn(1);
        var service = new AddressBookServiceImpl(mapper);
        var body = address(); body.setId(20L); body.setIsDefault(1);
        service.updateForUser(1L, body);
        var updated = ArgumentCaptor.forClass(AddressBook.class); verify(mapper).updateOwned(eq(1L), updated.capture());
        assertNull(updated.getValue().getUserId()); assertNull(updated.getValue().getIsDefault());
        var cause = new DataIntegrityViolationException("synthetic insert failure");
        when(mapper.insert(any(AddressBook.class))).thenThrow(cause);
        assertSame(cause, assertThrows(DataIntegrityViolationException.class, () -> service.createForUser(1L, address())));
    }

    @Test
    void deletedDefaultUsesMapperLatestRemainingAndLastAddressLeavesNoDefault() {
        var mapper = mock(AddressBookMapper.class);
        var current = new AddressBook(); current.setIsDefault(1);
        var next = new AddressBook(); next.setId(30L);
        when(mapper.findOwned(1L, 20L)).thenReturn(current);
        when(mapper.deleteOwned(1L, 20L)).thenReturn(1);
        when(mapper.findLatestByUser(1L)).thenReturn(next, null);
        when(mapper.setDefaultOwned(1L, 30L)).thenReturn(1);
        var service = new AddressBookServiceImpl(mapper);
        service.deleteForUser(1L, 20L); service.deleteForUser(1L, 20L);
        verify(mapper, times(1)).setDefaultOwned(1L, 30L);
    }

    @Test
    void cartRejectsMissingIdentityAndBothKindsAndChecksSaleabilityBeforeExistingQuantity() {
        var dishes = mock(DishService.class); var mapper = mock(ShoppingCartMapper.class);
        var observer = mock(AnalysisObservationService.class);
        var service = new ShoppingCartServiceImpl(dishes, mock(SetmealService.class), observer);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var body = new ShoppingCartChangeDTO(); body.setDishId(10L);
        assertThrows(BusinessException.class, () -> service.addItem(null, body));
        body.setSetmealId(20L);
        assertThrows(BusinessException.class, () -> service.addItem(1L, body));
        body.setSetmealId(null);
        var stopped = new Dish(); stopped.setStatus(0); when(dishes.getById(10L)).thenReturn(stopped);
        assertThrows(BusinessException.class, () -> service.addItem(1L, body));
        verifyNoInteractions(mapper, observer);
    }

    @Test
    void cartExistingAddOnlyChangesOneQuantityAndTrimsFlavor() {
        var dishes = mock(DishService.class); var mapper = mock(ShoppingCartMapper.class);
        var service = new ShoppingCartServiceImpl(dishes, mock(SetmealService.class), mock(AnalysisObservationService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var dish = new Dish(); dish.setStatus(1); when(dishes.getById(10L)).thenReturn(dish);
        var cart = new ShoppingCart(); cart.setId(30L); cart.setNumber(2);
        when(mapper.findItem(1L, 10L, null, "hot")).thenReturn(cart);
        when(mapper.updateQuantityOwned(1L, 30L, 3)).thenReturn(1);
        var body = new ShoppingCartChangeDTO(); body.setDishId(10L); body.setDishFlavor(" hot ");
        service.addItem(1L, body);
        verify(mapper).updateQuantityOwned(1L, 30L, 3);
        verify(mapper, never()).insert(any(ShoppingCart.class));
    }

    @Test
    void defensiveNullQuantityFallbackRemainsTwoWithoutInventingNullableDatabaseRows() {
        var dishes = mock(DishService.class); var mapper = mock(ShoppingCartMapper.class);
        var service = new ShoppingCartServiceImpl(dishes, mock(SetmealService.class), mock(AnalysisObservationService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var dish = new Dish(); dish.setStatus(1); when(dishes.getById(10L)).thenReturn(dish);
        var cart = new ShoppingCart(); cart.setId(30L);
        when(mapper.findItem(1L, 10L, null, null)).thenReturn(cart);
        when(mapper.updateQuantityOwned(1L, 30L, 2)).thenReturn(1);
        var body = new ShoppingCartChangeDTO(); body.setDishId(10L);
        service.addItem(1L, body);
        verify(mapper).updateQuantityOwned(1L, 30L, 2);
    }

    @Test
    void subtractionAtOneDeletesOnlyOwnedItemAndMissingItemIsRefused() {
        var mapper = mock(ShoppingCartMapper.class);
        var service = new ShoppingCartServiceImpl(mock(DishService.class), mock(SetmealService.class), mock(AnalysisObservationService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var body = new ShoppingCartChangeDTO(); body.setDishId(10L); body.setDishFlavor(" ");
        var cart = new ShoppingCart(); cart.setId(30L); cart.setNumber(1);
        when(mapper.findItem(1L, 10L, null, null)).thenReturn(cart);
        when(mapper.deleteOwned(1L, 30L)).thenReturn(1);
        service.subtractItem(1L, body);
        verify(mapper).deleteOwned(1L, 30L);
        assertThrows(BusinessException.class, () -> service.subtractItem(2L, body));
        verify(mapper, never()).deleteOwned(eq(2L), any());
    }
}
