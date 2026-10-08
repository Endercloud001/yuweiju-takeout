<script setup lang="ts">
import { formatRiskLevel } from '../../utils/order-risk'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOrderPage,
  confirmOrder,
  rejectOrder,
  cancelOrder,
  deliveryOrder,
  completeOrder,
  getOrderDetails,
} from '../../api/modules/order'
import type { OrderListItem, OrderConditionQuery, OrderDetail } from '../../types/order'
import { formatCurrency, formatDateTime, toDateTimeString } from '../../utils/format'
import { getOrderStatusLabel, getOrderStatusTagType } from '../../utils/constants'
import dividerUrl from '../../assets/reference_images/image29.png?url'
import orderInfoSubtitleUrl from '../../assets/reference_images/订单信息副标题图片.png?url'
import deliverySubtitleUrl from '../../assets/reference_images/配送情况副标题图片.png?url'
import dialogOrnamentUrl from '../../assets/reference_images/顶部和底部花纹图片.png?url'
import dialogTopSquareUrl from '../../assets/reference_images/方形花纹_透明.png?url'
import statusWaitPayIconUrl from '../../assets/reference_images/待付款.svg?url'
import statusWaitAcceptIconUrl from '../../assets/reference_images/待接单.svg?url'
import statusWaitDeliverIconUrl from '../../assets/reference_images/待派送.svg?url'
import statusDeliveringIconUrl from '../../assets/reference_images/派送中.svg?url'
import statusCancelledIconUrl from '../../assets/reference_images/已取消.svg?url'
import statusCompletedIconUrl from '../../assets/reference_images/已完成.svg?url'

const route = useRoute()

const query = reactive<OrderConditionQuery>({
  page: 1,
  pageSize: 10,
  status: route.query.status ? Number(route.query.status) : undefined,
  number: '',
  phone: '',
})

const loading = ref(false)
const total = ref(0)
const records = ref<OrderListItem[]>([])

const dialogVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OrderDetail | null>(null)
const dialogStatus = ref<number | null>(null)
const currentRow = ref<OrderListItem | null>(null)
const dateRange = ref<[Date | string | number, Date | string | number] | null>(null)
const router = useRouter()
const focusedField = ref<'number' | 'phone' | 'dateRange' | 'status' | null>(null)
const flipIsReset = ref(false)
const tableMaxHeight = 'calc(100vh - 180px)'
const operationChoice = reactive<Record<number, 'cancel' | 'complete' | 'view' | undefined>>({})
const pageSizeOptions = [10, 20, 30, 40] as const
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const pageInputText = ref(String(query.page))
const dialogOrnamentCss = computed(() => `url(${dialogOrnamentUrl})`)
const dialogTopSquareCss = computed(() => `url(${dialogTopSquareUrl})`)

const numberFloating = computed(() => (query.number ?? '').trim().length > 0 || focusedField.value === 'number')
const phoneFloating = computed(() => (query.phone ?? '').trim().length > 0 || focusedField.value === 'phone')
const deliveryTimelineStepCount = computed(() => {
  const d = detail.value
  if (!d) return 1
  const steps = [d.orderTime, d.estimatedDeliveryTime, d.deliveryTime].filter(Boolean).length
  return Math.max(1, steps)
})

function clampPage(page: number) {
  return Math.min(Math.max(1, page), totalPages.value)
}
function goToPage(page: number) {
  const next = clampPage(page)
  if (next === query.page) {
    pageInputText.value = String(next)
    return
  }
  query.page = next
  fetch()
}
function commitPageInput() {
  const parsed = Number(pageInputText.value)
  if (!Number.isFinite(parsed)) {
    pageInputText.value = String(query.page)
    return
  }
  goToPage(parsed)
}
function handlePageSizeChange() {
  query.page = 1
  fetch()
}

function canCancelOrder(status: number) {
  return [1, 3, 4].includes(status)
}
function canCompleteOrder(status: number) {
  return status === 4
}
function setOperationChoice(id: number, choice: 'cancel' | 'complete' | 'view') {
  operationChoice[id] = choice
}
async function handleOperationRadio(row: OrderListItem, choice: 'cancel' | 'complete' | 'view') {
  if (choice === 'view') {
    setOperationChoice(row.id, 'view')
    openDetail(row)
    return
  }

  if (choice === 'cancel') {
    if (!canCancelOrder(row.status)) return
    if (actionLoading[row.id]) return
    setOperationChoice(row.id, 'cancel')
    try {
      await handleCancel(row)
    } finally {
      setOperationChoice(row.id, 'view')
    }
    return
  }

  if (choice === 'complete') {
    if (!canCompleteOrder(row.status)) return
    if (actionLoading[row.id]) return
    setOperationChoice(row.id, 'complete')
    try {
      await handleComplete(row)
    } finally {
      setOperationChoice(row.id, 'view')
    }
  }
}

