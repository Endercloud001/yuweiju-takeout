<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CategoryItem } from '../../types/category'
import type { SetmealItem, SetmealPageQuery, SetmealStatus } from '../../types/setmeal'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { apiBase } from '../../api/http'
import { getCategoryList } from '../../api/modules/category'
import { deleteSetmeal, getSetmealPage, setSetmealStatus } from '../../api/modules/setmeal'
import dividerUrl from '../../assets/reference_images/image29.png?url'
import missingDishSvgRaw from '../../assets/reference_images/菜品图片缺失补位图.svg?raw'

type FocusedField = 'name' | 'category' | 'status' | null

const router = useRouter()

const query = reactive<SetmealPageQuery>({
  page: 1,
  pageSize: 10,
  name: '',
  categoryId: undefined,
  status: undefined,
})

const records = ref<SetmealItem[]>([])
const loading = ref(false)
const total = ref(0)

const focusedField = ref<FocusedField>(null)
const flipIsReset = ref(false)

const categoryOptions = ref<CategoryItem[]>([])

const selectedIds = ref<number[]>([])
const tableRef = ref<unknown>(null)

const pageSizeOptions = [10, 20, 30, 40] as const
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const pageInputText = ref(String(query.page))
const tableMaxHeight = computed(() => (query.pageSize === 10 ? undefined : 'calc(100vh - 190px)'))

const nameFloating = computed(() => (query.name ?? '').trim().length > 0 || focusedField.value === 'name')
const categoryFloating = computed(() => query.categoryId !== undefined || focusedField.value === 'category')
const statusFloating = computed(() => query.status !== undefined || focusedField.value === 'status')

