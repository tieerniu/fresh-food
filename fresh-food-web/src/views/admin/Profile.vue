<template>
  <div class="profile-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><User /></el-icon>
          <span>个人中心</span>
        </div>
      </template>

      <el-row :gutter="40">
        <!-- 左侧：用户信息 -->
        <el-col :xs="24" :md="12">
          <div class="section-title">账户信息</div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">
              {{ userInfo.username }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="getRoleType(userInfo.role)">
                {{ getRoleLabel(userInfo.role) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="姓名">
              {{ userInfo.fullName || '未设置' }}
            </el-descriptions-item>
            <el-descriptions-item label="联系方式">
              {{ userInfo.contactInfo || '未设置' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-col>

        <!-- 右侧：修改密码 -->
        <el-col :xs="24" :md="12">
          <div class="section-title">修改密码</div>
          <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-width="100px"
          >
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  show-password
                  placeholder="请输入当前密码"
              />
            </el-form-item>

            <el-form-item label="新密码" prop="newPassword">
              <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  show-password
                  placeholder="请输入新密码（至少6位）"
              />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  placeholder="请再次输入新密码"
              />
            </el-form-item>

            <el-form-item>
              <el-button
                  type="primary"
                  :loading="loading"
                  @click="handleChangePassword"
              >
                确认修改
              </el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { fetchCurrentUserInfo, getLocalUserInfo, changePassword, clearLocalAuth } from '@/api/user'

const router = useRouter()
const passwordFormRef = ref(null)
const loading = ref(false)

// 用户信息
const userInfo = reactive({
  username: '',
  role: '',
  fullName: '',
  contactInfo: ''
})

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 验证确认密码
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 表单验证规则
const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 获取角色标签
const getRoleLabel = (role) => {
  const labels = {
    'admin': '管理员',
    'enterprise': '企业用户',
    'consumer': '消费者'
  }
  return labels[role] || role
}

// 获取角色标签类型
const getRoleType = (role) => {
  const types = {
    'admin': 'danger',
    'enterprise': 'primary',
    'consumer': 'success'
  }
  return types[role] || 'info'
}

// 加载用户信息
const loadUserInfo = async () => {
  try {
    const res = await fetchCurrentUserInfo()
    if (res.code === 200 && res.data) {
      userInfo.username = res.data.username || ''
      userInfo.role = res.data.role || ''
      userInfo.fullName = res.data.fullName || ''
      userInfo.contactInfo = res.data.contactInfo || ''
      localStorage.setItem('userInfo', JSON.stringify(res.data))
      return
    }
  } catch (error) {
    // 读取接口失败时回退到本地缓存，避免页面空白
  }

  const localInfo = getLocalUserInfo()
  if (localInfo) {
    userInfo.username = localInfo.username || ''
    userInfo.role = localInfo.role || ''
    userInfo.fullName = localInfo.fullName || ''
    userInfo.contactInfo = localInfo.contactInfo || ''
  }
}

// 修改密码
const handleChangePassword = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
      try {
        const res = await changePassword({
          oldPassword: passwordForm.oldPassword,
          newPassword: passwordForm.newPassword
        })

        if (res.code === 200) {
          // 显示成功提示
          ElMessage.success('密码修改成功，请使用新密码重新登录')

          // 立即清除登录凭证
          clearLocalAuth()

          // 延迟 1.5 秒后跳转到登录页
          setTimeout(() => {
          router.push('/login')
        }, 1500)
      } else {
        ElMessage.error(res.message || '修改失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    } finally {
      loading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  if (passwordFormRef.value) {
    passwordFormRef.value.resetFields()
  }
}

// 页面加载
onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.profile-container {
  padding: 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.section-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
}

@media (max-width: 768px) {
  .el-col {
    margin-bottom: 30px;
  }
}
</style>