async function fetch() {
  loading.value = true
  try {
    const res = await getOrderPage(query)
    total.value = res.data.total
    records.value = res.data.records
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取订单失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: OrderListItem) {
  dialogVisible.value = true
  detailLoading.value = true
  dialogStatus.value = row.status
  currentRow.value = row
  try {
    const res = await getOrderDetails(row.id)
    detail.value = res.data
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取详情失败'
    ElMessage.error(message)
  } finally {
    detailLoading.value = false
  }
}

function gotoDetailPage() {
  if (!detail.value) return
  router.push({ name: 'OrderDetail', params: { id: detail.value.id } })
}

function onSearch() {
  if (dateRange.value && dateRange.value.length === 2) {
    const toDate = (v: Date | string | number) => {
      const d = v instanceof Date ? v : new Date(v)
      return Number.isNaN(d.getTime()) ? null : d
    }
    const start = toDate(dateRange.value[0])
    const end = toDate(dateRange.value[1])
    if (start && end) {
      query.beginTime = toDateTimeString(start)
      query.endTime = toDateTimeString(end)
    } else {
      query.beginTime = undefined
      query.endTime = undefined
    }
  } else {
    query.beginTime = undefined
    query.endTime = undefined
  }
  query.page = 1
  fetch()
}

function onReset() {
  query.page = 1
  query.pageSize = 10
  query.number = ''
  query.phone = ''
  query.status = undefined
  query.beginTime = undefined
  query.endTime = undefined
  dateRange.value = null
  fetch()
}

const actionLoading = reactive<Record<number, boolean>>({})

function lock(id: number) {
  actionLoading[id] = true
}
function unlock(id: number) {
  actionLoading[id] = false
}

function isMessageBoxCancel(e: unknown) {
  return e === 'cancel' || e === 'close'
}

async function handleConfirm(row: OrderListItem) {
  if (actionLoading[row.id]) return
  lock(row.id)
  const prev = row.status
  try {
    row.status = 3
    await confirmOrder(row.id)
    ElMessage.success('已接单')
    fetch()
  } catch (e) {
    row.status = prev
    throw e
  } finally {
    unlock(row.id)
  }
}
async function handleReject(row: OrderListItem) {
  if (actionLoading[row.id]) return false
  try {
    lock(row.id)
    const { value } = await ElMessageBox.prompt('请输入拒单原因', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '原因不能为空',
    })
    const prev = row.status
    row.status = 6
    try {
      await rejectOrder(row.id, value)
    } catch (e) {
      row.status = prev
      throw e
    }
    ElMessage.success('已拒单')
    fetch()
    return true
  } catch (e) {
    if (isMessageBoxCancel(e)) return false
    const message = e instanceof Error ? e.message : '拒单失败'
    ElMessage.error(message)
    return false
  } finally {
    unlock(row.id)
  }
}

async function dialogConfirm() {
  if (!currentRow.value) return
  try {
    await handleConfirm(currentRow.value)
    dialogVisible.value = false
  } catch (e) {
    const message = e instanceof Error ? e.message : '接单失败'
    ElMessage.error(message)
  }
}
async function dialogReject() {
  if (!currentRow.value) return
  const ok = await handleReject(currentRow.value)
  if (ok) dialogVisible.value = false
}
async function dialogDelivery() {
  if (!currentRow.value) return
  try {
    await handleDelivery(currentRow.value)
    dialogVisible.value = false
  } catch (e) {
    const message = e instanceof Error ? e.message : '派送失败'
    ElMessage.error(message)
  }
}
async function handleCancel(row: OrderListItem) {
  if (actionLoading[row.id]) return
  try {
    lock(row.id)
    const { value } = await ElMessageBox.prompt('请输入取消原因', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '原因不能为空',
    })
    const prev = row.status
    row.status = 6
    try {
      await cancelOrder(row.id, value)
    } catch (e) {
      row.status = prev
      throw e
    }
    ElMessage.success('已取消')
    fetch()
  } catch (e) {
    if (isMessageBoxCancel(e)) return
    const message = e instanceof Error ? e.message : '取消失败'
    ElMessage.error(message)
  } finally {
    unlock(row.id)
  }
}
async function handleDelivery(row: OrderListItem) {
  if (actionLoading[row.id]) return
  lock(row.id)
  const prev = row.status
  try {
    row.status = 4
    await deliveryOrder(row.id)
    ElMessage.success('已派送')
    fetch()
  } catch (e) {
    row.status = prev
    throw e
  } finally {
    unlock(row.id)
  }
}
async function handleComplete(row: OrderListItem) {
  if (actionLoading[row.id]) return
  lock(row.id)
  const prev = row.status
  try {
    row.status = 5
    await completeOrder(row.id)
    ElMessage.success('已完成')
    fetch()
  } catch (e) {
    row.status = prev
    throw e
  } finally {
    unlock(row.id)
  }
}

function handleFlipAction() {
  if (flipIsReset.value) {
    onReset()
    flipIsReset.value = false
    return
  }
  onSearch()
  flipIsReset.value = true
}

watch(
  () => route.query.status,
  (v) => {
    query.status = v ? Number(v) : undefined
    query.page = 1
    fetch()
  },
)

watch(
  () => [query.number, query.phone, query.status, dateRange.value],
  () => {
    flipIsReset.value = false
  },
)

watch(
  () => query.page,
  (v) => {
    pageInputText.value = String(v)
  },
)

onMounted(fetch)
</script>

