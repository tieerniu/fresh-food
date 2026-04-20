<template>
  <div class="records-container">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title-dot"></span>
            <div>
              <div class="page-title">溯源记录管理</div>
              <div class="page-subtitle">按批次维护生产、质检、流通等节点，形成完整溯源链路</div>
            </div>
          </div>
        </div>
      </template>

      <div class="record-toolbar">
        <el-input
            v-model="batchKeyword"
            :prefix-icon="Search"
            placeholder="搜索产品、批次号、基地或产地"
            clearable
            class="batch-search"
            @input="handleBatchSearch"
            @clear="handleBatchSearch"
        />
        <span class="toolbar-count">共 <strong>{{ filteredBatchList.length }}</strong> 个批次</span>
      </div>

      <div class="record-layout" v-loading="loading">
        <aside class="batch-panel">
          <div class="panel-head">
            <div>
              <span class="panel-title">批次目录</span>
              <span class="panel-desc">点击切换右侧时间线</span>
            </div>
          </div>

          <el-empty
              v-if="filteredBatchList.length === 0"
              description="暂无匹配批次"
              :image-size="90"
              class="side-empty"
          />

          <div v-else class="batch-list">
            <button
                v-for="batch in pagedBatchList"
                :key="batch.batchId"
                type="button"
                class="batch-card"
                :class="{ active: isBatchActive(batch) }"
                @click="selectBatch(batch.batchId)"
            >
              <div class="batch-card-top">
                <span class="batch-product-name">{{ batch.productName || '未命名产品' }}</span>
                <el-tag
                    size="small"
                    round
                    :type="getBatchProgress(batch) >= 100 ? 'success' : 'primary'"
                    effect="plain"
                >
                  {{ getBatchStageCount(batch) }}/{{ stageOptions.length }}
                </el-tag>
              </div>

              <el-tooltip :content="batch.batchCode || '-'" placement="top" :show-after="400">
                <span class="batch-code-chip">{{ formatBatchCode(batch.batchCode) }}</span>
              </el-tooltip>

              <div class="batch-meta">
                <span>{{ batch.manufacturerName || '未关联基地' }}</span>
                <span>{{ batch.origin || '未知产地' }}</span>
              </div>

              <el-progress
                  :percentage="getBatchProgress(batch)"
                  :show-text="false"
                  :stroke-width="5"
                  :color="getBatchProgressColor(batch)"
              />
            </button>

            <div class="batch-pager" v-if="filteredBatchList.length > batchPageSize">
              <el-pagination
                  v-model:current-page="batchPage"
                  :page-size="batchPageSize"
                  :total="filteredBatchList.length"
                  layout="prev, pager, next"
                  small
                  background
                  @current-change="handleBatchPageChange"
              />
            </div>
          </div>
        </aside>

        <section v-if="selectedBatch" class="trace-workspace">
          <div class="batch-hero">
            <div class="hero-main">
              <span class="hero-kicker">当前批次</span>
              <h3>{{ selectedBatch.productName || '未命名产品' }}</h3>
              <div class="hero-tags">
                <el-tooltip :content="selectedBatch.batchCode || '-'" placement="top" :show-after="400">
                  <span class="hero-chip code">{{ formatBatchCode(selectedBatch.batchCode) }}</span>
                </el-tooltip>
                <span class="hero-chip">{{ selectedBatch.manufacturerName || '未关联基地' }}</span>
                <span class="hero-chip">{{ selectedBatch.origin || '未知产地' }}</span>
              </div>
            </div>

            <div class="hero-stat">
              <span>节点数</span>
              <strong>{{ filteredRecords.length }}</strong>
            </div>
            <div class="hero-stat">
              <span>下一环节</span>
              <strong>{{ expectedNextStageLabel || '已完成' }}</strong>
            </div>
            <div class="hero-action">
              <el-button
                  type="primary"
                  class="add-node-btn"
                  @click="openAddDialog"
                  :disabled="!selectedBatchId || !expectedNextStage"
              >
                <el-icon><Plus /></el-icon>
                录入下一节点
              </el-button>
              <span>{{ expectedNextStageLabel ? `当前应录入：${expectedNextStageLabel}` : '标准环节已完整' }}</span>
            </div>
          </div>

          <div class="stage-flow">
            <div
                v-for="(stage, index) in stageOptions"
                :key="stage.value"
                class="stage-step"
                :class="getStageStepClass(stage.value)"
            >
              <span class="stage-index">{{ index + 1 }}</span>
              <span class="stage-name">{{ stage.label }}</span>
            </div>
          </div>

          <el-alert
              v-if="filteredRecords.length > 0 && !expectedNextStage"
              title="该批次已完成全部标准溯源环节，如需调整请编辑详情或删除后重新录入。"
              type="success"
              :closable="false"
              show-icon
              class="complete-alert"
          />

          <div v-if="filteredRecords.length === 0 && !loading" class="empty-workspace">
            <el-empty
                description="该批次暂无溯源节点，点击当前批次卡片中的按钮录入第一个节点"
                :image-size="120"
            />
          </div>

          <div v-else class="timeline-section">
            <div class="section-title">
              <div>
                <span>溯源时间线</span>
                <small>按记录时间正序展示，保证消费者端链路清晰</small>
              </div>
            </div>

            <el-timeline class="trace-timeline">
              <el-timeline-item
                  v-for="(record, index) in filteredRecords"
                  :key="record.recordId"
                  :type="getStageType(record.nodeStage)"
                  :hollow="index !== 0"
                  :timestamp="formatTime(record.recordedAt)"
                  placement="top"
                  size="large"
              >
                <el-card class="timeline-card" shadow="hover">
                  <div class="tl-card-header">
                    <div class="tl-card-left">
                      <el-tag :type="getStageType(record.nodeStage)" size="small" effect="dark" round>
                        {{ getStageLabel(record.nodeStage) }}
                      </el-tag>
                      <span class="tl-location" v-if="record.location">
                        <el-icon><Location /></el-icon>
                        {{ record.location }}
                      </span>
                    </div>
                    <div class="tl-card-right">
                      <span class="tl-operator" v-if="record.operator">
                        <el-icon><User /></el-icon>
                        {{ record.operator }}
                      </span>
                      <el-button type="primary" link size="small" @click="openEditDialog(record)">编辑</el-button>
                      <el-button type="danger" link size="small" @click="handleDelete(record)">删除</el-button>
                    </div>
                  </div>
                  <p class="tl-detail">{{ record.operationDetail }}</p>
                  <div class="tl-footer" v-if="record.temperatureData != null">
                    <span class="tl-temp">🌡️ {{ record.temperatureData }}℃</span>
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
          </div>
        </section>

        <section v-else class="trace-workspace empty-right">
          <el-empty
              description="请先从左侧选择一个产品批次"
              :image-size="150"
          />
        </section>
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑溯源记录' : '录入溯源节点'"
        width="560px"
        @close="resetForm"
    >
      <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="100px"
      >
        <el-form-item label="所属批次">
          <el-input
              :value="selectedBatch ? `${selectedBatch.productName} - ${formatBatchCode(selectedBatch.batchCode)}` : ''"
              disabled
          />
        </el-form-item>

        <el-form-item label="溯源环节" prop="nodeStage">
          <el-select
              v-model="formData.nodeStage"
              placeholder="请选择环节"
              style="width: 100%"
              :disabled="isEdit"
          >
            <el-option
                v-for="item in stageOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
                :disabled="!isEdit && item.value !== expectedNextStage"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="!isEdit && expectedNextStageLabel">
          <el-alert
              :title="`当前批次下一个应录入的标准环节为：${expectedNextStageLabel}`"
              type="info"
              :closable="false"
              show-icon
          />
        </el-form-item>

        <el-form-item v-if="isEdit">
          <el-alert
              title="为保证时间线顺序，编辑时仅允许修改环节详情；如需调整环节顺序，请删除后重新新增。"
              type="warning"
              :closable="false"
              show-icon
          />
        </el-form-item>

        <el-form-item label="操作员" prop="operator">
          <el-input v-model="formData.operator" placeholder="请输入操作员姓名" />
        </el-form-item>

        <el-form-item label="地点" prop="location">
          <el-input v-model="formData.location" placeholder="请输入操作地点" />
        </el-form-item>

        <el-form-item label="温度(℃)" prop="temperatureData">
          <el-input-number
              v-model="formData.temperatureData"
              :precision="1"
              :min="-50"
              :max="100"
              placeholder="选填"
              style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="详细描述" prop="operationDetail">
          <el-input
              v-model="formData.operationDetail"
              type="textarea"
              :rows="4"
              placeholder="请输入详细描述，如：完成有机蔬菜采摘，装箱入库"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ isEdit ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Location, User, Search } from '@element-plus/icons-vue'
