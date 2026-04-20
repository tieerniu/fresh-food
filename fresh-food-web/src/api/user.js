import request from '@/utils/request'

/**
 * 用户登录
 * @param {Object} data - { username, password }
 */
export function login(data) {
    return request({
        url: '/login',
        method: 'post',
        data
    })
}

/**
 * 修改密码
 * @param {Object} data - { oldPassword, newPassword }
 */
export function changePassword(data) {
    return request({
        url: '/user/password',
        method: 'post',
        data
    })
}

/**
 * 获取当前登录用户信息
 */
export function fetchCurrentUserInfo() {
    return request({
        url: '/user/me',
        method: 'get'
    })
}

/**
 * 获取当前登录用户信息（从 localStorage）
 */
export function getLocalUserInfo() {
    const userInfo = localStorage.getItem('userInfo')
    return userInfo ? JSON.parse(userInfo) : null
}

/**
 * 获取 enterprise 用户选项（管理员用于给批次分配所属企业）
 */
export function getEnterpriseOptions() {
    return request({
        url: '/user/enterprise-options',
        method: 'get'
    })
}

/**
 * 清理本地登录态
 */
export function clearLocalAuth() {
    localStorage.removeItem('userInfo')
    localStorage.removeItem('token')
}

/**
 * 退出登录
 */
export async function logout() {
    try {
        await request({
            url: '/user/logout',
            method: 'post'
        })
    } catch (error) {
        // 即使后端退出失败，也继续清理本地状态
    } finally {
        clearLocalAuth()
    }
}
