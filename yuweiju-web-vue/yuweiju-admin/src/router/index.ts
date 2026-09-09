import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import Cookies from 'js-cookie'
import { pinia } from '../stores'
import { useUserStore } from '../stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    hidden?: boolean
    icon?: string
    notNeedAuth?: boolean
    affix?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue'),
    meta: { title: '登录', hidden: true, notNeedAuth: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('../views/_builtin/404.vue'),
    meta: { title: '未找到', hidden: true, notNeedAuth: true },
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'dashboard', affix: true },
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('../views/order/index.vue'),
        meta: { title: '订单管理', icon: 'icon-order' },
      },
      {
        path: 'order/detail/:id',
        name: 'OrderDetail',
        component: () => import('../views/order/detail.vue'),
        meta: { title: '订单详情', hidden: true },
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('../views/category/index.vue'),
        meta: { title: '分类管理', icon: 'icon-category' },
      },
      {
        path: 'dish',
        name: 'Dish',
        component: () => import('../views/dish/index.vue'),
        meta: { title: '菜品管理', icon: 'icon-dish' },
      },
      {
        path: 'dish/add',
        name: 'DishAdd',
        component: () => import('../views/dish/add.vue'),
        meta: { title: '添加菜品', hidden: true },
      },
      {
        path: 'setmeal',
        name: 'Setmeal',
        component: () => import('../views/setmeal/index.vue'),
        meta: { title: '套餐管理', icon: 'icon-combo' },
      },
      {
        path: 'setmeal/add',
        name: 'SetmealAdd',
        component: () => import('../views/setmeal/add.vue'),
        meta: { title: '添加套餐', hidden: true },
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('../views/statistics/index.vue'),
        meta: { title: '数据统计', icon: 'icon-statistics' },
      },
      {
        path: 'employee',
        name: 'Employee',
        component: () => import('../views/employee/index.vue'),
        meta: { title: '员工管理', icon: 'icon-employee' },
      },
      {
        path: 'employee/add',
        name: 'EmployeeAdd',
        component: () => import('../views/employee/add.vue'),
        meta: { title: '添加员工', hidden: true },
      },
      {
        path: 'employee/edit',
        name: 'EmployeeEdit',
        component: () => import('../views/employee/add.vue'),
        meta: { title: '修改员工', hidden: true },
      },
      {
        path: 'inform',
        name: 'Inform',
        component: () => import('../views/inform/index.vue'),
        meta: { title: '通知中心', icon: 'icon-inform' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/404', meta: { hidden: true } },
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_to, _from, saved) => saved ?? { left: 0, top: 0 },
  routes,
})

NProgress.configure({ showSpinner: false })

router.beforeEach((to, _from, next) => {
  NProgress.start()
  const userStore = useUserStore(pinia)
  const token = userStore.token || Cookies.get('token')
  if (!token && !to.meta.notNeedAuth) {
    next({ path: '/login' })
  } else {
    next()
  }
})

router.afterEach((to) => {
  NProgress.done()
  document.title = (to.meta.title as string) || '余味居'
})

export default router
export { routes }
