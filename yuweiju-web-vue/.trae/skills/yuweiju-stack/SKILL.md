---
name: yuweiju-stack
description: 余味居前端项目的技术层约束与实现规范。涉及 API 层（http.ts/modules、拦截器、token、响应体、文件上传下载）、路由配置（Vue Router 4、meta 字段、守卫）、Pinia stores、类型定义等技术细节时使用此 skill。
---

## API 层（src/api/ + src/types/）

### 不可变约束

- `import.meta.env.VITE_API_BASE` → 默认 `/api`
- 请求头 token 字段名：`token`（从 Pinia user store 或 Cookie 读取）
- 响应体：`{ code: number, msg: string, data: T }`，`code === 1` 为成功

### 类型模板

```ts
// src/types/api.ts
export interface ApiResponse<T = unknown> { code: number; msg: string; data: T }
export interface PageResult<T> { total: number; records: T[] }
export interface PageQuery { page: number; pageSize: number }
```

### http.ts 要点

- 请求拦截器：从 `useUserStore().token` 或 Cookie 读取，写入 `config.headers.token`
- 响应拦截器：`code !== 1` 时统一 `ElMessage.error(msg)` 并 reject；`code === 401` 时跳登录
- 文件下载：调用方传 `responseType: 'blob'`，api 函数直接返回 `Blob`

### API 模块划分

```
src/api/modules/
  employee.ts   登录/退出、员工 CRUD、分页
  category.ts   分类分页、启停、CRUD
  dish.ts       菜品分页、起售/停售、CRUD、按分类查询
  setmeal.ts    套餐分页、起售/停售、CRUD、详情
  order.ts      分页搜索、详情、状态统计、接单/拒单/取消/派送/完成
  workspace.ts  工作台 overview、top10
  report.ts     统计报表、导出（返回 Blob）
  common.ts     文件上传（multipart）
```

### API 函数规范

```ts
// 函数签名示例
export async function getEmployeePage(params: PageQuery & { name?: string }): Promise<ApiResponse<PageResult<Employee>>>
export async function exportReport(): Promise<Blob>
```

---

## 路由（src/router/）

### 路由结构

```
/login           meta: { notNeedAuth: true }
/404             meta: { notNeedAuth: true, hidden: true }
/                redirect → /dashboard，component: Layout
  dashboard      meta: { title: '工作台', icon: '...', affix: true }
  order          meta: { title: '订单管理', icon: '...' }
  category       meta: { title: '分类管理', icon: '...' }
  dish           meta: { title: '菜品管理', icon: '...' }
  dish/add       meta: { title: '新增菜品', hidden: true }
  setmeal        meta: { title: '套餐管理', icon: '...' }
  setmeal/add    meta: { title: '新增套餐', hidden: true }
  statistics     meta: { title: '数据统计', icon: '...' }
  employee       meta: { title: '员工管理', icon: '...' }
  employee/add   meta: { title: '新增员工', hidden: true }
  inform         meta: { title: '通知公告', icon: '...' }
catch-all        redirect → /404
```

### 守卫逻辑

```ts
router.beforeEach((to, _from, next) => {
  NProgress.start()
  const token = useUserStore().token || getCookie('token')
  if (token) {
    next()
  } else if (to.meta.notNeedAuth) {
    next()
  } else {
    next('/login')
  }
  document.title = (to.meta.title as string) ?? '余味居'
})
router.afterEach(() => NProgress.done())
```

### 注意事项

- `scrollBehavior` 返回 `{ left, top }`（Vue Router 4），不用 `{ x, y }`
- 守卫不调用接口获取用户信息（避免刷新阻塞）

---

## Pinia Stores（src/stores/）

- `useUserStore`：持有 `token`、`userInfo`；提供 `login()`/`logout()` action
- `logout()` 须同时清除 Cookie 和 store 内存态，并跳转 `/login`
- 页面组件通过 store 读取用户信息，不重复读 Cookie
