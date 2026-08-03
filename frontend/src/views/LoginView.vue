<template>
  <div class="login-page">
    <div class="login-visual">
      <div class="brand-mark">浙</div>
      <p class="eyebrow">TEXT INTELLIGENCE PLATFORM</p>
      <h1>陈师傅工作台</h1>
      <p class="login-intro">安全、清晰地管理文档提取、组织权限与区域数据展示。</p>
      <div class="security-note"><span></span> 身份鉴别 · 最小权限 · 安全审计</div>
    </div>
    <el-card class="login-card" shadow="never">
      <p class="eyebrow">WELCOME BACK</p>
      <h2>登录管理平台</h2>
      <p class="muted">请输入您的组织账号与密码</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="请输入密码" />
        </el-form-item>
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submit">安全登录</el-button>
      </el-form>
      <p class="login-tip">连续 5 次失败将锁定账号 30 分钟</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { firstAllowedPath, login } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await login(form)
    ElMessage.success('登录成功')
    await router.replace(route.query.redirect || firstAllowedPath())
  } catch (error) {
    ElMessage.error(error.message)
  } finally { loading.value = false }
}
</script>
