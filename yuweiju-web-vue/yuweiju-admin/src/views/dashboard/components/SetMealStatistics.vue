<template>
  <div class="dashboard-panel">
    <div class="panel-header">
      <div class="panel-title-wrap">
        <img class="flame-icon" :src="flameUrl" alt="" />
        <div class="panel-title">套餐总览</div>
      </div>
      <router-link class="link" to="/setmeal">套餐管理 <el-icon><ArrowRight /></el-icon></router-link>
    </div>
    <img class="panel-divider" :src="dividerUrl" alt="" />
    <div class="metric-grid">
      <div class="metric">
        <div class="metric-label">已启售</div>
        <div class="metric-value">{{ data?.sold ?? 0 }}</div>
      </div>
      <div class="metric">
        <div class="metric-label">已停售</div>
        <div class="metric-value">{{ data?.discontinued ?? 0 }}</div>
      </div>
      <div class="action-wrap">
        <el-button class="add-btn" @click="router.push('/setmeal/add')">
          <el-icon class="add-icon"><Plus /></el-icon>
          新增套餐
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight, Plus } from '@element-plus/icons-vue'
import type { OverviewSimpleData } from '../../../types/workspace'
import dividerUrl from '../../../assets/reference_images/image29.png?url'
import flameUrl from '../../../assets/reference_images/image31.png?url'

defineProps<{
  data?: OverviewSimpleData
}>()

const router = useRouter()
</script>

<style scoped>
.dashboard-panel {
  padding: 24px;
  position: relative;
  height: 100%;
  box-sizing: border-box;
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
  grid-template-columns: 1fr 1fr auto;
  gap: 16px;
  position: relative;
  z-index: 1;
  align-items: stretch;
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

.action-wrap {
  display: flex;
  align-items: stretch;
}
.add-btn {
  height: auto;
  min-height: 80px;
  width: 120px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: rgba(217, 139, 43, 0.1);
  border: 1px solid rgba(217, 139, 43, 0.3);
  color: #d98b2b;
  font-weight: 600;
  font-size: 15px;
  transition: all 0.2s ease;
}
.add-btn:hover {
  background: rgba(217, 139, 43, 0.18);
  border-color: rgba(217, 139, 43, 0.4);
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(217, 139, 43, 0.12);
}
.add-icon {
  font-size: 24px;
}
</style>
