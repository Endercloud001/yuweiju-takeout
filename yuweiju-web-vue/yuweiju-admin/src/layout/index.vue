<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { routes } from '../router'
import { useUserStore } from '../stores/user'
import { pinia } from '../stores'
import logoUrl from '../assets/reference_images/medal_activity_40side_10.png?url'
import topbarBgUrl from '../assets/reference_images/image6.png?url'
import shopDialogBgUrl from '../assets/reference_images/image18.png?url'
import dialogTitleIconUrl from '../assets/reference_images/image26.png?url'
import passwordTitleIconUrl from '../assets/reference_images/image10.png?url'
import dialogDividerUrl from '../assets/reference_images/image29.png?url'
import orderBgUrl from '../assets/reference_images/image16.png?url'
import { employeeEditPassword } from '../api/modules/employee'
import { getShopStatus, setShopStatus, type ShopStatus } from '../api/modules/shop'
import {
  ArrowDown,
  BellFilled,
  DataLine,
  Dish,
  Document,
  Grid,
  HomeFilled,
  Lock,
  Management,
  Setting,
  SwitchButton,
  Tickets,
  UserFilled,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore(pinia)

const menuItems = (routes.find((r) => r.path === '/')?.children || []).filter((r) => !r.meta?.hidden)

const crumbs = computed(() => route.matched.filter((m) => m.meta && m.meta.title))
const topbarBgCss = computed(() => `url(${topbarBgUrl})`)
const shopDialogBgCss = computed(() => `url(${shopDialogBgUrl})`)
const dialogDividerBgCss = computed(() => `url(${dialogDividerUrl})`)
const orderBgCss = computed(() => `url(${orderBgUrl})`)

const shopStatus = ref<ShopStatus>(1)
const shopStatusLoading = ref(false)
const shopDialogVisible = ref(false)
const shopStatusDraft = ref<ShopStatus>(1)
const shopStatusLabel = computed(() => (shopStatus.value === 1 ? '营业中' : '打烊中'))

const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const focusedPasswordField = ref<'oldPassword' | 'newPassword' | 'confirmPassword' | null>(null)
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原始密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度需为 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, value, callback) => {
        if (!value) return callback()
        if (value !== passwordForm.newPassword) return callback(new Error('两次输入的新密码不一致'))
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleLogout() {
  await userStore.logout()
  await router.replace('/login')
}

async function fetchShopStatus() {
  shopStatusLoading.value = true
  try {
    const res = await getShopStatus()
    shopStatus.value = res.data
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取营业状态失败'
    ElMessage.error(message)
  } finally {
    shopStatusLoading.value = false
  }
}

function openShopDialog() {
  shopStatusDraft.value = shopStatus.value
  shopDialogVisible.value = true
}

async function saveShopStatus() {
  shopStatusLoading.value = true
  try {
    await setShopStatus(shopStatusDraft.value)
    shopStatus.value = shopStatusDraft.value
    shopDialogVisible.value = false
    ElMessage.success('营业状态已更新')
  } catch (e) {
    const message = e instanceof Error ? e.message : '设置营业状态失败'
    ElMessage.error(message)
  } finally {
    shopStatusLoading.value = false
  }
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

function openPasswordDialog() {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

async function submitPassword() {
  const ok = await passwordFormRef.value?.validate().catch(() => false)
  if (!ok) return
  const empId = userStore.userInfo?.id
  if (!empId) {
    ElMessage.error('未获取到员工信息，请重新登录后再试')
    return
  }
  passwordSubmitting.value = true
  try {
    await employeeEditPassword({
      empId,
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordDialogVisible.value = false
    ElMessage.success('密码修改成功')
  } catch (e) {
    const message = e instanceof Error ? e.message : '修改密码失败'
    ElMessage.error(message)
  } finally {
    passwordSubmitting.value = false
  }
}

function getMenuIcon(path: string) {
  if (path.startsWith('/dashboard')) return HomeFilled
  if (path.startsWith('/order')) return Tickets
  if (path.startsWith('/category')) return Grid
  if (path.startsWith('/dish')) return Dish
  if (path.startsWith('/setmeal')) return Document
  if (path.startsWith('/statistics')) return DataLine
  if (path.startsWith('/employee')) return UserFilled
  if (path.startsWith('/inform')) return BellFilled
  return Management
}

onMounted(() => {
  fetchShopStatus()
})
</script>

<template>
  <el-container class="layout">
    <el-header class="topbar">
      <div class="topbar-left">
        <div class="brand">
          <img class="brand-logo" :src="logoUrl" alt="余味居 Logo" />
          <div class="brand-title-wrap" aria-label="余味居">
            <span class="brand-char"><span class="brand-glyph">余</span></span>
            <span class="brand-char"><span class="brand-glyph">味</span></span>
            <span class="brand-char"><span class="brand-glyph">居</span></span>
          </div>
        </div>
      </div>
      <div class="topbar-right">
        <div class="topbar-actions">
          <el-button
              size="small"
              class="shop-status-btn"
              :loading="shopStatusLoading"
              @click="openShopDialog"
            >
              <span class="shop-status-dot" :data-status="shopStatus"></span>
              <span class="shop-status-text">{{ shopStatusLabel }}</span>
              <el-icon class="shop-status-gear"><Setting /></el-icon>
            </el-button>

            <el-dropdown trigger="click" placement="bottom-end">
              <el-button size="small" class="user-btn">
                <el-icon class="user-btn-icon"><UserFilled /></el-icon>
                <span class="user-btn-name">{{ userStore.username || '未命名' }}</span>
                <el-icon class="user-btn-caret"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu class="user-menu">
                  <el-dropdown-item @click="openPasswordDialog">
                    <el-icon><Lock /></el-icon>
                    修改密码
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
      </div>
    </el-header>
    <el-container>
      <el-aside width="260px" class="aside aside-dark">
        <el-scrollbar class="nav-scroll">
          <el-menu
            router
            :default-active="route.path"
            class="menu"
            background-color="#3f3f3f"
            text-color="#f0ebde"
            active-text-color="#f0ebde"
          >
            <el-menu-item v-for="r in menuItems" :key="r.path" :index="'/' + r.path">
              <el-icon class="menu-icon">
                <component :is="getMenuIcon('/' + r.path)" />
              </el-icon>
              <span class="menu-text">{{ r.meta?.title }}</span>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </el-aside>
      <el-main
        class="main"
        :class="{
          'is-dashboard': route.path === '/dashboard',
          'is-order': route.path === '/order',
          'is-category': route.path === '/category',
          'is-dish': route.path === '/dish',
          'is-dish-add': route.path.startsWith('/dish/add'),
          'is-setmeal': route.path === '/setmeal',
          'is-setmeal-add': route.path.startsWith('/setmeal/add'),
          'is-order-detail': route.path.startsWith('/order/detail'),
          'is-employee': route.path === '/employee',
          'is-employee-add': route.path.startsWith('/employee/add') || route.path.startsWith('/employee/edit'),
          'is-statistics': route.path === '/statistics',
        }"
      >
        <div class="content">
          <div
            class="page-header"
            v-if="
              route.path !== '/dashboard' &&
              route.path !== '/order' &&
              route.path !== '/category' &&
              route.path !== '/dish' &&
              !route.path.startsWith('/dish/add') &&
              route.path !== '/setmeal' &&
              !route.path.startsWith('/setmeal/add') &&
              !route.path.startsWith('/order/detail') &&
              route.path !== '/employee' &&
              !route.path.startsWith('/employee/add') &&
              !route.path.startsWith('/employee/edit') &&
              route.path !== '/statistics' &&
              route.path !== '/inform'
            "
          >
            <el-breadcrumb separator=">">
              <el-breadcrumb-item v-for="c in crumbs" :key="c.path">{{ c.meta?.title }}</el-breadcrumb-item>
            </el-breadcrumb>
            <div class="page-title">{{ route.meta.title }}</div>
          </div>
          <router-view />
        </div>
      </el-main>
    </el-container>

    <el-dialog v-model="shopDialogVisible" width="540px" class="shop-dialog" :show-close="false">
      <template #title>
        <div class="dialog-title-row">
          <div class="dialog-title">
            <img class="dialog-title-icon" :src="dialogTitleIconUrl" alt="" />
            <span class="dialog-title-text">营业状态设置</span>
          </div>
          <button class="dialog-close" type="button" aria-label="关闭" @click="shopDialogVisible = false">
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
      <div class="shop-dialog-body">
        <el-radio-group v-model="shopStatusDraft" class="status-group">
          <label class="status-card" :data-active="shopStatusDraft === 1">
            <el-radio :label="1" class="status-radio"></el-radio>
            <div class="status-body">
              <div class="status-title">营业中</div>
              <div class="status-desc">当前餐厅处于营业状态，自动接收任何订单，可点击打烊进入店铺打烊状态。</div>
            </div>
          </label>
          <label class="status-card" :data-active="shopStatusDraft === 0">
            <el-radio :label="0" class="status-radio"></el-radio>
            <div class="status-body">
              <div class="status-title">打烊中</div>
              <div class="status-desc">
                当前餐厅处于打烊状态，仅接受营业时间内的预定订单，可点击营业中手动恢复营业状态。
              </div>
            </div>
          </label>
        </el-radio-group>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <button class="stateful-btn is-ghost" type="button" @click="shopDialogVisible = false">取消</button>
          <button
            class="stateful-btn is-primary"
            type="button"
            :disabled="shopStatusLoading"
            :class="{ 'is-loading': shopStatusLoading }"
            @click="saveShopStatus"
          >
            <span class="btn-spinner" aria-hidden="true"></span>
            <span class="btn-text">{{ shopStatusLoading ? '处理中' : '确定' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogVisible"
      width="560px"
      class="password-dialog"
      :show-close="false"
      @closed="resetPasswordForm"
    >
      <template #title>
        <div class="dialog-title-row">
          <div class="dialog-title">
            <img class="dialog-title-icon" :src="passwordTitleIconUrl" alt="" />
            <span class="dialog-title-text">修改密码</span>
          </div>
          <button class="dialog-close" type="button" aria-label="关闭" @click="passwordDialogVisible = false">
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
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="112px">
        <el-form-item label="原始密码" prop="oldPassword">
          <div
            class="vanish-input"
            :data-active="focusedPasswordField === 'oldPassword'"
            :data-has-value="passwordForm.oldPassword.length > 0"
          >
            <span class="vanish-placeholder">请输入原始密码</span>
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder=""
              @focus="focusedPasswordField = 'oldPassword'"
              @blur="focusedPasswordField = null"
            />
          </div>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <div
            class="vanish-input"
            :data-active="focusedPasswordField === 'newPassword'"
            :data-has-value="passwordForm.newPassword.length > 0"
          >
            <span class="vanish-placeholder">6-20位密码，数字或字母，区分大小写</span>
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder=""
              @focus="focusedPasswordField = 'newPassword'"
              @blur="focusedPasswordField = null"
            />
          </div>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <div
            class="vanish-input"
            :data-active="focusedPasswordField === 'confirmPassword'"
            :data-has-value="passwordForm.confirmPassword.length > 0"
          >
            <span class="vanish-placeholder">请再次输入新密码</span>
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder=""
              @focus="focusedPasswordField = 'confirmPassword'"
              @blur="focusedPasswordField = null"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <button class="stateful-btn is-ghost" type="button" @click="passwordDialogVisible = false">取消</button>
          <button
            class="stateful-btn is-primary"
            type="button"
            :disabled="passwordSubmitting"
            :class="{ 'is-loading': passwordSubmitting }"
            @click="submitPassword"
          >
            <span class="btn-spinner" aria-hidden="true"></span>
            <span class="btn-text">{{ passwordSubmitting ? '处理中' : '保存' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  height: 100vh;
}
.topbar {
  display: grid;
  grid-template-columns: 260px 1fr;
  height: 80px;
  border-bottom: 1px solid var(--el-border-color);
  background-image: v-bind(topbarBgCss);
  background-size: cover;
  background-position: center;
  position: relative;
  overflow: hidden;
  padding: 0;
}
.topbar::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(14, 16, 27, 0.35), rgba(14, 16, 27, 0.18)),
    radial-gradient(900px 60px at 24% 20%, rgba(110, 29, 32, 0.55), transparent 60%);
  pointer-events: none;
}
.topbar::after {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.22;
  background: var(--app-noise);
  mix-blend-mode: multiply;
  pointer-events: none;
}
.topbar-left {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  color: #f0ebde;
  position: relative;
  z-index: 1;
}
.topbar-right {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 24px;
}
.aside {
  border-right: 1px solid var(--el-border-color);
  display: flex;
  flex-direction: column;
  height: 100%;
}
.aside-dark {
  background: #3f3f3f;
  color: #f0ebde;
  position: relative;
  overflow: hidden;
}
.aside-dark::after {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.12;
  background: var(--app-noise);
  mix-blend-mode: multiply;
  pointer-events: none;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  position: relative;
  z-index: 2;
}
.brand-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
  filter: drop-shadow(0 10px 20px rgba(14, 16, 27, 0.35));
}
.brand-title-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding-left: 8px;
}
.brand-char {
  position: relative;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  z-index: 2;
}

.brand-glyph {
  font-family: var(--app-font-brand);
  font-weight: 900;
  font-size: 30px;
  line-height: 1;
  letter-spacing: 0.5px;
  color: #7a1c1c;
  transform: translateY(-1px);
  text-shadow:
    0 12px 22px rgba(14, 16, 27, 0.22),
    0 1px 0 rgba(240, 235, 222, 0.14);
}

@supports (-webkit-background-clip: text) {
  .brand-glyph {
    background-image: linear-gradient(180deg, #8b1e1e, #7a1c1c);
    background-size: 100% 100%;
    background-repeat: no-repeat;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    color: transparent;
  }
}
.brand-char::before {
  content: '';
  position: absolute;
  inset: 0;
  transform: rotate(-6deg);
  width: 48px;
  height: 48px;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%) rotate(-8deg);
  border-radius: 999px;
  background:
    radial-gradient(circle at 35% 30%, rgba(240, 235, 222, 0.95), rgba(240, 235, 222, 0.62) 58%, rgba(240, 235, 222, 0.3));
  box-shadow:
    0 0 0 3px rgba(110, 29, 32, 0.82),
    0 16px 34px rgba(14, 16, 27, 0.22);
  z-index: -1;
}
.brand-char::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%) rotate(-8deg);
  width: 56px;
  height: 56px;
  border-radius: 999px;
  background: conic-gradient(
    from 0deg,
    rgba(217, 74, 43, 0.9),
    rgba(110, 29, 32, 0.98),
    rgba(217, 74, 43, 0.8),
    rgba(110, 29, 32, 0.96),
    rgba(217, 74, 43, 0.88)
  );
  -webkit-mask: radial-gradient(circle at 50% 50%, transparent 58%, #000 59% 74%, transparent 75%);
  mask: radial-gradient(circle at 50% 50%, transparent 58%, #000 59% 74%, transparent 75%);
  opacity: 0.9;
  filter: blur(0.25px);
  z-index: -2;
}
.nav-scroll {
  height: 100%;
}
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shop-status-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(240, 235, 222, 0.28);
  background: rgba(14, 16, 27, 0.18);
  color: rgba(240, 235, 222, 0.92);
  backdrop-filter: blur(10px);
}

.shop-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  display: inline-block;
  margin-right: 8px;
  box-shadow: 0 0 0 2px rgba(14, 16, 27, 0.18);
  background: rgba(94, 164, 128, 0.95);
}
.shop-status-dot[data-status='0'] {
  background: rgba(217, 74, 43, 0.92);
}

.shop-status-text {
  letter-spacing: 0.4px;
  font-weight: 700;
}

.shop-status-gear {
  margin-left: 6px;
  opacity: 0.9;
}

.user-btn {
  height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(240, 235, 222, 0.28);
  background: rgba(14, 16, 27, 0.18);
  color: rgba(240, 235, 222, 0.92);
  backdrop-filter: blur(10px);
}
.user-btn-icon {
  margin-right: 6px;
  opacity: 0.95;
}
.user-btn-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-btn-caret {
  margin-left: 6px;
  opacity: 0.9;
}

:deep(.user-menu.el-dropdown-menu) {
  padding: 6px;
  border-radius: var(--panel-radius);
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
  backdrop-filter: blur(10px);
}
:deep(.user-menu .el-dropdown-menu__item) {
  border-radius: 10px;
  color: rgba(14, 16, 27, 0.9);
  font-weight: 650;
  letter-spacing: 0.2px;
}
:deep(.user-menu .el-dropdown-menu__item:hover) {
  background: rgba(110, 29, 32, 0.06);
  color: var(--app-primary);
}

.shop-dialog-body {
  padding: 6px 2px 4px;
}
.status-group {
  width: 100%;
  display: grid;
  gap: 12px;
}
.status-card {
  border-radius: 16px;
  padding: 16px 16px 14px;
  border: 1px solid rgba(14, 16, 27, 0.12);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  transition:
    transform 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    border-color 220ms cubic-bezier(0.2, 0.9, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.2, 0.9, 0.2, 1);
  cursor: pointer;
  position: relative;
  padding-left: 54px;
}
.status-card[data-active='true'] {
  border-color: rgba(110, 29, 32, 0.38);
  box-shadow:
    0 18px 34px rgba(14, 16, 27, 0.12),
    0 0 0 1px rgba(110, 29, 32, 0.08);
  transform: translateY(-1px);
}
.status-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.status-title {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-weight: 900;
  font-size: 18px;
  color: rgba(14, 16, 27, 0.92);
  letter-spacing: 0.4px;
}
.status-desc {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(46, 52, 58, 0.76);
}
:deep(.status-radio.el-radio) {
  position: absolute;
  left: 18px;
  top: 50%;
  transform: translateY(-50%);
  align-items: center;
  height: auto;
  margin: 0;
}
:deep(.status-radio.el-radio .el-radio__label) {
  display: none;
}

:deep(.password-dialog .el-dialog__body) {
  padding-top: 10px;
}
:deep(.shop-dialog.el-dialog),
:deep(.password-dialog.el-dialog) {
  border-radius: var(--panel-radius);
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
  backdrop-filter: blur(12px);
  overflow: hidden;
  position: relative;
}
:deep(.shop-dialog .el-dialog__header),
:deep(.password-dialog .el-dialog__header) {
  padding: 18px 24px 30px;
  border-bottom: none;
  background-image: v-bind(dialogDividerBgCss);
  background-repeat: no-repeat;
  background-position: 24px calc(100% - 6px);
  background-size: calc(100% - 48px) 18px;
}
:deep(.shop-dialog .el-dialog__title),
:deep(.password-dialog .el-dialog__title) {
  color: inherit;
}
:deep(.shop-dialog .el-dialog__headerbtn),
:deep(.password-dialog .el-dialog__headerbtn) {
  top: 16px;
  right: 16px;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  transition: background 180ms cubic-bezier(0.2, 0.9, 0.2, 1), transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
:deep(.shop-dialog .el-dialog__headerbtn:hover),
:deep(.password-dialog .el-dialog__headerbtn:hover) {
  background: rgba(110, 29, 32, 0.08);
  transform: translateY(-1px);
}
:deep(.shop-dialog .el-dialog__close),
:deep(.password-dialog .el-dialog__close) {
  color: rgba(46, 52, 58, 0.72);
}
:deep(.shop-dialog .el-dialog__headerbtn:hover .el-dialog__close),
:deep(.password-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: var(--app-primary);
}

.dialog-title-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.dialog-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.dialog-title-icon {
  width: 26px;
  height: 26px;
  object-fit: contain;
  opacity: 0.95;
  filter: drop-shadow(0 10px 18px rgba(14, 16, 27, 0.18));
}
.dialog-title-text {
  font-family: var(--app-font-brand);
  font-size: 24px;
  color: var(--app-primary);
  font-weight: 900;
  letter-spacing: 1px;
  text-shadow: 0 8px 18px rgba(14, 16, 27, 0.14);
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
  transition: transform 180ms cubic-bezier(0.2, 0.9, 0.2, 1), filter 180ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.dialog-close:hover {
  transform: translateY(-1px);
  filter: drop-shadow(0 10px 18px rgba(14, 16, 27, 0.18));
}
.dialog-close:active {
  transform: translateY(0);
}

:deep(.shop-dialog.el-dialog) {
  border-color: rgba(110, 29, 32, 0.18);
  background-image:
    radial-gradient(900px 280px at 18% 0%, rgba(240, 235, 222, 0.5), rgba(240, 235, 222, 0.12) 60%, rgba(240, 235, 222, 0)),
    linear-gradient(180deg, rgba(240, 235, 222, 0.22), rgba(240, 235, 222, 0.58)),
    v-bind(shopDialogBgCss);
  background-size: cover, cover, cover;
  background-repeat: no-repeat;
  background-position: center;
  background-blend-mode: normal, multiply, normal;
}
:deep(.shop-dialog.el-dialog)::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--app-noise);
  opacity: 0.12;
  mix-blend-mode: multiply;
  pointer-events: none;
}
:deep(.password-dialog.el-dialog) {
  background-image:
    radial-gradient(900px 280px at 22% 0%, rgba(240, 235, 222, 0.38), rgba(240, 235, 222, 0.1) 60%, rgba(240, 235, 222, 0)),
    linear-gradient(180deg, rgba(240, 235, 222, 0.16), rgba(240, 235, 222, 0.48)),
    v-bind(shopDialogBgCss);
  background-size: cover, cover, cover;
  background-repeat: no-repeat;
  background-position: center;
  background-blend-mode: normal, multiply, normal;
}
:deep(.password-dialog.el-dialog)::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--app-noise);
  opacity: 0.16;
  mix-blend-mode: multiply;
  pointer-events: none;
}
:deep(.shop-dialog .el-dialog__header),
:deep(.shop-dialog .el-dialog__body),
:deep(.shop-dialog .el-dialog__footer),
:deep(.password-dialog .el-dialog__header),
:deep(.password-dialog .el-dialog__body),
:deep(.password-dialog .el-dialog__footer) {
  position: relative;
  z-index: 1;
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
  font-size: 13px;
  color: rgba(46, 52, 58, 0.5);
  letter-spacing: 0.2px;
  pointer-events: none;
  transition:
    transform 260ms cubic-bezier(0.2, 0.9, 0.2, 1),
    opacity 260ms cubic-bezier(0.2, 0.9, 0.2, 1),
    color 260ms cubic-bezier(0.2, 0.9, 0.2, 1);
}
.vanish-input[data-active='true'] .vanish-placeholder,
.vanish-input[data-has-value='true'] .vanish-placeholder {
  transform: translateY(-18px) scale(0.94);
  opacity: 0.72;
  color: rgba(110, 29, 32, 0.72);
}
:deep(.password-dialog .vanish-input .el-input__wrapper) {
  height: 26px;
  padding: 18px 14px 10px;
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
}
:deep(.password-dialog .vanish-input .el-input__inner) {
  font-weight: 650;
  letter-spacing: 0.2px;
}
.vanish-input[data-active='true'] :deep(.el-input__wrapper) {
  border-color: rgba(110, 29, 32, 0.5);
  box-shadow:
    0 0 0 4px rgba(110, 29, 32, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.75),
    0 14px 26px rgba(14, 16, 27, 0.1);
  transform: translateY(-1px);
}

