<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import {
  exportReport,
  getOrdersStatistics,
  getTop10,
  getTurnoverStatistics,
  getUserStatistics,
} from '../../api/modules/dashboard'
import {
  getHeatPredictionList,
  runAnalysisTraining,
  getUserClusterList,
  type HeatPredictionItem,
  type UserClusterItem,
} from '../../api/modules/analysis'
import { getOrderRiskModels, getOrderRiskReplay, runOrderRiskTraining } from '../../api/modules/order'
import { getDishById } from '../../api/modules/dish'
import type {
  OrdersStatistics,
  ReportQuery,
  Top10,
  TurnoverStatistics,
  UserStatistics,
} from '../../api/modules/dashboard'
import type { OrderRiskModelMeta } from '../../types/order'
import { formatDateTime } from '../../utils/format'

interface ClusterProfile {
  clusterId: number
  userCount: number
  avgScore: number
  topDishIds: number[]
  topDishNames: string[]
  userNames: string[]
  modelVersion: string
  featureVersion: string
}

interface HeatExplainPayload {
  xgboostEnabled?: boolean
  predictedWindowSales?: number
  salesQty7d?: number
  salesQty30d?: number
  decaySales30d?: number
  categoryId?: number
  dishName?: string
}

interface HeatInsightItem {
  dishId: number
  dishName: string
  version: string
  window: string
  predictedSalesQty: number
  heatScore: number
  explainScore: number
  explain: HeatExplainPayload
  reason: string
  fallback: boolean
}

const router = useRouter()

const timeOptions = [
  { label: '昨日', value: 'yesterday' },
  { label: '近 7 日', value: 'week' },
  { label: '近 30 日', value: 'month' },
] as const

type TimeRangeValue = (typeof timeOptions)[number]['value']

const selectedTime = ref<TimeRangeValue>('month')
const loading = ref(false)
const analysisLoading = ref(false)

const turnoverData = ref<TurnoverStatistics | null>(null)
const userData = ref<UserStatistics | null>(null)
const ordersData = ref<OrdersStatistics | null>(null)
const top10Data = ref<Top10 | null>(null)
const heatPredictionRaw = ref<HeatPredictionItem[]>([])
const userClustersRaw = ref<UserClusterItem[]>([])
const analysisError = ref('')
const analysisTrainingLoading = ref(false)
const riskTrainingLoading = ref(false)
const riskModelLoading = ref(false)
const replayLoading = ref(false)
const riskModelList = ref<OrderRiskModelMeta[]>([])
const selectedRiskModelVersion = ref('')
const riskReplay = ref<Record<string, unknown>>({})
const activeClusterId = ref<number | null>(null)
const clusterDialogVisible = ref(false)
const clusterDishNameMap = ref<Record<number, string>>({})

const revenueChartRef = ref<HTMLElement | null>(null)
const userChartRef = ref<HTMLElement | null>(null)
const orderChartRef = ref<HTMLElement | null>(null)
const salesChartRef = ref<HTMLElement | null>(null)
const clusterChartRef = ref<HTMLElement | null>(null)

let revenueChart: echarts.ECharts | null = null
let userChart: echarts.ECharts | null = null
let orderChart: echarts.ECharts | null = null
let salesChart: echarts.ECharts | null = null
let clusterChart: echarts.ECharts | null = null

const query: ReportQuery = reactive({
  begin: '',
  end: '',
})

const dateRangeText = computed(() => {
  if (!query.begin || !query.end) return '--'
  return `${query.begin} 至 ${query.end}`
})

const analysisQueryDate = computed(() => query.end || dayjs().format('YYYY-MM-DD'))

const heatInsights = computed<HeatInsightItem[]>(() => {
  return heatPredictionRaw.value.slice(0, 5).map((item) => {
    const explain = parseExplain(item.explainJson)
    const fallback = explain.xgboostEnabled === false
    return {
      dishId: item.dishId,
      dishName: item.dishName || `菜品 #${item.dishId}`,
      version: item.modelVersion,
      window: `${item.windowStart} ~ ${item.windowEnd}`,
      predictedSalesQty: Number(item.predictedSalesQty ?? 0),
      heatScore: Number(item.heatScore ?? 0),
      explainScore: Number(explain.predictedWindowSales ?? item.heatScore ?? 0),
      explain,
      reason: buildReason(item, explain),
      fallback,
    }
  })
})

const hasFallback = computed(() => {
  if (analysisError.value) return true
  if (heatInsights.value.length === 0) return true
  return heatInsights.value.some((item) => item.fallback)
})

