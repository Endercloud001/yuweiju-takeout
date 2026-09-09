<template>
  <div class="dashboard-panel">
    <div class="panel-header">
      <div class="panel-title-wrap">
        <img class="flame-icon" :src="flameUrl" alt="" />
        <div class="panel-title">今日数据</div>
      </div>
      <router-link class="link" to="/statistics">详细数据 <el-icon><ArrowRight /></el-icon></router-link>
    </div>
    <img class="panel-divider" :src="dividerUrl" alt="" />
    <div class="metric-grid">
      <div class="metric">
        <div class="metric-label">营业额</div>
        <div class="metric-value">{{ formatCurrency(data?.turnover ?? 0) }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">有效订单</div>
        <div class="metric-value">{{ data?.validOrderCount ?? 0 }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">订单完成率</div>
        <div class="metric-value">{{ completionRate }}%</div>
      </div>
      <div class="metric">
        <div class="metric-label">平均客单价</div>
        <div class="metric-value">{{ formatCurrency(data?.unitPrice ?? 0) }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">新增用户</div>
        <div class="metric-value">{{ data?.newUsers ?? 0 }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import type { BusinessData } from '../../../types/workspace'
import { formatCurrency } from '../../../utils/format'
import dividerUrl from '../../../assets/reference_images/image11.png?url'
import flameUrl from '../../../assets/reference_images/image31.png?url'

const props = defineProps<{
  data?: BusinessData
}>()

const completionRate = computed(() => {
  const rate = props.data?.orderCompletionRate ?? 0
  return Math.round(rate * 100)
})
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
.link {
  color: var(--app-text-secondary);
  text-decoration: none;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color 0.2s ease;
}
.link:hover {
  color: var(--app-primary);
}

.panel-divider {
  width: 100%;
  height: 24px;
  object-fit: contain;
  object-position: center;
  margin-bottom: 24px;
  opacity: 0.8;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  position: relative;
  z-index: 1;
}
.metric {
  background-color: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  padding: 20px 16px;
  border: 1px solid rgba(14, 16, 27, 0.04);
  box-shadow: 0 2px 10px rgba(14, 16, 27, 0.02);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.metric:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(14, 16, 27, 0.06);
}
.metric-label {
  font-size: 14px;
  color: var(--app-text-secondary);
}
.metric-value {
  font-weight: 850;
  font-size: 28px;
  color: var(--app-text);
  font-family: 'Franklin Gothic Demi', var(--app-font-text);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
