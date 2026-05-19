<template>
  <div class="products-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon><Box /></el-icon>
            <span>正式产品批次</span>
          </div>
          <el-tag type="success" round>审核通过后自动进入</el-tag>
        </div>
      </template>

      <!-- 搜索/筛选区 -->
      <div class="filter-bar">
        <el-input
            v-model="searchName"
            placeholder="搜索产品名称..."
            clearable
            prefix-icon="Search"
            class="filter-input"
            @clear="currentPage = 1"
            @input="currentPage = 1"
        />
        <span class="filter-summary">共 <strong>{{ filteredList.length }}</strong> 条记录</span>
      </div>

      <!-- 产品列表表格 -->
      <el-table
          v-loading="loading"
          :data="pagedList"
          stripe
          size="large"
          style="width: 100%"
          class="flat-table"
      >
        <el-table-column label="产品批次" min-width="320">
          <template #default="{ row }">
            <div class="batch-product-cell">
              <el-image
                  v-if="row.imageUrl"
                  :src="row.imageUrl"
                  fit="cover"
                  class="product-thumb"
                  :preview-src-list="[row.imageUrl]"
                  preview-teleported
                  hide-on-click-modal
              >
                <template #error>
                  <div class="image-placeholder">
                    <el-icon :size="22" color="#c0c4cc"><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
              <div v-else class="image-placeholder">
                <el-icon :size="22" color="#c0c4cc"><Picture /></el-icon>
              </div>

              <div class="batch-product-main">
                <div class="product-name-cell">{{ row.productName }}</div>
                <el-tooltip :content="row.batchCode" placement="top" :show-after="400">
                  <span class="batch-code-chip">{{ formatBatchCode(row.batchCode) }}</span>
                </el-tooltip>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="所属基地" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.manufacturerName" type="" size="small" round>
              {{ row.manufacturerName }}
            </el-tag>
            <span v-else class="muted-text">未关联基地</span>
          </template>
        </el-table-column>

        <el-table-column label="产地" min-width="110">
          <template #default="{ row }">
            <span class="origin-text">{{ row.origin || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="生产信息" width="210" align="center">
          <template #default="{ row }">
            <div class="production-cell">
              <span class="date-cell">{{ formatDate(row.productionDate) }}</span>
              <el-tag type="success" size="small" round>
                保质期 {{ row.shelfLifeDays }} 天
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="发码情况" width="180" align="center">
          <template #default="{ row }">
            <div class="issue-cell">
              <div class="issue-count">
                <span>{{ row.generatedQrCount || 0 }}</span>
                <span class="issue-divider">/</span>
                <span>{{ row.batchQuantity || 0 }}</span>
              </div>
              <el-progress
                  :percentage="getIssuePercent(row)"
                  :show-text="false"
                  :stroke-width="6"
                  :color="getIssuePercent(row) >= 100 ? '#f56c6c' : '#409eff'"
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="180">
          <template #default="{ row }">
            <span class="description-cell">{{ row.description || '暂无描述' }}</span>
          </template>
        </el-table-column>

        <el-table-column v-if="isAdmin" label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" link @click="openEditDialog(row)">
                编辑
              </el-button>
              <el-button type="danger" link @click="handleDelete(row)">
                删除
              </el-button>
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

    <!-- 编辑对话框：正式批次只能由审批通过自动生成 -->
    <el-dialog
        v-model="dialogVisible"
        title="编辑产品批次"
        width="600px"
        @close="resetForm"
    >
      <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="100px"
      >
        <el-form-item label="批次号" prop="batchCode">
          <el-input v-model="formData.batchCode" placeholder="系统自动生成" disabled />
        </el-form-item>

        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="formData.productName" placeholder="请输入产品名称" />
        </el-form-item>

        <el-form-item label="产地" prop="origin">
          <el-input v-model="formData.origin" placeholder="请输入产地" />
        </el-form-item>

        <el-form-item label="生产日期" prop="productionDate">
          <el-date-picker
              v-model="formData.productionDate"
              type="date"
              placeholder="选择生产日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
          />
        </el-form-item>

        <el-form-item v-if="isAdmin" label="所属企业" prop="manufacturerId">
          <el-select
              v-model="formData.manufacturerId"
              placeholder="请选择所属企业"
              filterable
              style="width: 100%"
          >
            <el-option
                v-for="item in enterpriseOptions"
                :key="item.userId"
                :label="`${item.fullName || item.username} (${item.username})`"
                :value="item.userId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="保质期(天)" prop="shelfLifeDays">
          <el-input-number
              v-model="formData.shelfLifeDays"
              :min="1"
              :max="9999"
              placeholder="请输入保质期天数"
              style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="批次数量" prop="batchQuantity">
          <el-input-number
              v-model="formData.batchQuantity"
              :min="1"
              :max="999999"
              placeholder="请输入本批次计划数量"
              style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="图片链接" prop="imageUrl">
          <el-input
              v-model="formData.imageUrl"
              placeholder="请去百度搜图，右键'复制图片地址'粘贴到这里 (选填)"
              clearable
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
              v-model="formData.description"
              type="textarea"
              :rows="3"
              placeholder="请输入产品描述"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { Box, Search, Picture } from '@element-plus/icons-vue'
import { getProductList, updateProduct, deleteProduct } from '@/api/product'
import { getEnterpriseOptions, getLocalUserInfo } from '@/api/user'

// 列表数据（后端全量）
const productList = ref([])
const loading = ref(false)
const enterpriseOptions = ref([])

const currentUser = getLocalUserInfo()
const isAdmin = computed(() => currentUser && currentUser.role === 'admin')

// ========== 搜索 & 分页 ==========
const searchName = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

// 按产品名称模糊过滤
const filteredList = computed(() => {
  if (!searchName.value) return productList.value
  const keyword = searchName.value.toLowerCase()
  return productList.value.filter(
      item => item.productName && item.productName.toLowerCase().includes(keyword)
  )
})

// 当前页数据
const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

// 对话框控制
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

// 表单数据
const initialFormData = {
  batchId: null,
  batchCode: '',
  productName: '',
  origin: '',
  productionDate: '',
  manufacturerId: null,
  shelfLifeDays: 30,
  batchQuantity: 100,
  description: '',
  imageUrl: ''
}

const formData = reactive({ ...initialFormData })

// 表单验证规则
const formRules = {
  batchCode: [
    { required: true, message: '请输入批次号', trigger: 'blur' },
    { max: 50, message: '批次号不能超过50个字符', trigger: 'blur' }
  ],
  productName: [
    { required: true, message: '请输入产品名称', trigger: 'blur' },
    { max: 100, message: '产品名称不能超过100个字符', trigger: 'blur' }
  ],
  origin: [
    { required: true, message: '请输入产地', trigger: 'blur' }
  ],
  productionDate: [
    { required: true, message: '请选择生产日期', trigger: 'change' }
  ],
  manufacturerId: [
    { required: isAdmin.value, message: '请选择所属企业', trigger: 'change' }
  ],
  shelfLifeDays: [
    { required: true, message: '请输入保质期', trigger: 'blur' }
  ],
  batchQuantity: [
    { required: true, message: '请输入批次数量', trigger: 'blur' }
  ]
}

// 加载列表数据
const loadList = async () => {
  loading.value = true
  try {
    const res = await getProductList()
    if (res.code === 200) {
      productList.value = res.data || []
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const loadEnterpriseOptions = async () => {
  if (!isAdmin.value) return
  try {
    const res = await getEnterpriseOptions()
    if (res.code === 200) {
      enterpriseOptions.value = res.data || []
    } else {
      ElMessage.error(res.message || '企业列表加载失败')
    }
  } catch (error) {
    ElMessage.error('企业列表加载失败: ' + error.message)
  }
}

const formatDate = (value) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day] = value
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  }
  return String(value).substring(0, 10)
}

const formatBatchCode = (value) => {
  if (!value) return '-'
  const code = String(value)
  const legacyMatch = code.match(/^BATCH_(\d{8})_(\d{6})(?:_?\d+)?$/)
  if (legacyMatch) {
    return `PB-${legacyMatch[1].slice(2)}-${legacyMatch[2]}`
  }
  return code
}

const getIssuePercent = (row) => {
  const total = Number(row.batchQuantity || 0)
  if (total <= 0) return 0
  const generated = Number(row.generatedQrCount || 0)
  return Math.min(100, Math.round((generated / total) * 100))
}

// 打开编辑对话框
const openEditDialog = (row) => {
  isEdit.value = true
  Object.assign(formData, {
    ...row,
    batchQuantity: row.batchQuantity || 1
  })
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, initialFormData)
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      const res = await updateProduct(formData)

      if (res.code === 200) {
        ElMessage.success('编辑成功')
        dialogVisible.value = false
        loadList()
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

// 删除产品
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除批次 "${row.batchCode}" 吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteProduct(row.batchId)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadList()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

// 页面加载时获取数据
onMounted(() => {
  loadEnterpriseOptions()
  loadList()
})
</script>

<style scoped>
.products-container {
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

/* 扁平化无边框表格 */
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

.flat-table :deep(.el-table__cell) {
  padding: 18px 0;
}

.flat-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.batch-product-cell {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

/* 产品缩略图 */
.product-thumb {
  width: 58px;
  height: 58px;
  border-radius: 14px;
  object-fit: cover;
  cursor: pointer;
  flex-shrink: 0;
}

/* 无图占位区 */
.image-placeholder {
  width: 58px;
  height: 58px;
  border-radius: 14px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px dashed #dcdfe6;
}

.batch-product-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

/* 产品名称加粗 */
.product-name-cell {
  font-weight: 600;
  color: #1d2129;
  font-size: 15px;
  line-height: 1.35;
}

.batch-code-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 190px;
  padding: 4px 9px;
  border-radius: 999px;
  background: #f5f7fb;
  color: #667085;
  font-size: 12px;
  line-height: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-cell,
.issue-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.production-cell,
.issue-cell {
  align-items: center;
}

.origin-text {
  font-size: 13px;
  color: #4b5565;
  line-height: 1.4;
}

.muted-text,
.description-cell {
  color: #98a2b3;
  font-size: 13px;
}

.date-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 104px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #f8fafc;
  color: #344054;
  font-size: 13px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.issue-cell {
  width: 120px;
  margin: 0 auto;
}

.issue-count {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  font-size: 14px;
  font-weight: 700;
  color: #2563eb;
}

.issue-divider {
  color: #c3cbd6;
  font-weight: 500;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.action-buttons .el-button + .el-button {
  margin-left: 0;
}

/* 分页器 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 4px;
}
</style>
