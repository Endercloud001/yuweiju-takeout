<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetails, submitOrderRiskFeedback } from '../../api/modules/order'
import type { OrderDetail } from '../../types/order'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { getOrderStatusLabel } from '../../utils/constants'

const route = useRoute()
const detail = ref<OrderDetail | null>(null)
const loading = ref(false)
const feedbackLoading = ref(false)

const orderId = computed(() => Number(route.params.id))

const showDeliveryTime = computed(() => {
  const status = detail.value?.status ?? 0
  return [3, 4, 5].includes(status)
})

const deliveryTimeLabel = computed(() => {
  const status = detail.value?.status ?? 0
  return status === 5 ? '送达时间' : '预计送达时间'
})

const deliveryTimeValue = computed(() => {
  if (!detail.value) return ''
  return detail.value.status === 5
    ? formatDateTime(detail.value.deliveryTime)
    : formatDateTime(detail.value.estimatedDeliveryTime)
})

const noteLabel = computed(() => (detail.value?.status === 6 ? '取消原因' : '备注'))

const noteValue = computed(() => {
  if (!detail.value) return '空'
  if (detail.value.status === 6) return detail.value.cancelReason || detail.value.rejectionReason || '空'
  return detail.value.remark || '空'
})

const riskDecisionLabel = computed(() => {
  const status = (detail.value?.decisionStatus || '').toUpperCase()
  if (status === 'APPROVE') return '已通过'
  if (status === 'REJECT') return '已驳回'
  if (status === 'REVIEW') return '待观察'
  return '待复核'
})

