<template>
  <div class="dashboard-panel">
    <div class="panel-header">
      <div class="panel-title-wrap">
        <img class="flame-icon" :src="flameUrl" alt="" />
        <div class="panel-title">订单信息</div>
      </div>
      <el-button size="small" :loading="loading" @click="emit('refresh')" class="refresh-btn">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>
    <img class="panel-divider" :src="dividerUrl" alt="" />
    <div class="metric-grid">
      <router-link class="metric warn" :to="{ path: '/order', query: { status: '2' } }">
        <div class="metric-label">待接单</div>
        <div class="metric-value">{{ data?.toBeConfirmed ?? 0 }}</div>
      </router-link>
      <router-link class="metric info" :to="{ path: '/order', query: { status: '3' } }">
        <div class="metric-label">待派送</div>
        <div class="metric-value">{{ data?.confirmed ?? 0 }}</div>
      </router-link>
      <router-link class="metric primary" :to="{ path: '/order', query: { status: '4' } }">
        <div class="metric-label">派送中</div>
        <div class="metric-value">{{ data?.deliveryInProgress ?? 0 }}</div>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import type { OrderStatusStatistics } from '../../../types/order'
import dividerUrl from '../../../assets/reference_images/image11.png?url'
import flameUrl from '../../../assets/reference_images/image31.png?url'

defineProps<{
  data?: OrderStatusStatistics
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()
</script>

<style scoped>
.dashboard-panel {
  padding: 24px;
  position: relative;
  border-radius: var(--panel-radius);
  background: var(--panel-bg);
  backdrop-filter: blur(2px);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
}
.dashboard-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: var(--panel-radius);
  box-shadow: var(--panel-inset-highlight);
  pointer-events: none;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  position: relative;
  z-index: 1;
}
.panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.flame-icon {
  width: 24px;
  height: 24px;
  object-fit: contain;
  opacity: 0.8;
}
.panel-title {
  font-family: var(--app-font-brand);
  font-size: 24px;
  color: var(--app-primary);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.panel-divider {
  width: 100%;
  height: 24px;
  object-fit: contain;
  object-position: center;
  margin-bottom: 24px;
  opacity: 0.8;
}

.refresh-btn {
  background: transparent;
  border-color: rgba(14, 16, 27, 0.15);
  color: var(--app-text-secondary);
}
.refresh-btn:hover {
  color: var(--app-primary);
  border-color: var(--app-primary);
  background: rgba(110, 29, 32, 0.05);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  position: relative;
  z-index: 1;
}
.metric {
  text-decoration: none;
  background-color: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  padding: 24px 20px;
  border: 1px solid rgba(14, 16, 27, 0.04);
  box-shadow: 0 2px 10px rgba(14, 16, 27, 0.02);
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.metric:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(14, 16, 27, 0.06);
}

.metric.warn {
  border-color: rgba(217, 74, 43, 0.26);
  background: rgba(255, 255, 255, 0.85);
}
.metric.warn .metric-value { color: var(--el-color-danger); }

.metric.info {
  border-color: rgba(74, 143, 163, 0.28);
  background: rgba(255, 255, 255, 0.85);
}
.metric.info .metric-value { color: var(--el-color-info); }

.metric.primary {
  border-color: rgba(110, 29, 32, 0.2);
  background: rgba(255, 255, 255, 0.85);
}
.metric.primary .metric-value { color: var(--app-primary); }

.metric-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text-secondary);
}
.metric-value {
  font-weight: 850;
  font-size: 36px;
  color: var(--app-text);
  font-family: 'Franklin Gothic Demi', var(--app-font-text);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

@media (max-width: 768px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
