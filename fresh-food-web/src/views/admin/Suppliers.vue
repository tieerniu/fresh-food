<template>
  <div class="suppliers-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon><OfficeBuilding /></el-icon>
            <span>合作基地管理</span>
          </div>
          <el-button type="primary" @click="openAddDialog">
            <el-icon><Plus /></el-icon>
            新增基地
          </el-button>
        </div>
      </template>

      <!-- 搜索/筛选区 -->
      <div class="filter-bar">
        <el-input
            v-model="searchName"
            placeholder="搜索基地名称..."
            clearable
            prefix-icon="Search"
            class="filter-input"
            @clear="currentPage = 1"
            @input="currentPage = 1"
        />
        <span class="filter-summary">共 <strong>{{ filteredList.length }}</strong> 条记录</span>
      </div>

      <!-- 表格 -->
      <el-table
          v-loading="loading"
          :data="pagedList"
          stripe
          style="width: 100%"
          class="flat-table"
      >
        <el-table-column prop="supplierName" label="基地名称" min-width="160">
          <template #default="{ row }">
            <span class="supplier-name-cell">{{ row.supplierName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="contactPerson" label="负责人" width="120" align="center" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" align="center" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />

        <el-table-column prop="qualificationLevel" label="资质评级" width="120" align="center">
          <template #default="{ row }">
            <el-tag
                v-if="row.qualificationLevel"
                :type="levelTagType(row.qualificationLevel)"
                size="small"
                round
            >
              {{ row.qualificationLevel }}
            </el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="企业账号" min-width="150" align="center">
          <template #default="{ row }">
            <span v-if="row.accountUsername" class="account-name">{{ row.accountUsername }}</span>
            <span v-else class="text-muted">未自动关联</span>
          </template>
        </el-table-column>

        <el-table-column label="账号状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.accountUsername" :type="row.accountEnabled === false ? 'info' : 'success'" size="small" round>
              {{ row.accountEnabled === false ? '已禁用' : '已启用' }}
            </el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="relatedBatchCount" label="关联批次" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.relatedBatchCount > 0 ? 'warning' : 'success'" size="small" round>
              {{ row.relatedBatchCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <div class="supplier-action-panel">
              <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
              <el-dropdown
                  trigger="click"
                  :disabled="!row.accountUsername"
                  @command="(command) => handleAccountCommand(row, command)"
              >
                <el-button type="warning" link class="account-action-btn">
                  账号管理
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="reset">重置密码</el-dropdown-item>
                    <el-dropdown-item command="toggle">
                      {{ row.accountEnabled === false ? '启用账号' : '禁用账号' }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页器 -->
      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="filteredList.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
            small
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑合作基地' : '新增合作基地'"
        width="560px"
        @close="resetForm"
    >
      <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="90px"
      >
        <el-form-item label="基地名称" prop="supplierName">
          <el-input v-model="formData.supplierName" placeholder="请输入基地名称" />
        </el-form-item>

        <el-form-item label="负责人" prop="contactPerson">
          <el-input v-model="formData.contactPerson" placeholder="请输入负责人姓名" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="地址" prop="address">
          <el-input v-model="formData.address" placeholder="请输入基地地址" />
        </el-form-item>

        <el-form-item label="资质评级" prop="qualificationLevel">
          <el-select v-model="formData.qualificationLevel" placeholder="请选择资质评级" style="width: 100%">
            <el-option label="A级 (优秀)" value="A" />
            <el-option label="B级 (良好)" value="B" />
            <el-option label="C级 (合格)" value="C" />
            <el-option label="D级 (待改进)" value="D" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ isEdit ? '保存' : '新增' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { OfficeBuilding, Plus, Search, ArrowDown } from '@element-plus/icons-vue'
import { getSupplierList, addSupplier, updateSupplier, deleteSupplier, resetSupplierPassword, updateSupplierAccountStatus } from '@/api/supplier'

// ========== 列表数据 ==========
const supplierList = ref([])
const loading = ref(false)

// ========== 搜索 & 分页 ==========
const searchName = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const filteredList = computed(() => {
  if (!searchName.value) return supplierList.value
  const keyword = searchName.value.toLowerCase()
  return supplierList.value.filter(
      item => item.supplierName && item.supplierName.toLowerCase().includes(keyword)
  )
})

const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

// ========== 对话框控制 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const initialFormData = {
  supplierId: null,
  supplierName: '',
  contactPerson: '',
  contactPhone: '',
  address: '',
  qualificationLevel: ''
}

const formData = reactive({ ...initialFormData })

const formRules = {
  supplierName: [
    { required: true, message: '请输入基地名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过100个字符', trigger: 'blur' }
  ],
  contactPerson: [
    { required: true, message: '请输入负责人', trigger: 'blur' }
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// ========== 辅助方法 ==========
const levelTagType = (level) => {
  const map = { 'A': 'success', 'B': 'primary', 'C': 'warning', 'D': 'danger' }
  return map[level] || 'info'
}

const copyAccountText = async (text) => {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success('账号信息已复制到剪贴板')
    } catch (e) {
      // ignore clipboard failure
    }
  }
}

// ========== 数据操作 ==========
const loadList = async () => {
  loading.value = true
  try {
    const res = await getSupplierList()
    if (res.code === 200) {
      supplierList.value = res.data || []
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  Object.assign(formData, row)
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(formData, initialFormData)
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      let res
      if (isEdit.value) {
        res = await updateSupplier(formData)
      } else {
        res = await addSupplier(formData)
      }

      if (res.code === 200) {
        if (!isEdit.value && res.data && res.data.username) {
          const clipText = `账号：${res.data.username}，密码：${res.data.password}`;
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(clipText).then(() => {
              ElMessage.success('账号密码已自动复制到剪贴板');
            }).catch(() => {});
          }

          ElMessageBox.alert(
              `基地添加成功！<br/>已自动生成专属登录账号：<br/><b>账号：${res.data.username}</b><br/><b>密码：${res.data.password}</b><br/>请务必发给基地负责人，并提醒首次登录后尽快修改密码。`,
              '开户成功',
              { dangerouslyUseHTMLString: true, confirmButtonText: '我知道了' }
          ).then(() => {
            dialogVisible.value = false
            loadList()
          })
        } else {
          ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
          dialogVisible.value = false
          loadList()
        }
      } else {
        ElMessage.error(res.message || '操作失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除合作基地 "${row.supplierName}" 吗？如果该基地账号下仍有关联批次，系统会阻止删除。`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteSupplier(row.supplierId)
      if (res.code === 200) {
        ElMessage.success(res.message || '删除成功')
        loadList()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

const handleResetPassword = (row) => {
  if (!row.accountUsername) {
    ElMessage.warning('该基地暂无可重置的企业账号')
    return
  }

  ElMessageBox.confirm(`确定要重置基地账号 "${row.accountUsername}" 的密码吗？重置后会强制该基地重新登录。`, '重置密码确认', {
    confirmButtonText: '确定重置',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await resetSupplierPassword(row.supplierId)
      if (res.code === 200 && res.data) {
        const clipText = `账号：${res.data.username}，新密码：${res.data.password}`
        await copyAccountText(clipText)
        await ElMessageBox.alert(
            `密码已重置。<br/><b>账号：${res.data.username}</b><br/><b>新密码：${res.data.password}</b><br/>请尽快通知基地负责人。`,
            '重置成功',
            { dangerouslyUseHTMLString: true, confirmButtonText: '我知道了' }
        )
        loadList()
      } else {
        ElMessage.error(res.message || '重置失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

const handleAccountCommand = (row, command) => {
  if (command === 'reset') {
    handleResetPassword(row)
    return
  }
  if (command === 'toggle') {
    handleToggleAccountStatus(row)
  }
}

const handleToggleAccountStatus = (row) => {
  if (!row.accountUsername) {
    ElMessage.warning('该基地暂无可管理的企业账号')
    return
  }

  const targetEnabled = row.accountEnabled === false
  const actionText = targetEnabled ? '启用' : '禁用'
  const description = targetEnabled
      ? `确定要启用基地账号 "${row.accountUsername}" 吗？启用后该基地可重新登录系统。`
      : `确定要禁用基地账号 "${row.accountUsername}" 吗？禁用后会立即强制退出该基地当前登录。`

  ElMessageBox.confirm(description, `${actionText}账号确认`, {
    confirmButtonText: `确定${actionText}`,
    cancelButtonText: '取消',
    type: targetEnabled ? 'info' : 'warning'
  }).then(async () => {
    try {
      const res = await updateSupplierAccountStatus(row.supplierId, targetEnabled)
      if (res.code === 200) {
        ElMessage.success(res.message || `${actionText}成功`)
        loadList()
      } else {
        ElMessage.error(res.message || `${actionText}失败`)
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.suppliers-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

/* 搜索栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-input {
  width: 280px;
}

.filter-summary {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}

.filter-summary strong {
  color: #409eff;
  font-weight: 600;
}

/* 扁平化表格 */
.flat-table :deep(th.el-table__cell) {
  background-color: #fafafa;
  color: #909399;
  font-weight: 500;
  font-size: 13px;
  border-bottom: 1px solid #ebeef5 !important;
}

.flat-table :deep(td.el-table__cell) {
  border-bottom: 1px solid #f2f3f5 !important;
}

.flat-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

/* 名称加粗 */
.supplier-name-cell {
  font-weight: 600;
  color: #1d2129;
  font-size: 14px;
}

.account-name {
  font-family: Consolas, 'Courier New', monospace;
  color: #606266;
  font-size: 13px;
}

.text-muted {
  color: #c0c4cc;
}

/* 操作按钮 */
.supplier-action-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.account-action-btn {
  padding-inline: 0;
}

/* 分页器 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 4px;
}
</style>