import { getRecordList, addRecord, updateRecord, deleteRecord } from '@/api/record'
import { getProductList } from '@/api/product'

const batchList = ref([])
const selectedBatchId = ref(null)
const batchKeyword = ref('')
const batchPage = ref(1)
const batchPageSize = 5

const recordList = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const initialFormData = {
  recordId: null,
  batchId: null,
  nodeStage: '',
  operator: '',
  location: '',
  temperatureData: null,
  operationDetail: ''
}

const formData = reactive({ ...initialFormData })

const formRules = {
  nodeStage: [
    { required: true, message: '请选择溯源环节', trigger: 'change' }
  ],
  operationDetail: [
    { required: true, message: '请输入详细描述', trigger: 'blur' }
  ]
}

const stageOptions = [
  { value: 'Production', label: '种植' },
  { value: 'Processing', label: '采摘' },
  { value: 'QualityCheck', label: '质检' },
  { value: 'Packaging', label: '包装' },
  { value: 'Transportation', label: '运输' },
  { value: 'Sales', label: '销售' }
]

const selectedBatch = computed(() => {
  return batchList.value.find(batch => String(batch.batchId) === String(selectedBatchId.value)) || null
})

const filteredBatchList = computed(() => {
  const keyword = batchKeyword.value.trim().toLowerCase()
  const list = keyword
      ? batchList.value.filter(batch => {
        const text = [
          batch.productName,
          batch.batchCode,
          batch.manufacturerName,
          batch.origin
        ].filter(Boolean).join(' ').toLowerCase()
        return text.includes(keyword)
      })
      : batchList.value

  return [...list].sort((a, b) => {
    const dateA = formatTimeForSort(a.productionDate || a.createdAt)
    const dateB = formatTimeForSort(b.productionDate || b.createdAt)
    return dateB.localeCompare(dateA)
  })
})

