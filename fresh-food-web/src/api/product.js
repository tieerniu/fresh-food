import request from '@/utils/request'

/**
 * 获取产品批次列表
 */
export function getProductList() {
    return request({
        url: '/product/list',
        method: 'get'
    })
}

/**
 * 新增产品批次
 * @param {Object} data - 产品批次数据
 */
export function addProduct(data) {
    return request({
        url: '/product/add',
        method: 'post',
        data
    })
}

/**
 * 删除产品批次
 * @param {number} id - 批次ID
 */
export function deleteProduct(id) {
    return request({
        url: `/product/delete/${id}`,
        method: 'delete'
    })
}

/**
 * 更新产品批次
 * @param {Object} data - 产品批次数据
 */
export function updateProduct(data) {
    return request({
        url: '/product/update',
        method: 'put',
        data
    })
}