const analysisVersion = computed(() => heatInsights.value[0]?.version || userClustersRaw.value[0]?.modelVersion || '--')
const analysisWindow = computed(() => heatInsights.value[0]?.window || '--')
const dataCutoffDate = computed(() => query.end || '--')
const dataSourceStatus = computed(() => {
  if (analysisError.value) return '分析接口异常'
  if (hasFallback.value) return '当前使用兜底逻辑'
  return '分析结果表'
})

const clusterProfiles = computed<ClusterProfile[]>(() => {
  const grouped = new Map<number, UserClusterItem[]>()
  userClustersRaw.value.forEach((item) => {
    const list = grouped.get(item.clusterId) || []
    list.push(item)
    grouped.set(item.clusterId, list)
  })

  return Array.from(grouped.entries())
    .map(([clusterId, items]) => {
      const dishCounter = new Map<number, number>()
      items.forEach((item) => {
        item.topDishIds.forEach((dishId, index) => {
          dishCounter.set(dishId, (dishCounter.get(dishId) || 0) + Math.max(1, 5 - index))
        })
      })
      const topDishIds = Array.from(dishCounter.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([dishId]) => dishId)

      return {
        clusterId,
        userCount: items.length,
        avgScore: Number((items.reduce((sum, item) => sum + Number(item.clusterScore || 0), 0) / Math.max(items.length, 1)).toFixed(4)),
        topDishIds,
        topDishNames: topDishIds.map((dishId) => clusterDishNameMap.value[dishId] || `菜品 #${dishId}`),
        userNames: items.map((item) => item.userName || `用户 #${item.userId}`).slice(0, 5),
        modelVersion: items[0]?.modelVersion || '--',
        featureVersion: items[0]?.featureVersion || '--',
      }
    })
    .sort((a, b) => a.clusterId - b.clusterId)
})

const clusterSummaryText = computed(() => {
  if (clusterProfiles.value.length === 0) return '暂无分群结果'
  return `共 ${clusterProfiles.value.length} 个分群，覆盖 ${userClustersRaw.value.length} 名用户`
})

const activeClusterProfile = computed(() => {
  if (activeClusterId.value === null) return null
  return clusterProfiles.value.find((item) => item.clusterId === activeClusterId.value) || null
})

const selectedRiskModel = computed(() => {
  if (!selectedRiskModelVersion.value) return null
  return riskModelList.value.find((item) => item.modelVersion === selectedRiskModelVersion.value) || null
})

const replayMetrics = computed(() => {
  const raw = (riskReplay.value.metrics ?? {}) as Record<string, unknown>
  const toNumber = (value: unknown) => {
    const num = Number(value)
    return Number.isFinite(num) ? num : null
  }
  return {
    prAuc: toNumber(raw.prAuc),
    recallTop20: toNumber(raw.recallTop20),
    falsePositiveRate: toNumber(raw.falsePositiveRate),
    trainSamples: toNumber(raw.trainSamples),
    holdoutSamples: toNumber(raw.holdoutSamples),
    trainPositives: toNumber(raw.trainPositives),
    holdoutPositives: toNumber(raw.holdoutPositives),
  }
})

function getDateRange() {
  const now = dayjs()
  let begin = now.format('YYYY-MM-DD')
  let end = now.format('YYYY-MM-DD')

  switch (selectedTime.value) {
    case 'yesterday':
      begin = now.subtract(1, 'day').format('YYYY-MM-DD')
      end = begin
      break
    case 'week':
      begin = now.subtract(6, 'day').format('YYYY-MM-DD')
      break
    case 'month':
    default:
      begin = now.subtract(29, 'day').format('YYYY-MM-DD')
      break
  }

  return { begin, end }
}

function splitList(value: string[] | number[] | string | number | null | undefined): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }
  if (value === null || value === undefined) {
    return []
  }
  return String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function parseExplain(raw: string | null): HeatExplainPayload {
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw) as HeatExplainPayload
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function buildReason(item: HeatPredictionItem, explain: HeatExplainPayload) {
  const fragments: string[] = []
  if (typeof explain.salesQty7d === 'number') {
    fragments.push(`近 7 日销量 ${Number(explain.salesQty7d).toFixed(0)}`)
  }
  if (typeof explain.salesQty30d === 'number') {
    fragments.push(`近 30 日销量 ${Number(explain.salesQty30d).toFixed(0)}`)
  }
  if (typeof explain.decaySales30d === 'number') {
    fragments.push(`衰减热度 ${Number(explain.decaySales30d).toFixed(2)}`)
  }
  if (typeof explain.predictedWindowSales === 'number') {
    fragments.push(`预测窗口销量 ${Number(explain.predictedWindowSales).toFixed(2)}`)
  }
  if (fragments.length === 0) {
    fragments.push(`热度分 ${Number(item.heatScore ?? 0).toFixed(2)}`)
  }
  return fragments.join(' · ')
}

