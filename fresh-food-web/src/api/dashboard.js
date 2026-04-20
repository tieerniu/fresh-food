import request from '@/utils/request'

/**
 * 获取控制台统计数据
 */
export function getDashboardStats() {
    return request({
        url: '/dashboard/stats',
        method: 'get'
    })
}

/**
 * 获取图表数据（扫码趋势 + 二维码状态分布）
 */
export function getChartData() {
    return request({
        url: '/dashboard/chart-data',
        method: 'get'
    })
}
