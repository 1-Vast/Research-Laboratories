import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import WorkbenchView from '../views/WorkbenchView.vue'
import UsersView from '../views/UsersView.vue'
import RolesView from '../views/RolesView.vue'
import { authState, clearAuth, hasPermission, setToken, setUser } from '../stores/auth'
import { fetchMe, tryRefresh } from '../api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/workbench' },
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/workbench', name: 'workbench', component: WorkbenchView },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: UsersView,
      meta: { permission: 'user:manage' },
    },
    {
      path: '/admin/roles',
      name: 'admin-roles',
      component: RolesView,
      meta: { permission: 'role:manage' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/workbench' },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  if (!authState.token) {
    const refreshed = await tryRefresh()
    if (!refreshed) {
      clearAuth()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    setToken(refreshed.accessToken)
    setUser(refreshed.user)
  }
  if (!authState.user) {
    try {
      setUser(await fetchMe())
    } catch {
      clearAuth()
      return { path: '/login' }
    }
  }
  const required = to.meta.permission as string | undefined
  if (required && !hasPermission(required)) {
    return { path: '/workbench' }
  }
  return true
})

export default router
