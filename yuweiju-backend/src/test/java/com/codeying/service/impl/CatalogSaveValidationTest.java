package com.codeying.service.impl;

import com.codeying.dto.admin.dish.DishDTO;
import com.codeying.dto.admin.setmeal.SetmealDTO;
import com.codeying.dto.admin.setmeal.SetmealDishDTO;
import com.codeying.entity.*;
import com.codeying.exception.BusinessException;
import com.codeying.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Direct application callers have the same invariants as HTTP. SQL rollback is in Issue60Probe. */
class CatalogSaveValidationTest {
    final DishService dishes = mock(DishService.class);
    final CategoryService categories = mock(CategoryService.class);
    final DishFlavorService flavors = mock(DishFlavorService.class);
    final SetmealService meals = mock(SetmealService.class);
    final SetmealDishService details = mock(SetmealDishService.class);
    final DishApplicationService dishApp = new DishApplicationServiceImpl(dishes, flavors, categories, details);
    final SetmealApplicationService mealApp = new SetmealApplicationServiceImpl(meals, details, categories, dishes);

    @BeforeEach void fixtures() {
        when(dishes.save(any(Dish.class))).thenAnswer(call -> { call.<Dish>getArgument(0).setId(10L); return true; });
        when(meals.save(any(Setmeal.class))).thenAnswer(call -> { call.<Setmeal>getArgument(0).setId(20L); return true; });
        Category dc = new Category(); dc.setType(1); when(categories.getById(1L)).thenReturn(dc);
        Category mc = new Category(); mc.setType(2); when(categories.getById(2L)).thenReturn(mc);
        Dish dish = new Dish(); dish.setId(10L); dish.setCategoryId(1L); dish.setPrice(BigDecimal.TEN); dish.setStatus(0);
        when(dishes.getById(10L)).thenReturn(dish);
        Setmeal meal = new Setmeal(); meal.setId(20L); meal.setCategoryId(2L); meal.setPrice(BigDecimal.TEN);
        when(meals.getById(20L)).thenReturn(meal);
        SetmealDish item = new SetmealDish(); item.setDishId(10L); item.setCopies(3);
        when(details.listBySetmeal(20L)).thenReturn(List.of(item));
    }
    DishDTO dish() {
        DishDTO dto = new DishDTO(); dto.setId(10L); dto.setName("valid"); dto.setCategoryId(1L); dto.setPrice(BigDecimal.TEN); return dto;
    }
    SetmealDishDTO detail(Long id, Integer copies) {
        SetmealDishDTO dto = new SetmealDishDTO(); dto.setDishId(id); dto.setCopies(copies); return dto;
    }
    SetmealDTO meal() {
        SetmealDTO dto = new SetmealDTO(); dto.setId(20L); dto.setName("valid"); dto.setCategoryId(2L); dto.setPrice(BigDecimal.TEN);
        dto.setSetmealDishes(List.of(detail(10L, 3))); return dto;
    }
    void reject(DishDTO d, SetmealDTO m) {
        assertThrows(BusinessException.class, () -> dishApp.create(1L,d));
        assertThrows(BusinessException.class, () -> dishApp.update(1L,d));
        assertThrows(BusinessException.class, () -> mealApp.create(1L,m));
        assertThrows(BusinessException.class, () -> mealApp.update(1L,m));
        verify(dishes,never()).save(any()); verify(dishes,never()).updateById(any(Dish.class));
        verify(meals,never()).save(any()); verify(meals,never()).updateById(any(Setmeal.class));
        verifyNoInteractions(flavors); verify(details,never()).deleteBySetmeals(anyList());
    }
    @ParameterizedTest @ValueSource(strings={"-0.01","0.001","99999999.991","100000000","1E+20"})
    void rejectsUnrepresentableMoneyWithoutWrites(String amount) {
        var d=dish(); var m=meal(); d.setPrice(new BigDecimal(amount)); m.setPrice(new BigDecimal(amount)); reject(d,m);
    }
    @ParameterizedTest @ValueSource(strings={"0","0.01","99999999.99","1.000","1E+2"})
    void acceptsExactMoneyAndStoppedComponents(String amount) {
        var d=dish(); var m=meal(); d.setPrice(new BigDecimal(amount)); m.setPrice(new BigDecimal(amount));
        dishApp.create(1L,d); dishApp.update(1L,d); mealApp.create(1L,m); mealApp.update(1L,m);
        verify(dishes).save(argThat(e -> e.getPrice().compareTo(new BigDecimal(amount))==0));
        verify(details,times(2)).saveBatch(argThat(rows -> rows.iterator().next().getCopies()==3));
    }
    @Test void rejectsMissingAndWrongCategoryOnCreateAndUpdate() {
        for(long id:List.of(99L,3L)) { var d=dish(); var m=meal(); d.setCategoryId(id); m.setCategoryId(id); reject(d,m); }
        var d=dish(); var m=meal(); d.setCategoryId(2L); m.setCategoryId(1L); reject(d,m);
    }
    @Test void rejectsEveryBadDetailWithoutFilteringOrDeleting() {
        List<List<SetmealDishDTO>> cases = new ArrayList<>();
        cases.add(List.of()); cases.add(Arrays.asList((SetmealDishDTO)null));
        for(var bad:List.of(detail(null,1),detail(99L,1),detail(10L,null),detail(10L,0),detail(10L,-1))) {
            cases.add(List.of(bad)); cases.add(Arrays.asList(detail(10L,3),bad));
        }
        for(var items:cases) {
            var m=meal(); m.setSetmealDishes(items);
            assertThrows(BusinessException.class,()->mealApp.create(1L,m));
            assertThrows(BusinessException.class,()->mealApp.update(1L,m));
        }
        var m=meal(); m.setSetmealDishes(null); assertThrows(BusinessException.class,()->mealApp.create(1L,m));
        verify(meals,never()).save(any()); verify(meals,never()).updateById(any(Setmeal.class));
        verify(details,never()).deleteBySetmeals(anyList());
    }
    @Test void createRequiresMoneyCategoryAndCompositionForDirectCallers() {
        var d=dish(); var m=meal(); d.setPrice(null); m.setPrice(null);
        assertThrows(BusinessException.class,()->dishApp.create(1L,d)); assertThrows(BusinessException.class,()->mealApp.create(1L,m));
        final var missingDish=dish(); missingDish.setCategoryId(null); final var missingMeal=meal(); missingMeal.setCategoryId(null);
        assertThrows(BusinessException.class,()->dishApp.create(1L,missingDish)); assertThrows(BusinessException.class,()->mealApp.create(1L,missingMeal));
        verify(dishes,never()).save(any()); verify(meals,never()).save(any());
    }
    @ParameterizedTest @ValueSource(ints={1,3,Integer.MAX_VALUE})
    void acceptsPositiveSqlIntegerCopiesWithoutChangingThem(int copies) {
        var m=meal(); m.setSetmealDishes(List.of(detail(10L,copies)));
        mealApp.create(1L,m); mealApp.update(1L,m);
        verify(details,times(2)).saveBatch(argThat(rows -> rows.iterator().next().getCopies()==copies));
    }
    @Test void partialUpdatesRetainAndValidateEffectiveValues() {
        var d=new DishDTO(); d.setId(10L); d.setName("new name"); dishApp.update(1L,d);
        var m=new SetmealDTO(); m.setId(20L); m.setImage("new image"); mealApp.update(1L,m);
        verify(details,never()).deleteBySetmeals(anyList());
        verify(dishes).updateById(argThat((Dish e) -> e.getPrice()==null && e.getCategoryId()==null));
        verify(meals).updateById(argThat((Setmeal e) -> e.getPrice()==null && e.getCategoryId()==null));
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0.001","100000000","-1"})
    void refusesUnstorableOrInvalidRetainedMoneyForPartialDirectCalls(String amount) {
        var price=amount==null?null:new BigDecimal(amount);
        dishes.getById(10L).setPrice(price); meals.getById(20L).setPrice(price);
        var d=new DishDTO(); d.setId(10L); var m=new SetmealDTO(); m.setId(20L);
        assertThrows(BusinessException.class,()->dishApp.update(1L,d));
        assertThrows(BusinessException.class,()->mealApp.update(1L,m));
        verify(dishes,never()).updateById(any(Dish.class)); verify(meals,never()).updateById(any(Setmeal.class));
    }
    @Test void partialUpdatesRejectInvalidRetainedValuesWithoutWrites() {
        var d=new DishDTO(); d.setId(10L); var m=new SetmealDTO(); m.setId(20L);
        dishes.getById(10L).setPrice(new BigDecimal("-1")); meals.getById(20L).setCategoryId(99L);
        assertThrows(BusinessException.class,()->dishApp.update(1L,d)); assertThrows(BusinessException.class,()->mealApp.update(1L,m));
        meals.getById(20L).setCategoryId(2L);
        when(details.listBySetmeal(20L)).thenReturn(List.of());
        assertThrows(BusinessException.class,()->mealApp.update(1L,m));
        SetmealDish invalid = new SetmealDish(); invalid.setDishId(99L); invalid.setCopies(1);
        when(details.listBySetmeal(20L)).thenReturn(List.of(invalid));
        assertThrows(BusinessException.class,()->mealApp.update(1L,m));
        invalid.setDishId(10L); invalid.setCopies(0);
        assertThrows(BusinessException.class,()->mealApp.update(1L,m));
        verify(dishes,never()).updateById(any(Dish.class)); verify(meals,never()).updateById(any(Setmeal.class));
    }
}
