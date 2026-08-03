<template>
  <el-container class="shell">
    <el-aside class="sidebar" width="248px">
      <div class="sidebar-brand"><span>浙</span><div><strong>文澜智析</strong><small>安全管理平台</small></div></div>
      <el-menu :default-active="route.path" router class="side-menu">
        <template v-for="item in menuTree" :key="item.id">
          <el-sub-menu v-if="item.children.length" :index="String(item.id)">
            <template #title><el-icon><component :is="icons[item.icon] || Grid" /></el-icon><span>{{ item.name }}</span></template>
            <el-menu-item v-for="child in item.children" :key="child.id" :index="child.path">
              <el-icon><component :is="icons[child.icon] || Document" /></el-icon><span>{{ child.name }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="icons[item.icon] || Grid" /></el-icon><span>{{ item.name }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div class="sidebar-foot">SECURITY BASELINE · V1.1</div>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div><p class="eyebrow">WORKSPACE</p><strong>{{ pageTitle }}</strong></div>
        <el-dropdown>
          <button class="user-chip"><span>{{ initials }}</span><div><strong>{{ authState.user?.displayName }}</strong><small>{{ authState.user?.positionName || '平台用户' }}</small></div></button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item @click="passwordDialog = true">修改密码</el-dropdown-item><el-dropdown-item divided @click="doLogout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </el-header>
      <el-main class="content"><router-view /></el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="passwordDialog" title="修改密码" width="460px" :close-on-click-modal="!authState.user?.mustChangePassword" :show-close="!authState.user?.mustChangePassword" :close-on-press-escape="!authState.user?.mustChangePassword">
    <el-alert v-if="authState.user?.mustChangePassword" title="首次登录或密码已到期，请先修改密码" type="warning" :closable="false" />
    <el-form label-position="top" class="password-form">
      <el-form-item label="原密码"><el-input v-model="passwordForm.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
      <p class="form-help">12-64 位，且包含大写、小写、数字和特殊字符。</p>
    </el-form>
    <template #footer><el-button type="primary" :loading="passwordLoading" @click="submitPassword">确认修改</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as ElementIcons from '@element-plus/icons-vue'
import { changePassword } from '../api'
import { authState, logout, menuTree } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const icons = ElementIcons
const { Grid, Document } = ElementIcons
const passwordDialog = ref(false)
const passwordLoading = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const flatMenus = computed(() => menuTree.value.flatMap((item) => [item, ...(item.children || [])]))
const pageTitle = computed(() => flatMenus.value.find((item) => item.path === route.path)?.name || '文澜智析')
const initials = computed(() => (authState.user?.displayName || '用').slice(0, 1))

const doLogout = async () => { logout(); await router.replace('/login') }
const submitPassword = async () => {
  passwordLoading.value = true
  try {
    await changePassword(passwordForm)
    ElMessage.success('密码已修改，请重新登录')
    await doLogout()
  } catch (error) { ElMessage.error(error.message) }
  finally { passwordLoading.value = false }
}
watch(() => authState.user?.mustChangePassword, (required) => { if (required) passwordDialog.value = true }, { immediate: true })
onMounted(() => { if (authState.user?.mustChangePassword) passwordDialog.value = true })
</script>