const pagedBatchList = computed(() => {
  const start = (batchPage.value - 1) * batchPageSize
  return filteredBatchList.value.slice(start, start + batchPageSize)
})

const filteredRecords = computed(() => {
  if (!selectedBatchId.value) return []
  return getRecordsByBatch(selectedBatchId.value).sort((a, b) => {
    const timeA = formatTimeForSort(a.recordedAt)
    const timeB = formatTimeForSort(b.recordedAt)
    return timeA.localeCompare(timeB)
  })
})

const completedStageKeys = computed(() => {
  return new Set(filteredRecords.value.map(record => record.nodeStage).filter(Boolean))
})

const expectedNextStage = computed(() => {
  if (filteredRecords.value.length === 0) {
    return stageOptions[0]?.value || ''
  }
  const lastStage = filteredRecords.value[filteredRecords.value.length - 1]?.nodeStage
  const currentIndex = stageOptions.findIndex(option => option.value === lastStage)
  return currentIndex >= 0 ? (stageOptions[currentIndex + 1]?.value || '') : ''
})

const expectedNextStageLabel = computed(() => {
  const item = stageOptions.find(option => option.value === expectedNextStage.value)
  return item ? item.label : ''
})

const getRecordsByBatch = (batchId) => {
  return recordList.value.filter(record => String(record.batchId) === String(batchId))
}

const getBatchStageCount = (batch) => {
  return new Set(getRecordsByBatch(batch.batchId).map(record => record.nodeStage).filter(Boolean)).size
}

const getBatchProgress = (batch) => {
  return Math.round((getBatchStageCount(batch) / stageOptions.length) * 100)
}

const getBatchProgressColor = (batch) => {
  const progress = getBatchProgress(batch)
  if (progress >= 100) return '#67c23a'
  if (progress >= 50) return '#409eff'
  return '#e6a23c'
}

const isBatchActive = (batch) => {
  return String(batch.batchId) === String(selectedBatchId.value)
}

const selectBatch = (batchId) => {
  selectedBatchId.value = batchId
}

const handleBatchSearch = () => {
  batchPage.value = 1
  const firstBatch = filteredBatchList.value[0]
  if (firstBatch) {
    selectBatch(firstBatch.batchId)
  }
}

const handleBatchPageChange = () => {
  const firstBatch = pagedBatchList.value[0]
  if (firstBatch) {
    selectBatch(firstBatch.batchId)
  }
}

const ensureSelectedBatch = () => {
  if (selectedBatchId.value && selectedBatch.value) return
  selectedBatchId.value = filteredBatchList.value[0]?.batchId || batchList.value[0]?.batchId || null
}

