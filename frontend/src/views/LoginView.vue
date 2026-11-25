<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { apiErrorMessage } from '../api/http'
import { setToken, setUser } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const result = await login(username.value.trim(), password.value)
    setToken(result.accessToken)
    setUser(result.user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workbench'
    await router.push(redirect)
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <form class="card auth-card" @submit.prevent="submit">
      <h1>LabFlow 登录</h1>
      <p class="sub">科研实验室智能协同管理平台</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="field">
        <label for="username">用户名</label>
        <input id="username" v-model="username" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="password">密码</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <div class="actions">
        <button class="primary" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
        <RouterLink to="/register">注册新账号</RouterLink>
      </div>
      <p class="muted" style="margin-top: 18px; font-size: 12px">
        演示账号：admin_demo / pi_demo / leader_demo / researcher_a / researcher_b，口令均为 Labflow@2026
      </p>
    </form>
  </div>
</template>
