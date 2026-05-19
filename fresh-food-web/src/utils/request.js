import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE || '/api',
    timeout: 5000
})

// 请求拦截器：携带服务端登录 Token
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token
        }

        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器
request.interceptors.response.use(
    response => {
        return response.data
    },
    error => {
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('userInfo')
            localStorage.removeItem('token')
            if (window.location.pathname.startsWith('/admin')) {
                window.location.href = '/login'
                return Promise.reject(error)
            }
        }
        ElMessage.error(error.message || '请求失败')
        return Promise.reject(error)
    }
)

export default request
