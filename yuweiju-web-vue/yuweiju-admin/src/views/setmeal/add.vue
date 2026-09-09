<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Cookies from 'js-cookie'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadProps } from 'element-plus'
import { apiBase } from '../../api/http'
import { getCategoryList } from '../../api/modules/category'
import { getDishPage } from '../../api/modules/dish'
import { addSetmeal, editSetmeal, getSetmealById } from '../../api/modules/setmeal'
import DefaultImagePicker from '../../components/default-image-picker.vue'
import type { CategoryItem } from '../../types/category'
import type { DishItem } from '../../types/dish'
import type { SetmealSaveBody } from '../../types/setmeal'
import type { DefaultImageItem } from '../../types/common'
import { formatCurrency } from '../../utils/format'
import ornamentUrl from '../../assets/reference_images/顶部和底部花纹图片.png?url'
import topSquareUrl from '../../assets/reference_images/方形花纹_透明.png?url'
import dialogHeaderBgUrl from '../../assets/reference_images/image22.png?url'

const route = useRoute()
const router = useRouter()

const categoryOptions = ref<CategoryItem[]>([])
const loading = ref(false)
const saving = ref(false)

const ornamentCss = computed(() => `url(${ornamentUrl})`)
const topSquareCss = computed(() => `url(${topSquareUrl})`)
const dialogHeaderCss = computed(() => `url(${dialogHeaderBgUrl})`)

const setmealId = computed(() => {
  const raw = route.query.id
  const text = typeof raw === 'string' ? raw : Array.isArray(raw) ? raw[0] : undefined
  const id = text ? Number(text) : NaN
  return Number.isFinite(id) ? id : null
})

const formRef = ref<FormInstance>()

interface SetmealFormModel {
  name: string
  categoryId: number | null
  price: string
  image: string
  description: string
}

const form = reactive<SetmealFormModel>({
  name: '',
  categoryId: null,
  price: '',
  image: '',
  description: '',
})

type SelectedDish = { id: number; name: string; price: number }

const selectedDishes = ref<SelectedDish[]>([])

const dishPickerVisible = ref(false)
const dishCategories = ref<CategoryItem[]>([])
const activeDishCategoryId = ref<number | null>(null)
const dishRecords = ref<DishItem[]>([])
const dishLoading = ref(false)
const dishTableRef = ref<unknown>(null)
const dishPickerSelected = ref<Map<number, SelectedDish>>(new Map())
let syncingSelection = false