const getStageStepClass = (stage) => {
  return {
    completed: completedStageKeys.value.has(stage),
    current: expectedNextStage.value === stage
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  if (Array.isArray(timeStr)) {
    const [year, month, day, hour, minute] = timeStr
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour || 0).padStart(2, '0')}:${String(minute || 0).padStart(2, '0')}`
  }
  return String(timeStr).replace('T', ' ').substring(0, 16)
}

const formatTimeForSort = (timeStr) => {
  if (!timeStr) return ''
  if (Array.isArray(timeStr)) {
    const [year, month, day, hour, minute, second] = timeStr
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour || 0).padStart(2, '0')}:${String(minute || 0).padStart(2, '0')}:${String(second || 0).padStart(2, '0')}`
  }
  return String(timeStr)
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

const getStageLabel = (stage) => {
  const item = stageOptions.find(option => option.value === stage)
  return item ? item.label : stage
}

const getStageType = (stage) => {
  const types = {
    Production: 'success',
    Processing: 'primary',
    QualityCheck: 'warning',
    Packaging: '',
    Transportation: 'danger',
    Sales: 'info'
  }
  return types[stage] || 'info'
}

const loadBatchList = async () => {
  try {
    const res = await getProductList()
    if (res.code === 200) {
      batchList.value = res.data || []
      ensureSelectedBatch()
    } else {
      ElMessage.error(res.message || '批次列表加载失败')
    }
  } catch (error) {
    ElMessage.error('批次列表加载失败: ' + error.message)
  }
}