<template>
  <div class="order-page">
    <img class="order-divider is-top" :src="dividerUrl" alt="" />

    <div class="order-search" role="search" aria-label="订单查询">
      <div class="search-grid">
        <div class="floating-field field-number" :data-floating="numberFloating">
          <div class="float-label">订单号</div>
          <el-input
            v-model="query.number"
            placeholder=""
            class="float-control"
            @keyup.enter="handleFlipAction"
            @focus="focusedField = 'number'"
            @blur="focusedField = null"
          />
        </div>

        <div class="floating-field field-phone" :data-floating="phoneFloating">
          <div class="float-label">手机号</div>
          <el-input
            v-model="query.phone"
            placeholder=""
            class="float-control"
            @keyup.enter="handleFlipAction"
            @focus="focusedField = 'phone'"
            @blur="focusedField = null"
          />
        </div>

        <div class="plain-field field-range">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            class="float-control"
          />
        </div>

        <div class="plain-field field-status">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            class="float-control status-select"
          >
            <el-option label="待接单" :value="2" />
            <el-option label="待派送" :value="3" />
            <el-option label="派送中" :value="4" />
            <el-option label="已完成" :value="5" />
            <el-option label="已取消" :value="6" />
          </el-select>
        </div>

        <div class="field-actions">
          <button
            class="flip-btn"
            type="button"
            :data-flipped="flipIsReset"
            :disabled="loading"
            @click="handleFlipAction"
          >
            <span class="flip-face flip-front">查询</span>
            <span class="flip-face flip-back">重置</span>
          </button>
        </div>
      </div>
    </div>

    <div class="order-table" aria-label="订单列表">
      <el-table :data="records" v-loading="loading" :table-layout="'fixed'" :max-height="tableMaxHeight">
        <el-table-column prop="number" label="订单号" min-width="140" align="center" header-align="center" />
        <el-table-column label="状态" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tooltip
                v-if="[1, 2, 3, 4, 5, 6].includes(row.status)"
                effect="light"
                placement="top"
                popper-class="status-tip"
              >
                <template #content>
                  <div class="status-tip-content">{{ getOrderStatusLabel(row.status) }}</div>
                </template>
                <span class="status-icon-wrap">
                  <img v-if="row.status === 1" class="status-icon" :src="statusWaitPayIconUrl" alt="待付款" />
                  <img v-else-if="row.status === 2" class="status-icon" :src="statusWaitAcceptIconUrl" alt="待接单" />
                  <img v-else-if="row.status === 3" class="status-icon" :src="statusWaitDeliverIconUrl" alt="待派送" />
                  <img v-else-if="row.status === 4" class="status-icon" :src="statusDeliveringIconUrl" alt="派送中" />
                  <img v-else-if="row.status === 5" class="status-icon" :src="statusCompletedIconUrl" alt="已完成" />
                  <img v-else class="status-icon" :src="statusCancelledIconUrl" alt="已取消" />
                </span>
              </el-tooltip>
              <el-tag v-else size="small" :type="getOrderStatusTagType(row.status)">{{ getOrderStatusLabel(row.status) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="订单菜品" min-width="180" align="center" header-align="center">
          <template #default="{ row }">
            <div class="ellipsis">
              <el-popover placement="top-start" trigger="hover" :content="row.orderDishes || ''">
                <template #reference>
                  <span>{{ row.orderDishes }}</span>
                </template>
              </el-popover>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="180" align="center" header-align="center">
          <template #default="{ row }">
            <div class="ellipsis">
              <el-popover placement="top-start" trigger="hover" :content="row.address || ''">
                <template #reference>
                  <span>{{ row.address }}</span>
                </template>
              </el-popover>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预计送达时间" width="170" align="center" header-align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.estimatedDeliveryTime) }}
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="140" align="center">
          <template #default="{ row }">
            <span :title="row.riskReasons || ''">{{ formatRiskLevel(row.riskLevel) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实收金额" width="130" align="center" header-align="center">
          <template #default="{ row }">
            {{ formatCurrency(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120" align="center" header-align="center">
          <template #default="{ row }">
            <div class="ellipsis">
              <el-popover placement="top-start" trigger="hover" :content="row.remark || ''">
                <template #reference>
                  <span>{{ row.remark }}</span>
                </template>
              </el-popover>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="op-cell">
              

              <div class="radio-inputs op-radio" :data-loading="!!actionLoading[row.id]">
                <label class="radio">
                  <input
                    type="radio"
                    :name="`op-${row.id}`"
                    :checked="(operationChoice[row.id] ?? 'view') === 'cancel'"
                    :disabled="!!actionLoading[row.id] || !canCancelOrder(row.status)"
                    @change="handleOperationRadio(row, 'cancel')"
                  />
                  <span class="name" :data-disabled="!canCancelOrder(row.status)" @click.stop="handleOperationRadio(row, 'cancel')"
                    >取消</span
                  >
                </label>
                <label class="radio">
                  <input
                    type="radio"
                    :name="`op-${row.id}`"
                    :checked="(operationChoice[row.id] ?? 'view') === 'complete'"
                    :disabled="!!actionLoading[row.id] || !canCompleteOrder(row.status)"
                    @change="handleOperationRadio(row, 'complete')"
                  />
                  <span
                    class="name"
                    :data-disabled="!canCompleteOrder(row.status)"
                    @click.stop="handleOperationRadio(row, 'complete')"
                    >完成</span
                  >
                </label>
                <label class="radio">
                  <input
                    type="radio"
                    :name="`op-${row.id}`"
                    :checked="(operationChoice[row.id] ?? 'view') === 'view'"
                    @change="handleOperationRadio(row, 'view')"
                  />
                  <span class="name" @click.stop="handleOperationRadio(row, 'view')">查看</span>
                </label>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination" aria-label="分页">
        <el-select v-model="query.pageSize" class="page-size-select" @change="handlePageSizeChange">
          <el-option v-for="s in pageSizeOptions" :key="s" :label="`${s}条/页`" :value="s" />
        </el-select>

        <div class="pager-simple">
          <button class="pager-arrow" type="button" :disabled="query.page <= 1" @click="goToPage(query.page - 1)">
            ‹
          </button>
          <el-input
            v-model="pageInputText"
            class="pager-input"
            inputmode="numeric"
            @keyup.enter="commitPageInput"
            @blur="commitPageInput"
          />
          <span class="pager-split">/</span>
          <span class="pager-total">{{ totalPages }}</span>
          <button
            class="pager-arrow"
            type="button"
            :disabled="query.page >= totalPages"
            @click="goToPage(query.page + 1)"
          >
            ›
          </button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" width="72%" class="order-info-dialog" :show-close="false">
      <template #header>
        <!-- 顶栏：深红色背景 + 花纹 + 方形角饰 + 标题 + 状态 + 关闭 -->
        <div class="order-info-topbar">
          <div class="topbar-square topbar-square-left"></div>
          <div class="topbar-square topbar-square-right"></div>
          <div class="topbar-left">
            <div class="topbar-title">余味居 · 订单详情</div>
          </div>
          <div class="topbar-right">
            <span class="topbar-status-text">订单情况：{{ getOrderStatusLabel(dialogStatus) }}</span>
            <button class="topbar-close" type="button" aria-label="关闭" @click="dialogVisible = false">×</button>
          </div>
        </div>
      </template>

      <!-- 浮动操作按钮（悬浮在右下角，位于顶栏之上） -->
      <div class="dialog-actions">
        <el-button type="primary" plain @click="gotoDetailPage">打开详情页</el-button>
        <el-button
          v-if="dialogStatus === 3"
          type="primary"
          :loading="!!(currentRow && currentRow.id && actionLoading[currentRow.id])"
          :disabled="!!(currentRow && currentRow.id && actionLoading[currentRow.id])"
          @click="dialogDelivery"
          >派送</el-button
        >
        <el-button
          v-if="dialogStatus === 2"
          type="primary"
          :loading="!!(currentRow && currentRow.id && actionLoading[currentRow.id])"
          :disabled="!!(currentRow && currentRow.id && actionLoading[currentRow.id])"
          @click="dialogConfirm"
          >接单</el-button
        >
        <el-button
          v-if="dialogStatus === 2"
          type="danger"
          :loading="!!(currentRow && currentRow.id && actionLoading[currentRow.id])"
          :disabled="!!(currentRow && currentRow.id && actionLoading[currentRow.id])"
          @click="dialogReject"
          >拒单</el-button
        >
        <el-button @click="dialogVisible = false">关闭</el-button>
      </div>

      <!-- 内容区：羊皮纸底色 -->
      <div class="dialog-parchment-body">
        <el-scrollbar height="60vh" v-loading="detailLoading">
          <div v-if="detail" class="order-info-layout">

            <!-- 订单信息卡片（在上） -->
            <section class="detail-card">
              <!-- 副标题图片 -->
              <div class="card-subtitle-wrap">
                <img class="card-subtitle-img" :src="orderInfoSubtitleUrl" alt="订单信息" />
              </div>
              <!-- 带双层边框的卡片内容 -->
              <div class="card-border-outer">
                <div class="card-border-inner">
                  <div class="card-content">
                    <div class="info-lines">
                      <div class="info-line">
                        <span class="info-k">下单时间：</span>
                        <span class="info-v">{{ formatDateTime(detail.orderTime) }}</span>
                      </div>
                      <div class="info-line">
                        <span class="info-k">用户名：</span>
                        <span class="info-v">{{ detail.consignee || '—' }}</span>
                      </div>
                      <div class="info-line">
                        <span class="info-k">手机号：</span>
                        <span class="info-v">{{ detail.phone || '—' }}</span>
                      </div>
                      <div class="info-line" v-if="[3, 4, 5].includes(dialogStatus || 0)">
                        <span class="info-k">{{ dialogStatus === 5 ? '送达时间：' : '预计送达时间：' }}</span>
                        <span class="info-v">
                          {{
                            dialogStatus === 5
                              ? formatDateTime(detail.deliveryTime)
                              : formatDateTime(detail.estimatedDeliveryTime)
                          }}
                        </span>
                      </div>
                      <div class="info-line">
                        <span class="info-k">备注：</span>
                        <span class="info-v" v-if="dialogStatus === 6">{{ detail.cancelReason || detail.rejectionReason || '空' }}</span>
                        <span class="info-v" v-else>{{ detail.remark || '空' }}</span>
                      </div>
                    </div>

                    <div class="dish-lines">
                      <div v-for="(d, i) in detail.orderDetailList || []" :key="i" class="dish-line">
                        <span class="dish-name">{{ d.name }}</span>
                        <span class="dish-qty">{{ d.number ? `×${d.number}` : '×1' }}</span>
                        <span class="dish-price">{{ d.amount ? formatCurrency(d.amount) : '—' }}</span>
                      </div>
                    </div>

                    <div class="total-line" v-if="detail.amount !== undefined">
                      <span class="total-k">实收金额：</span>
                      <span class="total-v">{{ formatCurrency(detail.amount) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 配送情况卡片（在下） -->
            <section class="detail-card">
              <div class="card-subtitle-wrap">
                <img class="card-subtitle-img card-subtitle-img--delivery" :src="deliverySubtitleUrl" alt="配送情况" />
              </div>
              <div class="card-border-outer">
                <div class="card-border-inner">
                  <div class="card-content delivery-content" :data-steps="deliveryTimelineStepCount">
                    <el-timeline class="delivery-timeline">
                      <el-timeline-item v-if="detail.orderTime" :timestamp="formatDateTime(detail.orderTime)" type="primary"
                        >下单</el-timeline-item
                      >
                      <el-timeline-item
                        v-if="detail.estimatedDeliveryTime"
                        :timestamp="formatDateTime(detail.estimatedDeliveryTime)"
                        type="info"
                        >预计送达</el-timeline-item
                      >
                      <el-timeline-item v-if="detail.deliveryTime" :timestamp="formatDateTime(detail.deliveryTime)" type="success"
                        >送达</el-timeline-item
                      >
                    </el-timeline>
                  </div>
                </div>
              </div>
            </section>

          </div>
        </el-scrollbar>

        <!-- 底部花纹 -->
        <div class="dialog-bottom-ornament">
          <img class="bottom-ornament-img" :src="dialogOrnamentUrl" alt="" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-page {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.order-search,
.order-table {
  background: transparent;
}
.order-search {
  margin-bottom: 10px;
}
.search-grid {
  display: flex;
  flex-wrap: nowrap;
  gap: 18px;
  align-items: flex-end;
}
.plain-field {
  width: auto;
  flex: 0 0 auto;
}
.floating-field {
  position: relative;
  width: auto;
  flex: 0 0 auto;
}
.field-number {
  width: 220px;
}
.field-phone {
  width: 220px;
}
.field-range {
  flex: 0 0 350px;
  min-width: 280px;
  margin-right: 18px;
  flex-shrink: 0;
}
.field-status {
  width: 200px;
  flex-shrink: 0;
}
.field-actions {
  margin-left: auto;
  display: flex;
  justify-content: flex-end;
  min-width: 140px;
}
.float-label {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-56%);
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 0.6px;
  color: #6e1d20;
  pointer-events: none;
  z-index: 2;
  transition:
    transform 240ms cubic-bezier(0.2, 0.9, 0.2, 1),
    color 240ms cubic-bezier(0.2, 0.9, 0.2, 1),
    opacity 240ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.floating-field[data-floating='true'] .float-label {
  top: 0;
  transform: translateY(-60%) scale(0.9);
  transform-origin: left center;
  color: #6e1d20;
  opacity: 0.95;
  padding: 0 8px;
  border-radius: 999px;
  background: #f8f6f1;
}
.float-control {
  width: 100%;
}
:deep(.order-search .el-input__wrapper),
:deep(.order-search .el-select__wrapper),
:deep(.order-search .el-date-editor.el-input__wrapper) {
  border-radius: 14px;
  background: #f8f6f1;
  border: 2px outset #e7c551;
  box-shadow: 0 10px 22px rgba(14, 16, 27, 0.08);
  min-height: 44px;
  transition:
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
:deep(.order-search .el-input__wrapper.is-focus),
:deep(.order-search .el-select__wrapper.is-focused),
:deep(.order-search .el-date-editor.el-input__wrapper.is-focus) {
  border-color: #e7c551;
  box-shadow:
    0 0 0 4px rgba(110, 29, 32, 0.08),
    0 16px 30px rgba(14, 16, 27, 0.12);
  transform: translateY(-1px);
}
:deep(.order-search .el-input__inner) {
  color: #6e1d20;
}
:deep(.order-search .el-input__inner::placeholder) {
  color: #6e1d20;
  opacity: 0.55;
}
:deep(.order-search .el-select__wrapper) {
  align-items: center;
}
:deep(.field-status .el-select__wrapper) {
  height: 44px;
  min-height: 44px;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
  position: relative;
}
:deep(.order-search .el-select__selected-item) {
  color: #6e1d20;
  display: flex;
  align-items: center;
  height: 100%;
  line-height: 1;
  position: static;
}
:deep(.order-search .el-select__placeholder) {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 20px;
  color: #91896f;
  opacity: 1;
  display: flex;
  align-items: center;
  height: 100%;
  line-height: 1;
}
:deep(.field-status .el-select__selected-item),
:deep(.field-status .el-select__placeholder) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  line-height: 1;
  padding-top: 0;
  padding-bottom: 10px;
  margin-top: 0;
  margin-bottom: 0;
  width: 100%;
  flex: 1 1 auto;
  text-align: center;
  box-sizing: border-box;
  padding-left: 28px;
  padding-right: 28px;
}

:deep(.field-status .el-select__suffix) {
  position: absolute;
  right: 12px;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
}
:deep(.order-search .el-range-input) {
  color: #6e1d20;
}
:deep(.order-search .el-range-input::placeholder) {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 20px;
  color: #91896f;
  opacity: 1;
}
:deep(.field-range .el-date-editor) {
  width: 100%;
  min-width: 0;
}
:deep(.field-status .el-select) {
  width: 100%;
}
:deep(.status-select .el-select__popper .el-select-dropdown) {
  border-radius: 12px;
  border: 1px solid rgba(110, 29, 32, 0.22);
  background: #f8f6f1;
  box-shadow: 0 18px 38px rgba(14, 16, 27, 0.14);
}
:deep(.status-select .el-select__popper .el-select-dropdown__item) {
  position: relative;
  padding-left: 28px;
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 18px;
  color: #6e1d20;
}
:deep(.status-select .el-select__popper .el-select-dropdown__item.selected)::before {
  content: '✓';
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #6e1d20;
  font-size: 14px;
}
:deep(.status-select .el-select__popper .el-select-dropdown__item.hover, .status-select .el-select__popper .el-select-dropdown__item:hover) {
  background: rgba(110, 29, 32, 0.06);
}
.flip-btn {
  height: 44px;
  width: 140px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.22);
  background: rgba(110, 29, 32, 0.92);
  color: rgba(240, 235, 222, 0.96);
  font-weight: 750;
  letter-spacing: 0.6px;
  position: relative;
  transform-style: preserve-3d;
  transition:
    transform 600ms cubic-bezier(0.2, 0.9, 0.2, 1),
    background 600ms cubic-bezier(0.2, 0.9, 0.2, 1),
    color 600ms cubic-bezier(0.2, 0.9, 0.2, 1);
  cursor: pointer;
}
.flip-btn:disabled {
  cursor: not-allowed;
  opacity: 0.82;
}
.flip-btn[data-flipped='true'] {
  transform: rotateX(180deg);
  background: rgba(255, 255, 255, 0.72);
  color: rgba(14, 16, 27, 0.88);
}
.flip-face {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  backface-visibility: hidden;
  border-radius: 999px;
}
.flip-front {
  transform: rotateX(0deg);
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 20px;
}
.flip-back {
  transform: rotateX(180deg);
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 20px;
}
.order-divider {
  width: 100%;
  height: auto;
  object-fit: contain;
  object-position: center;
  align-self: stretch;
  opacity: 0.95;
  pointer-events: none;
}
.order-divider.is-top {
  margin: 8px 0 18px;
}
:deep(.order-table .el-table) {
  background: transparent;
  --el-table-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(248, 246, 241, 0.06);
}
:deep(.order-table .el-table__inner-wrapper::before) {
  background: transparent;
}
:deep(.order-table .el-table__inner-wrapper) {
  background: transparent;
}
:deep(.order-table .el-scrollbar__view) {
  background: transparent;
}
:deep(.order-table .el-table__header-wrapper th.el-table__cell) {
  background: rgba(248, 246, 241, 0.06);
  color: #dfd5cb;
  font-weight: 900;
  font-size: 24px;
  font-family: '喜鹊招牌体', var(--app-font-brand);
  border-bottom: 1px solid #f0ebde;
}
:deep(.order-table .el-table__cell) {
  border-bottom: 1px solid #f0ebde;
}
:deep(.order-table .el-table__header-wrapper .el-table__cell .cell) {
  text-align: center;
}
:deep(.order-table .el-table__header-wrapper) {
  padding-bottom: 5px;
}
:deep(.order-table .el-table__body tr:nth-child(odd) > td.el-table__cell) {
  background: rgba(248, 246, 241, 0.02);
}
:deep(.order-table .el-table__body tr:nth-child(even) > td.el-table__cell) {
  background: rgba(248, 246, 241, 0.04);
}
:deep(.order-table .el-table__body tr:hover > td.el-table__cell) {
  background: rgba(248, 246, 241, 0.06);
}
:deep(.order-table .el-table__body-wrapper) {
  border-bottom: 1px solid #f0ebde;
}
:deep(.order-table .el-table__body-wrapper td.el-table__cell .cell) {
  color: #dfd5cb;
  text-align: center;
}
 :deep(.order-table .el-table__body),
 :deep(.order-table .el-table__header) {
  background: transparent;
}
.op-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}
.radio-inputs {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  border-radius: 12px;
  background-color: rgba(248, 246, 241, 0.82);
  box-sizing: border-box;
  border: 1px solid rgba(110, 29, 32, 0.18);
  padding: 4px;
  width: 260px;
  font-size: 14px;
}
.radio-inputs[data-loading='true'] {
  opacity: 0.78;
}
.radio-inputs .radio {
  flex: 1 1 auto;
  text-align: center;
}
.radio-inputs .radio input {
  display: none;
}
.radio-inputs .radio .name {
  display: flex;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: none;
  padding: 8px 0;
  color: rgba(14, 16, 27, 0.86);
  transition:
    background 180ms cubic-bezier(0.2, 0.9, 0.2, 1),
    color 180ms cubic-bezier(0.2, 0.9, 0.2, 1),
    transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.radio-inputs .radio .name[data-disabled='true'] {
  cursor: not-allowed;
  color: rgba(46, 52, 58, 0.42);
}
.radio-inputs .radio input:checked + .name {
  background-color: rgba(255, 255, 255, 0.92);
  font-weight: 800;
  color: #6e1d20;
}
.radio-inputs .radio input:checked + .name[data-disabled='true'] {
  background-color: rgba(255, 255, 255, 0.72);
  color: rgba(46, 52, 58, 0.42);
}

.status-cell {
  display: flex;
  justify-content: center;
}
.status-icon-wrap {
  display: inline-flex;
  padding: 4px;
  border-radius: 999px;
}
.status-icon {
  width: 26px;
  height: 26px;
  object-fit: contain;
  transition: transform 500ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.status-icon[alt='待派送'] {
  width: 35px;
  height: 35px;
}
.status-icon-wrap:hover .status-icon {
  transform: rotate(360deg) scale(1.08);
}
:deep(.status-tip) {
  border-radius: 12px;
  background: rgba(248, 246, 241, 0.96);
  box-shadow: 0 18px 40px rgba(14, 16, 27, 0.16);
}
.status-tip-content {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 14px;
  color: rgba(14, 16, 27, 0.9);
  letter-spacing: 0.3px;
  padding: 2px 2px;
}
.order-table {
  border-radius: 16px;
  border: 1px solid rgba(14, 16, 27, 0.08);
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(14, 16, 27, 0.14);
  background: transparent;
}
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  background: transparent;
  color: #dfd5cb;
}
.pager-simple {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.pager-split {
  opacity: 0.9;
}
.pager-total {
  font-variant-numeric: tabular-nums;
}
.pager-arrow {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: 1px solid rgba(240, 235, 222, 0.55);
  background: transparent;
  color: #dfd5cb;
  cursor: pointer;
  transition:
    transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1),
    background 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.pager-arrow:hover {
  transform: translateY(-1px);
  background: rgba(240, 235, 222, 0.12);
}
.pager-arrow:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
}
.pager-input {
  width: 54px;
}
:deep(.pager-input .el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  border: 1px solid rgba(240, 235, 222, 0.55);
  border-radius: 10px;
  min-height: 32px;
}
:deep(.pager-input .el-input__inner) {
  text-align: center;
  color: #dfd5cb;
  font-variant-numeric: tabular-nums;
}
.page-size-select {
  width: 120px;
}
:deep(.page-size-select .el-select__wrapper) {
  background: transparent;
  box-shadow: none;
  border: 1px solid rgba(240, 235, 222, 0.55);
  border-radius: 10px;
  min-height: 32px;
}
:deep(.page-size-select .el-select__selected-item),
:deep(.page-size-select .el-select__placeholder) {
  color: #dfd5cb;
}
/* ── Dialog 全局覆盖 ──────────────────────────────────── */
:deep(.order-info-dialog.el-dialog) {
  border-radius: 0;
  overflow: hidden;
  position: relative;
  background: #e8e0cc;
  box-shadow: 0 28px 64px rgba(14, 16, 27, 0.38);
  padding: 0;
}
:deep(.order-info-dialog .el-dialog__body) {
  padding: 0;
  color: rgba(14, 16, 27, 0.9);
  background: transparent;
  position: relative;
}
:deep(.order-info-dialog .el-dialog__header) {
  padding: 0;
  margin-right: 0;
  background: transparent !important;
}
:deep(.order-info-dialog .el-dialog__footer) {
  padding: 0;
  background: transparent;
}

/* ── 顶栏（深红 + 花纹横铺） ── */
.order-info-topbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 72px;
  background-color: #6e1d20;
  isolation: isolate;
  border-bottom: 3px solid #5c171a;
  box-shadow: 0 3px 0 0 #c8a84b;
  overflow: hidden;
}
.order-info-topbar::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: v-bind(dialogOrnamentCss);
  background-repeat: repeat-x;
  background-size: auto 100%;
  background-position: center;
  opacity: 0.25;
  mix-blend-mode: multiply;
  filter: brightness(0.62) contrast(1.35) saturate(1.1);
  pointer-events: none;
  z-index: 0;
}

/* 方形角饰 */
.topbar-square {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 52px;
  height: 52px;
  background-image: v-bind(dialogTopSquareCss);
  background-repeat: no-repeat;
  background-size: 200% 100%;
  background-position: left center;
  pointer-events: none;
  z-index: 4;
}
.topbar-square-left  { left: 8px; background-position: left center; }
.topbar-square-right { right: 8px; background-position: right center; }

.topbar-left {
  display: flex;
  align-items: center;
  padding-left: 64px;
  position: relative;
  z-index: 5;
}
.topbar-title {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 30px;
  font-weight: 900;
  letter-spacing: 3px;
  color: #e7c551;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.45);
  white-space: nowrap;
}
.topbar-right {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  padding-right: 64px;
  position: relative;
  z-index: 5;
}
.topbar-status-text {
  font-family: var(--app-font-brand);
  font-size: 18px;
  font-weight: 500;
  color: rgba(240, 235, 222, 0.9);
  letter-spacing: 0.5px;
  white-space: nowrap;
}
.topbar-close {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1.5px solid rgba(240, 235, 222, 0.5);
  background: rgba(0, 0, 0, 0.25);
  color: rgba(240, 235, 222, 0.9);
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 17px;
  line-height: 1;
  transition: background 160ms, color 160ms;
  flex-shrink: 0;
}
.topbar-close:hover {
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
}

/* ── 内容区（羊皮纸色） ── */
.dialog-parchment-body {
  background: #e8e0cc;
  position: relative;
}
.order-info-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px 28px 16px;
}

/* ── 卡片 ── */
.detail-card {
  display: flex;
  flex-direction: column;
}
/* 副标题图片：透明底，无背景色 */
.card-subtitle-wrap {
  position: relative;
  z-index: 2;
  margin-bottom: -1px;
  background: transparent !important;
  background-color: transparent !important;
  line-height: 0;
}
.card-subtitle-img {
  display: block;
  height: 46px;
  max-width: 420px;
  object-fit: contain;
  object-position: left bottom;
  background: transparent !important;
}
.card-subtitle-img--delivery {
  height: 54px;
  max-width: 420px;
}
/* 外框：切角边框 */
.card-border-outer {
  position: relative;
  background: #e8e0cc;
  border: 2px solid #8b1a1a;
  clip-path: polygon(
    14px 0%, calc(100% - 14px) 0%,
    100% 14px, 100% calc(100% - 14px),
    calc(100% - 14px) 100%, 14px 100%,
    0% calc(100% - 14px), 0% 14px
  );
  padding: 8px;
}
/* 内框：切角 */
.card-border-inner {
  border: 1px solid #8b1a1a;
  clip-path: polygon(
    10px 0%, calc(100% - 10px) 0%,
    100% 10px, 100% calc(100% - 10px),
    calc(100% - 10px) 100%, 10px 100%,
    0% calc(100% - 10px), 0% 10px
  );
  background: #e8e0cc;
  min-height: 80px;
}
.card-content {
  padding: 18px 22px 18px;
}
.delivery-content {
  min-height: 120px;
}
.delivery-content[data-steps='1'] {
  min-height: 120px;
}
.delivery-content[data-steps='2'] {
  min-height: 160px;
}
.delivery-content[data-steps='3'] {
  min-height: 220px;
}

/* ── 信息行：键深色，值金黄色 ── */
.info-lines {
  display: flex;
  flex-direction: column;
  gap: 11px;
  padding-bottom: 4px;
}
.info-line {
  display: flex;
  align-items: baseline;
  gap: 0;
  font-size: 15px;
  line-height: 1.5;
}
.info-k {
  font-weight: 800;
  letter-spacing: 0.3px;
  color: #1a1208;
  white-space: nowrap;
}
.info-v {
  font-weight: 700;
  color: #c8a020;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.25px;
}

/* ── 菜品列表 ── */
.dish-lines {
  margin-top: 12px;
  border-top: 1px dashed rgba(110, 29, 32, 0.35);
  padding-top: 10px;
  display: flex;
  flex-direction: column;
}
.dish-line {
  display: grid;
  grid-template-columns: 1fr 64px 96px;
  gap: 8px;
  align-items: baseline;
  padding: 9px 0;
  border-bottom: 1px dashed rgba(110, 29, 32, 0.22);
  font-size: 15px;
}
.dish-name {
  color: #1a1208;
  letter-spacing: 0.25px;
}
.dish-qty {
  color: rgba(46, 52, 58, 0.75);
  font-variant-numeric: tabular-nums;
  text-align: center;
}
.dish-price {
  color: #c8a020;
  font-weight: 800;
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.total-line {
  margin-top: 14px;
  display: flex;
  align-items: baseline;
  gap: 0;
  font-size: 15px;
}
.total-k {
  font-weight: 800;
  color: #1a1208;
  letter-spacing: 0.3px;
}
.total-v {
  font-weight: 900;
  font-size: 22px;
  letter-spacing: 0.5px;
  color: #c8a020;
  font-variant-numeric: tabular-nums;
}

/* ── 配送时间轴 ── */
.delivery-timeline {
  padding: 10px 12px;
}
:deep(.order-info-dialog .el-timeline-item__timestamp) {
  color: rgba(46, 52, 58, 0.72);
  font-variant-numeric: tabular-nums;
}
:deep(.order-info-dialog .el-timeline-item__content) {
  color: #1a1208;
  letter-spacing: 0.2px;
  font-weight: 600;
}

/* ── 底部花纹栏（深红色条带 + 花纹，与顶栏对称） ── */
.dialog-bottom-ornament {
  position: relative;
  width: 100%;
  background-color: #6e1d20;
  isolation: isolate;
  border-top: 3px solid #5c171a;
  box-shadow: 0 -3px 0 0 #c8a84b;
  height: 40px;
  overflow: hidden;
}
.dialog-bottom-ornament::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: v-bind(dialogOrnamentCss);
  background-repeat: repeat-x;
  background-size: auto 100%;
  background-position: center;
  opacity: 0.25;
  mix-blend-mode: multiply;
  filter: brightness(0.62) contrast(1.35) saturate(1.1);
  pointer-events: none;
  z-index: 0;
}
.bottom-ornament-img {
  display: block;
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.22;
  mix-blend-mode: multiply;
  z-index: 1;
  pointer-events: none;
}

/* ── 浮动操作按钮 ── */
.dialog-actions {
  position: fixed;
  right: 22px;
  bottom: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.22);
  background: rgba(232, 224, 204, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 16px 34px rgba(14, 16, 27, 0.2);
  z-index: 9999;
}
:deep(.dialog-actions .el-button) {
  border-radius: 999px;
}
@media (max-width: 1200px) {
  .dialog-actions {
    border-radius: 16px;
  }
  .order-info-layout {
    padding: 18px 14px 12px;
  }
  .topbar-title {
    font-size: 24px;
  }
}

@media (max-width: 1200px) {
  .search-grid {
    align-items: end;
  }
  .field-actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
