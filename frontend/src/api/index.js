import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 120000
})

// 统一解包: 直接返回 res.data; 错误提取为 Error
api.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.error || err.message || '请求失败'
    return Promise.reject(new Error(msg))
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