const loadRecordList = async () => {
  loading.value = true
  try {
    const res = await getRecordList()
    if (res.code === 200) {
      recordList.value = res.data || []
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
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择一个产品批次')
    return
  }
  if (!expectedNextStage.value) {
    ElMessage.info('该批次已完成全部标准溯源环节')
    return
  }
  isEdit.value = false
  resetForm()
  formData.batchId = selectedBatchId.value
  formData.nodeStage = expectedNextStage.value
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  Object.assign(formData, {
    recordId: row.recordId,
    batchId: row.batchId,
    nodeStage: row.nodeStage,
    operator: row.operator || '',
    location: row.location || '',
    temperatureData: row.temperatureData,
    operationDetail: row.operationDetail || ''
  })
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(formData, initialFormData)
  formData.batchId = selectedBatchId.value
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
      const submitData = { ...formData, batchId: selectedBatchId.value }

      let res
      if (isEdit.value) {
        res = await updateRecord(submitData)
      } else {
        res = await addRecord(submitData)
      }

      if (res.code === 200) {
        ElMessage.success(isEdit.value ? '编辑成功' : '添加成功')
        dialogVisible.value = false
        loadRecordList()
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
  ElMessageBox.confirm(`确定要删除 "${getStageLabel(row.nodeStage)}" 节点吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteRecord(row.recordId)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadRecordList()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

onMounted(async () => {
  await Promise.all([loadBatchList(), loadRecordList()])
  ensureSelectedBatch()
})

watch(filteredBatchList, (list) => {
  const maxPage = Math.max(1, Math.ceil(list.length / batchPageSize))
  if (batchPage.value > maxPage) {
    batchPage.value = maxPage
  }
  if (list.length > 0 && !list.some(batch => String(batch.batchId) === String(selectedBatchId.value))) {
    selectedBatchId.value = list[0].batchId
  }
})
</script>

<style scoped>
.records-container {
  padding: 0;
}

.main-card {
  border-radius: 18px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #1d2129;
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
  font-weight: 400;
}

.add-node-btn {
  border-radius: 16px;
  padding: 10px 18px;
  font-weight: 600;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.18);
}

.record-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.batch-search {
  width: 360px;
}

.batch-search :deep(.el-input__wrapper) {
  border-radius: 16px;
  padding: 0 14px;
  min-height: 44px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  background: #fbfdff;
}

.toolbar-count {
  margin-left: auto;
  font-size: 13px;
  color: #8a94a6;
}

.toolbar-count strong {
  color: #409eff;
}

.record-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.batch-panel,
.trace-workspace {
  border: 1px solid #edf0f5;
  border-radius: 18px;
  background: #fff;
}

.batch-panel {
  padding: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.panel-desc {
  display: block;
  margin-top: 4px;
  color: #98a2b3;
  font-size: 12px;
}

.side-empty {
  padding-top: 90px;
}

.batch-list {
  display: flex;
  flex-direction: column;
}

.batch-card {
  width: 100%;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  background: #fbfcff;
  padding: 14px;
  margin-bottom: 12px;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.batch-card:last-of-type {
  margin-bottom: 0;
}

.batch-card:hover {
  border-color: #cfe3ff;
  background: #f7fbff;
}

.batch-card.active {
  border-color: #409eff;
  background: linear-gradient(135deg, #f3f8ff 0%, #ffffff 100%);
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.12);
}

.batch-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
}

.batch-product-name {
  color: #1f2937;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-code-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 5px 10px;
  border-radius: 999px;
  background: #f3f6fb;
  color: #667085;
  font-size: 12px;
  line-height: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 12px 0 10px;
  color: #8a94a6;
  font-size: 12px;
}

.batch-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-pager {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

.batch-pager :deep(.el-pagination) {
  --el-pagination-button-width: 28px;
  --el-pagination-button-height: 28px;
}

.trace-workspace {
  min-height: 560px;
  padding: 18px;
}

.empty-right {
  display: flex;
  align-items: center;
  justify-content: center;
}

.batch-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px 128px 172px;
  gap: 14px;
  align-items: stretch;
  padding: 18px;
  border-radius: 18px;
  background: linear-gradient(135deg, #f0f7ff 0%, #f7fbfd 100%);
  border: 1px solid #eaf1fb;
  margin-bottom: 16px;
}

.hero-main {
  min-width: 0;
}

.hero-kicker {
  color: #8a94a6;
  font-size: 12px;
}

.hero-main h3 {
  margin: 6px 0 12px;
  color: #1f2937;
  font-size: 22px;
  line-height: 1.2;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-chip {
  display: inline-flex;
  align-items: center;
  max-width: 220px;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid #edf2f7;
  color: #667085;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-chip.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.hero-stat {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #edf2f7;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.hero-stat span {
  color: #8a94a6;
  font-size: 12px;
}

.hero-stat strong {
  color: #1f2937;
  font-size: 22px;
}

.hero-action {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #edf2f7;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 9px;
}

.hero-action .add-node-btn {
  width: 100%;
  justify-content: center;
}

.hero-action span {
  color: #8a94a6;
  font-size: 12px;
  text-align: center;
  line-height: 1.4;
}

.stage-flow {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.stage-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 14px;
  background: #f7f8fb;
  color: #8a94a6;
  border: 1px solid #eef2f7;
}

.stage-step.completed {
  background: #f1fbf4;
  color: #209653;
  border-color: #dcefe3;
}

.stage-step.current {
  background: #fff8eb;
  color: #c27616;
  border-color: #f6dfb9;
}

.stage-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  font-size: 12px;
  font-weight: 700;
}

.stage-name {
  font-size: 13px;
  font-weight: 600;
}

.complete-alert {
  margin-bottom: 16px;
}

.empty-workspace {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.timeline-section {
  border: 1px solid #edf0f5;
  border-radius: 18px;
  padding: 18px 18px 4px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.section-title span {
  display: block;
  font-size: 17px;
  font-weight: 800;
  color: #1f2937;
}

.section-title small {
  display: block;
  margin-top: 4px;
  color: #98a2b3;
  font-size: 12px;
}

.trace-timeline {
  padding: 0 0 0 12px;
}

.timeline-card {
  border-radius: 14px;
  margin-top: -4px;
  transition: box-shadow 0.2s;
  border-color: #edf0f5;
}

.timeline-card:hover {
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.tl-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.tl-card-left,
.tl-card-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tl-location,
.tl-operator {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.tl-location {
  color: #8a94a6;
}

.tl-operator {
  color: #606266;
  font-weight: 500;
  background: #f4f6f8;
  padding: 3px 10px;
  border-radius: 12px;
}

.tl-detail {
  color: #1f2937;
  margin: 12px 0 4px;
  line-height: 1.7;
  font-size: 14px;
}

.tl-footer {
  margin-top: 8px;
}

.tl-temp {
  font-size: 12px;
  color: #98a2b3;
}

@media (max-width: 1200px) {
  .record-layout {
    grid-template-columns: 1fr;
  }

  .batch-panel {
    min-height: auto;
  }

  .stage-flow {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .card-header,
  .record-toolbar,
  .batch-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .batch-search {
    width: 100%;
  }

  .batch-hero {
    display: flex;
  }

  .stage-flow {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
