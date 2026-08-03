import { createRouter, createWebHistory } from 'vue-router'
import { authState, firstAllowedPath, hasPermission, loadProfile } from '../stores/auth'
import AppLayout from '../layouts/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import ExtractPanel from '../components/ExtractPanel.vue'
import SearchPanel from '../components/SearchPanel.vue'
import { logout } from '../stores/auth'

const routes = [
  { path: '/login', component: LoginView, meta: { public: true } },
  {
    path: '/',
    component: AppLayout,
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', component: { template: '<div />' } },
      { path: 'attachments/extract', component: ExtractPanel, meta: { permission: 'attachment:extract' } },
      { path: 'attachments/search', component: SearchPanel, meta: { permission: 'attachment:search' } },
      { path: 'zhejiang-3d', component: () => import('../views/ZhejiangMapView.vue'), meta: { permission: 'visualization:zhejiang:view' } },
      { path: 'system/users', component: () => import('../views/UsersView.vue'), meta: { permission: 'system:user:list' } },
      { path: 'system/roles', component: () => import('../views/RolesView.vue'), meta: { permission: 'system:role:list' } },
      { path: 'system/companies', component: () => import('../views/CompaniesView.vue'), meta: { permission: 'system:company:list' } },
      { path: 'system/positions', component: () => import('../views/PositionsView.vue'), meta: { permission: 'system:position:list' } },
      { path: 'system/menus', component: () => import('../views/MenusView.vue'), meta: { permission: 'system:menu:list' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  if (to.meta.public) {
    if (!authState.token) return true
    if (!authState.user) {
      try { await loadProfile() } catch { logout(); return true }
    }
    return firstAllowedPath()
  }
  if (!authState.token) return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  if (!authState.user) {
    try { await loadProfile() } catch { return '/login' }
  }
  if (to.path === '/' || to.path === '/home') return firstAllowedPath()
  if (to.meta.permission && !hasPermission(to.meta.permission)) return firstAllowedPath()
  return true
})

export default router
