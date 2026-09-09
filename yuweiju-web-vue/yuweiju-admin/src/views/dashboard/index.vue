<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrderStatusStatistics } from '../../api/modules/order'
import {
  getBusinessData,
  getOverviewDishes,
  getOverviewOrders,
  getOverviewSetmeals,
} from '../../api/modules/workspace'
import type { OrderStatusStatistics } from '../../types/order'
import type { BusinessData, OverviewOrdersData, OverviewSimpleData } from '../../types/workspace'
import CuisineStatistics from './components/CuisineStatistics.vue'
import OrderList from './components/OrderList.vue'
import Orderview from './components/Orderview.vue'
import Overview from './components/Overview.vue'
import SetMealStatistics from './components/SetMealStatistics.vue'
import bgImageUrl from '../../assets/reference_images/image13.png?url'

const businessData = ref<BusinessData>()
const orderviewData = ref<OverviewOrdersData>()
const dishesData = ref<OverviewSimpleData>()
const setMealData = ref<OverviewSimpleData>()
const orderStatics = ref<OrderStatusStatistics>()

const loading = ref(false)
const orderLoading = ref(false)

async function fetchAll() {
  loading.value = true
  try {
    const [biz, orders, dishes, setmeals] = await Promise.all([
      getBusinessData(),
      getOverviewOrders(),
      getOverviewDishes(),
      getOverviewSetmeals(),
    ])
    businessData.value = biz.data
    orderviewData.value = orders.data
    dishesData.value = dishes.data
    setMealData.value = setmeals.data
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取工作台数据失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function fetchOrderStatistics() {
  orderLoading.value = true
  try {
    const res = await getOrderStatusStatistics()
    orderStatics.value = res.data
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取订单统计失败'
    ElMessage.error(message)
  } finally {
    orderLoading.value = false
  }
}

onMounted(() => {
  fetchAll()
  fetchOrderStatistics()
})

const bgCss = `url(${bgImageUrl})`
</script>

<template>
  <div class="dashboard-container">
    <Overview :data="businessData" />
    <Orderview :data="orderviewData" />
    <div class="two-col-grid">
      <CuisineStatistics :data="dishesData" />
      <SetMealStatistics :data="setMealData" />
    </div>
    <OrderList :data="orderStatics" :loading="orderLoading" @refresh="fetchOrderStatistics" />
  </div>
</template>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  background-image: v-bind(bgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
  padding: 40px 60px;
  border-radius: 0;
  flex: 1;
  min-height: 100%;
  box-shadow: none;
  margin: 0;
  box-sizing: border-box;
}

.two-col-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 1100px) {
  .dashboard-container {
    padding: 24px;
  }
  .two-col-grid {
    grid-template-columns: 1fr;
  }
}
</style>
