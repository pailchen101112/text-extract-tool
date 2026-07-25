<template>
  <el-container class="app">
    <el-header class="header">
      <h1>文本提取与检索工具</h1>
      <span class="sub">Java8 + SpringBoot + Tika + MySQL</span>
    </el-header>
    <el-main>
      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane label="文本提取" name="extract">
          <ExtractPanel />
        </el-tab-pane>
        <el-tab-pane label="文本检索" name="search">
          <SearchPanel />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import ExtractPanel from './components/ExtractPanel.vue'
import SearchPanel from './components/SearchPanel.vue'

const activeTab = ref('extract')
</script>
