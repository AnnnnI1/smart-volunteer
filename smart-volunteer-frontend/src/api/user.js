import request from '@/utils/request'
// 登录
export function userLogin(username, password) {
    return request({
        url: '/user/login',
        method: 'post',
        headers: {
            'Content-Type': 'application/json',
            isToken: false
          },
        data: { username, password }
    })
}

export function userRegister(username, nickname, password, role) {
    return request({
        url: '/user/register',
        method: 'post',
        headers: {
            'Content-Type': 'application/json'
        },
        data: {
            username,
            nickname,
            password,
            role
        }
    })
}

//更新用户信息
export function userUpdateInfo(userInfo) {
    return request({
        url: '/user/updateInfo',
        method: 'post',
        headers: {
            'Content-Type': 'application/json',
            isToken: true
        },
        data: userInfo
    })
}

//修改密码
export function changePassword(oldPassword, newPassword) {
    return request({
        url: '/user/changePassword',
        method: 'post',
        headers: {
            'Content-Type': 'application/json',
            isToken: true
        },
        data: {oldPassword, newPassword}
    })
}

// 上传头像（multipart/form-data）
export function uploadAvatar(formData) {
    return request({
        url: '/user/avatar',
        method: 'post',
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        data: formData
    })
}

// 管理员获取用户列表（applyOnly=true 只看申请升级者）
export function adminListUsers(params) {
    return request({ url: '/user/admin/list', method: 'get', params })
}

// 管理员修改用户角色
export function adminUpdateUserRole(id, role) {
    return request({ url: `/user/admin/${id}/role`, method: 'put', params: { role } })
}

// 志愿者申请成为组织者（新增申请理由字段）
export function applyOrganizer(params) {
    return request({ url: '/user/apply-organizer', method: 'post', data: params })
}

// 管理员审核组织者申请
export function auditOrganizer(id, auditStatus, rejectReason) {
    return request({ url: `/user/admin/${id}/audit-organizer`, method: 'put', params: { auditStatus, rejectReason } })
}

// 获取当前用户最新信息（从 DB 读取，不依赖 JWT）
export function getUserMe() {
    return request({ url: '/user/me', method: 'get' })
}

// export function logout() {
//     return request({
//         url: '/logout',
//         method: 'post'
//     })
// }

// export function getUserInfo(userId) {
//     return request ({
//         url: '/user/userInfo',
//         method: 'get',
//         params: {"userId":userId}
//     })
// }


// export function savaUserInfo(userinfo) {
//     return request({
//         url: '/user/userInfo',
//         method: 'put',
//         data: userinfo
//     })
// }