async function fetchAllData() {
  loading.value = true
  analysisLoading.value = true
  analysisError.value = ''
  try {
    const { begin, end } = getDateRange()
    query.begin = begin
    query.end = end

    const [turnoverRes, userRes, ordersRes, top10Res, heatRes, clusterRes] = await Promise.all([
      getTurnoverStatistics(query),
      getUserStatistics(query),
      getOrdersStatistics(query),
      getTop10(query),
      getHeatPredictionList({ date: end, limit: 20 }),
      getUserClusterList({ date: end, limit: 100 }),
    ])

    turnoverData.value = turnoverRes.data
    userData.value = userRes.data
    ordersData.value = ordersRes.data
    top10Data.value = top10Res.data
    heatPredictionRaw.value = heatRes.data
    userClustersRaw.value = clusterRes.data

    await hydrateClusterDishNames()

    window.setTimeout(() => {
      updateCharts()
    }, 80)
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : '获取分析数据失败'
    ElMessage.error('获取统计数据失败')
  } finally {
    loading.value = false
    analysisLoading.value = false
  }
}

async function fetchRiskModels() {
  riskModelLoading.value = true
  try {
    const res = await getOrderRiskModels(20)
    riskModelList.value = res.data || []
    if (riskModelList.value.length > 0 && !selectedRiskModelVersion.value) {
      selectedRiskModelVersion.value = riskModelList.value[0].modelVersion
    }
    if (!selectedRiskModelVersion.value) {
      riskReplay.value = {}
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取风控模型列表失败')
  } finally {
    riskModelLoading.value = false
  }
}

async function fetchReplay(modelVersion?: string) {
  replayLoading.value = true
  try {
    const res = await getOrderRiskReplay(modelVersion)
    riskReplay.value = res.data || {}
  } catch (error) {
    riskReplay.value = {}
    ElMessage.error(error instanceof Error ? error.message : '获取回放指标失败')
  } finally {
    replayLoading.value = false
  }
}

async function handleRunRiskTraining() {
  if (riskTrainingLoading.value) return
  riskTrainingLoading.value = true
  try {
    await runOrderRiskTraining()
    ElMessage.success('风控训练任务已触发')
    await fetchRiskModels()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '触发风控训练失败')
  } finally {
    riskTrainingLoading.value = false
  }
}

async function handleRunAnalysisTraining() {
  if (analysisTrainingLoading.value) return
  analysisTrainingLoading.value = true
  try {
    await runAnalysisTraining(query.end ? { date: query.end } : undefined)
    ElMessage.success('热度分析与数据挖掘重训已完成，结果已刷新')
    await fetchAllData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '触发热度分析重训失败')
  } finally {
    analysisTrainingLoading.value = false
  }
}

function formatMetric(value: number | null, digits = 4) {
  if (value === null) return '--'
  return value.toFixed(digits)
}

function resolveModelTagType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function handleRiskModelRowClick(row: OrderRiskModelMeta) {
  selectedRiskModelVersion.value = row.modelVersion
}

async function hydrateClusterDishNames() {
  const dishIds = Array.from(
    new Set(
      userClustersRaw.value.flatMap((item) => item.topDishIds || []).filter((dishId) => Number.isFinite(dishId)),
    ),
  ).slice(0, 15)

  if (dishIds.length === 0) {
    clusterDishNameMap.value = {}
    return
  }

  const entries = await Promise.all(
    dishIds.map(async (dishId) => {
      try {
        const res = await getDishById(dishId)
        return [dishId, res.data.name || `菜品 #${dishId}`] as const
      } catch {
        return [dishId, `菜品 #${dishId}`] as const
      }
    }),
  )
  clusterDishNameMap.value = Object.fromEntries(entries)
}

