<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { pinia } from '../../stores'
import { useUserStore } from '../../stores/user'
import bgUrl from '../../assets/又几载.png?url'
import brandLogoUrl from '../../assets/reference_images/image31.png?url'

const router = useRouter()
const userStore = useUserStore(pinia)

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)
const formRef = ref<FormInstance | null>(null)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  loading.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) {
      loading.value = false
      return
    }
    await userStore.login({ username: form.username, password: form.password })
    await router.replace('/')
  } catch (e) {
    const message = e instanceof Error ? e.message : '登录失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

function handleClear() {
  form.username = ''
  form.password = ''
  formRef.value?.clearValidate()
}
</script>

<template>
  <div class="page" :style="{ '--login-bg-url': `url(${bgUrl})` }">
    <div class="panel">
      <div class="right">
        <div class="login-panel">
          <div class="login-brand">
            <img class="brand-logo" :src="brandLogoUrl" alt="" />
            <div class="login-title">余味居</div>
          </div>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent>
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                autocomplete="username"
                placeholder="用户名"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                autocomplete="current-password"
                placeholder="密码"
              />
            </el-form-item>
            <div class="hint in-panel">建议使用公司统一账号登录。若忘记密码，请联系管理员重置。</div>
            <div class="actions">
              <button
                class="flip-btn"
                :disabled="loading"
                @click.prevent="handleLogin"
                :data-flipped="false"
              >
                <span class="flip-face flip-front">登录</span>
                <span class="flip-face flip-back">登录</span>
              </button>
              <button
                class="flip-btn"
                @click.prevent="handleClear"
                :data-flipped="true"
              >
                <span class="flip-face flip-front">清空</span>
                <span class="flip-face flip-back">清空</span>
              </button>
            </div>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 20px;
  position: relative;
  box-sizing: border-box;
  overflow: hidden;
  width: 100vw;
  background-color: var(--app-bg);
  background-image: var(--login-bg-url);
  background-position: left center;
  background-size: cover;
  background-repeat: no-repeat;
}
.page::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.1;
  background:
    radial-gradient(1200px 500px at 12% 0%, rgba(110, 29, 32, 0.08), transparent 60%),
    radial-gradient(900px 540px at 100% 0%, rgba(74, 143, 163, 0.06), transparent 56%);
  pointer-events: none;
}
.panel {
  width: 100%;
  max-width: 1320px;
  display: flex;
  justify-content: flex-end;
  position: relative;
  z-index: 1;
}
.right {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 18px;
}
.login-panel {
  width: 480px;
  max-width: 92vw;
  border-radius: 18px;
  border: 1px solid rgba(14, 16, 27, 0.12);
  background: rgba(240, 235, 222, 0.22);
  box-shadow: 0 18px 38px rgba(14, 16, 27, 0.14);
  backdrop-filter: blur(20px) saturate(120%);
  -webkit-backdrop-filter: blur(20px) saturate(120%);
  padding: 16px 22px 16px;
  transform: translateY(-3vh);
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 8px;
}
.login-title {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 38px;
  font-weight: 900;
  letter-spacing: 1px;
  color: #6E1D20;
  line-height: 1.2;   
  margin-bottom: 8px;
}
:deep(.login-panel .el-form) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
:deep(.login-panel .el-input__wrapper) {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(14, 16, 27, 0.12);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.75),
    0 10px 18px rgba(14, 16, 27, 0.06);
  transition:
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
  width: 100%;
}
:deep(.login-panel .el-input) {
  width: 100%;
}
:deep(.login-panel .el-input__inner) {
  font-weight: 650;
  font-size: 16px;
  line-height: 1.4;
  letter-spacing: 0.2px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}
.hint {
  font-size: 12px;
  color: rgba(46, 52, 58, 0.7);
  padding: 0 2px;
}
.hint.in-panel {
  margin: 4px 2px;
}
/* 复用订单管理页“查询/重置”按钮的翻转样式 */
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
@media (max-width: 980px) {
  .panel {
    justify-content: center;
  }
}
</style>
