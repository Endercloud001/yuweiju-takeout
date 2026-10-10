<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { formatDateTime } from '../../utils/format'
import { addCategory, deleteCategory, editCategory, getCategoryPage, setCategoryStatus } from '../../api/modules/category'
import type { CategoryItem, CategoryPageQuery, CategoryStatus, CategoryType } from '../../types/category'
import dividerUrl from '../../assets/reference_images/image29.png?url'
import dialogTitleIconUrl from '../../assets/reference_images/image10.png?url'

type FocusedField = 'name' | 'type' | null

const query = reactive<CategoryPageQuery>({
  page: 1,
  pageSize: 10,
  name: '',
  type: undefined,
})

const records = ref<CategoryItem[]>([])
const loading = ref(false)
const total = ref(0)

const focusedField = ref<FocusedField>(null)
const focusedDialogField = ref<'name' | 'sort' | null>(null)
const flipIsReset = ref(false)

const pageSizeOptions = [10, 20, 30, 40] as const
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const pageInputText = ref(String(query.page))
const tableMaxHeight = 'calc(100vh - 190px)'
const dialogDividerCss = computed(() => `url(${dividerUrl})`)

const nameFloating = computed(() => (query.name ?? '').trim().length > 0 || focusedField.value === 'name')
const typeFloating = computed(() => query.type !== undefined || focusedField.value === 'type')

type DialogMode = 'add' | 'edit'

interface CategoryDialogForm {
  id?: number
  name: string
  sort: string
  type: CategoryType
}

const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('add')
const dialogSubmitting = ref(false)
const dialogFormRef = ref<FormInstance>()
const dialogForm = reactive<CategoryDialogForm>({
  id: undefined,
  name: '',
  sort: '',
  type: 1,
})

const dialogTitle = computed(() => {
  if (dialogMode.value === 'edit') return '修改分类'
  return dialogForm.type === 1 ? '新增菜品分类' : '新增套餐分类'
})

