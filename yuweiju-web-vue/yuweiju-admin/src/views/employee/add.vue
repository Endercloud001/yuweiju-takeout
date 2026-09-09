<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { addEmployee, editEmployee, getEmployeeById } from '../../api/modules/employee'
import type { EmployeeSaveBody } from '../../types/employee'
import ornamentUrl from '../../assets/reference_images/顶部和底部花纹图片.png?url'
import topSquareUrl from '../../assets/reference_images/方形花纹_透明.png?url'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)

const ornamentCss = computed(() => `url(${ornamentUrl})`)
const topSquareCss = computed(() => `url(${topSquareUrl})`)

const employeeId = computed(() => {
  const raw = route.query.id
  const text = typeof raw === 'string' ? raw : Array.isArray(raw) ? raw[0] : undefined
  const id = text ? Number(text) : NaN
  return Number.isFinite(id) ? id : null
})

const isEdit = computed(() => employeeId.value !== null)

const formRef = ref<FormInstance>()
interface EmployeeFormModel {
  username: string
  password: string
  name: string
  phone: string
  sex: string
  idNumber: string
}

const form = reactive<EmployeeFormModel>({
  username: '',
  password: '',
  name: '',
  phone: '',
  sex: '男',
  idNumber: '',
})

const phoneReg = /^1[3-9]\d{9}$/
const idNumberReg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/

const rules: FormRules<EmployeeFormModel> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '账号长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入员工姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '姓名长度在 2 到 10 个字符', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !phoneReg.test(value)) {
          callback(new Error('请输入正确的手机号'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  idNumber: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !idNumberReg.test(value)) {
          callback(new Error('请输入正确的身份证号'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function gotoList() {
  router.push({ name: 'Employee' })
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
    const payload: EmployeeSaveBody = {
      username: form.username.trim(),
      password: form.password.trim(),
      name: form.name.trim(),
      phone: form.phone.trim(),
      sex: form.sex,
      idNumber: form.idNumber.trim(),
    }

    if (employeeId.value) {
      await editEmployee({ ...payload, id: employeeId.value })
      ElMessage.success('保存成功')
    } else {
      await addEmployee(payload)
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

async function fetchDetail() {
  if (!employeeId.value) return
  loading.value = true
  try {
    const res = await getEmployeeById(employeeId.value)
    form.username = res.data.username
    form.name = res.data.name
    form.phone = res.data.phone
    form.sex = res.data.sex
    form.idNumber = res.data.idNumber
    form.password = ''
  } catch (e) {
    const message = e instanceof Error ? e.message : '获取详情失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<template>
  <div class="employee-add-page">
    <section class="detail-frame">
      <header class="detail-topbar">
        <div class="topbar-square topbar-square-left"></div>
        <div class="topbar-square topbar-square-right"></div>
        <div class="topbar-left">
          <div class="topbar-title">{{ isEdit ? '余味居·修改员工' : '余味居·新增员工' }}</div>
        </div>
        <div class="topbar-right">
          <span class="topbar-status-text">还请各位，一一入座</span>
          <button class="topbar-close" type="button" aria-label="关闭" @click="gotoList">×</button>
        </div>
      </header>

      <div class="detail-body">
        <div class="detail-layout">
          <div class="card-border-outer">
            <div class="card-border-inner">
              <el-form
                ref="formRef"
                class="employee-form"
                :model="form"
                :rules="rules"
                label-width="110px"
                v-loading="loading"
              >
                <div class="form-grid-single">
                  <el-form-item label="账号" prop="username" required>
                    <el-input v-model="form.username" class="name-input" placeholder="请输入账号" maxlength="20" :disabled="isEdit" />
                  </el-form-item>

                  <el-form-item v-if="!isEdit" label="密码" prop="password" required>
                    <el-input v-model="form.password" type="password" class="name-input" placeholder="请输入密码" show-password maxlength="20" />
                  </el-form-item>

                  <el-form-item v-else label="密码">
                    <el-input v-model="form.password" type="password" class="name-input" placeholder="不修改请留空" show-password maxlength="20" />
                  </el-form-item>

                  <el-form-item label="员工姓名" prop="name" required>
                    <el-input v-model="form.name" class="name-input" placeholder="请输入员工姓名" maxlength="10" />
                  </el-form-item>

                  <el-form-item label="手机号" prop="phone" required>
                    <el-input v-model="form.phone" class="name-input" placeholder="请输入手机号" maxlength="11" />
                  </el-form-item>

                  <el-form-item label="性别" prop="sex">
                    <el-radio-group v-model="form.sex">
                      <el-radio label="男">男</el-radio>
                      <el-radio label="女">女</el-radio>
                    </el-radio-group>
                  </el-form-item>

                  <el-form-item label="身份证号" prop="idNumber" required>
                    <el-input v-model="form.idNumber" class="name-input" placeholder="请输入身份证号" maxlength="18" />
                  </el-form-item>
                </div>

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
  </div>
</template>

<style scoped>
.employee-add-page {
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
  padding: 20px 80px 50px;
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
  width: 100%;
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

.employee-form {
  padding: 18px 22px 18px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.form-grid-single {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 450px;
  margin-right: 10px;
}

:deep(.employee-form .el-input__wrapper),
:deep(.employee-form .el-select__wrapper),
:deep(.employee-form .el-textarea__inner) {
  border-radius: 14px;
}

:deep(.employee-form .el-form-item) {
  align-items: center;
}

:deep(.employee-form .el-form-item__label) {
  font-family: 'SimHei', '黑体', sans-serif;
  font-size: 16px;
  font-weight: 700;
  color: #0e101b;
  height: 40px;
  line-height: 40px;
  display: inline-flex;
  align-items: center;
}

.name-input {
  width: 350px;
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

@media (max-width: 1100px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .topbar-title {
    font-size: 22px;
  }
  .detail-body {
    padding: 12px 14px 18px;
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
</style>
