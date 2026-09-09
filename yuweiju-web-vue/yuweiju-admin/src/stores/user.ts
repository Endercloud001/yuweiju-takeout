import Cookies from 'js-cookie'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { employeeLogin, employeeLogout } from '../api/modules/employee'
import type { EmployeeLoginBody, EmployeeLoginData } from '../types/employee'

const tokenKey = 'token'
const userInfoKey = 'userInfo'

function getCookieJson<T>(key: string): T | null {
  const raw = Cookies.get(key)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(Cookies.get(tokenKey) ?? '')
  const userInfo = ref<EmployeeLoginData | null>(getCookieJson<EmployeeLoginData>(userInfoKey))
  const username = computed(() => userInfo.value?.userName ?? userInfo.value?.name ?? '')

  function setToken(value: string) {
    token.value = value
    Cookies.set(tokenKey, value)
  }

  function clearToken() {
    token.value = ''
    Cookies.remove(tokenKey)
  }

  function setUserInfo(value: EmployeeLoginData) {
    userInfo.value = value
    Cookies.set(userInfoKey, JSON.stringify(value))
  }

  function clearUserInfo() {
    userInfo.value = null
    Cookies.remove(userInfoKey)
  }

  async function login(payload: EmployeeLoginBody) {
    const res = await employeeLogin(payload)
    setToken(res.data.token)
    setUserInfo(res.data)
    return res
  }

  async function logout() {
    try {
      await employeeLogout()
    } finally {
      clearToken()
      clearUserInfo()
    }
  }

  function reset() {
    clearToken()
    clearUserInfo()
  }

  return { token, userInfo, username, login, logout, reset }
})