const rules: FormRules<SetmealFormModel> = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  categoryId: [
    { required: true, message: '必须选择一个分类', trigger: 'change' },
    {
      validator: (_rule, value: number | null, callback) => {
        if (!value) {
          callback(new Error('必须选择一个分类'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  price: [
    {
      validator: (_rule, value: string, callback) => {
        const trimmed = String(value ?? '').trim()
        const numberValue = Number(trimmed)
        if (!trimmed || !Number.isFinite(numberValue) || numberValue <= 0) {
          callback(new Error('请输入正确的套餐价格'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const uploadAction = computed(() => `${apiBase}/admin/common/upload`)
const uploadHeaders = computed<Record<string, string>>(() => {
  const token = Cookies.get('token')
  const headers: Record<string, string> = {}
  if (token) headers.token = token
  return headers
})

const defaultImagePickerVisible = ref(false)
const imagePreviewUrl = computed(() => form.image)

const handleUploadSuccess: UploadProps['onSuccess'] = (response) => {
  if (!response || typeof response !== 'object') {
    ElMessage.error('上传失败')
    return
  }
  const data = response as { code?: number; msg?: string; data?: string }
  if (Number(data.code) !== 1 || !data.data) {
    ElMessage.error(data.msg || '上传失败')
    return
  }
  form.image = data.data
  ElMessage.success('上传成功')
}

function gotoList() {
  router.push({ name: 'Setmeal' })
}

function openDefaultImagePicker() {
  defaultImagePickerVisible.value = true
}

function handleDefaultImageSelected(image: DefaultImageItem) {
  form.image = image.url
  ElMessage.success('已选择默认图片')
}

async function submit() {
  if (saving.value) return
  const elForm = formRef.value
  if (!elForm) return
  try {
    const valid = await elForm.validate()
    if (!valid) return
  } catch {
    return
  }

  saving.value = true
  try {
    if (selectedDishes.value.length === 0) {
      ElMessage.error('请先添加套餐菜品')
      return
    }
    const priceNumber = Number(form.price.trim())
    const payload: SetmealSaveBody = {
      name: form.name.trim(),
      categoryId: form.categoryId ?? 0,
      price: priceNumber,
      image: form.image || undefined,
      description: form.description?.trim() || undefined,
      setmealDishes: selectedDishes.value.map((d) => ({ dishId: d.id, copies: 1 })),
    }

    if (setmealId.value) {
      await editSetmeal({ ...payload, id: setmealId.value })
      ElMessage.success('保存成功')
    } else {
      await addSetmeal(payload)
      ElMessage.success('保存成功')
    }

    gotoList()
  } catch (e) {
    const message = e instanceof Error ? e.message : '保存失败'
    ElMessage.error(message)
  } finally {
    saving.value = false
  }
}

async function fetchCategories() {
  try {
    const res = await getCategoryList(2)
    categoryOptions.value = res.data
  } catch {
    categoryOptions.value = []
  }
}

async function fetchDetail() {
  if (!setmealId.value) return
  loading.value = true
  try {
    const res = await getSetmealById(setmealId.value)
    form.name = res.data.name
    form.categoryId = res.data.categoryId
    form.price = String(res.data.price ?? '')
    form.image = res.data.image ?? ''
    form.description = res.data.description ?? ''
    selectedDishes.value =
      res.data.setmealDishes?.map((d) => ({
        id: d.dishId,
        name: d.name || `菜品#${d.dishId}`,
        price: d.price ?? 0,
      })) ?? []
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取详情失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

function clearDishSelection() {
  const maybe = dishTableRef.value as unknown as { clearSelection?: () => void }
  if (typeof maybe?.clearSelection === 'function') {
    maybe.clearSelection()
  }
}

function toggleDishSelection(row: DishItem, selected: boolean) {
  const maybe = dishTableRef.value as unknown as { toggleRowSelection?: (row: DishItem, selected: boolean) => void }
  if (typeof maybe?.toggleRowSelection === 'function') {
    maybe.toggleRowSelection(row, selected)
  }
}

async function syncSelectionForCurrentData() {
  syncingSelection = true
  await nextTick()
  dishPickerSelected.value.forEach((_, id) => {
    const row = dishRecords.value.find((r) => r.id === id)
    if (row) {
      toggleDishSelection(row, true)
    }
  })
  syncingSelection = false
}

function handleDishSelectionChange(rows: DishItem[]) {
  if (syncingSelection) return
  const selectedSet = new Set(rows.map((r) => r.id))
  for (const row of dishRecords.value) {
    if (selectedSet.has(row.id)) {
      dishPickerSelected.value.set(row.id, { id: row.id, name: row.name, price: row.price })
    } else {
      dishPickerSelected.value.delete(row.id)
    }
  }
}

async function removePickedDish(id: number) {
  dishPickerSelected.value.delete(id)
  await syncSelectionForCurrentData()
}

async function fetchDishCategories() {
  try {
    const res = await getCategoryList(1)
    dishCategories.value = res.data
    if (!activeDishCategoryId.value && dishCategories.value.length > 0) {
      activeDishCategoryId.value = dishCategories.value[0].id
    }
  } catch {
    dishCategories.value = []
  }
}

async function fetchDishList() {
  if (!activeDishCategoryId.value) {
    dishRecords.value = []
    return
  }
  dishLoading.value = true
  try {
    const res = await getDishPage({ page: 1, pageSize: 200, categoryId: activeDishCategoryId.value })
    dishRecords.value = res.data.records
    await syncSelectionForCurrentData()
  } catch {
    dishRecords.value = []
  } finally {
    dishLoading.value = false
  }
}

async function openDishPicker() {
  dishPickerSelected.value = new Map(selectedDishes.value.map((d) => [d.id, d]))
  dishPickerVisible.value = true
  await nextTick()
  clearDishSelection()
  if (dishCategories.value.length === 0) {
    await fetchDishCategories()
  }
  await fetchDishList()
}

function cancelDishPicker() {
  dishPickerVisible.value = false
}

function confirmDishPicker() {
  selectedDishes.value = Array.from(dishPickerSelected.value.values())
  dishPickerVisible.value = false
}

async function changeDishCategory(id: number) {
  activeDishCategoryId.value = id
  await fetchDishList()
}

onMounted(() => {
  fetchCategories()
  fetchDetail()
})
</script>

<template>
  <div class="setmeal-add-page">
    <section class="detail-frame">
      <header class="detail-topbar">
        <div class="topbar-square topbar-square-left"></div>
        <div class="topbar-square topbar-square-right"></div>
        <div class="topbar-left">
          <div class="topbar-title">余味居·新增套餐</div>
        </div>
        <div class="topbar-right">
          <span class="topbar-status-text">热锅冷油，火候正好</span>
          <button class="topbar-close" type="button" aria-label="关闭" @click="gotoList">×</button>
        </div>
      </header>

      <div class="detail-body">
        <div class="detail-layout">
          <div class="card-border-outer">
            <div class="card-border-inner">
              <el-form ref="formRef" class="setmeal-form" :model="form" :rules="rules" label-width="110px" v-loading="loading">
                <div class="form-grid">
                  <div class="left">
                    <el-form-item label="套餐名称" prop="name" required>
                      <el-input v-model="form.name" class="name-input" placeholder="请输入套餐名称" maxlength="20" />
                    </el-form-item>

                    <el-form-item label="套餐价格" prop="price" required>
                      <el-input v-model="form.price" class="price-input" placeholder="请输入价格" inputmode="decimal" />
                    </el-form-item>

                    <el-form-item label="套餐菜品">
                      <div class="setmeal-dishes">
                        <button class="add-dish-btn" type="button" @click="openDishPicker">+ 添加菜品</button>
                        <div v-if="selectedDishes.length > 0" class="selected-dish-summary">
                          <span class="summary-text">已选 {{ selectedDishes.length }} 个菜品：</span>
                          <span class="summary-list">
                            <span v-for="d in selectedDishes" :key="d.id" class="summary-chip">{{ d.name }}</span>
                          </span>
                        </div>
                      </div>
                    </el-form-item>

                    <el-form-item label="套餐图片">
                      <div class="upload-row">
                        <el-upload
                          v-if="!imagePreviewUrl"
                          class="upload-card"
                          :action="uploadAction"
                          :headers="uploadHeaders"
                          :show-file-list="false"
                          :on-success="handleUploadSuccess"
                        >
                          <div class="upload-inner">
                            <div class="upload-icon" aria-hidden="true">↑</div>
                            <div class="upload-text">上传图片</div>
                          </div>
                        </el-upload>

                        <div v-else class="upload-preview-inline">
                          <img :src="imagePreviewUrl" alt="" class="preview-img-inline" />
                        </div>

                        <button class="library-card" type="button" @click="openDefaultImagePicker">
                          <div class="upload-inner">
                            <div class="upload-icon" aria-hidden="true">库</div>
                            <div class="upload-text">从默认图片库选择图片</div>
                          </div>
                        </button>

                        <div class="upload-hint">
                          <div class="hint-line">图片大小不超过 2M</div>
                          <div class="hint-line">仅能上传 PNG/JPEG/JPG 类型图片</div>
                          <div class="hint-line">建议上传长宽相同尺寸的图片</div>
                        </div>
                      </div>
                    </el-form-item>
                  </div>

                  <div class="right">
                    <el-form-item label="套餐分类" prop="categoryId">
                      <el-select v-model="form.categoryId" placeholder="请选择分类" clearable class="category-select">
                        <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
                      </el-select>
                    </el-form-item>
                  </div>
                </div>

                <el-form-item label="套餐描述">
                  <el-input v-model="form.description" type="textarea" :rows="4" placeholder="套餐描述，最长200字" maxlength="200" />
                </el-form-item>

                <div class="form-actions">
                  <button class="action-btn is-ghost" type="button" :disabled="saving" @click="gotoList">取消</button>
                  <button class="action-btn is-primary" type="button" :disabled="saving" :class="{ 'is-loading': saving }" @click="submit">
                    <span class="btn-spinner" aria-hidden="true"></span>
                    <span class="btn-text">保存</span>
                  </button>
                </div>
              </el-form>
            </div>
          </div>
        </div>
      </div>

      <footer class="detail-bottombar">
        <img class="bottombar-ornament" :src="ornamentUrl" alt="" />
      </footer>
    </section>

    <DefaultImagePicker
      v-model="defaultImagePickerVisible"
      scene="setmeal"
      :current-url="form.image"
      @select="handleDefaultImageSelected"
    />

    <el-dialog v-model="dishPickerVisible" class="dish-picker-dialog" width="1100px" :show-close="false" align-center>
      <template #title>
        <div class="picker-title-row">
          <div class="picker-title">添加菜品</div>
          <button class="picker-close" type="button" aria-label="关闭" @click="cancelDishPicker">×</button>
        </div>
      </template>

      <div class="picker-split" v-loading="dishLoading">
        <div class="picker-left">
          <div class="picker-sidebar">
            <button
              v-for="c in dishCategories"
              :key="c.id"
              class="sidebar-item"
              type="button"
              :data-active="activeDishCategoryId === c.id ? 'true' : 'false'"
              @click="changeDishCategory(c.id)"
            >
              {{ c.name }}
            </button>
          </div>
          <div class="picker-list">
            <el-table
              ref="dishTableRef"
              :data="dishRecords"
              :row-key="'id'"
              :reserve-selection="true"
              :max-height="520"
              @selection-change="handleDishSelectionChange"
            >
              <el-table-column type="selection" width="50" :selectable="(row: DishItem) => row.status === 1" />
              <el-table-column prop="name" label="菜品名称" min-width="180" />
              <el-table-column label="在售状态" width="120" align="center">
                <template #default="{ row }">
                  <span class="dish-status" :data-status="row.status">{{ row.status === 1 ? '在售' : '停售' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="价格" width="120" align="right">
                <template #default="{ row }">
                  <span class="dish-price">{{ formatCurrency(row.price) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div class="picker-right">
          <div class="picked-title">已选菜品({{ dishPickerSelected.size }})</div>
          <div class="picked-list">
            <div v-for="d in Array.from(dishPickerSelected.values())" :key="d.id" class="picked-card">
              <div class="picked-main">
                <div class="picked-name">{{ d.name }}</div>
                <div class="picked-price">{{ formatCurrency(d.price) }}</div>
              </div>
              <button class="picked-remove" type="button" aria-label="移除" @click="removePickedDish(d.id)">×</button>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="picker-footer">
          <button class="picker-btn is-cancel" type="button" @click="cancelDishPicker">取消</button>
          <button class="picker-btn is-confirm" type="button" @click="confirmDishPicker">添加</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.setmeal-add-page {
  width: 100%;
  height: 100%;
  min-height: 100%;
  padding: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.detail-frame {
  width: 100%;
  max-width: none;
  margin: 0;
  border-radius: 0;
  overflow: hidden;
  position: relative;
  box-shadow: 0 18px 42px rgba(14, 16, 27, 0.14);
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 100%;
}

.detail-topbar {
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
  z-index: 1;
  overflow: hidden;
}
.detail-topbar::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: v-bind(ornamentCss);
  background-repeat: repeat-x;
  background-size: auto 100%;
  background-position: center;
  opacity: 0.25;
  mix-blend-mode: multiply;
  filter: brightness(0.62) contrast(1.35) saturate(1.1);
  pointer-events: none;
  z-index: 0;
}

.topbar-square {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 52px;
  height: 52px;
  background-image: v-bind(topSquareCss);
  background-repeat: no-repeat;
  background-size: 200% 100%;
  background-position: left center;
  pointer-events: none;
  z-index: 4;
}
.topbar-square-left {
  left: 8px;
  background-position: left center;
}
.topbar-square-right {
  right: 8px;
  background-position: right center;
}

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
  gap: 12px;
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
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid rgba(240, 235, 222, 0.4);
  background: rgba(14, 16, 27, 0.18);
  color: rgba(240, 235, 222, 0.92);
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  display: grid;
  place-items: center;
  transition: transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1), background 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.topbar-close:hover {
  transform: translateY(-1px);
  background: rgba(14, 16, 27, 0.28);
}

.detail-body {
  position: relative;
  z-index: 1;
  padding: 24px 28px 22px;
  background: #e8e0cc;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.detail-layout {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.card-border-outer {
  position: relative;
  background: #e8e0cc;
  border: 2px solid #8b1a1a;
  clip-path: polygon(
    14px 0%,
    calc(100% - 14px) 0%,
    100% 14px,
    100% calc(100% - 14px),
    calc(100% - 14px) 100%,
    14px 100%,
    0% calc(100% - 14px),
    0% 14px
  );
  padding: 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.card-border-inner {
  border: 1px solid #8b1a1a;
  clip-path: polygon(
    10px 0%,
    calc(100% - 10px) 0%,
    100% 10px,
    100% calc(100% - 10px),
    calc(100% - 10px) 100%,
    10px 100%,
    0% calc(100% - 10px),
    0% 10px
  );
  background: #e8e0cc;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.setmeal-form {
  padding: 18px 22px 18px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: 750px 1fr;
  gap: 28px;
  align-items: start;
}

.upload-row {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

:deep(.upload-card .el-upload),
.upload-preview-inline,
.library-card {
  width: 160px;
  height: 160px;
}

:deep(.upload-card .el-upload) {
  border-radius: 14px;
  border: 1px dashed rgba(14, 16, 27, 0.18);
  background: rgba(255, 255, 255, 0.55);
  display: grid;
  place-items: center;
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

:deep(.upload-card .el-upload:hover) {
  transform: translateY(-1px);
  border-color: rgba(110, 29, 32, 0.45);
  box-shadow: 0 16px 32px rgba(14, 16, 27, 0.12);
}

.upload-inner {
  display: grid;
  place-items: center;
  gap: 10px;
  color: rgba(14, 16, 27, 0.75);
}

.upload-icon {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid rgba(14, 16, 27, 0.18);
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.6);
}

.upload-text {
  font-weight: 650;
}

.upload-preview-inline {
  border-radius: 14px;
  border: 1px solid rgba(14, 16, 27, 0.18);
  background: rgba(255, 255, 255, 0.55);
  display: grid;
  place-items: center;
  overflow: hidden;
  transition: transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.library-card {
  border-radius: 14px;
  border: 1px dashed rgba(110, 29, 32, 0.22);
  background: rgba(255, 255, 255, 0.55);
  display: grid;
  place-items: center;
  cursor: pointer;
  padding: 0;
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.library-card:hover {
  transform: translateY(-1px);
}

.preview-img-inline {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-hint {
  color: rgba(46, 52, 58, 0.72);
  font-size: 12px;
  line-height: 1.6;
  min-width: 240px;
}

.hint-line {
  white-space: nowrap;
}

:deep(.setmeal-form .el-input__wrapper),
:deep(.setmeal-form .el-select__wrapper),
:deep(.setmeal-form .el-textarea__inner) {
  border-radius: 14px;
}

:deep(.setmeal-form .el-form-item) {
  align-items: center;
}

:deep(.setmeal-form .el-form-item__label) {
  font-family: 'SimHei', '黑体', sans-serif;
  font-size: 16px;
  font-weight: 700;
  color: #0e101b;
  height: 40px;
  line-height: 40px;
  display: inline-flex;
  align-items: center;
}

.category-select {
  width: 50%;
  min-width: 240px;
}

.name-input,
.price-input {
  width: 350px;
}

.setmeal-dishes {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.add-dish-btn {
  width: 350px;
  height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid rgba(110, 29, 32, 0.18);
  background: rgba(231, 197, 81, 0.9);
  color: rgba(14, 16, 27, 0.86);
  font-weight: 650;
  cursor: pointer;
  transition: transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1), box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.add-dish-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 26px rgba(14, 16, 27, 0.12);
}

.selected-dish-summary {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  flex-wrap: wrap;
  max-width: 750px;
}

.summary-text {
  color: rgba(14, 16, 27, 0.72);
  font-weight: 650;
}

.summary-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-chip {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.16);
  background: rgba(255, 255, 255, 0.45);
  color: rgba(14, 16, 27, 0.82);
  font-size: 12px;
}

.picker-title-row {
  position: relative;

  width: 100%;
  height: 100%;

  display: flex;
  align-items: center;

  padding-left: 24px;   
  padding-right: 60px; 

  box-sizing: border-box;
}

.picker-title {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 22px;
  font-weight: 900;
  color: rgba(14, 16, 27, 0.86);
}

.picker-close {
  position: absolute !important;

  top: 32px;
  right: 16px;

  width: 32px;
  height: 32px;

  border-radius: 50%;
  border: 1px solid rgba(0,0,0,0.15);
  background: rgba(255,255,255,0.9);

  display: flex;
  align-items: center;
  justify-content: center;

  cursor: pointer;

  z-index: 999; 
}

.picker-close:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(14, 16, 27, 0.12);
}

:deep(.dish-picker-dialog.el-dialog) {
  border-radius: 16px;
  background: rgba(240, 235, 222, 0.96);
  box-shadow: 0 30px 80px rgba(14, 16, 27, 0.25);
  border: 1px solid rgba(110, 29, 32, 0.18);
}

:deep(.dish-picker-dialog .el-dialog__header) {
  padding: 0 !important;
  margin: 0 !important;

  width: 100% !important;
  height: 90px;

  background-image: v-bind(dialogHeaderCss);
  background-size: 100% 100%;   
  background-repeat: no-repeat;
  background-position: center;

  position: relative;
}

:deep(.dish-picker-dialog .el-dialog__body) {
  padding: 10px 22px 16px;
}

:deep(.dish-picker-dialog .el-dialog__footer) {
  padding: 0 22px 18px;
}

:deep(.dish-picker-dialog .el-dialog) {
  overflow: hidden;  /* ❗防止 header 被内层裁剪 */
}

.picker-split {
  display: grid;
  grid-template-columns: 1.35fr 0.65fr;
  gap: 0;
  min-height: 560px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(110, 29, 32, 0.14);
  background: rgba(255, 255, 255, 0.42);
}

.picker-left {
  display: grid;
  grid-template-columns: 170px 1fr;
  min-width: 0;
}

.picker-sidebar {
  padding: 14px 10px;
  border-right: 1px solid rgba(110, 29, 32, 0.12);
  background: rgba(255, 255, 255, 0.26);
  overflow: auto;
}

.sidebar-item {
  width: 100%;
  padding: 10px 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-weight: 700;
  color: rgba(14, 16, 27, 0.74);
  transition: background 180ms cubic-bezier(0.2, 0.9, 0.2, 1), color 180ms cubic-bezier(0.2, 0.9, 0.2, 1), box-shadow 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.sidebar-item + .sidebar-item {
  margin-top: 8px;
}

.sidebar-item:hover {
  background: rgba(110, 29, 32, 0.06);
}

.sidebar-item[data-active='true'] {
  background: linear-gradient(90deg, rgba(110, 29, 32, 0.92), rgba(110, 29, 32, 0.72));
  color: rgba(240, 235, 222, 0.96);
  box-shadow: 0 12px 24px rgba(14, 16, 27, 0.12);
}

.picker-list {
  padding: 10px 12px;
  min-width: 0;
}

:deep(.picker-list .el-table) {
  --el-table-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(110, 29, 32, 0.06);
  background: transparent;
}

.dish-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.16);
  background: rgba(14, 16, 27, 0.06);
  color: rgba(14, 16, 27, 0.72);
}

.dish-status[data-status='1'] {
  border-color: rgba(26, 168, 107, 0.45);
  background: rgba(26, 168, 107, 0.12);
  color: rgba(14, 16, 27, 0.78);
}

.dish-price {
  font-variant-numeric: tabular-nums;
  color: rgba(14, 16, 27, 0.78);
  font-weight: 700;
}

.picker-right {
  border-left: 1px solid rgba(110, 29, 32, 0.12);
  background: rgba(255, 255, 255, 0.26);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.picked-title {
  padding: 14px 14px 10px;
  font-weight: 900;
  color: rgba(14, 16, 27, 0.82);
}

.picked-list {
  padding: 0 14px 14px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.picked-card {
  border-radius: 14px;
  border: 1px solid rgba(110, 29, 32, 0.16);
  background: rgba(255, 255, 255, 0.55);
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.picked-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.picked-name {
  font-weight: 800;
  color: rgba(14, 16, 27, 0.82);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 260px;
}

.picked-price {
  font-weight: 800;
  color: rgba(14, 16, 27, 0.76);
  font-variant-numeric: tabular-nums;
}

.picked-remove {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.22);
  background: rgba(110, 29, 32, 0.92);
  color: rgba(240, 235, 222, 0.96);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1), box-shadow 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.picked-remove:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(14, 16, 27, 0.12);
}

.picker-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.picker-btn {
  height: 40px;
  min-width: 108px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.16);
  cursor: pointer;
  font-weight: 800;
  letter-spacing: 0.4px;
  transition: transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1), box-shadow 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.picker-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 30px rgba(14, 16, 27, 0.12);
}

.picker-btn.is-cancel {
  background: rgba(255, 255, 255, 0.75);
  color: rgba(14, 16, 27, 0.82);
}

.picker-btn.is-confirm {
  background: rgba(231, 197, 81, 0.92);
  border-color: rgba(231, 197, 81, 0.8);
  color: rgba(14, 16, 27, 0.86);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 6px;
  margin-top: auto;
}

.action-btn {
  height: 42px;
  min-width: 112px;
  border-radius: 999px;
  border: 1px solid rgba(110, 29, 32, 0.14);
  background: rgba(255, 255, 255, 0.7);
  color: rgba(14, 16, 27, 0.86);
  font-weight: 700;
  letter-spacing: 0.4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    background 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.action-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(110, 29, 32, 0.22);
  box-shadow: 0 14px 30px rgba(14, 16, 27, 0.12);
}

.action-btn:disabled {
  cursor: not-allowed;
  opacity: 0.82;
  transform: none;
}

.action-btn.is-primary {
  border-color: rgba(14, 16, 27, 0.18);
  background: rgba(14, 16, 27, 0.82);
  color: rgba(240, 235, 222, 0.96);
}

.btn-spinner {
  width: 0;
  height: 0;
  display: none;
}

.action-btn.is-loading .btn-spinner {
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

.detail-bottombar {
  position: relative;
  z-index: 1;
  height: 40px;
  background-color: #6e1d20;
  isolation: isolate;
  border-top: 3px solid #5c171a;
  box-shadow: 0 -3px 0 0 #c8a84b;
  overflow: hidden;
}
.detail-bottombar::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: v-bind(ornamentCss);
  background-repeat: repeat-x;
  background-size: auto 100%;
  background-position: center;
  opacity: 0.25;
  mix-blend-mode: multiply;
  filter: brightness(0.62) contrast(1.35) saturate(1.1);
  pointer-events: none;
  z-index: 0;
}
.bottombar-ornament {
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

@media (max-width: 1100px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .upload-row {
    flex-wrap: wrap;
  }
  .topbar-title {
    font-size: 22px;
  }
  .detail-body {
    padding: 18px 14px 18px;
  }
  .topbar-right {
    padding-right: 18px;
  }
  .topbar-left {
    padding-left: 18px;
  }
  .topbar-square {
    display: none;
  }
}
</style>
