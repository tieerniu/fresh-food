import request from '@/utils/request'

export function getApplicationList(params = {}) {
    return request({
        url: '/application/list',
        method: 'get',
        params
    })
}

export function submitApplication(data) {
    return request({
        url: '/application/submit',
        method: 'post',
        data
    })
}

export function resubmitApplication(data) {
    return request({
        url: '/application/resubmit',
        method: 'put',
        data
    })
}

export function approveApplication(id, data = {}) {
    return request({
        url: `/application/approve/${id}`,
        method: 'post',
        data
    })
}

export function rejectApplication(id, data) {
    return request({
        url: `/application/reject/${id}`,
        method: 'post',
        data
    })
}

export function denyApplication(id, data) {
    return request({
        url: `/application/deny/${id}`,
        method: 'post',
        data
    })
}
