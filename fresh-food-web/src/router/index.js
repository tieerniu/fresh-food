import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: to => {
        if (to.query && to.query.code) {
          return { path: '/trace', query: to.query }
        }
        return { path: '/login' }
      }
    },
    {
      path: '/trace',
      name: 'TraceQuery',
      component: () => import('@/views/TraceQuery.vue'),
      meta: { title: '溯源查询' }
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/admin',
      component: () => import('@/layout/AdminLayout.vue'),
      redirect: '/admin/dashboard',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/admin/Dashboard.vue'),
          meta: { title: '控制台' }
        },
        {
          path: 'products',
          name: 'Products',
          component: () => import('@/views/admin/Products.vue'),
          meta: { title: '产品管理' }
        },
        {
          path: 'suppliers',
          name: 'Suppliers',
          component: () => import('@/views/admin/Suppliers.vue'),
          meta: { title: '合作基地管理', adminOnly: true }
        },
        {
          path: 'records',
          name: 'Records',
          component: () => import('@/views/admin/Records.vue'),
          meta: { title: '溯源记录管理' }
        },
        {
          path: 'qrcodes',
          name: 'QrCodes',
          component: () => import('@/views/admin/QrCodes.vue'),
          meta: { title: '二维码管理', adminOnly: true }
        },
        {
          path: 'inspections',
          name: 'Inspections',
          component: () => import('@/views/admin/Inspections.vue'),
          meta: { title: '质检与召回', adminOnly: true }
        },
        {
          path: 'warnings',
          name: 'Warnings',
          component: () => import('@/views/admin/Warnings.vue'),
          meta: { title: '防伪预警中心', adminOnly: true }
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/admin/Profile.vue'),
          meta: { title: '个人中心' }
        }

      ]
    }
  ]
})

/**
 * 从 localStorage 获取当前用户角色
 */
function getUserRole() {
  try {
    const userInfo = localStorage.getItem('userInfo')
    if (userInfo) {
      return JSON.parse(userInfo).role || ''
    }
  } catch (e) {
    // 解析失败忽略
  }
  return ''
}

// 路由守卫：检查登录状态 + 角色权限校验
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  // 1. 如果访问需要认证的页面，但没有登录
  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  // 2. 如果已经登录，访问登录页时跳转到后台首页
  if (to.path === '/login' && token) {
    next('/admin/dashboard')
    return
  }

  // 3. /admin 根路径重定向：统一进入控制台
  if (to.path === '/admin' || to.path === '/admin/') {
    next('/admin/dashboard')
    return
  }

  // 4. 角色权限校验：enterprise 用户不可访问 adminOnly 页面
  if (to.meta.adminOnly) {
    const role = getUserRole()
    if (role === 'enterprise') {
      next('/admin/products')
      return
    }
  }

  next()
})

export default router
