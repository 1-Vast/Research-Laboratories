<script setup lang="ts">
import { computed } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { authState } from '../stores/auth'

const user = computed(() => authState.user)
const permissions = computed(() => user.value?.permissions ?? [])
</script>

<template>
  <AppLayout>
    <div class="grid-2">
      <section class="card">
        <h2>当前用户</h2>
        <p><strong>{{ user?.displayName }}</strong>（{{ user?.username }}）</p>
        <p class="muted">{{ user?.email }}</p>
        <p>
          角色：
          <span v-for="role in user?.roles" :key="role.code" class="tag">{{ role.name }} · {{ role.code }}</span>
        </p>
        <p class="muted" style="font-size: 12px">
          auth_version = {{ user?.authVersion }}：改密或禁用后版本递增，旧令牌立即失效
        </p>
      </section>

      <section class="card">
        <h2>第一阶段建设状态</h2>
        <div class="notice">已完成：工程骨架、数据库迁移、注册/登录/刷新/退出、RBAC 五表、用户与角色管理接口。</div>
        <p class="muted" style="font-size: 13px">
          第二阶段将接入项目立项与结项、任务分派与验收、周报汇报；第三阶段接入资源预约、成果知识、
          实时通知与 AI 周报草稿。
        </p>
      </section>
    </div>

    <section class="card" style="margin-top: 18px">
      <h2>我的权限（共 {{ permissions.length }} 项）</h2>
      <p class="muted" style="font-size: 13px">
        权限由后端按数据库实时下发，管理员修改角色权限后下一次请求即生效；前端仅用于展示。
      </p>
      <div class="chips">
        <span v-for="permission in permissions" :key="permission" class="tag ok">{{ permission }}</span>
      </div>
    </section>
  </AppLayout>
</template>
