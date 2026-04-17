import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/pages/Login.vue'
import { useAuthStore } from '@/utils/auth'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'Login', component: Login },

  {
    path: '/homepage',
    component: () => import('@/components/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/homepage/dashboard' },
      { path: 'default', redirect: '/homepage/dashboard' },

      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/dashboard/Dashboard.vue')
      },
      {
        path: 'recommend',
        name: 'RecommendPage',
        component: () => import('@/pages/volunteer/RecommendPage.vue'),
        meta: { requiresAuth: true }
      },

      {
        path: 'activities',
        name: 'ActivityList',
        component: () => import('@/pages/activity/ActivityList.vue')
      },
      {
        path: 'activity/:id',
        name: 'ActivityDetail',
        component: () => import('@/pages/activity/ActivityDetail.vue')
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/pages/user/Profile.vue')
      },
      {
        path: 'admin/activities',
        name: 'ActivityManage',
        component: () => import('@/pages/admin/ActivityManage.vue'),
        meta: { requiresAuth: true, requiresManage: true }
      },
      {
        path: 'admin/users',
        name: 'UserManage',
        component: () => import('@/pages/admin/UserManage.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/nl2sql',
        name: 'NL2Sql',
        component: () => import('@/pages/admin/NL2Sql.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/knn',
        name: 'KnnMatch',
        component: () => import('@/pages/admin/KnnMatch.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/risk',
        name: 'RiskPage',
        component: () => import('@/pages/admin/RiskPage.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/ai',
        name: 'AdminAI',
        component: () => import('@/pages/admin/AdminAI.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/audit',
        name: 'ActivityAudit',
        component: () => import('@/pages/admin/ActivityAudit.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.userToken) {
    next('/login')
    return
  }

  if (to.meta.requiresAdmin && authStore.userInfo?.role !== 0) {
    next('/homepage/activities')
    return
  }

  if (to.meta.requiresManage && ![0, 2].includes(authStore.userInfo?.role)) {
    next('/homepage/activities')
    return
  }

  next()
})

export default router
