<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElEmpty, ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getDefaultImages } from '../api/modules/common'
import type { DefaultImageItem, DefaultImageScene } from '../types/common'

interface Props {
  modelValue: boolean
  scene: DefaultImageScene
  currentUrl?: string
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  currentUrl: '',
  title: '从默认图片库选择图片',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [image: DefaultImageItem]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const images = ref<DefaultImageItem[]>([])
const selectedKey = ref('')
const errorMessage = ref('')
const keyword = ref('')

const filteredImages = computed(() => {
  const searchText = keyword.value.trim().toLowerCase()
  if (!searchText) {
    return images.value
  }
  return images.value.filter((item) => {
    return item.name.toLowerCase().includes(searchText) || item.objectKey.toLowerCase().includes(searchText)
  })
})

function pickDefaultSelection() {
  if (filteredImages.value.length === 0) {
    selectedKey.value = ''
    return
  }
  const matched = filteredImages.value.find((item) => item.url === props.currentUrl || item.objectKey === props.currentUrl)
  selectedKey.value = matched?.objectKey ?? filteredImages.value[0].objectKey
}

async function loadImages() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getDefaultImages({ scene: props.scene, limit: 48 })
    images.value = res.data ?? []
    pickDefaultSelection()
  } catch (error) {
    images.value = []
    selectedKey.value = ''
    errorMessage.value = error instanceof Error ? error.message : '获取默认图片失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

function closeDialog() {
  visible.value = false
}

function chooseImage(item: DefaultImageItem) {
  selectedKey.value = item.objectKey
}

function confirmSelection() {
  const selected = images.value.find((item) => item.objectKey === selectedKey.value)
  if (!selected) {
    ElMessage.warning('请先选择一张图片')
    return
  }
  emit('select', selected)
  closeDialog()
}

watch(
  () => props.modelValue,
  (nextVisible) => {
    if (nextVisible) {
      void loadImages()
    }
  }
)

watch(keyword, () => {
  pickDefaultSelection()
})
</script>

<template>
  <el-dialog v-model="visible" class="default-image-picker-dialog" width="1080px" :show-close="false" align-center>
    <template #header>
      <div class="picker-header">
        <div class="picker-title">{{ title }}</div>
        <button class="picker-close" type="button" aria-label="关闭" @click="closeDialog">×</button>
      </div>
    </template>

    <div class="picker-body" v-loading="loading">
      <div class="picker-toolbar">
        <el-input
          v-model="keyword"
          class="picker-search"
          clearable
          placeholder="按文件名或对象键搜索"
          :prefix-icon="Search"
        />
        <div class="picker-count">共 {{ filteredImages.length }} 张</div>
      </div>
      <div v-if="errorMessage" class="picker-error">{{ errorMessage }}</div>
      <div v-else-if="filteredImages.length === 0 && !loading" class="picker-empty">
        <el-empty description="默认图片库为空" />
      </div>
      <div v-else class="picker-grid">
        <button
          v-for="item in filteredImages"
          :key="item.objectKey"
          class="picker-card"
          type="button"
          :data-selected="selectedKey === item.objectKey ? 'true' : 'false'"
          @click="chooseImage(item)"
        >
          <div class="preview-frame">
            <img class="preview-image" :src="item.url" :alt="item.name" loading="lazy" />
          </div>
          <div class="meta">
            <div class="name" :title="item.name">{{ item.name }}</div>
            <div class="key" :title="item.objectKey">{{ item.objectKey }}</div>
          </div>
        </button>
      </div>
    </div>

    <template #footer>
      <div class="picker-footer">
        <button class="picker-btn is-cancel" type="button" @click="closeDialog">取消</button>
        <button class="picker-btn is-confirm" type="button" @click="confirmSelection">确认使用</button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  height: 82px;
  padding: 0 20px 0 24px;
  box-sizing: border-box;
  background: linear-gradient(90deg, #6e1d20 0%, #8b1a1a 100%);
  color: #f0ebde;
}

.picker-title {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.picker-close {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid rgba(240, 235, 222, 0.35);
  background: rgba(14, 16, 27, 0.15);
  color: #f0ebde;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.picker-body {
  min-height: 420px;
}

.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.picker-search {
  flex: 1;
  min-width: 0;
}

.picker-count {
  flex: none;
  font-size: 13px;
  color: rgba(46, 52, 58, 0.72);
  white-space: nowrap;
}

.picker-error {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(217, 74, 43, 0.08);
  color: #a93e28;
  font-weight: 600;
}

.picker-empty {
  min-height: 380px;
  display: grid;
  place-items: center;
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
  max-height: 620px;
  overflow: auto;
  padding-right: 4px;
}

.picker-card {
  border-radius: 16px;
  border: 1px solid rgba(110, 29, 32, 0.14);
  background: rgba(255, 255, 255, 0.72);
  padding: 10px;
  text-align: left;
  cursor: pointer;
  transition: transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1), box-shadow 180ms cubic-bezier(0.2, 0.9, 0.2, 1), border-color 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}

.picker-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(14, 16, 27, 0.12);
}

.picker-card[data-selected='true'] {
  border-color: rgba(110, 29, 32, 0.65);
  box-shadow: 0 0 0 2px rgba(110, 29, 32, 0.12);
}

.preview-frame {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(240, 235, 222, 0.84);
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.meta {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  font-size: 14px;
  font-weight: 700;
  color: #0e101b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.key {
  font-size: 12px;
  color: rgba(46, 52, 58, 0.72);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

:deep(.default-image-picker-dialog.el-dialog) {
  border-radius: 18px;
  background: rgba(240, 235, 222, 0.98);
  overflow: hidden;
}

:deep(.default-image-picker-dialog .el-dialog__header) {
  padding: 0;
  margin: 0;
}

:deep(.default-image-picker-dialog .el-dialog__body) {
  padding: 18px 22px 14px;
}

:deep(.default-image-picker-dialog .el-dialog__footer) {
  padding: 0 22px 18px;
}

:deep(.picker-search .el-input__wrapper) {
  border-radius: 999px;
}
</style>
