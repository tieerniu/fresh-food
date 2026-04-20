import request from '@/utils/request'

/**
 * 获取供应商列表
 */
export function getSupplierList() {
    return request({
        url: '/supplier/list',
        method: 'get'
    })
}

/**
 * 新增供应商
 * @param {Object} data - 供应商数据
 */
export function addSupplier(data) {
    return request({
        url: '/supplier/add',
        method: 'post',
        data
    })
}

/**
 * 更新供应商
 * @param {Object} data - 供应商数据
 */
export function updateSupplier(data) {
    return request({
        url: '/supplier/update',
        method: 'put',
        data
    })
}

/**
 * 删除供应商
 * @param {number} id - 供应商ID
 */
export function deleteSupplier(id) {
    return request({
        url: `/supplier/delete/${id}`,
        method: 'delete'
    })
}

/**
 * 重置基地账号密码
 * @param {number} id - 供应商ID
 */
export function resetSupplierPassword(id) {
    return request({
        url: `/supplier/reset-password/${id}`,
        method: 'post'
    })
}

/**
 * 启用/禁用基地账号
 * @param {number} id - 供应商ID
 * @param {boolean} enabled - 是否启用
 */
export function updateSupplierAccountStatus(id, enabled) {
    return request({
        url: `/supplier/account-status/${id}`,
        method: 'put',
        data: { enabled }
    })
}
