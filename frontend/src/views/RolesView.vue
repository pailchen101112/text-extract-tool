<template>
  <section class="page-card"><div class="page-heading"><div><p class="eyebrow">ACCESS CONTROL</p><h2>角色管理</h2><p>将菜单与操作权限组合为职责角色。</p></div><el-button v-if="canWrite" type="primary" @click="open()">新增角色</el-button></div>
    <el-table :data="rows" v-loading="loading"><el-table-column prop="code" label="角色编码" min-width="160"/><el-table-column prop="name" label="角色名称" min-width="160"/><el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip/><el-table-column label="授权项" width="100"><template #default="{row}">{{row.menuIds.length}}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='ENABLED'?'success':'info'">{{row.status==='ENABLED'?'启用':'停用'}}</el-tag></template></el-table-column><el-table-column v-if="canWrite" label="操作" width="150"><template #default="{row}"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" :disabled="row.code==='SUPER_ADMIN'" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
  </section>
  <el-dialog v-model="visible" :title="form.id?'编辑角色':'新增角色'" width="620px"><el-form label-position="top"><el-row :gutter="16"><el-col :span="12"><el-form-item label="角色编码" required><el-input v-model="form.code" :disabled="form.code==='SUPER_ADMIN'"/></el-form-item></el-col><el-col :span="12"><el-form-item label="角色名称" required><el-input v-model="form.name"/></el-form-item></el-col></el-row><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2"/></el-form-item><el-form-item label="菜单与权限"><div class="permission-tree"><el-tree ref="treeRef" :data="menuTreeData" node-key="id" show-checkbox default-expand-all :props="{label:'name',children:'children'}"><template #default="{data}"><span>{{data.name}} <small>{{data.permission||data.type}}</small></span></template></el-tree></div></el-form-item><el-form-item label="状态"><el-switch v-model="form.enabled"/></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
</template>
<script setup>
import { computed,nextTick,onMounted,reactive,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { menuApi,roleApi } from '../api'
import { hasPermission } from '../stores/auth'
const rows=ref([]),menus=ref([]),loading=ref(false),visible=ref(false),saving=ref(false),treeRef=ref()
const form=reactive({id:null,code:'',name:'',description:'',enabled:true})
const canWrite=computed(()=>hasPermission('system:role:write'))
const menuTreeData=computed(()=>{const map=new Map(menus.value.map(i=>[i.id,{...i,children:[]}])) ,roots=[];for(const item of map.values()){if(item.parentId&&map.has(item.parentId))map.get(item.parentId).children.push(item);else roots.push(item)}return roots})
const load=async()=>{loading.value=true;try{[rows.value,menus.value]=await Promise.all([roleApi.list(),menuApi.list()])}catch(e){ElMessage.error(e.message)}finally{loading.value=false}}
const open=async row=>{Object.assign(form,row?{...row,enabled:row.status==='ENABLED'}:{id:null,code:'',name:'',description:'',enabled:true});visible.value=true;await nextTick();treeRef.value.setCheckedKeys(row?.menuIds||[])}
const save=async()=>{if(!form.code.trim()||!form.name.trim())return ElMessage.warning('请填写角色编码和名称');saving.value=true;try{const ids=[...new Set([...treeRef.value.getCheckedKeys(),...treeRef.value.getHalfCheckedKeys()])];const p={code:form.code,name:form.name,description:form.description,status:form.enabled?'ENABLED':'DISABLED',menuIds:ids};form.id?await roleApi.update(form.id,p):await roleApi.create(p);visible.value=false;ElMessage.success('保存成功');await load()}catch(e){ElMessage.error(e.message)}finally{saving.value=false}}
const remove=async row=>{try{await ElMessageBox.confirm(`确认删除角色“${row.name}”？`,'删除确认',{type:'warning'});await roleApi.remove(row.id);ElMessage.success('已删除');await load()}catch(e){if(e!=='cancel')ElMessage.error(e.message)}}
onMounted(load)
</script>