:deep(.password-dialog .el-form) {
  max-width: 500px;
  margin: 0 auto;
}
:deep(.password-dialog .el-form-item) {
  align-items: center;
}
:deep(.password-dialog .el-form-item__label) {
  font-family: '喜鹊聚珍体 regular', var(--app-font-brand);
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.4px;
  color: rgba(14, 16, 27, 0.9);
  line-height: 26px;
  padding-top: 0;
  white-space: nowrap;
  word-break: keep-all;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
}
.stateful-btn {
  height: 42px;
  min-width: 112px;
  padding: 0 18px;
  border-radius: 12px;
  border: 1px solid rgba(14, 16, 27, 0.12);
  background: rgba(255, 255, 255, 0.7);
  color: rgba(14, 16, 27, 0.86);
  font-weight: 750;
  letter-spacing: 0.4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  position: relative;
  overflow: hidden;
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
  background:
    radial-gradient(260px 90px at 16% 0%, rgba(255, 255, 255, 0.55), transparent 60%),
    var(--app-noise);
  opacity: 0.22;
  mix-blend-mode: multiply;
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
.stateful-btn.is-primary::before {
  opacity: 0.16;
  mix-blend-mode: soft-light;
}
.stateful-btn:disabled {
  cursor: not-allowed;
  opacity: 0.82;
  transform: none;
  box-shadow: none;
}
.btn-spinner {
  width: 0;
  height: 0;
  display: none;
}
.stateful-btn.is-loading .btn-spinner {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  border: 2px solid rgba(240, 235, 222, 0.55);
  border-top-color: rgba(240, 235, 222, 0.98);
  display: inline-block;
  animation: spin 900ms linear infinite;
}
.stateful-btn.is-loading.is-ghost .btn-spinner {
  border-color: rgba(46, 52, 58, 0.2);
  border-top-color: rgba(46, 52, 58, 0.8);
}
.btn-text {
  position: relative;
  z-index: 1;
}
.stateful-btn::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  height: 3px;
  width: 0%;
  background: rgba(240, 235, 222, 0.7);
  opacity: 0;
}
.stateful-btn.is-loading::after {
  opacity: 1;
  animation: loadingBar 1800ms cubic-bezier(0.2, 0.9, 0.2, 1) infinite;
}
.stateful-btn.is-loading.is-ghost::after {
  background: rgba(110, 29, 32, 0.22);
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes loadingBar {
  0% {
    width: 0%;
    transform: translateX(0);
  }
  55% {
    width: 72%;
    transform: translateX(0);
  }
  100% {
    width: 72%;
    transform: translateX(40%);
  }
}
:deep(.shop-dialog .el-dialog__footer .el-button),
:deep(.password-dialog .el-dialog__footer .el-button) {
  border-radius: 10px;
}
:deep(.shop-dialog .el-dialog__footer .el-button--primary),
:deep(.password-dialog .el-dialog__footer .el-button--primary) {
  background: #6e1d20;
  border-color: #6e1d20;
}
:deep(.shop-dialog .el-dialog__footer .el-button--primary:hover),
:deep(.password-dialog .el-dialog__footer .el-button--primary:hover) {
  background: #7a2326;
  border-color: #7a2326;
}
:deep(.password-dialog .el-input__wrapper) {
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--panel-border);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
}
:deep(.password-dialog .el-input__wrapper:hover),
:deep(.password-dialog .el-input__wrapper.is-focus) {
  border-color: var(--app-primary);
  box-shadow:
    0 0 0 3px rgba(110, 29, 32, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}
:deep(.shop-dialog .el-radio.is-checked .el-radio__inner) {
  background-color: var(--app-primary);
  border-color: var(--app-primary);
}
.main {
  padding: 16px 24px 24px;
  background: transparent;
  box-shadow: none;
  border: none;
  display: flex;
  flex-direction: column;
}
.main.is-dashboard {
  padding: 0;
}
.main.is-order-detail {
  padding: 0;
}
.main.is-dish-add {
  padding: 0;
}
.main.is-setmeal-add {
  padding: 0;
}
.main.is-employee-add {
  padding: 0;
}
.main.is-statistics {
  padding: 0;
}
.main.is-order {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-category {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-dish {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-dish-add {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-setmeal {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-setmeal-add {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-employee {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-statistics {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-employee-add {
  background-image: v-bind(orderBgCss);
  background-size: cover;
  background-repeat: no-repeat;
  background-position: center;
}
.main.is-dashboard .content {
  max-width: none;
  margin: 0;
  height: 100%;
}
.main.is-category .content {
  max-width: none;
  margin: 0;
}
.main.is-dish .content {
  max-width: none;
  margin: 0;
}
.main.is-dish-add .content {
  max-width: none;
  margin: 0;
  height: 100%;
}
.main.is-setmeal .content {
  max-width: none;
  margin: 0;
}
.main.is-setmeal-add .content {
  max-width: none;
  margin: 0;
  height: 100%;
}
.main.is-employee .content {
  max-width: none;
  margin: 0;
}
.main.is-employee-add .content {
  max-width: none;
  margin: 0;
  height: 100%;
}
.main.is-order-detail .content {
  max-width: none;
  margin: 0;
  height: 100%;
}
.main.is-statistics .content {
  max-width: none;
  margin: 0;
}
.content {
  margin: 0 auto;
  width: 100%;
  max-width: 1600px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 12px;
}
.page-title {
  font-weight: 650;
  font-size: 16px;
  color: rgba(14, 16, 27, 0.86);
}

:deep(.menu.el-menu) {
  border-right: none;
  width: 100%;
}
:deep(.menu .el-menu-item) {
  margin: 0;
  border-radius: 0;
  height: 60px;
  line-height: 60px;
  position: relative;
  justify-content: flex-start;
  padding-left: 18px;
  font-size: 20px;
  font-weight: 650;
  font-family: var(--app-font-sidebar);
  letter-spacing: 0.8px;
  -webkit-text-stroke: 0.3px transparent;
  text-shadow:
    0 1px 0 rgba(14, 16, 27, 0.28),
    0 0 0.8px #782d19;
}
:deep(.menu .el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.06);
  color: #f0ebde;
}
:deep(.menu .el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 4px;
  background: #6e1d20;
}

.menu-icon {
  margin-right: 10px;
  font-size: 20px;
  opacity: 0.95;
}
.menu-text {
  transform: translateY(0.5px);
}

@media print {
  .aside,
  .topbar {
    display: none !important;
  }
  .main {
    padding: 0 !important;
    background: #fff !important;
  }
}
</style>
