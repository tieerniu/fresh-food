import request from '@/utils/request'

/**
 * 获取溯源记录列表
 */
export function getRecordList() {
    return request({
        url: '/record/list',
        method: 'get'
    })
}

/**
 * 新增溯源记录
 * @param {Object} data - 溯源记录数据
 */
export function addRecord(data) {
    return request({
        url: '/record/add',
        method: 'post',
        data
    })
}

/**
 * 删除溯源记录
 * @param {number} id - 记录ID
 */
export function deleteRecord(id) {
    return request({
        url: `/record/delete/${id}`,
        method: 'delete'
    })
}

/**
 * 更新溯源记录
 * @param {Object} data - 溯源记录数据
 */
export function updateRecord(data) {
    return request({
        url: '/record/update',
        method: 'put',
        data
    })
}
