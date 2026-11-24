import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('healthdekho-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      localStorage.removeItem('healthdekho-token')
      window.dispatchEvent(new Event('healthdekho:session-expired'))
    }
    return Promise.reject(error)
  },
)

export function messageFromError(error) {
  return error.response?.data?.message || error.message || 'Something went wrong. Please try again.'
}

export default api
