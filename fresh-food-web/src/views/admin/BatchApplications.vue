<template>
  <div class="applications-container">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title-dot"></span>
            <div>
              <div class="page-title">{{ isAdmin ? '批次准入审核' : '批次准入申报' }}</div>
              <div class="page-subtitle">
                {{ isAdmin ? '审核合作基地申报材料，通过后自动生成正式产品批次' : '提交准入申报，审核通过后才能录入溯源并生成二维码' }}
              </div>
            </div>
          </div>
          <el-button v-if="!isAdmin" type="primary" class="primary-action" @click="openSubmitDialog">
            <el-icon><Plus /></el-icon>
            提交准入申报
          </el-button>
        </div>
      </template>

      <div class="process-strip">
        <div class="process-copy">
          <strong>{{ isAdmin ? '审核口径' : '申报说明' }}</strong>
          <span>{{ isAdmin ? '先核验生产责任与安全承诺，再决定是否进入正式批次链路。' : '申报未通过前，该批次不能录入溯源节点、不能生成二维码。' }}</span>
        </div>
        <div class="process-flow">
          <span>提交申报</span>
          <span>管理员审核</span>
          <span>正式批次</span>
          <span>溯源发码</span>
        </div>
      </div>

      <div class="approval-stats">
        <div v-for="item in statCards" :key="item.label" class="stat-card" :class="item.tone">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
      </div>

      <div class="filter-bar">
        <el-input
            v-model="keyword"
            :prefix-icon="Search"
            :placeholder="isAdmin ? '搜索产品、申报编号、基地或产地' : '搜索产品、申报编号或产地'"
            clearable
            class="filter-input"
            @input="currentPage = 1"
            @clear="currentPage = 1"
        />
        <el-select
            v-model="statusFilter"
            placeholder="申报状态"
            class="status-select"
            @change="handleStatusChange"
        >
          <el-option label="全部有效状态" value="All" />
          <el-option label="待审核" value="Pending" />
          <el-option label="退回补正" value="Rejected" />
          <el-option label="不予通过" value="Denied" />
          <el-option label="已转正式批次" value="Converted" />
          <el-option label="已作废" value="Voided" />
        </el-select>
        <span class="filter-summary">共 <strong>{{ filteredList.length }}</strong> 条申报</span>
      </div>

      <el-table
          v-loading="loading"
          :data="pagedList"
          stripe
          size="large"
          class="flat-table"
          style="width: 100%"
      >
        <el-table-column label="申报批次" min-width="260">
          <template #default="{ row }">
            <div class="application-main">
              <strong>{{ row.productName }}</strong>
              <div class="application-tags">
                <span class="code-chip">{{ row.applicationNo }}</span>
              </div>
              <small>提交 {{ formatTime(row.createdAt) }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column :label="isAdmin ? '基地 / 产地' : '产地'" min-width="170">
          <template #default="{ row }">
            <div class="source-cell">
              <el-tag v-if="isAdmin" size="small" round>{{ row.manufacturerName || '未知基地' }}</el-tag>
              <strong v-else>{{ row.origin || '-' }}</strong>
              <span v-if="isAdmin">{{ row.origin || '-' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="生产周期" width="170">
          <template #default="{ row }">
            <div class="date-lines">
              <div>
                <span>生产</span>
                <strong>{{ formatDate(row.productionDate) || '-' }}</strong>
              </div>
              <div>
                <span>上市</span>
                <strong>{{ formatDate(row.expectedMarketDate) || '-' }}</strong>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="责任信息" width="160">
          <template #default="{ row }">
            <div class="responsibility-cell">
              <strong>{{ row.productionManager || '-' }}</strong>
              <span>{{ row.managerPhone || '-' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="申报数量" width="110" align="center">
          <template #default="{ row }">
            <strong class="quantity-text">{{ row.batchQuantity || 0 }}</strong>
          </template>
        </el-table-column>

        <el-table-column label="审批进度" min-width="230">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-tag :type="getStatusType(row.status)" round>
                {{ getStatusLabel(row.status) }}
              </el-tag>
              <span v-if="row.convertedBatchCode" class="code-chip success">{{ formatBatchCode(row.convertedBatchCode) }}</span>
              <span v-else class="muted-text">{{ row.reviewOpinion || getStatusHint(row.status) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                  v-if="isAdmin && row.status === 'Pending'"
                  type="primary"
                  link
                  @click="openReviewDrawer(row)"
              >
                审核
              </el-button>
              <el-button
                  v-else
                  type="primary"
                  link
                  @click="openReviewDrawer(row)"
              >
                查看
              </el-button>
              <el-button
                  v-if="!isAdmin && row.status === 'Rejected'"
                  type="warning"
                  link
                  @click="openResubmitDialog(row)"
              >
                补正
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="filteredList.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
            small
        />
      </div>
    </el-card>

    <el-drawer
        v-model="detailDrawerVisible"
        :title="selectedApplication ? `${selectedApplication.productName} 的准入材料` : '准入材料详情'"
        size="520px"
    >
      <template v-if="selectedApplication">
        <div class="drawer-status">
          <el-tag :type="getStatusType(selectedApplication.status)" round>
            {{ getStatusLabel(selectedApplication.status) }}
          </el-tag>
          <span>{{ selectedApplication.applicationNo }}</span>
        </div>

        <el-descriptions :column="1" border class="detail-descriptions">
          <el-descriptions-item label="合作基地" v-if="isAdmin">
            {{ selectedApplication.manufacturerName || '未知基地' }}
          </el-descriptions-item>
          <el-descriptions-item label="产品名称">
            {{ selectedApplication.productName }}
          </el-descriptions-item>
          <el-descriptions-item label="产地">
            {{ selectedApplication.origin || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="生产日期">
            {{ formatDate(selectedApplication.productionDate) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预计上市">
            {{ formatDate(selectedApplication.expectedMarketDate) || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="保质期">
            {{ selectedApplication.shelfLifeDays || 0 }} 天
          </el-descriptions-item>
          <el-descriptions-item label="申报数量">
            {{ selectedApplication.batchQuantity || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="生产负责人">
            {{ selectedApplication.productionManager || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            {{ selectedApplication.managerPhone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="生产地址">
            {{ selectedApplication.productionAddress || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="安全承诺">
            {{ selectedApplication.qualityCommitment ? '已确认' : '未确认' }}
          </el-descriptions-item>
          <el-descriptions-item label="申报说明">
            {{ selectedApplication.description || '暂无说明' }}
          </el-descriptions-item>
          <el-descriptions-item label="审核意见" v-if="selectedApplication.status !== 'Pending'">
            {{ selectedApplication.reviewOpinion || '暂无审核意见' }}
          </el-descriptions-item>
          <el-descriptions-item label="正式批次" v-if="selectedApplication.convertedBatchCode">
            {{ formatBatchCode(selectedApplication.convertedBatchCode) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-footer">
          <template v-if="isAdmin && selectedApplication.status === 'Pending'">
            <el-button type="success" @click="handleApprove(selectedApplication)">
              通过并生成正式批次
            </el-button>
            <el-button type="warning" plain @click="handleReturnForCorrection(selectedApplication)">
              退回补正
            </el-button>
            <el-button type="danger" plain @click="handleDeny(selectedApplication)">
              不予通过
            </el-button>
          </template>
          <template v-else-if="!isAdmin && selectedApplication.status === 'Rejected'">
            <el-button type="primary" @click="openResubmitDialog(selectedApplication)">
              修改补正并重新提交
            </el-button>
          </template>
          <template v-else>
            <el-button @click="detailDrawerVisible = false">关闭</el-button>
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '批次准入补正申报' : '批次准入申报书'"
        width="760px"
        class="application-dialog"
        @close="resetForm"
    >
      <el-alert
          title="申报通过后才会生成正式产品批次。请确保生产信息、责任人信息和安全承诺真实完整。"
          type="info"
          :closable="false"
          show-icon
          class="dialog-alert"
      />

      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <div class="form-section">
          <div class="form-section-title">一、产品基础信息</div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="产品名称" prop="productName">
                <el-input v-model="formData.productName" placeholder="例如：苹果、香蕉、葡萄" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="产地" prop="origin">
                <el-input v-model="formData.origin" placeholder="请输入具体产地" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section">
          <div class="form-section-title">二、生产批次信息</div>
          <el-row :gutter="16">
            <el-col :span="12">
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
            </el-col>
            <el-col :span="12">
              <el-form-item label="预计上市日期" prop="expectedMarketDate">
                <el-date-picker
                    v-model="formData.expectedMarketDate"
                    type="date"
                    placeholder="选择预计上市日期"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="保质期（天）" prop="shelfLifeDays">
                <el-input-number v-model="formData.shelfLifeDays" :min="1" :max="9999" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="申报数量" prop="batchQuantity">
                <el-input-number v-model="formData.batchQuantity" :min="1" :max="999999" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section">
          <div class="form-section-title">三、生产责任信息</div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="生产负责人" prop="productionManager">
                <el-input v-model="formData.productionManager" placeholder="请输入生产负责人姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="负责人联系电话" prop="managerPhone">
                <el-input v-model="formData.managerPhone" placeholder="请输入联系电话" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="实际生产地址" prop="productionAddress">
            <el-input v-model="formData.productionAddress" placeholder="请输入基地或生产地详细地址" />
          </el-form-item>
        </div>

        <div class="form-section">
          <div class="form-section-title">四、补充说明与承诺</div>
          <el-form-item label="产品图片链接" prop="imageUrl">
            <el-input v-model="formData.imageUrl" placeholder="选填，填写产品图片地址" clearable />
          </el-form-item>
          <el-form-item label="申报说明" prop="description">
            <el-input
                v-model="formData.description"
                type="textarea"
                :rows="3"
                placeholder="可说明采摘、生产环境、上市计划等信息"
            />
          </el-form-item>
          <el-form-item prop="qualityCommitment" label-width="0">
            <div class="commitment-panel">
              <el-checkbox v-model="formData.qualityCommitment" class="commitment-check">
                我承诺本批次产品信息真实、来源清晰，并符合食品安全与溯源管理要求
              </el-checkbox>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ isEdit ? '提交补正' : '提交准入审核' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  approveApplication,
  denyApplication,
  getApplicationList,
  rejectApplication,
  resubmitApplication,
  submitApplication
} from '@/api/application'
import { getLocalUserInfo } from '@/api/user'

const currentUser = getLocalUserInfo()
const isAdmin = computed(() => currentUser && currentUser.role === 'admin')

const loading = ref(false)
const applicationList = ref([])
const selectedApplicationId = ref(null)
const detailDrawerVisible = ref(false)
const keyword = ref('')
const statusFilter = ref('All')
const currentPage = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const initialFormData = {
  applicationId: null,
  productName: '',
  origin: '',
  productionDate: '',
  expectedMarketDate: '',
  shelfLifeDays: 30,
  batchQuantity: 100,
  productionManager: '',
  managerPhone: '',
  productionAddress: '',
  qualityCommitment: false,
  description: '',
  imageUrl: ''
}

const formData = reactive({ ...initialFormData })

const formRules = {
  productName: [
    { required: true, message: '请输入产品名称', trigger: 'blur' },
    { max: 100, message: '产品名称不能超过100个字符', trigger: 'blur' }
  ],
  origin: [{ required: true, message: '请输入产地', trigger: 'blur' }],
  productionDate: [{ required: true, message: '请选择生产日期', trigger: 'change' }],
  expectedMarketDate: [{ required: true, message: '请选择预计上市日期', trigger: 'change' }],
  shelfLifeDays: [{ required: true, message: '请输入保质期', trigger: 'blur' }],
  batchQuantity: [{ required: true, message: '请输入申报数量', trigger: 'blur' }],
  productionManager: [{ required: true, message: '请输入生产负责人', trigger: 'blur' }],
  managerPhone: [{ required: true, message: '请输入负责人联系电话', trigger: 'blur' }],
  productionAddress: [{ required: true, message: '请输入生产地址', trigger: 'blur' }],
  qualityCommitment: [
    {
      validator: (rule, value, callback) => {
        value ? callback() : callback(new Error('请先确认质检与安全承诺'))
      },
      trigger: 'change'
    }
  ]
}

const statusWeight = {
  Pending: 1,
  Rejected: 2,
  Denied: 3,
  Converted: 4,
  Voided: 5
}

const filteredList = computed(() => {
  let list = [...applicationList.value].sort((a, b) => {
    const weightDiff = (statusWeight[a.status] || 9) - (statusWeight[b.status] || 9)
    if (weightDiff !== 0) return weightDiff
    return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
  })

  if (statusFilter.value && statusFilter.value !== 'All') {
    list = list.filter(item => item.status === statusFilter.value)
  }

  if (!keyword.value) return list
  const target = keyword.value.toLowerCase()
  return list.filter(item => [
    item.productName,
    item.applicationNo,
    item.manufacturerName,
    item.origin,
    item.convertedBatchCode,
    item.productionManager
  ].some(value => value && String(value).toLowerCase().includes(target)))
})

const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const selectedApplication = computed(() => {
  return applicationList.value.find(item => item.applicationId === selectedApplicationId.value)
      || filteredList.value.find(item => item.applicationId === selectedApplicationId.value)
      || null
})

const statCards = computed(() => [
  { label: '待审核', value: countByStatus('Pending'), tone: 'warning', hint: '需要管理员确认' },
  { label: '退回补正', value: countByStatus('Rejected'), tone: 'danger', hint: '企业可修改重提' },
  { label: '不予通过', value: countByStatus('Denied'), tone: 'neutral', hint: '本次申报终止' },
  { label: '正式批次', value: countByStatus('Converted'), tone: 'success', hint: '已进入产品管理' }
])

const countByStatus = (status) => applicationList.value.filter(item => item.status === status).length

const loadList = async () => {
  loading.value = true
  try {
    const requestStatus = statusFilter.value === 'Voided' ? 'Voided' : 'All'
    const res = await getApplicationList({ status: requestStatus })
    if (res.code === 200) {
      applicationList.value = res.data || []
    } else {
      ElMessage.error(res.message || '申报列表加载失败')
    }
  } catch (error) {
    ElMessage.error('申报列表加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleStatusChange = () => {
  currentPage.value = 1
  loadList()
}

const openReviewDrawer = (row) => {
  selectedApplicationId.value = row.applicationId
  detailDrawerVisible.value = true
}

const openSubmitDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openResubmitDialog = (row) => {
  isEdit.value = true
  detailDrawerVisible.value = false
  Object.assign(formData, {
    applicationId: row.applicationId,
    productName: row.productName,
    origin: row.origin,
    productionDate: formatDate(row.productionDate),
    expectedMarketDate: formatDate(row.expectedMarketDate),
    shelfLifeDays: row.shelfLifeDays || 30,
    batchQuantity: row.batchQuantity || 1,
    productionManager: row.productionManager || '',
    managerPhone: row.managerPhone || '',
    productionAddress: row.productionAddress || '',
    qualityCommitment: Boolean(row.qualityCommitment),
    description: row.description || '',
    imageUrl: row.imageUrl || ''
  })
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(formData, initialFormData)
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const res = isEdit.value
          ? await resubmitApplication(formData)
          : await submitApplication(formData)
      if (res.code === 200) {
        ElMessage.success(res.message || '提交成功')
        dialogVisible.value = false
        loadList()
      } else {
        ElMessage.error(res.message || '提交失败')
      }
    } catch (error) {
      ElMessage.error('提交失败: ' + error.message)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleApprove = (row) => {
  ElMessageBox.confirm(
      `确认通过“${row.productName}”的准入申报吗？通过后将自动生成正式产品批次。`,
      '审核通过',
      {
        confirmButtonText: '通过并生成正式批次',
        cancelButtonText: '取消',
        type: 'success'
      }
  ).then(async () => {
    const res = await approveApplication(row.applicationId, { reviewOpinion: '审核通过，准入材料完整，生成正式产品批次' })
    if (res.code === 200) {
      ElMessage.success(res.message || '审核通过')
      detailDrawerVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.message || '审核失败')
    }
  }).catch(() => {})
}

const handleReturnForCorrection = (row) => {
  ElMessageBox.prompt(`请输入“${row.productName}”需要补正的内容`, '退回补正', {
    confirmButtonText: '确认退回补正',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '例如：生产地址不完整，请补充具体基地地址后重新提交',
    inputValidator: (value) => !!value && !!value.trim(),
    inputErrorMessage: '补正意见不能为空'
  }).then(async ({ value }) => {
    const res = await rejectApplication(row.applicationId, { reviewOpinion: value })
    if (res.code === 200) {
      ElMessage.success(res.message || '已退回补正')
      detailDrawerVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.message || '退回失败')
    }
  }).catch(() => {})
}

const handleDeny = (row) => {
  ElMessageBox.prompt(`请输入“${row.productName}”不予通过的原因`, '不予通过', {
    confirmButtonText: '确认不予通过',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '例如：申报材料与基地资质不匹配，本次申报不予通过',
    inputValidator: (value) => !!value && !!value.trim(),
    inputErrorMessage: '审核意见不能为空'
  }).then(async ({ value }) => {
    const res = await denyApplication(row.applicationId, { reviewOpinion: value })
    if (res.code === 200) {
      ElMessage.success(res.message || '已不予通过')
      detailDrawerVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  }).catch(() => {})
}

const getStatusHint = (status) => {
  const map = {
    Pending: '等待管理员审核',
    Rejected: '需要补正后重提',
    Denied: '本次申报终止',
    Converted: '已生成正式批次',
    Voided: '申报记录已作废'
  }
  return map[status] || '暂无处理结果'
}

const getStatusLabel = (status) => {
  const map = {
    Pending: '待审核',
    Rejected: '退回补正',
    Denied: '不予通过',
    Converted: '已转正式批次',
    Voided: '已作废'
  }
  return map[status] || status || '-'
}

const getStatusType = (status) => {
  const map = {
    Pending: 'warning',
    Rejected: 'danger',
    Denied: 'info',
    Converted: 'success',
    Voided: 'info'
  }
  return map[status] || 'info'
}

const formatDate = (value) => {
  if (!value) return ''
  if (Array.isArray(value)) {
    const [year, month, day] = value
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  }
  return String(value).substring(0, 10)
}

const formatTime = (value) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0] = value
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
  }
  return String(value).replace('T', ' ').substring(0, 16)
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

onMounted(loadList)
</script>

<style scoped>
.applications-container {
  padding: 0;
}

.main-card {
  border-radius: 18px;
}

.card-header,
.header-left,
.filter-bar,
.application-tags,
.action-buttons,
.drawer-status {
  display: flex;
  align-items: center;
}

.card-header {
  justify-content: space-between;
  gap: 16px;
}

.header-left {
  gap: 12px;
}

.title-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #eef6ff;
  flex-shrink: 0;
}

.page-title {
  font-size: 19px;
  font-weight: 800;
  color: #1f2937;
}

.page-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #8a94a6;
}

.primary-action {
  border-radius: 16px;
  padding: 10px 18px;
  font-weight: 600;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.18);
}

.process-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  margin-bottom: 18px;
  padding: 16px 18px;
  border: 1px solid #edf0f5;
  border-radius: 18px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%);
}

.process-copy strong {
  display: block;
  color: #1f2937;
  font-size: 16px;
}

.process-copy span {
  display: block;
  margin-top: 5px;
  color: #8a94a6;
  font-size: 13px;
}

.process-flow {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.process-flow span {
  padding: 7px 12px;
  border-radius: 999px;
  background: #f1f7ff;
  color: #409eff;
  font-size: 12px;
  font-weight: 700;
}

.approval-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.stat-card {
  min-height: 86px;
  padding: 15px 16px;
  border: 1px solid #edf0f5;
  border-radius: 16px;
  background: #fbfcff;
}

.stat-card span,
.stat-card small {
  display: block;
  color: #8a94a6;
  font-size: 13px;
}

.stat-card strong {
  display: block;
  margin: 5px 0 3px;
  color: #1f2937;
  font-size: 26px;
}

.stat-card.warning strong {
  color: #d97706;
}

.stat-card.danger strong {
  color: #dc2626;
}

.stat-card.success strong {
  color: #14915f;
}

.stat-card.neutral strong {
  color: #475467;
}

.filter-bar {
  gap: 12px;
  margin-bottom: 16px;
}

.filter-input {
  width: 330px;
}

.status-select {
  width: 170px;
}

.filter-summary {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}

.filter-summary strong {
  color: #409eff;
}

.flat-table :deep(th.el-table__cell) {
  background-color: #fafafa;
  color: #667085;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5 !important;
}

.flat-table :deep(td.el-table__cell) {
  border-bottom: 1px solid #f2f3f5 !important;
}

.flat-table :deep(.el-table__cell) {
  padding: 17px 0;
}

.flat-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.application-main,
.source-cell,
.date-lines,
.responsibility-cell,
.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.application-main strong,
.source-cell strong,
.responsibility-cell strong {
  color: #1f2937;
  font-size: 15px;
  line-height: 1.35;
}

.application-tags {
  gap: 8px;
  color: #8a94a6;
  font-size: 12px;
}

.application-main small {
  color: #98a2b3;
  font-size: 12px;
}

.plain-text,
.source-cell span,
.responsibility-cell span,
.muted-text,
.date-lines span {
  color: #8a94a6;
  font-size: 13px;
}

.date-lines {
  gap: 6px;
}

.date-lines div {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.date-lines strong {
  color: #344054;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.quantity-text {
  color: #2563eb;
}

.code-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 210px;
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

.code-chip.success {
  background: #ecfdf5;
  color: #14915f;
}

.progress-cell {
  align-items: flex-start;
  gap: 8px;
}

.progress-cell .muted-text {
  display: inline-block;
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-buttons {
  justify-content: center;
  gap: 10px;
}

.action-buttons .el-button + .el-button {
  margin-left: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 4px;
}

.drawer-status {
  justify-content: space-between;
  margin-bottom: 18px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fbff;
  color: #8a94a6;
  font-size: 13px;
}

.detail-descriptions {
  margin-bottom: 20px;
}

.detail-descriptions :deep(.el-descriptions__label) {
  width: 112px;
  color: #667085;
  font-weight: 600;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid #edf0f5;
}

.dialog-alert {
  margin-bottom: 18px;
}

.form-section {
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid #edf0f5;
  border-radius: 16px;
  background: #fbfcff;
}

.form-section-title {
  margin-bottom: 14px;
  color: #1f2937;
  font-size: 15px;
  font-weight: 800;
}

.commitment-panel {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fbff;
}

.commitment-check {
  line-height: 1.5;
  white-space: normal;
}

@media (max-width: 1100px) {
  .approval-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .process-strip {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .card-header,
  .filter-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-input,
  .status-select {
    width: 100%;
  }

  .filter-summary {
    margin-left: 0;
  }

  .approval-stats {
    grid-template-columns: 1fr;
  }
}
</style>
