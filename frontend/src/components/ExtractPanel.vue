<template>
  <div class="panel">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>方式一：服务器文件路径</template>
          <el-input
            v-model="filePath"
            placeholder="输入服务器本地文件绝对路径, 如 /Users/xx/test.pdf"
          />
          <div class="row">
            <el-checkbox v-model="useCache">使用缓存（按文件名命中）</el-checkbox>
            <el-button type="primary" :loading="loading" @click="doExtractPath">提取</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>方式二：上传文件流</template>
          <el-upload
            drag
            :auto-upload="false"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :limit="1"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或<em>点击选择</em></div>
            <template #tip>
              <div class="tip">支持 pdf / doc / docx / txt / html / rtf</div>
            </template>
          </el-upload>
          <div class="row">
            <el-checkbox v-model="useCache">使用缓存</el-checkbox>
            <el-button type="primary" :loading="loading" :disabled="!file" @click="doExtractUpload">
              上传并提取
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="result" class="result">
      <template #header>
        <span>提取结果</span>
        <el-tag v-if="result.cacheHit" type="success" size="small" style="margin-left: 8px">
          缓存命中
        </el-tag>
        <el-tag v-else type="warning" size="small" style="margin-left: 8px">新提取</el-tag>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="文件名">{{ result.fileName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ result.fileType }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatSize(result.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="字符数">{{ result.textLength }}</el-descriptions-item>
        <el-descriptions-item label="SHA-256" :span="2">{{ result.contentHash }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="result.truncated"
        type="warning"
        :closable="false"
        style="margin: 10px 0"
      >
        文本超过上限已截断，仅显示部分内容。
      </el-alert>
      <div class="text-actions">
        <el-button text type="primary" @click="copyText">复制全文</el-button>
      </div>
      <el-input
        :model-value="result.extractedText"
        type="textarea"
        :rows="16"
        readonly
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { extractByPath, extractByUpload } from '../api'

const filePath = ref('')
const useCache = ref(true)
const file = ref(null)
const loading = ref(false)
const result = ref(null)

const onFileChange = (uploadFile) => {
  file.value = uploadFile.raw
}
const onFileRemove = () => {
  file.value = null
}

const doExtractPath = async () => {
  if (!filePath.value.trim()) {
    ElMessage.warning('请输入文件路径')
    return
  }
  loading.value = true
  try {
    result.value = await extractByPath(filePath.value.trim(), useCache.value)
    ElMessage.success(result.value.cacheHit ? '缓存命中' : '提取成功')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const doExtractUpload = async () => {
  if (!file.value) {
    ElMessage.warning('请选择文件')
    return
  }
  loading.value = true
  try {
    result.value = await extractByUpload(file.value, useCache.value)
    ElMessage.success(result.value.cacheHit ? '缓存命中' : '提取成功')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const formatSize = (bytes) => {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

const copyText = async () => {
  if (!result.value?.extractedText) return
  try {
    await navigator.clipboard.writeText(result.value.extractedText)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>