async function fetchDetail() {
  if (!Number.isFinite(orderId.value)) {
    ElMessage.error('订单ID无效')
    detail.value = null
    return
  }
  loading.value = true
  try {
    const res = await getOrderDetails(orderId.value)
    detail.value = res.data
  } catch (e) {
    const msg = e instanceof Error ? e.message : '获取详情失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  window.print()
}

function handleExport() {
  if (!detail.value) return
  const blob = new Blob([JSON.stringify(detail.value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `order-${detail.value.number || orderId.value}.json`
  a.click()
  URL.revokeObjectURL(url)
}

async function submitFeedback(decision: 'approve' | 'reject' | 'review') {
  if (!detail.value || feedbackLoading.value) return
  try {
    const { value } = await ElMessageBox.prompt('可选填写复核说明（可留空）', `提交复核结果：${decision}`, {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：已核实订单正常',
      inputPattern: /^(.*)$/s,
    })
    feedbackLoading.value = true
    await submitOrderRiskFeedback({
      orderId: detail.value.id,
      decision,
      reason: value?.trim() ? value.trim() : undefined,
    })
    ElMessage.success('复核结果已提交')
    await fetchDetail()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    const msg = e instanceof Error ? e.message : '复核提交失败'
    ElMessage.error(msg)
  } finally {
    feedbackLoading.value = false
  }
}

watch(orderId, fetchDetail, { immediate: true })
</script>

<template>
  <div class="order-detail-page" v-loading="loading">
    <div class="action-bar">
      <el-button @click="handleExport">导出JSON</el-button>
      <el-button type="primary" @click="handlePrint">打印</el-button>
      <el-button type="success" :loading="feedbackLoading" @click="submitFeedback('approve')">复核通过</el-button>
      <el-button type="danger" :loading="feedbackLoading" @click="submitFeedback('reject')">复核驳回</el-button>
      <el-button type="warning" :loading="feedbackLoading" @click="submitFeedback('review')">继续观察</el-button>
    </div>

    <div v-if="detail" class="detail-grid">
      <section class="card">
        <h3>订单信息</h3>
        <div class="kv-grid">
          <div class="kv-item">
            <span class="k">订单状态</span>
            <span class="v">{{ getOrderStatusLabel(detail.status) }}</span>
          </div>
          <div class="kv-item">
            <span class="k">下单时间</span>
            <span class="v">{{ formatDateTime(detail.orderTime) || '--' }}</span>
          </div>
          <div class="kv-item">
            <span class="k">用户名称</span>
            <span class="v">{{ detail.consignee || '--' }}</span>
          </div>
          <div class="kv-item">
            <span class="k">手机号</span>
            <span class="v">{{ detail.phone || '--' }}</span>
          </div>
          <div v-if="showDeliveryTime" class="kv-item">
            <span class="k">{{ deliveryTimeLabel }}</span>
            <span class="v">{{ deliveryTimeValue || '--' }}</span>
          </div>
          <div class="kv-item full">
            <span class="k">地址</span>
            <span class="v">{{ detail.address || '--' }}</span>
          </div>
          <div class="kv-item full">
            <span class="k">{{ noteLabel }}</span>
            <span class="v">{{ noteValue }}</span>
          </div>
        </div>

        <div class="dish-lines">
          <div v-for="(d, i) in detail.orderDetailList || []" :key="i" class="dish-line">
            <span>{{ d.name }}</span>
            <span>x{{ d.number || 0 }}</span>
            <span>{{ d.amount ? formatCurrency(d.amount) : '--' }}</span>
          </div>
        </div>

        <div class="total">实收金额：{{ detail.amount !== undefined ? formatCurrency(detail.amount) : '--' }}</div>
      </section>

      <section class="card">
        <h3>配送时间线</h3>
        <el-timeline>
          <el-timeline-item v-if="detail.orderTime" :timestamp="formatDateTime(detail.orderTime)" type="primary">下单</el-timeline-item>
          <el-timeline-item v-if="detail.estimatedDeliveryTime" :timestamp="formatDateTime(detail.estimatedDeliveryTime)" type="info">预计送达</el-timeline-item>
          <el-timeline-item v-if="detail.deliveryTime" :timestamp="formatDateTime(detail.deliveryTime)" type="success">送达</el-timeline-item>
        </el-timeline>
      </section>

      <section class="card">
        <h3>风控复核</h3>
        <div class="kv-grid">
          <div class="kv-item">
            <span class="k">风险分</span>
            <span class="v">{{ detail.riskScore ?? '--' }}</span>
          </div>
          <div class="kv-item">
            <span class="k">风险等级</span>
            <span class="v">{{ detail.riskLevel || '--' }}</span>
          </div>
          <div class="kv-item">
            <span class="k">模型版本</span>
            <span class="v">{{ detail.modelVersion || '--' }}</span>
          </div>
          <div class="kv-item">
            <span class="k">复核状态</span>
            <span class="v">{{ riskDecisionLabel }}</span>
          </div>
          <div class="kv-item full">
            <span class="k">风险说明</span>
            <span class="v">{{ detail.riskReasons || '--' }}</span>
          </div>
        </div>
      </section>
    </div>

    <el-empty v-else description="未找到订单详情" />
  </div>
</template>

<style scoped>
.order-detail-page {
  padding: 16px;
}

.action-bar {
  position: sticky;
  top: 12px;
  z-index: 10;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(240, 235, 222, 0.92);
  border: 1px solid rgba(110, 29, 32, 0.12);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.card {
  background: #f0ebde;
  border: 1px solid rgba(110, 29, 32, 0.1);
  border-radius: 14px;
  padding: 16px;
}

.card h3 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #28160f;
}

.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.kv-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kv-item.full {
  grid-column: 1 / -1;
}

.k {
  font-size: 12px;
  color: #7b6f61;
}

.v {
  font-size: 14px;
  color: #28160f;
  word-break: break-word;
}

.dish-lines {
  margin-top: 12px;
  border-top: 1px dashed rgba(110, 29, 32, 0.3);
}

.dish-line {
  display: grid;
  grid-template-columns: 1fr 80px 120px;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(110, 29, 32, 0.18);
}

.total {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 700;
  color: #6e1d20;
}

@media (max-width: 900px) {
  .kv-grid {
    grid-template-columns: 1fr;
  }

  .dish-line {
    grid-template-columns: 1fr;
  }
}
</style>
