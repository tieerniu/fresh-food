<template>
  <div class="warnings-container">
    <el-card shadow="hover" class="warning-panel">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon-warning"><Warning /></el-icon>
            <div class="header-copy">
              <span>防伪预警中心</span>
              <small>预警发现风险，质检给出结论，召回执行闭环</small>
            </div>
            <el-tag v-if="openCount > 0" type="danger" size="small" round effect="dark">
              {{ openCount }} 条未闭环
            </el-tag>
          </div>
          <el-radio-group v-model="filterStatus" size="small" @change="currentPage = 1">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="Pending">待处理</el-radio-button>
            <el-radio-button value="InInspection">待质检</el-radio-button>
            <el-radio-button value="Handled">已处理</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <div class="filter-bar">
        <el-input
            v-model="keyword"
            placeholder="搜索防伪码 / 产品名称 / 批次号"
            clearable
            class="filter-input"
            @clear="currentPage = 1"
            @input="currentPage = 1"
        />
        <span class="filter-tip">
          <el-icon><InfoFilled /></el-icon>
          误报忽略后 6 小时内不会重复生成同一码预警
        </span>
        <el-button
            v-if="handledCount > 0"
            type="danger"
            plain
            size="small"
            class="clear-btn"
            @click="handleClearHandled"
        >
          清空已处理
        </el-button>
      </div>

      <div class="warning-stats" v-if="warningList.length > 0">
        <div class="stat-item stat-danger">
          <span class="stat-label-top">待处理</span>
          <span class="stat-num">{{ pendingCount }}</span>
          <span class="stat-text">需人工确认风险处置</span>
        </div>
        <div class="stat-item stat-warning">
          <span class="stat-label-top">待质检</span>
          <span class="stat-num">{{ inspectionCount }}</span>
          <span class="stat-text">已进入核验流程</span>
        </div>
        <div class="stat-item stat-success">
          <span class="stat-label-top">已处理</span>
          <span class="stat-num">{{ handledCount }}</span>
          <span class="stat-text">已完成闭环或忽略</span>
        </div>
        <div class="stat-item stat-info">
          <span class="stat-label-top">总计</span>
          <span class="stat-num">{{ warningList.length }}</span>
          <span class="stat-text">所有历史预警记录</span>
        </div>
      </div>

      <el-table
          v-loading="loading"
          :data="pagedList"
          stripe
          style="width: 100%"
          class="flat-table"
          :row-class-name="tableRowClassName"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />

        <el-table-column label="产品 / 批次" min-width="220">
          <template #default="{ row }">
            <div class="product-block">
              <div class="product-name">{{ row.productName || '未知产品' }}</div>
              <div class="product-meta">
                <span>{{ row.batchCode || '无批次号' }}</span>
                <span v-if="row.manufacturerName"> / {{ row.manufacturerName }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="uniqueCode" label="防伪码" width="180" align="center">
          <template #default="{ row }">
            <el-text type="primary" size="small" tag="code">{{ row.uniqueCode }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="warningType" label="预警类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagColor(row.warningType)" size="small" round>
              {{ typeLabel(row.warningType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="二维码状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="qrStatusType(row.qrStatus)" size="small" round>
              {{ qrStatusLabel(row.qrStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="scanCount" label="扫码次数" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.scanCount ?? 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="预警内容" min-width="280">
          <template #default="{ row }">
            <div class="warning-content-cell">
              <div class="warning-content-text">{{ row.warningContent || '暂无预警说明' }}</div>
              <span class="warning-time-inline">{{ formatTime(row.createdAt) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="处理进度" width="120" align="center">
          <template #default="{ row }">
            <el-tag
                :type="warningProgressType(row)"
                size="small"
                round
                :effect="isOpenWarning(row.status) ? 'dark' : 'light'"
            >
              {{ warningProgressLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(command) => handleActionCommand(row, command)">
              <el-button type="primary" link class="warning-action-trigger">
                操作
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="isPendingStatus(row.status)" command="resolve">处理预警</el-dropdown-item>
                  <el-dropdown-item v-if="row.linkedInspectionId" command="inspection">查看质检</el-dropdown-item>
                  <el-dropdown-item command="qr" :divided="isPendingStatus(row.status) || !!row.linkedInspectionId">
                    查看二维码
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除记录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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

    <el-dialog
        v-model="dialogVisible"
        title="防伪预警处理"
        width="640px"
        class="warning-dialog"
        align-center
    >
      <div v-if="currentWarning" class="dialog-content">
        <div class="warning-case-card">
          <div class="warning-case-head">
            <div>
              <div class="warning-case-title">{{ currentWarning.productName || '未知产品' }}</div>
              <div class="warning-case-sub">
                {{ currentWarning.batchCode || '无批次号' }} / {{ currentWarning.manufacturerName || '未知基地' }}
              </div>
            </div>
            <el-tag :type="warningProgressType(currentWarning)" round>
              {{ warningProgressLabel(currentWarning) }}
            </el-tag>
          </div>
          <div class="warning-case-code">
            防伪码：<strong>{{ currentWarning.uniqueCode }}</strong>
          </div>
          <p class="warning-case-desc">{{ currentWarning.warningContent }}</p>
        </div>
        <p class="dialog-tip">请选择这条预警的处置方案：</p>
        <div class="decision-grid">
          <button
              v-for="option in actionOptions"
              :key="option.value"
              type="button"
              class="decision-card"
              :class="{ 'is-active': actionType === option.value }"
              @click="actionType = option.value"
          >
            <div class="decision-card-icon" :class="`tone-${option.tone}`">
              <el-icon><component :is="option.icon" /></el-icon>
            </div>
            <div class="decision-card-body">
              <div class="decision-card-title">{{ option.title }}</div>
              <div class="decision-card-desc">{{ option.description }}</div>
            </div>
          </button>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitResolve" :loading="submitLoading">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, Lock, InfoFilled, DocumentChecked, ArrowDown } from '@element-plus/icons-vue'
import { getWarningList, resolveWarning, deleteWarning, clearHandledWarnings } from '@/api/warning'

const router = useRouter()

const warningList = ref([])
const loading = ref(false)
const filterStatus = ref('')
const keyword = ref('')

const dialogVisible = ref(false)
const currentWarning = ref(null)
const actionType = ref('ignore')
const submitLoading = ref(false)

const actionOptions = [
  {
    value: 'ignore',
    title: '误报忽略',
    description: '标记为误报，并在 6 小时内抑制同一码重复生成新预警。',
    tone: 'neutral',
    icon: InfoFilled
  },
  {
    value: 'inspect',
    title: '发起质检',
    description: '自动创建待质检任务，由人工录入结论后决定关闭预警或升级召回。',
    tone: 'warning',
    icon: DocumentChecked
  },
  {
    value: 'freeze',
    title: '冻结拦截',
    description: '立即按高风险处理，把该二维码状态更新为召回并中止继续流通。',
    tone: 'danger',
    icon: Lock
  }
]

const currentPage = ref(1)
const pageSize = ref(10)

const isPendingStatus = (status) => status === 'Pending' || status === '待处理'
const isInspectionStatus = (status) => status === 'InInspection' || status === '待质检'
const isOpenWarning = (status) => isPendingStatus(status) || isInspectionStatus(status)

const pendingCount = computed(() =>
    warningList.value.filter(w => isPendingStatus(w.status)).length
)

const inspectionCount = computed(() =>
    warningList.value.filter(w => isInspectionStatus(w.status)).length
)

const handledCount = computed(() =>
    warningList.value.filter(w => !isOpenWarning(w.status)).length
)

const openCount = computed(() => pendingCount.value + inspectionCount.value)

const filteredList = computed(() => {
  let list = warningList.value

  if (filterStatus.value === 'Pending') {
    list = list.filter(w => isPendingStatus(w.status))
  } else if (filterStatus.value === 'InInspection') {
    list = list.filter(w => isInspectionStatus(w.status))
  } else if (filterStatus.value === 'Handled') {
    list = list.filter(w => !isOpenWarning(w.status))
  }

  if (keyword.value) {
    const text = keyword.value.toLowerCase()
    list = list.filter(item =>
        (item.uniqueCode && item.uniqueCode.toLowerCase().includes(text)) ||
        (item.productName && item.productName.toLowerCase().includes(text)) ||
        (item.batchCode && item.batchCode.toLowerCase().includes(text)) ||
        (item.manufacturerName && item.manufacturerName.toLowerCase().includes(text))
    )
  }

  return list
})

const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const typeLabel = (type) => {
  const map = {
    HighScanCount: '扫码异常',
    InvalidCode: '无效码',
    LocationAnomaly: '地点异常',
    FrequentScan: '高频扫码'
  }
  return map[type] || type || '未知'
}

const typeTagColor = (type) => {
  const map = {
    HighScanCount: 'danger',
    InvalidCode: 'warning',
    LocationAnomaly: 'primary',
    FrequentScan: 'danger'
  }
  return map[type] || 'info'
}

const warningProgressLabel = (row) => {
  if (isPendingStatus(row.status)) return '待处理'
  if (isInspectionStatus(row.status)) return '待质检'
  if (row.status === 'Ignored' || row.status === '已忽略') return '已忽略'
  const map = {
    VerifiedPass: '核验通过',
    EscalatedRecall: '升级召回',
    Frozen: '紧急冻结',
    Ignore: '已忽略',
    InspectionCreated: '待质检',
    InspectionCompleted: '已核验'
  }
  return map[row.disposalType] || '已处理'
}

const warningProgressType = (row) => {
  if (isPendingStatus(row.status)) return 'danger'
  if (isInspectionStatus(row.status)) return 'warning'
  if (row.status === 'Ignored' || row.status === '已忽略') return 'info'
  if (row.disposalType === 'EscalatedRecall' || row.disposalType === 'Frozen') return 'danger'
  if (row.disposalType === 'VerifiedPass') return 'success'
  if (row.disposalType === 'Ignore') return 'info'
  return 'success'
}

const qrStatusLabel = (status) => {
  const normalizedStatus = status === 'Sold' ? 'Active' : status
  const map = {
    Active: '激活',
    Recalled: '召回',
    Expired: '过期'
  }
  return map[normalizedStatus] || '未知'
}

const qrStatusType = (status) => {
  const normalizedStatus = status === 'Sold' ? 'Active' : status
  const map = {
    Active: 'success',
    Recalled: 'danger',
    Expired: 'warning'
  }
  return map[normalizedStatus] || 'info'
}

const formatTime = (val) => {
  if (!val) return ''
  if (Array.isArray(val)) {
    const [y, m, d, h, mi] = val
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`
  }
  return String(val).replace('T', ' ').substring(0, 16)
}

const tableRowClassName = ({ row }) => (isPendingStatus(row.status) ? 'warning-row' : '')

const loadList = async () => {
  loading.value = true
  try {
    const res = await getWarningList()
    if (res.code === 200) {
      warningList.value = res.data || []
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleResolve = (row) => {
  currentWarning.value = row
  actionType.value = 'ignore'
  dialogVisible.value = true
}

const submitResolve = async () => {
  if (!currentWarning.value) return

  submitLoading.value = true
  try {
    const res = await resolveWarning(currentWarning.value.warningId, actionType.value)
    if (res.code === 200) {
      ElMessage.success(actionType.value === 'inspect' ? '质检任务已创建' : '处理成功')
      dialogVisible.value = false
      await loadList()
      if (actionType.value === 'inspect' && res.data && res.data.linkedInspectionId) {
        router.push({
          name: 'Inspections',
          query: {
            inspectionId: res.data.linkedInspectionId,
            warningId: currentWarning.value.warningId
          }
        })
      }
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除防伪码 ${row.uniqueCode} 的这条预警记录吗？删除后如果该码继续异常扫码，系统仍可能再次生成新预警。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      const res = await deleteWarning(row.warningId)
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

const handleActionCommand = (row, command) => {
  if (command === 'resolve') {
    handleResolve(row)
    return
  }
  if (command === 'inspection') {
    goToInspection(row)
    return
  }
  if (command === 'qr') {
    goToQrCode(row)
    return
  }
  if (command === 'delete') {
    handleDelete(row)
  }
}

const handleClearHandled = () => {
  ElMessageBox.confirm(
      '确定要清空所有“已处理/已忽略”的历史预警吗？此操作主要用于清理测试数据。',
      '清空确认',
      {
        confirmButtonText: '确定清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      const res = await clearHandledWarnings()
      if (res.code === 200) {
        ElMessage.success(res.message || '清理成功')
        loadList()
      } else {
        ElMessage.error(res.message || '清理失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

const goToQrCode = (row) => {
  router.push({
    name: 'QrCodes',
    query: { code: row.uniqueCode }
  })
}

const goToInspection = (row) => {
  if (!row.linkedInspectionId) return
  router.push({
    name: 'Inspections',
    query: {
      inspectionId: row.linkedInspectionId,
      warningId: row.warningId
    }
  })
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.warnings-container {
  padding: 0;
}

.warning-panel {
  position: relative;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.header-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-copy small {
  font-size: 12px;
  font-weight: 500;
  color: #7b8797;
}

.header-icon-warning {
  color: #ef5d5d;
  font-size: 22px;
  box-shadow: 0 10px 18px rgba(239, 93, 93, 0.12);
}

.filter-bar {
  align-items: center;
}

.filter-input {
  width: 320px;
}

.filter-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.06);
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.clear-btn {
  margin-left: auto;
}

.warning-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 16px 16px 15px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.88));
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.04);
}

.stat-label-top {
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #8893a3;
}

.stat-num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
}

.stat-text {
  font-size: 13px;
  color: #7b8797;
  line-height: 1.5;
}

.stat-danger .stat-num { color: #f56c6c; }
.stat-warning .stat-num { color: #e6a23c; }
.stat-success .stat-num { color: #67c23a; }
.stat-info .stat-num { color: #409eff; }

.flat-table :deep(.warning-row) {
  background-color: #fff7f7 !important;
}

.flat-table :deep(.warning-row:hover > td) {
  background-color: #fff0f0 !important;
}

.product-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name {
  font-weight: 600;
  color: #172033;
}

.product-meta {
  font-size: 12px;
  color: #8d99aa;
  line-height: 1.6;
}

.warning-content-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warning-content-text {
  color: #4b5565;
  line-height: 1.65;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.warning-time-inline {
  font-size: 12px;
  color: #9aa5b3;
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

.warning-action-trigger {
  min-width: 58px;
  padding-inline: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 4px;
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.warning-case-card {
  padding: 18px 18px 16px;
  border-radius: 20px;
  background:
      radial-gradient(circle at top right, rgba(239, 93, 93, 0.08), transparent 28%),
      linear-gradient(180deg, #fffafb 0%, #fffefe 100%);
  border: 1px solid rgba(239, 93, 93, 0.12);
}

.warning-case-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.warning-case-title {
  font-size: 18px;
  font-weight: 700;
  color: #1b2331;
}

.warning-case-sub {
  margin-top: 5px;
  font-size: 13px;
  color: #7c889a;
}

.warning-case-code {
  margin-bottom: 10px;
  font-size: 14px;
  color: #485468;
}

.warning-case-code strong {
  color: #1b2331;
  word-break: break-all;
}

.warning-case-desc {
  margin: 0;
  color: #596579;
  line-height: 1.7;
}

.dialog-tip {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #435066;
}

.decision-grid {
  display: grid;
  gap: 12px;
}

.decision-card {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: #ffffff;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
  text-align: left;
}

.decision-card:hover {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.28);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
}

.decision-card.is-active {
  border-color: rgba(37, 99, 235, 0.42);
  background: linear-gradient(180deg, rgba(244, 248, 255, 0.98), rgba(255, 255, 255, 0.98));
  box-shadow: 0 16px 26px rgba(37, 99, 235, 0.12);
}

.decision-card-icon {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  font-size: 18px;
}

.tone-neutral {
  background: rgba(148, 163, 184, 0.14);
  color: #667085;
}

.tone-warning {
  background: rgba(245, 158, 11, 0.14);
  color: #d97706;
}

.tone-danger {
  background: rgba(239, 93, 93, 0.12);
  color: #dc2626;
}

.decision-card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.decision-card-title {
  font-size: 15px;
  font-weight: 700;
  color: #1d2635;
}

.decision-card-desc {
  font-size: 13px;
  line-height: 1.65;
  color: #6b7788;
  word-break: break-word;
}

@media (max-width: 1200px) {
  .warning-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .filter-input {
    width: 100%;
  }

  .filter-tip,
  .clear-btn {
    width: 100%;
    justify-content: center;
  }

  .clear-btn {
    margin-left: 0;
  }

  .warning-stats {
    grid-template-columns: 1fr;
  }

  .warning-case-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
