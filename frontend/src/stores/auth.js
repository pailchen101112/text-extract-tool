import { computed, reactive } from 'vue'
import { getProfile, login as loginApi } from '../api'

export const authState = reactive({
  token: localStorage.getItem('access_token') || '',
  user: null
})

export const isAuthenticated = computed(() => Boolean(authState.token))

export const hasPermission = (permission) => {
  const permissions = authState.user?.permissions || []
  return permissions.includes('*') || permissions.includes(permission)
}

export const menuTree = computed(() => {
  const source = (authState.user?.menus || []).filter(
    (item) => item.visible && item.type !== 'BUTTON'
  )
  const byParent = new Map()
  source.forEach((item) => {
    const key = item.parentId || 0
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push({ ...item, children: [] })
  })
  const build = (parentId) =>
    (byParent.get(parentId) || [])
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((item) => ({ ...item, children: build(item.id) }))
  return build(0)
})

export const login = async (credentials) => {
  const result = await loginApi(credentials)
  authState.token = result.token
  authState.user = result.user
  localStorage.setItem('access_token', result.token)
  return result.user
}

export const loadProfile = async () => {
  if (!authState.token) return null
  authState.user = await getProfile()
  return authState.user
}

export const logout = () => {
  authState.token = ''
  authState.user = null
  localStorage.removeItem('access_token')
}

export const firstAllowedPath = () => {
  const walk = (items) => {
    for (const item of items) {
      if (item.path) return item.path
      const child = walk(item.children || [])
      if (child) return child
    }
    return '/login'
  }
  return walk(menuTree.value)
}
