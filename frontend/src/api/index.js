import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 120000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 统一解包: 直接返回 res.data; 错误提取为 Error
api.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.error || err.message || '请求失败'
    const error = new Error(msg)
    error.status = err.response?.status
    error.code = err.response?.data?.code
    if (error.status === 401) {
      localStorage.removeItem('access_token')
      if (window.location.pathname !== '/login') window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

/** 按服务器文件路径提取 */
export const extractByPath = (filePath, useCache = true) =>
  api.post('/extract/path', { filePath, useCache })

/** 按上传文件流提取 */
export const extractByUpload = (file, useCache = true) => {
  const form = new FormData()
  form.append('file', file)
  form.append('useCache', useCache)
  return api.post('/extract/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 判断多个文本是否在目标文本中 */
export const searchMatch = (payload) => api.post('/search/match', payload)

/** 列出已缓存文档 */
export const listDocuments = () => api.get('/documents')

/** 查看某缓存文档详情 */
export const getDocument = (id) => api.get(`/documents/${id}`)

export const login = (payload) => api.post('/auth/login', payload)
export const getProfile = () => api.get('/auth/me')
export const changePassword = (payload) => api.put('/auth/change-password', payload)

const resource = (name) => ({
  list: () => api.get(`/admin/${name}`),
  create: (payload) => api.post(`/admin/${name}`, payload),
  update: (id, payload) => api.put(`/admin/${name}/${id}`, payload),
  remove: (id) => api.delete(`/admin/${name}/${id}`)
})

export const companyApi = resource('companies')
export const positionApi = resource('positions')
export const menuApi = resource('menus')
export const roleApi = resource('roles')
export const userApi = {
  ...resource('users'),
  unlock: (id) => api.post(`/admin/users/${id}/unlock`)
}