const dialogRules: FormRules<CategoryDialogForm> = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 20, message: '分类名称长度为 1-20', trigger: 'blur' },
  ],
  sort: [
    { required: true, message: '请输入排序', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        const v = String(value ?? '').trim()
        if (!/^\d+$/.test(v)) {
          callback(new Error('排序需为非负整数'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
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

function getTypeLabel(type: CategoryType) {
  return type === 1 ? '菜品分类' : '套餐分类'
}

function getStatusLabel(status: CategoryStatus) {
  return status === 1 ? '启用' : '禁用'
}

async function fetch() {
  loading.value = true
  try {
    const trimmedName = (query.name ?? '').trim()
    const params: CategoryPageQuery = {
      page: query.page,
      pageSize: query.pageSize,
      name: trimmedName ? trimmedName : undefined,
      type: query.type,
    }
    const res = await getCategoryPage(params)
    total.value = res.data.total
    records.value = res.data.records
    if (query.page > totalPages.value) {
      query.page = totalPages.value
      pageInputText.value = String(query.page)
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取分类失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetch()
}

function onReset() {
  query.page = 1
  query.pageSize = 10
  query.name = ''
  query.type = undefined
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

function resetDialogForm() {
  dialogForm.id = undefined
  dialogForm.name = ''
  dialogForm.sort = ''
  dialogForm.type = 1
  focusedDialogField.value = null
  dialogFormRef.value?.clearValidate()
}

function openAdd(type: CategoryType) {
  dialogMode.value = 'add'
  resetDialogForm()
  dialogForm.type = type
  dialogVisible.value = true
}

function openEdit(row: CategoryItem) {
  dialogMode.value = 'edit'
  resetDialogForm()
  dialogForm.id = row.id
  dialogForm.name = row.name
  dialogForm.sort = String(row.sort)
  dialogForm.type = row.type
  dialogVisible.value = true
}

async function submitDialog() {
  if (dialogSubmitting.value) return
  const form = dialogFormRef.value
  if (!form) return

  try {
    const valid = await form.validate()
    if (!valid) return
  } catch {
    return
  }

  dialogSubmitting.value = true
  try {
    const sortNumber = Number(dialogForm.sort.trim())
    if (!Number.isFinite(sortNumber)) {
      ElMessage.error('排序需为非负整数')
      return
    }

    if (dialogMode.value === 'add') {
      await addCategory({
        name: dialogForm.name.trim(),
        sort: sortNumber,
        type: dialogForm.type,
      })
      ElMessage.success('新增成功')
    } else {
      if (!dialogForm.id) return
      await editCategory({
        id: dialogForm.id,
        name: dialogForm.name.trim(),
        sort: sortNumber,
        type: dialogForm.type,
      })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    fetch()
  } catch (e) {
    const message = e instanceof Error ? e.message : '提交失败'
    ElMessage.error(message)
  } finally {
    dialogSubmitting.value = false
  }
}

function isMessageBoxCancel(e: unknown) {
  return e === 'cancel' || e === 'close'
}

async function handleDelete(row: CategoryItem) {
  try {
    await ElMessageBox.confirm('确认删除该分类？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    if (isMessageBoxCancel(e)) return
    return
  }

  try {
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    fetch()
  } catch (e) {
    const message = e instanceof Error ? e.message : '删除失败'
    ElMessage.error(message)
  }
}

async function handleToggleStatus(row: CategoryItem) {
  const nextStatus: CategoryStatus = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm('确认调整该分类的状态？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    if (isMessageBoxCancel(e)) return
    return
  }

  try {
    await setCategoryStatus(row.id, nextStatus)
    ElMessage.success('状态更新成功')
    fetch()
  } catch (e) {
    const message = e instanceof Error ? e.message : '状态更新失败'
    ElMessage.error(message)
  }
}

watch(
  () => [query.name, query.type],
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

watch(
  () => dialogVisible.value,
  (v) => {
    if (!v) resetDialogForm()
  },
)

onMounted(() => {
  fetch()
})
</script>

<template>
  <div class="category-page">
    <img class="order-divider is-top" :src="dividerUrl" alt="" />

    <div class="order-search" role="search" aria-label="分类查询">
      <div class="search-grid search-grid-category">
        <div class="floating-field field-name" :data-floating="nameFloating">
          <div class="float-label">分类名称</div>
          <el-input
            v-model="query.name"
            placeholder=""
            class="float-control"
            @keyup.enter="handleFlipAction"
            @focus="focusedField = 'name'"
            @blur="focusedField = null"
          />
        </div>

        <div class="floating-field field-type" :data-floating="typeFloating">
          <div class="float-label">分类类型</div>
          <el-select
            v-model="query.type"
            placeholder=""
            clearable
            class="float-control status-select"
            @focus="focusedField = 'type'"
            @blur="focusedField = null"
          >
            <el-option label="菜品分类" :value="1" />
            <el-option label="套餐分类" :value="2" />
          </el-select>
        </div>

        <div class="field-actions">
          <button class="flip-btn" type="button" :data-flipped="flipIsReset" :disabled="loading" @click="handleFlipAction">
            <span class="flip-face flip-front">查询</span>
            <span class="flip-face flip-back">重置</span>
          </button>
        </div>

        <div class="add-actions">
          <button class="solid-btn" type="button" :disabled="loading" @click="openAdd(1)">+ 新增菜品分类</button>
          <button class="solid-btn is-secondary" type="button" :disabled="loading" @click="openAdd(2)">
            + 新增套餐分类
          </button>
        </div>
      </div>
    </div>

    <div class="order-table" aria-label="分类列表">
      <el-table :data="records" v-loading="loading" :table-layout="'fixed'" :max-height="tableMaxHeight">
        <el-table-column prop="name" label="分类名称" min-width="180" align="center" header-align="center" />
        <el-table-column label="分类类型" width="140" align="center" header-align="center">
          <template #default="{ row }">
            <span class="type-cell">{{ getTypeLabel(row.type) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="120" align="center" header-align="center" />
        <el-table-column label="状态" width="140" align="center" header-align="center">
          <template #default="{ row }">
            <span class="status-pill" :data-status="row.status">
              <span class="status-dot" aria-hidden="true"></span>
              <span class="status-text">{{ getStatusLabel(row.status) }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" width="220" align="center" header-align="center">
          <template #default="{ row }">
            <span class="time-cell">{{ row.updateTime ? formatDateTime(row.updateTime) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" header-align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="text-action is-primary" type="button" :disabled="loading" @click="openEdit(row)">修改</button>
              <button class="text-action is-danger" type="button" :disabled="loading" @click="handleDelete(row)">删除</button>
              <button class="text-action" type="button" :disabled="loading" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
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

    <el-dialog v-model="dialogVisible" width="560px" class="category-dialog" :show-close="false" @closed="resetDialogForm">
      <template #title>
        <div class="dialog-title-row">
          <div class="dialog-title">
            <img class="dialog-title-icon" :src="dialogTitleIconUrl" alt="" />
            <span class="dialog-title-text">{{ dialogTitle }}</span>
          </div>
          <button class="dialog-close" type="button" aria-label="关闭" @click="dialogVisible = false">
            <svg xmlns="http://www.w3.org/2000/svg" width="26" height="26" viewBox="0 0 24 24">
              <path
                fill="#807658"
                fill-rule="evenodd"
                d="M22 12c0 5.523-4.477 10-10 10S2 17.523 2 12S6.477 2 12 2s10 4.477 10 10M8.97 8.97a.75.75 0 0 1 1.06 0L12 10.94l1.97-1.97a.75.75 0 0 1 1.06 1.06L13.06 12l1.97 1.97a.75.75 0 0 1-1.06 1.06L12 13.06l-1.97 1.97a.75.75 0 0 1-1.06-1.06L10.94 12l-1.97-1.97a.75.75 0 0 1 0-1.06"
                clip-rule="evenodd"
              />
            </svg>
          </button>
        </div>
      </template>

      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="112px">
        <el-form-item label="分类名称" prop="name">
          <div
            class="vanish-input"
            :data-active="focusedDialogField === 'name'"
            :data-has-value="dialogForm.name.trim().length > 0"
          >
            <span class="vanish-placeholder">请输入分类名称</span>
            <el-input
              v-model="dialogForm.name"
              placeholder=""
              maxlength="20"
              @focus="focusedDialogField = 'name'"
              @blur="focusedDialogField = null"
            />
          </div>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <div
            class="vanish-input"
            :data-active="focusedDialogField === 'sort'"
            :data-has-value="dialogForm.sort.trim().length > 0"
          >
            <span class="vanish-placeholder">请输入排序</span>
            <el-input
              v-model="dialogForm.sort"
              placeholder=""
              inputmode="numeric"
              @focus="focusedDialogField = 'sort'"
              @blur="focusedDialogField = null"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <button class="stateful-btn is-ghost" type="button" @click="dialogVisible = false">取消</button>
          <button
            class="stateful-btn is-primary"
            type="button"
            :disabled="dialogSubmitting"
            :class="{ 'is-loading': dialogSubmitting }"
            @click="submitDialog"
          >
            <span class="btn-spinner" aria-hidden="true"></span>
            <span class="btn-text">{{ dialogSubmitting ? '处理中' : '确定' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.category-page {
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

.search-grid-category {
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

.field-type {
  width: 240px;
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

:deep(.field-type .el-select__wrapper) {
  height: 44px;
  min-height: 44px;
  box-sizing: border-box;
  padding-top: 0;
  padding-bottom: 0;
  position: relative;
}

:deep(.field-name .el-input__wrapper) {
  height: 44px;
  min-height: 44px;
  box-sizing: border-box;
}

:deep(.order-search .el-input__wrapper.is-focus),
:deep(.order-search .el-select__wrapper.is-focused) {
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

:deep(.field-type .el-select__selected-item),
:deep(.field-type .el-select__placeholder) {
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

:deep(.field-type .el-select__suffix) {
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

.solid-btn.is-secondary {
  background: #f9f9f9;
  color: rgba(14, 16, 27, 0.86);
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
.time-cell {
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

.dialog-title-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  position: relative;
  padding-bottom: 18px;
}

.dialog-title-row::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 6px;
  height: 12px;
  opacity: 0.9;
  background-image: v-bind(dialogDividerCss);
  background-repeat: repeat-x;
  background-size: auto 100%;
  pointer-events: none;
}

.dialog-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dialog-title-icon {
  width: 26px;
  height: 26px;
  object-fit: contain;
  opacity: 0.92;
  filter: drop-shadow(0 10px 18px rgba(14, 16, 27, 0.18));
}

.dialog-title-text {
  font-family: var(--app-font-brand);
  font-size: 24px;
  font-weight: 750;
  letter-spacing: 0.6px;
  color: rgba(14, 16, 27, 0.84);
}

.dialog-close {
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  display: grid;
  place-items: center;
  border-radius: 999px;
  transition: transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1), filter 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.dialog-close:hover {
  transform: translateY(-1px);
  filter: drop-shadow(0 10px 18px rgba(14, 16, 27, 0.18));
}

.dialog-close:active {
  transform: translateY(0);
}

:deep(.category-dialog.el-dialog) {
  border-radius: var(--panel-radius);
  background: rgba(240, 235, 222, 0.96);
  box-shadow: var(--panel-shadow);
  border: 1px solid var(--panel-border);
}

:deep(.category-dialog.el-dialog)::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: var(--app-noise);
  opacity: 0.12;
  mix-blend-mode: multiply;
  pointer-events: none;
}

:deep(.category-dialog .el-dialog__header) {
  padding: 18px 24px 30px;
  border-bottom: none;
}

:deep(.category-dialog .el-dialog__body) {
  padding: 8px 24px 12px;
}

:deep(.category-dialog .el-dialog__footer) {
  padding: 0 24px 22px;
}

:deep(.category-dialog .el-form-item) {
  align-items: center;
}

:deep(.category-dialog .el-form-item__label) {
  font-family: '喜鹊聚珍体 regular', var(--app-font-sidebar);
  font-size: 20px;
  font-weight: 400;
  color: rgba(14, 16, 27, 0.78);
  height: 54px;
  line-height: 54px;
  display: inline-flex;
  align-items: center;
}

.vanish-input {
  position: relative;
  width: 100%;
}

.vanish-placeholder {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: rgba(46, 52, 58, 0.6);
  pointer-events: none;
  transition:
    transform 260ms cubic-bezier(0.2, 0.9, 0.2, 1),
    opacity 260ms cubic-bezier(0.2, 0.9, 0.2, 1),
    color 260ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.vanish-input[data-active='true'] .vanish-placeholder,
.vanish-input[data-has-value='true'] .vanish-placeholder {
  transform: translateY(-18px) scale(0.94);
  opacity: 0;
  color: rgba(110, 29, 32, 0.72);
}

:deep(.category-dialog .vanish-input .el-input__wrapper) {
  height: 26px;
  padding: 18px 14px 10px;
  border-radius: 14px;
  background: rgba(248, 246, 241, 0.7);
  border: 1px solid rgba(110, 29, 32, 0.18);
  box-shadow: none;
  transition:
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

:deep(.category-dialog .vanish-input .el-input__inner) {
  font-weight: 650;
  letter-spacing: 0.2px;
}

.vanish-input[data-active='true'] :deep(.el-input__wrapper) {
  border-color: rgba(110, 29, 32, 0.5);
  box-shadow: 0 0 0 4px rgba(110, 29, 32, 0.08);
  transform: translateY(-1px);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
}

.stateful-btn {
  height: 42px;
  min-width: 112px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.14);
  background: rgba(248, 246, 241, 0.72);
  color: rgba(14, 16, 27, 0.86);
  font-weight: 700;
  letter-spacing: 0.4px;
  position: relative;
  cursor: pointer;
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    background 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.stateful-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
    radial-gradient(600px 200px at 10% 0%, rgba(110, 29, 32, 0.15), transparent 60%),
    radial-gradient(400px 220px at 90% 0%, rgba(74, 143, 163, 0.12), transparent 60%),
    var(--app-noise);
  opacity: 0.1;
  pointer-events: none;
}

.stateful-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(110, 29, 32, 0.22);
  box-shadow: 0 14px 30px rgba(14, 16, 27, 0.12);
}

.stateful-btn:active {
  transform: translateY(0);
}

.stateful-btn.is-ghost {
  background: rgba(255, 255, 255, 0.55);
}

.stateful-btn.is-primary {
  border-color: rgba(110, 29, 32, 0.28);
  background: rgba(110, 29, 32, 0.92);
  color: rgba(240, 235, 222, 0.96);
}

.stateful-btn:disabled {
  cursor: not-allowed;
  opacity: 0.82;
}

.btn-spinner {
  width: 0;
  height: 0;
  display: none;
}

.stateful-btn.is-loading .btn-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 999px;
  border: 2px solid rgba(240, 235, 222, 0.58);
  border-top-color: rgba(240, 235, 222, 0.96);
  animation: spin 900ms linear infinite;
  margin-right: 8px;
  vertical-align: -2px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
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
