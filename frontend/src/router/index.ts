import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue')
    },
    {
      path: '/',
      name: 'root',
      component: () => import('@/layout/BaseLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue')
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/Profile.vue')
        },
        {
          path: 'video/:id',
          name: 'videoDetail',
          component: () => import('@/views/VideoDetail.vue')
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/Knowledge/index.vue')
        },
        {
          path: 'knowledge/:id',
          name: 'frameworkDetail',
          component: () => import('@/views/Knowledge/FrameworkDetail.vue')
        },
        {
          path: 'drafts',
          name: 'drafts',
          component: () => import('@/views/Knowledge/Drafts.vue')
        },
        {
          path: 'drafts/:id',
          name: 'draftDetail',
          component: () => import('@/views/Knowledge/DraftDetail.vue')
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/Search/index.vue')
        }
      ]
    }
  ]
})

// Navigation Guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.name !== 'login' && !token) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
