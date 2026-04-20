import request from '@/utils/request'

/**
 * 获取质检记录列表
 */
export function getInspectionList() {
    return request({
        url: '/inspection/list',
        method: 'get'
    })
}

/**
 * 新增质检记录
 */
export function addInspection(data) {
    return request({
        url: '/inspection/add',
        method: 'post',
        data
    })
}

/**
 * 更新质检记录
 */
export function updateInspection(data) {
    return request({
        url: '/inspection/update',
        method: 'put',
        data
    })
}

/**
 * 删除质检记录
 */
export function deleteInspection(id) {
    return request({
        url: `/inspection/delete/${id}`,
        method: 'delete'
    })
}