function createMissingImageUrl() {
  let cleaned = missingDishSvgRaw.replace(/<\?xml[^>]*\?>/gi, '').replace(/<!doctype[^>]*>/gi, '').trim()
  cleaned = cleaned.replace(/viewBox=(['"])0\s+0\s+1024\s+1024\1/i, 'viewBox="150 70 820 820"')
  if (/preserveAspectRatio=/i.test(cleaned)) {
    cleaned = cleaned.replace(/preserveAspectRatio=(['"])[\s\S]*?\1/i, 'preserveAspectRatio="xMidYMid slice"')
  } else {
    cleaned = cleaned.replace(/<svg\b/i, '<svg preserveAspectRatio="xMidYMid slice"')
  }
  if (typeof window === 'undefined' || typeof URL === 'undefined' || typeof Blob === 'undefined') {
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(cleaned)}`
  }
  return URL.createObjectURL(new Blob([cleaned], { type: 'image/svg+xml' }))
}

const missingImageUrl = createMissingImageUrl()

function resolveImageUrl(value?: string) {
  const v = (value ?? '').trim()
  if (!v) return ''
  if (/^(https?:)?\/\//.test(v) || v.startsWith('data:') || v.startsWith('blob:')) return v
  return `${apiBase}/common/download?name=${encodeURIComponent(v)}`
}

function getSetmealImageUrl(value?: string) {
  return resolveImageUrl(value) || missingImageUrl
}

function getIsPlaceholder(value?: string) {
  return !resolveImageUrl(value)
}

function handleImgError(e: Event) {
  const img = e.target as HTMLImageElement | null
  if (!img) return
  if (img.src === missingImageUrl) return
  img.dataset.placeholder = 'true'
  const wrap = img.parentElement as HTMLElement | null
  if (wrap) {
    wrap.dataset.placeholder = 'true'
  }
  img.src = missingImageUrl
}

function getStatusLabel(status: SetmealStatus) {
  return status === 1 ? '启售' : '停售'
}

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

function onSearch() {
  query.page = 1
  fetch()
}

function onReset() {
  query.page = 1
  query.pageSize = 10
  query.name = ''
  query.categoryId = undefined
  query.status = undefined
  selectedIds.value = []
  clearSelection()
  fetch()
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

function clearSelection() {
  const maybe = tableRef.value as unknown as { clearSelection?: () => void }
  if (typeof maybe?.clearSelection === 'function') {
    maybe.clearSelection()
  }
}

function handleSelectionChange(rows: SetmealItem[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function isMessageBoxCancel(e: unknown) {
  return e === 'cancel' || e === 'close'
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    await ElMessageBox.alert('请先选择删除对象！', '提示', { confirmButtonText: '确定' })
    return
  }
  try {
    await ElMessageBox.confirm('确认批量删除所选套餐？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    if (isMessageBoxCancel(e)) return
    return
  }

  try {
    await deleteSetmeal(selectedIds.value.join(','))
    ElMessage.success('删除成功')
    selectedIds.value = []
    clearSelection()
    fetch()
  } catch (e) {
    const message = e instanceof Error ? e.message : '删除失败'
    ElMessage.error(message)
  }
}

async function handleDelete(row: SetmealItem) {
  try {
    await ElMessageBox.confirm('确认删除该套餐？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    if (isMessageBoxCancel(e)) return
    return
  }

  try {
    await deleteSetmeal(String(row.id))
    ElMessage.success('删除成功')
    fetch()
  } catch (e) {
    const message = e instanceof Error ? e.message : '删除失败'
    ElMessage.error(message)
  }
}

async function handleToggleStatus(row: SetmealItem) {
  const next: SetmealStatus = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm('确认调整该套餐的售卖状态？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    if (isMessageBoxCancel(e)) return
    return
  }

  try {
    await setSetmealStatus(row.id, next)
    ElMessage.success('状态更新成功')
    fetch()
  } catch (e) {
    const message = e instanceof Error ? e.message : '状态更新失败'
    ElMessage.error(message)
  }
}

function gotoAdd() {
  router.push({ name: 'SetmealAdd' })
}

function gotoEdit(row: SetmealItem) {
  router.push({ name: 'SetmealAdd', query: { id: String(row.id) } })
}

async function fetchCategoryOptions() {
  try {
    const res = await getCategoryList(2)
    categoryOptions.value = res.data
  } catch {
    categoryOptions.value = []
  }
}

async function fetch() {
  loading.value = true
  try {
    const trimmedName = (query.name ?? '').trim()
    const params: SetmealPageQuery = {
      page: query.page,
      pageSize: query.pageSize,
      name: trimmedName ? trimmedName : undefined,
      categoryId: query.categoryId,
      status: query.status,
    }
    const res = await getSetmealPage(params)
    total.value = res.data.total
    records.value = res.data.records
    if (query.page > totalPages.value) {
      query.page = totalPages.value
      pageInputText.value = String(query.page)
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取套餐失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

watch(
  () => [query.name, query.categoryId, query.status],
  () => {
    flipIsReset.value = false
  },
)

watch(
  () => query.page,
  () => {
    pageInputText.value = String(query.page)
  },
)

onMounted(() => {
  fetchCategoryOptions()
  fetch()
})
</script>

<template>
  <div class="setmeal-page">
    <img class="order-divider is-top" :src="dividerUrl" alt="" />

    <div class="order-search" role="search" aria-label="套餐查询">
      <div class="search-grid search-grid-setmeal">
        <div class="floating-field field-name" :data-floating="nameFloating">
          <div class="float-label">套餐名称</div>
          <el-input
            v-model="query.name"
            placeholder=""
            class="float-control"
            @keyup.enter="handleFlipAction"
            @focus="focusedField = 'name'"
            @blur="focusedField = null"
          />
        </div>

        <div class="floating-field field-category" :data-floating="categoryFloating">
          <div class="float-label">套餐分类</div>
          <el-select
            v-model="query.categoryId"
            placeholder=""
            clearable
            class="float-control status-select"
            @focus="focusedField = 'category'"
            @blur="focusedField = null"
          >
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </div>

        <div class="floating-field field-status" :data-floating="statusFloating">
          <div class="float-label">售卖状态</div>
          <el-select
            v-model="query.status"
            placeholder=""
            clearable
            class="float-control status-select"
            @focus="focusedField = 'status'"
            @blur="focusedField = null"
          >
            <el-option label="启售" :value="1" />
            <el-option label="停售" :value="0" />
          </el-select>
        </div>

        <div class="field-actions">
          <button class="flip-btn" type="button" :data-flipped="flipIsReset" :disabled="loading" @click="handleFlipAction">
            <span class="flip-face flip-front">查询</span>
            <span class="flip-face flip-back">重置</span>
          </button>
        </div>

        <div class="add-actions">
          <button class="danger-swell-btn" type="button" :disabled="loading" @click="handleBatchDelete">
            <span class="danger-swell-bg" aria-hidden="true"></span>
            <span class="danger-swell-left" aria-hidden="true">
              <svg xmlns="http://www.w3.org/2000/svg" class="danger-swell-wave" viewBox="0 0 487 487">
                <path
                  fill-opacity=".1"
                  fill-rule="nonzero"
                  fill="#FFF"
                  d="M0 .3c67 2.1 134.1 4.3 186.3 37 52.2 32.7 89.6 95.8 112.8 150.6 23.2 54.8 32.3 101.4 61.2 149.9 28.9 48.4 77.7 98.8 126.4 149.2H0V.3z"
                ></path>
              </svg>
            </span>
            <span class="danger-swell-right" aria-hidden="true">
              <svg xmlns="http://www.w3.org/2000/svg" class="danger-swell-wave" viewBox="0 0 487 487">
                <path
                  fill-opacity=".1"
                  fill-rule="nonzero"
                  fill="#FFF"
                  d="M487 486.7c-66.1-3.6-132.3-7.3-186.3-37s-95.9-85.3-126.2-137.2c-30.4-51.8-49.3-99.9-76.5-151.4C70.9 109.6 35.6 54.8.3 0H487v486.7z"
                ></path>
              </svg>
            </span>
            <span class="danger-swell-gloss" aria-hidden="true"></span>
            <span class="danger-swell-text">批量删除</span>
          </button>

          <button class="solid-btn" type="button" :disabled="loading" @click="gotoAdd">+ 新建套餐</button>
        </div>
      </div>
    </div>

    <div class="order-table" aria-label="套餐列表">
      <el-table
        ref="tableRef"
        :data="records"
        v-loading="loading"
        :table-layout="'fixed'"
        :max-height="tableMaxHeight"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="56" align="center" header-align="center" />
        <el-table-column prop="name" label="套餐名称" min-width="160" align="center" header-align="center" />
        <el-table-column label="图片" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <div class="thumb-wrap" :data-placeholder="getIsPlaceholder(row.image) ? 'true' : 'false'">
              <img
                class="setmeal-thumb"
                :src="getSetmealImageUrl(row.image)"
                alt=""
                :data-placeholder="getIsPlaceholder(row.image) ? 'true' : 'false'"
                @error="handleImgError"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="套餐分类" width="160" align="center" header-align="center">
          <template #default="{ row }">
            <span class="type-cell">{{ row.categoryName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="120" align="center" header-align="center">
          <template #default="{ row }">
            <span class="price-cell">{{ formatCurrency(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售卖状态" width="140" align="center" header-align="center">
          <template #default="{ row }">
            <span class="status-pill" :data-status="row.status">
              <span class="status-dot" aria-hidden="true"></span>
              <span class="status-text">{{ getStatusLabel(row.status) }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最后操作时间" width="220" align="center" header-align="center">
          <template #default="{ row }">
            <span class="time-cell">{{ row.updateTime ? formatDateTime(row.updateTime) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="text-action is-primary" type="button" :disabled="loading" @click="gotoEdit(row)">修改</button>
              <button class="text-action is-danger" type="button" :disabled="loading" @click="handleDelete(row)">删除</button>
              <button class="text-action" type="button" :disabled="loading" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '停售' : '启售' }}
              </button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination" aria-label="分页">
        <el-select v-model="query.pageSize" class="page-size-select" :disabled="loading" @change="handlePageSizeChange">
          <el-option v-for="s in pageSizeOptions" :key="s" :label="`${s}条/页`" :value="s" />
        </el-select>

        <div class="pager-simple">
          <button class="pager-arrow" type="button" :disabled="loading || query.page <= 1" @click="goToPage(query.page - 1)">‹</button>
          <el-input
            v-model="pageInputText"
            class="pager-input"
            inputmode="numeric"
            :disabled="loading"
            @keyup.enter="commitPageInput"
            @blur="commitPageInput"
          />
          <span class="pager-split">/</span>
          <span class="pager-total">{{ totalPages }}</span>
          <button
            class="pager-arrow"
            type="button"
            :disabled="loading || query.page >= totalPages"
            @click="goToPage(query.page + 1)"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.setmeal-page {
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
  flex-wrap: wrap;
  gap: 18px;
  align-items: flex-end;
}

.floating-field {
  position: relative;
  width: auto;
  flex: 0 0 auto;
}

.field-name {
  width: 260px;
}

.field-category {
  width: 260px;
}

.field-status {
  width: 220px;
}

.field-actions {
  display: flex;
  justify-content: flex-start;
  min-width: 140px;
}

.add-actions {
  margin-left: auto;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
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
:deep(.order-search .el-select__wrapper) {
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
:deep(.order-search .el-select__wrapper.is-focused) {
  border-color: #e7c551;
  box-shadow:
    0 0 0 4px rgba(110, 29, 32, 0.08),
    0 16px 30px rgba(14, 16, 27, 0.12);
  transform: translateY(-1px);
}

:deep(.field-category .el-select__wrapper),
:deep(.field-status .el-select__wrapper),
:deep(.field-name .el-input__wrapper) {
  height: 44px;
  min-height: 44px;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
  position: relative;
}

:deep(.order-search .el-input__inner) {
  color: #6e1d20;
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

:deep(.field-category .el-select__selected-item),
:deep(.field-category .el-select__placeholder),
:deep(.field-status .el-select__selected-item),
:deep(.field-status .el-select__placeholder) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  line-height: 1;
  width: 100%;
  flex: 1 1 auto;
  text-align: center;
  box-sizing: border-box;
  padding-left: 28px;
  padding-right: 28px;
  padding-bottom: 10px;
}

:deep(.field-category .el-select__suffix),
:deep(.field-status .el-select__suffix) {
  position: absolute;
  right: 12px;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
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

.solid-btn {
  height: 44px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.22);
  background: rgba(110, 29, 32, 0.92);
  color: rgba(240, 235, 222, 0.96);
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 18px;
  letter-spacing: 0.4px;
  cursor: pointer;
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    background 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
  box-shadow: 0 12px 26px rgba(14, 16, 27, 0.14);
}

.solid-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 32px rgba(14, 16, 27, 0.18);
}

.solid-btn:disabled {
  cursor: not-allowed;
  opacity: 0.82;
  transform: none;
}

.danger-swell-btn {
  position: relative;
  height: 44px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.16);
  background: #f9f9f9;
  color: rgba(14, 16, 27, 0.88);
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 18px;
  letter-spacing: 0.4px;
  cursor: pointer;
  overflow: hidden;
  isolation: isolate;
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
  box-shadow: 0 12px 26px rgba(14, 16, 27, 0.12);
}

.danger-swell-btn:disabled {
  cursor: not-allowed;
  opacity: 0.82;
}

.danger-swell-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(217, 74, 43, 0.35);
  box-shadow: 0 16px 32px rgba(14, 16, 27, 0.18);
}

.danger-swell-bg {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0;
  height: 0;
  transform: translate(-50%, -50%);
  border-radius: 999px;
  background: rgba(217, 74, 43, 0.94);
  transition: width 520ms cubic-bezier(0.2, 0.9, 0.2, 1), height 520ms cubic-bezier(0.2, 0.9, 0.2, 1);
  z-index: 0;
}

.danger-swell-btn:hover .danger-swell-bg {
  width: 260px;
  height: 260px;
}

.danger-swell-left,
.danger-swell-right {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 56px;
  opacity: 0.95;
  z-index: 1;
  pointer-events: none;
}

.danger-swell-left {
  left: -10px;
}

.danger-swell-right {
  right: -14px;
}

.danger-swell-wave {
  width: auto;
  height: 100%;
  object-fit: cover;
}

.danger-swell-gloss {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  opacity: 0.26;
  background: linear-gradient(180deg, transparent, transparent, rgba(255, 255, 255, 0.32));
  z-index: 2;
  pointer-events: none;
}

.danger-swell-text {
  position: relative;
  z-index: 3;
  font-size: 18px;
  font-weight: 600;
  color: rgba(14, 16, 27, 0.88);
  transition: color 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.danger-swell-btn:hover .danger-swell-text {
  color: rgba(255, 255, 255, 0.96);
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

.order-table {
  border-radius: 16px;
  border: 1px solid rgba(14, 16, 27, 0.08);
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(14, 16, 27, 0.14);
  background: transparent;
}

.thumb-wrap {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  margin: 0 auto;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(240, 235, 222, 0.7);
  box-shadow: 0 12px 26px rgba(14, 16, 27, 0.18);
  background: rgba(248, 246, 241, 0.86);
}

.thumb-wrap[data-placeholder='true'] {
  background: transparent;
  box-shadow: none;
  border-color: rgba(240, 235, 222, 0.35);
}

.setmeal-thumb {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.setmeal-thumb[data-placeholder='true'] {
  transform: scale(1.38);
  transform-origin: center;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.text-action {
  appearance: none;
  border: none;
  background: transparent;
  padding: 6px 6px;
  border-radius: 10px;
  cursor: pointer;
  color: rgba(223, 213, 203, 0.92);
  font-weight: 650;
  letter-spacing: 0.2px;
  transition:
    background 180ms cubic-bezier(0.2, 0.9, 0.2, 1),
    transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.text-action:hover {
  background: rgba(240, 235, 222, 0.12);
  transform: translateY(-1px);
}

.text-action:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
}

.text-action.is-primary {
  color: rgba(232, 208, 139, 0.96);
}

.text-action.is-danger {
  color: rgba(217, 74, 43, 0.95);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(240, 235, 222, 0.35);
  background: rgba(14, 16, 27, 0.16);
}

.status-pill[data-status='1'] {
  border-color: rgba(26, 168, 107, 0.55);
  background: rgba(26, 168, 107, 0.14);
}

.status-pill[data-status='0'] {
  border-color: rgba(240, 235, 222, 0.28);
  background: rgba(14, 16, 27, 0.16);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(240, 235, 222, 0.7);
}

.status-pill[data-status='1'] .status-dot {
  background: rgba(26, 168, 107, 0.9);
}

.status-text,
.type-cell,
.time-cell,
.price-cell {
  color: rgba(223, 213, 203, 0.92);
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

@media (max-width: 1240px) {
  .field-actions {
    width: 100%;
  }
  .add-actions {
    width: 100%;
    justify-content: flex-start;
    margin-left: 0;
  }
}
</style>
