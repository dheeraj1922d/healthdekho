import axios from 'axios'

// The React app and the API are served by the same Spring Boot jar, so "/api" always works.
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000, // Bedrock can take a few seconds
})

// Attach the login token to every request.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('healthdekho-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// If the token expired, drop it and tell the app to show the login page.
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
