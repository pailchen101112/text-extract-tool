<template>
  <section class="page-card">
    <div class="page-heading"><div><p class="eyebrow">ORGANIZATION</p><h2>岗位管理</h2><p>维护岗位与所属公司的绑定关系。</p></div><el-button v-if="canWrite" type="primary" @click="open()">新增岗位</el-button></div>
    <el-table :data="rows" v-loading="loading"><el-table-column prop="code" label="岗位编码" min-width="150"/><el-table-column prop="name" label="岗位名称" min-width="180"/><el-table-column label="所属公司" min-width="180"><template #default="{row}">{{companyName(row.companyId)}}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='ENABLED'?'success':'info'">{{row.status==='ENABLED'?'启用':'停用'}}</el-tag></template></el-table-column><el-table-column v-if="canWrite" label="操作" width="150"><template #default="{row}"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
  </section>
  <el-dialog v-model="visible" :title="form.id?'编辑岗位':'新增岗位'" width="520px"><el-form label-position="top"><el-form-item label="所属公司" required><el-select v-model="form.companyId" style="width:100%"><el-option v-for="item in companies" :key="item.id" :label="item.name" :value="item.id"/></el-select></el-form-item><el-form-item label="岗位编码" required><el-input v-model="form.code"/></el-form-item><el-form-item label="岗位名称" required><el-input v-model="form.name"/></el-form-item><el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用"/></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
</template>
<script setup>
import { computed,onMounted,reactive,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { companyApi,positionApi } from '../api'
import { hasPermission } from '../stores/auth'
const rows=ref([]),companies=ref([]),loading=ref(false),visible=ref(false),saving=ref(false)
const form=reactive({id:null,companyId:null,code:'',name:'',enabled:true})
const canWrite=computed(()=>hasPermission('system:position:write'))
const companyName=id=>companies.value.find(i=>i.id===id)?.name||'-'
const load=async()=>{loading.value=true;try{[rows.value,companies.value]=await Promise.all([positionApi.list(),companyApi.list()])}catch(e){ElMessage.error(e.message)}finally{loading.value=false}}
const open=row=>{Object.assign(form,row?{...row,enabled:row.status==='ENABLED'}:{id:null,companyId:null,code:'',name:'',enabled:true});visible.value=true}
const save=async()=>{if(!form.companyId||!form.code.trim()||!form.name.trim())return ElMessage.warning('请完整填写必填项');saving.value=true;try{const p={companyId:form.companyId,code:form.code,name:form.name,status:form.enabled?'ENABLED':'DISABLED'};form.id?await positionApi.update(form.id,p):await positionApi.create(p);visible.value=false;ElMessage.success('保存成功');await load()}catch(e){ElMessage.error(e.message)}finally{saving.value=false}}
const remove=async row=>{try{await ElMessageBox.confirm(`确认删除“${row.name}”？`,'删除确认',{type:'warning'});await positionApi.remove(row.id);ElMessage.success('已删除');await load()}catch(e){if(e!=='cancel')ElMessage.error(e.message)}}
onMounted(load)
</script>
