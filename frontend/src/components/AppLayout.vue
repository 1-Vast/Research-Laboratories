<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { authState, clearAuth, hasPermission } from '../stores/auth'
import { logout } from '../api/auth'

const router = useRouter()
const displayName = computed(() => authState.user?.displayName || authState.user?.username || '')

async function handleLogout() {
  try {
    await logout()
  } finally {
    clearAuth()
    router.push('/login')
  }
}
</script>

<template>
  <header class="layout-header">
    <span class="brand">LabFlow</span>
    <nav>
      <RouterLink to="/workbench" active-class="active">工作台</RouterLink>
      <RouterLink v-if="hasPermission('user:manage')" to="/admin/users" active-class="active">
        用户管理
      </RouterLink>
      <RouterLink v-if="hasPermission('role:manage')" to="/admin/roles" active-class="active">
        角色与权限
      </RouterLink>
    </nav>
    <div class="layout-user">
      <span>{{ displayName }}</span>
      <span class="tag">{{ authState.user?.roles.map((r) => r.name).join(' / ') }}</span>
      <button type="button" @click="handleLogout">退出登录</button>
    </div>
  </header>
  <main class="layout-main">
    <RouterView />
  </main>
</template>
