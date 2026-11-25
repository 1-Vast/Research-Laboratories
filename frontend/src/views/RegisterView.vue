<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth'
import { apiErrorMessage } from '../api/http'

const router = useRouter()
const username = ref('')
const password = ref('')
const displayName = ref('')
const email = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await register({
      username: username.value.trim(),
      password: password.value,
      displayName: displayName.value || undefined,
      email: email.value || undefined,
    })
    await router.push('/login')
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
      <h1>注册账号</h1>
      <p class="sub">新账号默认分配「科研人员」角色，需由实验室负责人邀请后才有项目范围</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="field">
        <label for="r-username">用户名</label>
        <input id="r-username" v-model="username" required minlength="3" maxlength="32" />
      </div>
      <div class="field">
        <label for="r-password">密码（至少 8 位，需含字母和数字）</label>
        <input id="r-password" v-model="password" type="password" required minlength="8" />
      </div>
      <div class="field">
        <label for="r-name">昵称</label>
        <input id="r-name" v-model="displayName" maxlength="80" />
      </div>
      <div class="field">
        <label for="r-email">邮箱</label>
        <input id="r-email" v-model="email" type="email" />
      </div>
      <div class="actions">
        <button class="primary" type="submit" :disabled="loading">
          {{ loading ? '提交中…' : '注册' }}
        </button>
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </form>
  </div>
</template>
