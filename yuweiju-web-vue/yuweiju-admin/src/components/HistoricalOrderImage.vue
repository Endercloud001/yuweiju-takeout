<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import placeholder from '../assets/reference_images/菜品图片缺失补位图.svg'

const props = defineProps<{ image?: string | null; row: object; name: string }>()
const failed = ref(false)
const version = ref(0)
watch(() => [props.image, props.row], () => {
  failed.value = false
  version.value++
}, { flush: 'sync' })
const source = computed(() =>
  !failed.value && props.image?.trim() ? props.image : placeholder,
)
function onError(event: Event) {
  const target = event.currentTarget
  // Vue may update an old node's event invoker before removal. Check the node itself.
  if (target instanceof HTMLImageElement && target.dataset.imageVersion === String(version.value)
      && target.getAttribute('src') === props.image && source.value !== placeholder) {
    failed.value = true
  }
}
</script>

<template>
  <img :key="`${version}:${source}`" class="historical-order-image" :src="source" :alt="name" :data-image-version="version" @error="onError" />
</template>

<style scoped>
.historical-order-image {
  display: inline-block;
  width: 32px;
  height: 32px;
  margin-right: 8px;
  object-fit: cover;
  vertical-align: middle;
  border-radius: 4px;
}
</style>
