import request from '@/utils/request'

/**
 * 获取预警列表
 */
export function getWarningList() {
    return request({
        url: '/warning/list',
        method: 'get'
    })
}

/**
 * 标记预警为已处理
 * @param {number} id - 预警ID
 */
export function resolveWarning(id, actionType) {
    return request({
        url: `/warning/resolve/${id}`,
        method: 'put',
        params: { actionType }
    })
}

/**
 * 删除预警记录
 * @param {number} id - 预警ID
 */
export function deleteWarning(id) {
    return request({
        url: `/warning/delete/${id}`,
        method: 'delete'
    })
}

/**
 * 清空已处理预警
 */
export function clearHandledWarnings() {
    return request({
        url: '/warning/clear-handled',
        method: 'delete'
    })
}
