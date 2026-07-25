<template>
  <div class="panel">
    <el-card>
      <template #header>目标文本来源</template>
      <el-radio-group v-model="sourceType" style="margin-bottom: 12px">
        <el-radio value="cache">从缓存文件选择</el-radio>
        <el-radio value="inline">直接粘贴文本</el-radio>
      </el-radio-group>

      <div v-if="sourceType === 'cache'">
        <el-select
          v-model="fileName"
          placeholder="选择已缓存的文件"
          filterable
          style="width: 100%"
          :loading="docsLoading"
        >
          <el-option
            v-for="d in docs"
            :key="d.id"
            :label="`${d.fileName} (${d.fileType}, ${d.textLength}字)`"
            :value="d.fileName"
          />
        </el-select>
        <el-button link type="primary" @click="loadDocs">刷新列表</el-button>
      </div>
      <div v-else>
        <el-input
          v-model="haystack"
          type="textarea"
          :rows="8"
          placeholder="粘贴目标文本"
        />
      </div>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>待判断文本（每行一条）</template>
      <el-input
        v-model="needlesText"
        type="textarea"
        :rows="8"
        placeholder="每行输入一个待判断的文本，例如：&#10;合同金额&#10;甲方签字&#10;2024年"
      />
      <div class="row" style="margin-top: 12px">
        <el-checkbox v-model="caseSensitive">区分大小写</el-checkbox>
        <el-button type="primary" :loading="loading" @click="doSearch">判断</el-button>
      </div>
    </el-card>

    <el-card v-if="result" style="margin-top: 16px">
      <template #header>
        <span>结果（来源：{{ result.source }}，目标文本 {{ result.haystackLength }} 字）</span>
      </template>
      <el-table :data="result.results" border size="small">
        <el-table-column label="#" type="index" width="50" />
        <el-table-column label="是否包含" width="110">
          <template #default="{ row }">
            <el-tag :type="row.found ? 'success' : 'info'" size="small">
              {{ row.found ? '是 ✓' : '否 ✗' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="待判断文本" prop="needle" min-width="220" show-overflow-tooltip />
        <el-table-column label="出现次数" prop="count" width="100" />
        <el-table-column label="首次位置" prop="firstIndex" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { searchMatch, listDocuments } from '../api'

const sourceType = ref('cache')
const fileName = ref('')
const haystack = ref('')
const needlesText = ref('')
const caseSensitive = ref(false)
const loading = ref(false)
const result = ref(null)

const docs = ref([])
const docsLoading = ref(false)

const loadDocs = async () => {
  docsLoading.value = true
  try {
    docs.value = await listDocuments()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    docsLoading.value = false
  }
}

onMounted(loadDocs)

const doSearch = async () => {
  const needles = needlesText.value
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  if (needles.length === 0) {
    ElMessage.warning('请输入至少一条待判断文本')
    return
  }
  if (sourceType.value === 'cache' && !fileName.value) {
    ElMessage.warning('请选择缓存文件')
    return
  }

  const payload = { needles, caseSensitive: caseSensitive.value }
  if (sourceType.value === 'cache') {
    payload.fileName = fileName.value
  } else {
    payload.haystack = haystack.value
  }

  loading.value = true
  try {
    result.value = await searchMatch(payload)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>
