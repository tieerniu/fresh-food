import request from '@/utils/request'

/**
 * 获取二维码列表
 */
export function getQrCodeList() {
    return request({
        url: '/qrcode/list',
        method: 'get'
    })
}

/**
 * 批量生成二维码
 * @param {Object} data - { batchId, count }
 */
export function generateQrCode(data) {
    return request({
        url: '/qrcode/generate',
        method: 'post',
        data
    })
}

/**
 * 更新二维码状态
 * @param {Object} data - { qrId, status }
 */
export function updateQrCodeStatus(data) {
    return request({
        url: '/qrcode/updateStatus',
        method: 'put',
        data
    })
}

/**
 * 删除二维码
 * @param {number} id - 二维码ID
 */
export function deleteQrCode(id) {
    return request({
        url: `/qrcode/delete/${id}`,
        method: 'delete'
    })
}