async function handleExport() {
  try {
    const blob = await exportReport(query)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `运营数据报表_${dayjs().format('YYYYMMDD')}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

function initCharts() {
  if (revenueChartRef.value) revenueChart = echarts.init(revenueChartRef.value)
  if (userChartRef.value) userChart = echarts.init(userChartRef.value)
  if (orderChartRef.value) orderChart = echarts.init(orderChartRef.value)
  if (salesChartRef.value) salesChart = echarts.init(salesChartRef.value)
  if (clusterChartRef.value) {
    clusterChart = echarts.init(clusterChartRef.value)
    clusterChart.on('click', (params) => {
      const clusterId = Number(params.name)
      if (Number.isFinite(clusterId)) {
        openClusterDialog(clusterId)
      }
    })
  }
}

function updateCharts() {
  updateRevenueChart()
  updateUserChart()
  updateOrderChart()
  updateSalesChart()
  updateClusterChart()
}

function updateRevenueChart() {
  if (!revenueChart || !turnoverData.value) return
  const dateList = splitList(turnoverData.value.dateList)
  const turnoverList = splitList(turnoverData.value.turnoverList).map(Number)
  revenueChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '12%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dateList,
      axisLine: { lineStyle: { color: '#d9d1c5' } },
      axisLabel: { color: '#6b6257', fontSize: 11 },
      name: '营业额（元）',
      nameLocation: 'middle',
      nameGap: 30,
      nameTextStyle: { color: '#6b6257', fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#e9e0d4' } },
      axisLabel: { color: '#6b6257', fontSize: 11 },
    },
    series: [
      {
        name: '营业额（元）',
        type: 'line',
        smooth: false,
        symbol: 'none',
        itemStyle: { color: '#d68a32' },
        lineStyle: { width: 2, color: '#d68a32' },
        data: turnoverList,
      },
    ],
  })
}

function updateUserChart() {
  if (!userChart || !userData.value) return
  const dateList = splitList(userData.value.dateList)
  const totalUserList = splitList(userData.value.totalUserList).map(Number)
  const newUserList = splitList(userData.value.newUserList).map(Number)
  userChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: [
        { name: '用户总量（个）', icon: 'line' },
        { name: '新增用户（个）', icon: 'line' },
      ],
      bottom: 0,
      itemWidth: 24,
      itemHeight: 2,
      textStyle: { color: '#6b6257', fontSize: 12 },
    },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dateList,
      axisLine: { lineStyle: { color: '#d9d1c5' } },
      axisLabel: { color: '#6b6257', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#e9e0d4' } },
      axisLabel: { color: '#6b6257', fontSize: 11 },
    },
    series: [
      {
        name: '用户总量（个）',
        type: 'line',
        smooth: false,
        symbol: 'none',
        itemStyle: { color: '#d68a32' },
        lineStyle: { width: 2, color: '#d68a32' },
        data: totalUserList,
      },
      {
        name: '新增用户（个）',
        type: 'line',
        smooth: false,
        symbol: 'none',
        itemStyle: { color: '#b85c4f' },
        lineStyle: { width: 2, color: '#b85c4f' },
        data: newUserList,
      },
    ],
  })
}

function updateOrderChart() {
  if (!orderChart || !ordersData.value) return
  const dateList = splitList(ordersData.value.dateList)
  const orderCountList = splitList(ordersData.value.orderCountList).map(Number)
  const validOrderCountList = splitList(ordersData.value.validOrderCountList).map(Number)
  orderChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: [
        { name: '订单总数（个）', icon: 'line' },
        { name: '有效订单（个）', icon: 'line' },
      ],
      bottom: 0,
      itemWidth: 24,
      itemHeight: 2,
      textStyle: { color: '#6b6257', fontSize: 12 },
    },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dateList,
      axisLine: { lineStyle: { color: '#d9d1c5' } },
      axisLabel: { color: '#6b6257', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#e9e0d4' } },
      axisLabel: { color: '#6b6257', fontSize: 11 },
    },
    series: [
      {
        name: '订单总数（个）',
        type: 'line',
        smooth: false,
        symbol: 'none',
        itemStyle: { color: '#d68a32' },
        lineStyle: { width: 2, color: '#d68a32' },
        data: orderCountList,
      },
      {
        name: '有效订单（个）',
        type: 'line',
        smooth: false,
        symbol: 'none',
        itemStyle: { color: '#b85c4f' },
        lineStyle: { width: 2, color: '#b85c4f' },
        data: validOrderCountList,
      },
    ],
  })
}

function updateSalesChart() {
  if (!salesChart || !top10Data.value) return
  const nameList = splitList(top10Data.value.nameList)
  const numberList = splitList(top10Data.value.numberList).map(Number)
  salesChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '1%', right: '1%', bottom: 10, top: 50, containLabel: true },
    xAxis: {
      type: 'category',
      data: nameList,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#53483f', fontSize: 12, rotate: 90 },
    },
    yAxis: { type: 'value', show: false },
    series: [
      {
        name: '销量',
        type: 'bar',
        data: numberList,
        barWidth: 25,
        barCategoryGap: '175%',
        itemStyle: {
          color: '#d68a32',
          borderRadius: [4, 4, 0, 0],
        },
        label: {
          show: true,
          position: 'top',
          color: '#53483f',
          fontSize: 12,
          fontWeight: 600,
          formatter: '{c}',
        },
        showBackground: true,
        backgroundStyle: { color: '#efe6d8', borderRadius: [4, 4, 0, 0] },
      },
    ],
  })
}

function updateClusterChart() {
  if (!clusterChart) return
  clusterChart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '分群分布',
        type: 'pie',
        radius: ['44%', '72%'],
        center: ['50%', '56%'],
        label: {
          color: '#53483f',
          formatter: ({ name, percent }: { name: string; percent: number }) => `簇 ${name}\n${percent}%`,
        },
        labelLine: { lineStyle: { color: '#b9aa97' } },
        itemStyle: {
          borderColor: '#f0ebde',
          borderWidth: 2,
        },
        data: clusterProfiles.value.map((profile, index) => ({
          name: String(profile.clusterId),
          value: profile.userCount,
          itemStyle: {
            color: ['#6e1d20', '#c6792d', '#4a8fa3', '#a35757', '#8f6d43'][index % 5],
          },
        })),
      },
    ],
  })
}

function handleResize() {
  revenueChart?.resize()
  userChart?.resize()
  orderChart?.resize()
  salesChart?.resize()
  clusterChart?.resize()
}

function getCompletionRate() {
  if (!ordersData.value) return '--'
  const value = Number(ordersData.value.orderCompletionRate ?? 0)
  return value > 1 ? `${value.toFixed(1)}%` : `${(value * 100).toFixed(1)}%`
}

function openDishManagement(item: HeatInsightItem) {
  router.push({ name: 'Dish', query: { name: item.dishName, from: 'analysis' } })
}

function openClusterDialog(clusterId: number) {
  activeClusterId.value = clusterId
  clusterDialogVisible.value = true
}

watch(
  () => clusterProfiles.value,
  (profiles) => {
    if (profiles.length > 0 && activeClusterId.value === null) {
      activeClusterId.value = profiles[0].clusterId
    }
    window.setTimeout(() => updateClusterChart(), 30)
  },
  { deep: true },
)

watch(selectedRiskModelVersion, (value) => {
  if (!value) {
    riskReplay.value = {}
    return
  }
  fetchReplay(value)
})

onMounted(() => {
  initCharts()
  fetchAllData()
  fetchRiskModels()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  revenueChart?.dispose()
  userChart?.dispose()
  orderChart?.dispose()
  salesChart?.dispose()
  clusterChart?.dispose()
})
</script>

<template>
  <div class="statistics-page" v-loading="loading">
    <div class="statistics-header">
      <div class="time-filter">
        <div class="segmented-radio">
          <label v-for="opt in timeOptions" :key="opt.value">
            <input v-model="selectedTime" type="radio" name="time-range" :value="opt.value" @change="fetchAllData()" />
            <span>{{ opt.label }}</span>
          </label>
        </div>
      </div>
      <div class="date-range">已选时间：{{ dateRangeText }}</div>
      <button class="export-btn" @click="handleExport">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
        数据导出
      </button>
    </div>

    <div class="statistics-grid">
      <div class="chart-card">
        <div class="card-title">营业额统计</div>
        <div ref="revenueChartRef" class="chart-container" />
      </div>

      <div class="chart-card">
        <div class="card-title">用户统计</div>
        <div ref="userChartRef" class="chart-container" />
      </div>

      <div class="chart-card">
        <div class="card-title">订单统计</div>
        <div v-if="ordersData" class="order-metrics">
          <div class="metric-col">
            <span class="metric-label">订单完成率</span>
            <span class="metric-value bold">{{ getCompletionRate() }}</span>
          </div>
          <span class="metric-op">=</span>
          <div class="metric-col">
            <span class="metric-label">有效订单</span>
            <span class="metric-value">{{ ordersData.validOrderCount }}</span>
          </div>
          <span class="metric-op">/</span>
          <div class="metric-col">
            <span class="metric-label">订单总数</span>
            <span class="metric-value">{{ ordersData.totalOrderCount }}</span>
          </div>
        </div>
        <div ref="orderChartRef" class="chart-container order-chart" />
      </div>

      <div class="chart-card">
        <div class="card-title">销量排行 TOP10</div>
        <div ref="salesChartRef" class="chart-container sales-chart" />
      </div>
    </div>

    <div class="analysis-grid">
      <div class="chart-card insight-card" :class="{ 'is-fallback': hasFallback }">
        <div class="card-head">
          <div>
            <div class="card-title">热度洞察卡片</div>
            <div class="card-subtitle">展示预测热度 Top5与推荐理由</div>
          </div>
          <el-tag v-if="hasFallback" type="warning" effect="light" round>当前使用兜底逻辑</el-tag>
        </div>

        <div v-if="heatInsights.length > 0" class="heat-list">
          <button v-for="item in heatInsights" :key="item.dishId" class="heat-item" type="button" @click="openDishManagement(item)">
            <div class="heat-item-main">
              <div class="heat-rank">Top {{ heatInsights.findIndex((heat) => heat.dishId === item.dishId) + 1 }}</div>
              <div class="heat-copy">
                <div class="heat-name">{{ item.dishName }}</div>
                <div class="heat-reason">
                  <div>近7日销量：{{ Number(item.explain.salesQty7d ?? 0).toFixed(0) }}</div>
                  <div>近30日销量：{{ Number(item.explain.salesQty30d ?? 0).toFixed(0) }}</div>
                  <div>衰减热度：{{ Number(item.explain.decaySales30d ?? 0).toFixed(2) }}</div>
                  <div>预测窗口销量：{{ Number(item.explain.predictedWindowSales ?? item.predictedSalesQty ?? 0).toFixed(2) }}</div>
                </div>
              </div>
            </div>
            <div class="heat-meta">
              <div class="heat-score">{{ item.heatScore.toFixed(2) }}</div>
            </div>
          </button>
        </div>
        <div v-else class="empty-block">暂无热度预测结果</div>
      </div>

      <div class="chart-card cluster-card">
        <div class="card-head">
          <div>
            <div class="card-title">用户分群概览</div>
            <div class="card-subtitle">点击分群图各块可查看分群画像与候选菜品</div>
          </div>
          <div class="cluster-summary">{{ clusterSummaryText }}</div>
        </div>
        <div ref="clusterChartRef" class="chart-container cluster-chart" />
      </div>

      <div class="chart-card console-card" :class="{ 'is-fallback': hasFallback }">
        <div class="card-head">
          <div>
            <div class="card-title">全局分析控制台</div>
            <div class="card-subtitle">时间窗、截止日期与回源状态</div>
          </div>
          <button class="export-btn" type="button" :disabled="analysisTrainingLoading" @click="handleRunAnalysisTraining">
            {{ analysisTrainingLoading ? '手动重训中...' : '手动重训' }}
          </button>
        </div>

        <div class="console-list">
          <div class="console-item">
            <span class="console-label">当前分析时间窗</span>
            <span class="console-value">{{ analysisWindow }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">数据截止日期</span>
            <span class="console-value">{{ dataCutoffDate }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">模型版本</span>
            <span class="console-value console-break">{{ analysisVersion }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">时间跨度</span>
            <span class="console-value">{{ selectedTime === 'yesterday' ? '昨日' : selectedTime === 'week' ? '近 7 日' : '近 30 日' }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">分析查询日期</span>
            <span class="console-value">{{ analysisQueryDate }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">分析状态</span>
            <span class="console-value">{{ dataSourceStatus }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="risk-grid">
      <div class="chart-card risk-model-card">
        <div class="card-head">
          <div>
            <div class="card-title">风控模型版本</div>
            <div class="card-subtitle">查看训练窗口、状态，并可手动触发重训</div>
          </div>
          <button class="export-btn" type="button" :disabled="riskTrainingLoading" @click="handleRunRiskTraining">
            {{ riskTrainingLoading ? '手动重训中...' : '手动重训' }}
          </button>
        </div>
        <el-table
          :data="riskModelList"
          v-loading="riskModelLoading"
          height="300"
          row-key="modelVersion"
          @row-click="handleRiskModelRowClick"
        >
          <el-table-column prop="modelVersion" label="模型版本" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="resolveModelTagType(row.status)" effect="light" round>{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="训练窗口" min-width="220">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.trainWindowStart) || '--' }} ~ {{ formatDateTime(row.trainWindowEnd) || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) || '--' }}</template>
          </el-table-column>
        </el-table>
        <div class="risk-model-actions">
          <span class="console-label">选择模型查看回放：</span>
          <el-select v-model="selectedRiskModelVersion" placeholder="请选择模型版本" style="width: 360px; max-width: 100%">
            <el-option
              v-for="item in riskModelList"
              :key="item.modelVersion"
              :label="`${item.modelVersion} (${item.status})`"
              :value="item.modelVersion"
            />
          </el-select>
        </div>
      </div>

      <div class="chart-card risk-replay-card" v-loading="replayLoading">
        <div class="card-head">
          <div>
            <div class="card-title">回放评估面板</div>
            <div class="card-subtitle">展示 PR-AUC、Recall@Top20 与误报率等离线指标</div>
          </div>
          <el-tag :type="selectedRiskModel?.status === 'ACTIVE' ? 'success' : 'info'" effect="light" round>
            {{ selectedRiskModel?.modelVersion || '未选择模型' }}
          </el-tag>
        </div>
        <div class="console-list">
          <div class="console-item">
            <el-tooltip
              content="PR-AUC（精确率-召回率曲线下面积）用于衡量模型在不同阈值下对正样本的整体识别能力，越接近 1 表示排序与识别效果越好。"
              placement="top-start"
            >
              <span class="console-label metric-tooltip-trigger">PR-AUC</span>
            </el-tooltip>
            <span class="console-value">{{ formatMetric(replayMetrics.prAuc) }}</span>
          </div>
          <div class="console-item">
            <el-tooltip
              content="Recall@Top20% 召回率表示：在模型评分最高的前 20% 样本中，命中了多少全部真实正样本，反映模型对风险样本的覆盖能力。"
              placement="top-start"
            >
              <span class="console-label metric-tooltip-trigger">Recall@Top20%</span>
            </el-tooltip>
            <span class="console-value">{{ formatMetric(replayMetrics.recallTop20) }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">误报率(FPR)</span>
            <span class="console-value">{{ formatMetric(replayMetrics.falsePositiveRate) }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">训练样本</span>
            <span class="console-value">{{ replayMetrics.trainSamples ?? '--' }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">Hold-out 样本</span>
            <span class="console-value">{{ replayMetrics.holdoutSamples ?? '--' }}</span>
          </div>
          <div class="console-item">
            <span class="console-label">正样本(训练/回放)</span>
            <span class="console-value">{{ replayMetrics.trainPositives ?? '--' }} / {{ replayMetrics.holdoutPositives ?? '--' }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="clusterDialogVisible" width="560px" :title="activeClusterProfile ? `簇 ${activeClusterProfile.clusterId} 分群画像` : '分群画像'">
      <template v-if="activeClusterProfile">
        <div class="profile-grid">
          <div class="profile-box">
            <span class="profile-label">用户数量</span>
            <span class="profile-value">{{ activeClusterProfile.userCount }}</span>
          </div>
          <div class="profile-box">
            <span class="profile-label">平均置信度</span>
            <span class="profile-value">{{ activeClusterProfile.avgScore.toFixed(4) }}</span>
          </div>
          <div class="profile-box wide">
            <span class="profile-label">模型版本</span>
            <span class="profile-value profile-break">{{ activeClusterProfile.modelVersion }}</span>
          </div>
          <div class="profile-box wide">
            <span class="profile-label">候选菜品</span>
            <div class="chip-list">
              <el-tag v-for="name in activeClusterProfile.topDishNames" :key="name" effect="light" round>
                {{ name }}
              </el-tag>
            </div>
          </div>
          <div class="profile-box wide">
            <span class="profile-label">样例用户</span>
            <div class="chip-list muted">
              <span v-for="name in activeClusterProfile.userNames" :key="name" class="user-chip">{{ name }}</span>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.statistics-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  padding: 20px 24px 28px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top right, rgba(214, 138, 50, 0.08), transparent 22%),
    linear-gradient(180deg, #f2ede3 0%, #ece7db 100%);
}

:global(.page-header),
:global(.layout-header .page-title),
:global(.header-title),
:global(.breadcrumb) {
  display: none !important;
}

.statistics-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.time-filter {
  display: flex;
  gap: 8px;
}

.date-range {
  font-size: 14px;
  color: #6b6257;
  flex: 1;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #f7f1e7;
  border: 1px solid #d8ccb9;
  border-radius: 12px;
  font-size: 14px;
  color: #4a4137;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.export-btn:hover {
  border-color: #c6792d;
  color: #c6792d;
  transform: translateY(-1px);
}

.export-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
}

.export-btn .icon {
  width: 16px;
  height: 16px;
}

.segmented-radio {
  display: flex;
  flex-wrap: wrap;
}

.segmented-radio input[type='radio'] {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.segmented-radio input[type='radio']:checked + span {
  background: #fbf3df;
  border-color: #c6792d;
  color: #6e1d20;
  box-shadow: inset 0 0 0 1px #c6792d;
}

.segmented-radio label span {
  display: block;
  cursor: pointer;
  background: #f7f1e7;
  padding: 8px 16px;
  border: 1px solid #d8ccb9;
  color: #4a4137;
  transition: all 0.2s ease;
}

.segmented-radio label:first-child span {
  border-radius: 12px 0 0 12px;
}

.segmented-radio label:last-child span {
  border-radius: 0 12px 12px 0;
}

.statistics-grid,
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.analysis-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: stretch;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-card {
  background: #f0ebde;
  border: 1px solid rgba(110, 29, 32, 0.08);
  border-radius: 20px;
  padding: 20px;
  box-shadow: 0 12px 28px rgba(86, 55, 31, 0.06);
  display: flex;
  flex-direction: column;
  min-height: 320px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #28160f;
  margin-bottom: 4px;
}

.card-subtitle,
.cluster-summary {
  font-size: 12px;
  color: #7b6f61;
}

.chart-container {
  flex: 1;
  width: 100%;
  min-height: 260px;
}

.sales-chart {
  min-height: 320px;
}

.cluster-chart {
  flex: 1;
  min-height: 300px;
  margin: auto 0 0;
}

.order-metrics {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.metric-col {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-label {
  font-size: 13px;
  color: #7b6f61;
}

.metric-value {
  font-size: 28px;
  font-weight: 500;
  color: #28160f;
}

.metric-value.bold {
  font-weight: 700;
}

.metric-op {
  font-size: 22px;
  color: #9b8f82;
  margin-top: 18px;
}

.heat-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.heat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  border: 1px solid #dfd4c5;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.45);
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.heat-item:hover {
  border-color: #c6792d;
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(110, 29, 32, 0.08);
}

.heat-item-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.heat-rank {
  min-width: 56px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fbf3df;
  color: #6e1d20;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  align-self: center;
}

.heat-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.heat-name {
  color: #28160f;
  font-size: 15px;
  font-weight: 700;
}

.heat-reason {
  margin-top: 4px;
  color: #7b6f61;
  font-size: 12px;
  line-height: 1.5;
  display: grid;
  gap: 2px;
}

.heat-meta {
  min-width: 88px;
  text-align: right;
}

.heat-score {
  font-size: 24px;
  font-weight: 700;
  color: #6e1d20;
}

.console-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.risk-model-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.console-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid #e2d8ca;
}

.console-label {
  color: #7b6f61;
  font-size: 13px;
}

.metric-tooltip-trigger {
  cursor: help;
  text-decoration: underline dotted rgba(123, 111, 97, 0.7);
  text-underline-offset: 3px;
}

.console-value {
  color: #28160f;
  font-size: 13px;
  font-weight: 600;
  text-align: right;
}

.console-break,
.profile-break {
  word-break: break-all;
}

.empty-block {
  display: grid;
  place-items: center;
  min-height: 220px;
  border-radius: 16px;
  border: 1px dashed #d2c4b0;
  color: #8c7e6e;
  background: rgba(255, 255, 255, 0.28);
}

.is-fallback {
  border-color: rgba(217, 74, 43, 0.28);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.profile-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 16px;
  background: #f7f1e7;
  border: 1px solid #e2d8ca;
}

.profile-box.wide {
  grid-column: 1 / -1;
}

.profile-label {
  color: #7b6f61;
  font-size: 12px;
}

.profile-value {
  color: #28160f;
  font-size: 16px;
  font-weight: 700;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip-list.muted {
  gap: 6px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #efe6d8;
  color: #5d5145;
  font-size: 12px;
}

@media (max-width: 1360px) {
  .analysis-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .risk-grid {
    grid-template-columns: 1fr;
  }

  .console-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1200px) {
  .statistics-grid,
  .analysis-grid,
  .risk-grid {
    grid-template-columns: 1fr;
  }

  .chart-card {
    min-height: 300px;
  }

  .heat-item,
  .console-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .heat-meta {
    min-width: 0;
    text-align: left;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
