<template>
  <div class="inspections-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><WarnTriangleFilled /></el-icon>
            <span>质检与缺陷召回</span>
          </div>
          <el-button type="primary" @click="openAddDialog">
            <el-icon><Plus /></el-icon>
            新增质检
          </el-button>
        </div>
      </template>

      <!-- 筛选 -->
      <div class="filter-bar">
        <el-input
            v-model="searchText"
            placeholder="搜索质检员、报告编号或备注..."
            clearable
            prefix-icon="Search"
            class="filter-input"
            @clear="currentPage = 1"
            @input="currentPage = 1"
        />
        <el-select v-model="filterResult" placeholder="质检结果" clearable style="width: 130px" @change="currentPage = 1">
          <el-option label="合格" value="合格" />
          <el-option label="不合格" value="不合格" />
        </el-select>
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
        <el-table-column type="index" label="序号" width="70" align="center" />

        <el-table-column prop="batchId" label="产品批次" width="200">
          <template #default="{ row }">
            <span class="batch-name">{{ getBatchLabel(row.batchId) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="来源" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.sourceWarningId" type="warning" size="small" round>
              预警联动 #{{ row.sourceWarningId }}
            </el-tag>
            <el-tag v-else type="info" size="small" round>
              日常抽检
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="任务状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="inspectionStatusType(row.inspectionStatus)" size="small" round>
              {{ inspectionStatusLabel(row.inspectionStatus, row.inspectionResult) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="inspector" label="质检员" width="120" align="center" />

        <el-table-column prop="inspectionResult" label="质检结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag
                v-if="!row.inspectionResult"
                type="info"
                size="small"
                round
            >
              待录入
            </el-tag>
            <el-tag
                v-else
                :type="row.inspectionResult === '合格' ? 'success' : 'danger'"
                size="small"
                round
                effect="dark"
            >
              {{ row.inspectionResult }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="isRecalled" label="启动召回" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRecalled" type="danger" size="small" round>已召回</el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="inspectionDate" label="质检日期" width="130" align="center">
          <template #default="{ row }">
            {{ formatDate(row.inspectionDate) }}
          </template>
        </el-table-column>

        <el-table-column prop="remarks" label="备注" min-width="180" show-overflow-tooltip />

        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
              <el-button
                  v-if="!row.isRecalled && !row.sourceWarningId"
                  type="danger"
                  link
                  @click="handleDelete(row)"
              >
                删除
              </el-button>
              <span v-else class="text-muted">{{ row.sourceWarningId ? '联动锁定' : '召回锁定' }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑质检记录' : '新增质检记录'"
        width="560px"
        @close="resetForm"
    >
      <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="100px"
      >
        <el-form-item label="产品批次" prop="batchId">
          <el-select
              v-model="formData.batchId"
              placeholder="请选择产品批次"
              style="width: 100%"
              filterable
              :disabled="isEdit || isWarningLinkedForm"
          >
            <el-option
                v-for="batch in batchList"
                :key="batch.batchId"
                :label="`${batch.productName} - ${batch.batchCode}`"
                :value="batch.batchId"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="isWarningLinkedForm" label="来源预警">
          <el-tag type="warning" size="small" round>预警工单 #{{ formData.sourceWarningId }}</el-tag>
        </el-form-item>

        <el-form-item label="质检机构" prop="inspectionOrg">
          <el-input v-model="formData.inspectionOrg" placeholder="请输入质检机构或检测部门（选填）" />
        </el-form-item>

        <el-form-item label="报告编号" prop="reportNo">
          <el-input v-model="formData.reportNo" placeholder="请输入报告编号（选填）" />
        </el-form-item>

        <el-form-item label="附件链接" prop="reportUrl">
          <el-input v-model="formData.reportUrl" placeholder="可填写报告图片或PDF链接（选填）" />
        </el-form-item>

        <el-form-item label="质检员" prop="inspector">
          <el-input v-model="formData.inspector" placeholder="请输入质检员姓名" />
        </el-form-item>

        <el-form-item label="质检结果" prop="inspectionResult">
            <el-select v-model="formData.inspectionResult" placeholder="请选择质检结果" style="width: 100%" :disabled="isRecallLocked">
              <el-option label="合格" value="合格" :disabled="formData.isRecalled" />
              <el-option label="不合格" value="不合格" />
            </el-select>
        </el-form-item>

        <el-form-item label="质检日期" prop="inspectionDate">
          <el-date-picker
              v-model="formData.inspectionDate"
              type="date"
              placeholder="选择质检日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="启动召回" prop="isRecalled">
          <el-switch
              v-model="formData.isRecalled"
              active-text="是"
              inactive-text="否"
              active-color="#f56c6c"
              :disabled="isRecallLocked || isWarningLinkedForm"
          />
        </el-form-item>

        <el-form-item v-if="isWarningLinkedForm">
          <el-alert
              title="该质检任务由防伪预警自动发起。录入“合格”将自动关闭预警；录入“不合格”将自动升级为召回。"
              type="warning"
              :closable="false"
              show-icon
          />
        </el-form-item>

        <el-form-item v-if="selectedBatchRecallLocked && !isEdit">
          <el-alert
              title="该批次已经进入召回状态。新增记录只能继续保持召回，不允许恢复为正常批次。"
              type="error"
              :closable="false"
              show-icon
          />
        </el-form-item>

        <el-form-item v-else-if="formData.isRecalled">
          <el-alert
              title="已开启召回时，质检结果必须为“不合格”，系统会自动冻结该批次全部二维码。"
              type="warning"
              :closable="false"
              show-icon
          />
        </el-form-item>

        <el-form-item v-if="isEdit && formData.isRecalled">
          <el-alert
              title="该记录已经触发召回，召回结论不可撤销；你仍可补充备注、质检员或日期。"
              type="error"
              :closable="false"
              show-icon
          />
        </el-form-item>

        <el-form-item label="备注" prop="remarks">
          <el-input
              v-model="formData.remarks"
              type="textarea"
              :rows="3"
              placeholder="请输入备注（选填）"
          />
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarnTriangleFilled, Plus, Search } from '@element-plus/icons-vue'
import { getInspectionList, addInspection, updateInspection, deleteInspection } from '@/api/inspection'
import { getProductList } from '@/api/product'

const route = useRoute()
const router = useRouter()

// ========== 列表数据 ==========
const inspectionList = ref([])
const batchList = ref([])
const loading = ref(false)
const lastAutoOpenedInspectionId = ref(null)

// ========== 搜索 & 分页 ==========
const searchText = ref('')
const filterResult = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const filteredList = computed(() => {
  let list = inspectionList.value
  if (filterResult.value) {
    list = list.filter(item => item.inspectionResult === filterResult.value)
  }
  if (searchText.value) {
    const keyword = searchText.value.toLowerCase()
    list = list.filter(item =>
        (item.inspector && item.inspector.toLowerCase().includes(keyword)) ||
        (item.reportNo && item.reportNo.toLowerCase().includes(keyword)) ||
        (item.remarks && item.remarks.toLowerCase().includes(keyword))
    )
  }
  return list
})

const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const recalledBatchIds = computed(() => {
  const ids = new Set()
  inspectionList.value.forEach(item => {
    if (item.isRecalled && item.batchId != null) {
      ids.add(item.batchId)
    }
  })
  return ids
})

const selectedBatchRecallLocked = computed(() => {
  return formData.batchId != null && recalledBatchIds.value.has(formData.batchId)
})

const isRecallLocked = computed(() => {
  return (!isEdit.value && selectedBatchRecallLocked.value) || (isEdit.value && !!formData.isRecalled)
})

const isWarningLinkedForm = computed(() => !!formData.sourceWarningId)

// ========== 辅助方法 ==========
const getBatchLabel = (batchId) => {
  const batch = batchList.value.find(b => b.batchId === batchId)
  return batch ? `${batch.productName} - ${batch.batchCode}` : `批次#${batchId}`
}

const formatDate = (val) => {
  if (!val) return '-'
  if (Array.isArray(val)) {
    const [y, m, d] = val
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }
  return String(val).substring(0, 10)
}

const inspectionStatusLabel = (status, result) => {
  if (status === 'Pending' || status === '待质检' || (!status && !result)) return '待质检'
  return '已完成'
}

const inspectionStatusType = (status) => {
  if (status === 'Pending' || status === '待质检') return 'warning'
  return 'success'
}

// ========== 对话框 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const initialFormData = {
  inspectionId: null,
  batchId: null,
  sourceType: 'Routine',
  sourceWarningId: null,
  inspectionStatus: 'Completed',
  inspector: '',
  inspectionOrg: '',
  reportNo: '',
  reportUrl: '',
  inspectionResult: '',
  isRecalled: false,
  remarks: '',
  inspectionDate: ''
}

const formData = reactive({ ...initialFormData })

const formRules = {
  batchId: [{ required: true, message: '请选择产品批次', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入质检员', trigger: 'blur' }],
  inspectionResult: [{ required: true, message: '请选择质检结果', trigger: 'change' }],
  inspectionDate: [{ required: true, message: '请选择质检日期', trigger: 'change' }]
}

watch(
    () => formData.isRecalled,
    (value) => {
      if (value) {
        formData.inspectionResult = '不合格'
      }
    }
)

watch(
    () => selectedBatchRecallLocked.value,
    (locked) => {
      if (locked && !isEdit.value) {
        formData.isRecalled = true
        formData.inspectionResult = '不合格'
      }
    }
)

watch(
    () => formData.inspectionResult,
    (value) => {
      if (!isWarningLinkedForm.value) return
      if (value === '不合格') {
        formData.isRecalled = true
      }
      if (value === '合格') {
        formData.isRecalled = false
      }
    }
)

// ========== 数据操作 ==========
const loadList = async () => {
  loading.value = true
  try {
    const res = await getInspectionList()
    if (res.code === 200) {
      inspectionList.value = res.data || []
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const loadBatches = async () => {
  try {
    const res = await getProductList()
    if (res.code === 200) {
      batchList.value = res.data || []
    }
  } catch (e) {
    console.error('加载批次列表失败', e)
  }
}

const openAddDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  Object.assign(formData, {
    ...row,
    sourceType: row.sourceType || (row.sourceWarningId ? 'Warning' : 'Routine'),
    inspectionStatus: row.inspectionStatus || (row.inspectionResult ? 'Completed' : 'Pending'),
    inspectionDate: formatDate(row.inspectionDate)
  })
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(formData, initialFormData)
  if (formRef.value) formRef.value.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const submitData = {
        ...formData,
        sourceType: formData.sourceWarningId ? 'Warning' : 'Routine',
        inspectionStatus: formData.inspectionResult ? 'Completed' : 'Pending'
      }
      let res
      if (isEdit.value) {
        res = await updateInspection(submitData)
      } else {
        res = await addInspection(submitData)
      }
      if (res.code === 200) {
        ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
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

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除质检记录 #${row.inspectionId} 吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteInspection(row.inspectionId)
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

const tryOpenInspectionFromRoute = () => {
  const inspectionId = Number(route.query.inspectionId)
  if (!inspectionId || inspectionId === lastAutoOpenedInspectionId.value) {
    return
  }
  const target = inspectionList.value.find(item => item.inspectionId === inspectionId)
  if (!target) {
    return
  }
  lastAutoOpenedInspectionId.value = inspectionId
  openEditDialog(target)
  router.replace({ name: 'Inspections' })
}

onMounted(() => {
  loadList()
  loadBatches()
})

watch(
    () => route.query.inspectionId,
    () => {
      tryOpenInspectionFromRoute()
    }
)

watch(
    () => inspectionList.value.length,
    () => {
      tryOpenInspectionFromRoute()
    }
)
</script>

<style scoped>
.inspections-container {
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

.header-icon {
  color: #e6a23c;
  font-size: 22px;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-input {
  width: 240px;
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

/* 表格 */
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

.batch-name {
  font-weight: 600;
  color: #1d2129;
  font-size: 13px;
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  align-items: center;
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
</style>
