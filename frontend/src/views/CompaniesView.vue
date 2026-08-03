<template>
  <section class="page-card">
    <div class="page-heading"><div><p class="eyebrow">ORGANIZATION</p><h2>公司管理</h2><p>维护组织层级与账号归属边界。</p></div><el-button v-if="canWrite" type="primary" @click="open()">新增公司</el-button></div>
    <el-table :data="rows" v-loading="loading" row-key="id">
      <el-table-column prop="code" label="公司编码" min-width="150" />
      <el-table-column prop="name" label="公司名称" min-width="180" />
      <el-table-column label="上级公司" min-width="160"><template #default="{ row }">{{ companyName(row.parentId) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
  </section>
  <el-dialog v-model="visible" :title="form.id ? '编辑公司' : '新增公司'" width="520px">
    <el-form label-position="top"><el-form-item label="公司编码" required><el-input v-model="form.code" /></el-form-item><el-form-item label="公司名称" required><el-input v-model="form.name" /></el-form-item><el-form-item label="上级公司"><el-select v-model="form.parentId" clearable style="width:100%"><el-option v-for="item in parentOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item></el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { companyApi } from '../api'
import { hasPermission } from '../stores/auth'
const rows=ref([]),loading=ref(false),visible=ref(false),saving=ref(false)
const form=reactive({id:null,code:'',name:'',parentId:null,enabled:true})
const canWrite=computed(()=>hasPermission('system:company:write'))
const parentOptions=computed(()=>rows.value.filter(i=>i.id!==form.id))
const companyName=(id)=>id ? rows.value.find(i=>i.id===id)?.name || '-' : '—'
const load=async()=>{loading.value=true;try{rows.value=await companyApi.list()}catch(e){ElMessage.error(e.message)}finally{loading.value=false}}
const open=(row)=>{Object.assign(form,row?{...row,enabled:row.status==='ENABLED'}:{id:null,code:'',name:'',parentId:null,enabled:true});visible.value=true}
const save=async()=>{if(!form.code.trim()||!form.name.trim())return ElMessage.warning('请填写公司编码和名称');saving.value=true;try{const payload={code:form.code,name:form.name,parentId:form.parentId,status:form.enabled?'ENABLED':'DISABLED'};form.id?await companyApi.update(form.id,payload):await companyApi.create(payload);visible.value=false;ElMessage.success('保存成功');await load()}catch(e){ElMessage.error(e.message)}finally{saving.value=false}}
const remove=async(row)=>{try{await ElMessageBox.confirm(`确认删除“${row.name}”？`,'删除确认',{type:'warning'});await companyApi.remove(row.id);ElMessage.success('已删除');await load()}catch(e){if(e!=='cancel')ElMessage.error(e.message)}}
onMounted(load)
</script>
