import request from '../utils/request'

const TRACE_DEVICE_KEY = 'trace_h5_device_id'

function generateDeviceId() {
    if (typeof window !== 'undefined' && window.crypto && window.crypto.randomUUID) {
        return window.crypto.randomUUID()
    }
    return `h5-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

function getTraceDeviceId() {
    if (typeof window === 'undefined' || !window.localStorage) {
        return generateDeviceId()
    }

    let deviceId = window.localStorage.getItem(TRACE_DEVICE_KEY)
    if (!deviceId) {
        deviceId = generateDeviceId()
        window.localStorage.setItem(TRACE_DEVICE_KEY, deviceId)
    }
    return deviceId
}

/**
 * 查询溯源信息
 * @param {string} code - 防伪码
 * @returns {Promise} 溯源数据
 */
export function queryTrace(code) {
    return request({
        url: `/trace/query/${code}`,
        method: 'get',
        headers: {
            'X-Trace-Device-Id': getTraceDeviceId(),
            'X-Scan-Source': 'H5'
        }
    })
}

/**
 * 获取移动端公开入口配置
 * @returns {Promise}
 */
export function getTracePublicConfig() {
    return request({
        url: '/trace/public-config',
        method: 'get'
    })
}
