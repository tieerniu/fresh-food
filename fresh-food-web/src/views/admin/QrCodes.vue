<template>
  <div class="qrcodes-container">
    <el-card shadow="hover" class="qr-panel">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title-dot"></span>
            <div class="header-copy">
              <span>二维码管理</span>
              <small>先按批次看发码和风险，再进入单码明细处理</small>
            </div>
          </div>
          <el-button type="primary" class="generate-btn" @click="openGenerateDialog()">
            <el-icon><Plus /></el-icon>
            生成二维码
          </el-button>
        </div>
      </template>

      <div class="entry-row">
        <div class="entry-tip">
          当前移动端入口：
          <span>{{ getBaseUrl() }}</span>
        </div>
        <span class="sync-tip">扫码数据每 3 秒自动同步</span>
      </div>

      <div class="qr-stats">
        <div class="stat-card stat-total">
          <span class="stat-label">二维码总数</span>
          <strong>{{ statusSummary.total }}</strong>
          <small>所有已生成防伪码</small>
        </div>
        <div class="stat-card stat-active">
          <span class="stat-label">激活码</span>
          <strong>{{ statusSummary.active }}</strong>
          <small>消费者可正常查询</small>
        </div>
        <div class="stat-card stat-recalled">
          <span class="stat-label">召回码</span>
          <strong>{{ statusSummary.recalled }}</strong>
          <small>已冻结或召回处理</small>
        </div>
        <div class="stat-card stat-expired">
          <span class="stat-label">过期码</span>
          <strong>{{ statusSummary.expired }}</strong>
          <small>超过业务有效期</small>
        </div>
        <div class="stat-card stat-risk">
          <span class="stat-label">风险码</span>
          <strong>{{ statusSummary.risk }}</strong>
          <small>存在未闭环预警</small>
        </div>
      </div>

      <div class="filter-bar">
        <el-input
            v-model="keyword"
            placeholder="搜索产品、批次、唯一码、基地或产地"
            clearable
            :prefix-icon="Search"
            class="filter-input"
            @clear="currentPage = 1"
            @input="currentPage = 1"
        />
        <el-select
            v-model="filterStatus"
            placeholder="二维码状态"
            clearable
            class="filter-select"
            @change="currentPage = 1"
        >
          <el-option label="全部状态" value="" />
          <el-option label="含激活码" value="Active" />
          <el-option label="含召回码" value="Recalled" />
          <el-option label="含过期码" value="Expired" />
        </el-select>
        <el-select
            v-model="riskFilter"
            placeholder="风险筛选"
            clearable
            class="filter-select"
            @change="currentPage = 1"
        >
          <el-option label="全部批次" value="" />
          <el-option label="仅看风险批次" value="risk" />
          <el-option label="仅看正常批次" value="normal" />
        </el-select>
        <span class="filter-summary">共 <strong>{{ filteredBatchRows.length }}</strong> 个批次</span>
      </div>

      <el-table
          v-loading="loading"
          :data="pagedBatchRows"
          stripe
          row-key="batchId"
          style="width: 100%"
          class="flat-table batch-table"
      >
        <el-table-column label="产品批次" min-width="250">
          <template #default="{ row }">
            <div class="batch-cell">
              <div class="batch-name">{{ row.productName }}</div>
              <el-tooltip :content="row.batchCode || '-'" placement="top" :show-after="400">
                <span class="batch-code-chip">{{ formatBatchCode(row.batchCode) }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="基地 / 产地" min-width="160">
          <template #default="{ row }">
            <div class="origin-cell">
              <el-tag v-if="row.manufacturerName" size="small" round>
                {{ row.manufacturerName }}
              </el-tag>
              <span v-else class="muted-text">未关联基地</span>
              <span class="origin-text">{{ row.origin || '未知产地' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="发码进度" min-width="170" align="center">
          <template #default="{ row }">
            <div class="issue-cell">
              <div class="issue-count">
                <strong>{{ row.generatedCount }}</strong>
                <span>/</span>
                <span>{{ row.batchQuantity || row.generatedCount }}</span>
              </div>
              <el-progress
                  :percentage="row.issuePercent"
                  :show-text="false"
                  :stroke-width="7"
                  :color="row.issuePercent >= 100 ? '#f56c6c' : '#409eff'"
              />
              <small>剩余 {{ row.remainingCount }} 个可生成</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态分布" min-width="210">
          <template #default="{ row }">
            <div class="status-stack">
              <el-tag type="success" size="small" round>激活 {{ row.statusCounts.Active }}</el-tag>
              <el-tag type="warning" size="small" round>召回 {{ row.statusCounts.Recalled }}</el-tag>
              <el-tag type="danger" size="small" round>过期 {{ row.statusCounts.Expired }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="风险 / 扫码" min-width="180" align="center">
          <template #default="{ row }">
            <div class="risk-scan-cell">
              <el-tag v-if="row.riskCount > 0" type="danger" round effect="dark">
                {{ row.riskCount }} 个风险码
              </el-tag>
              <el-tag v-else type="success" round effect="plain">正常</el-tag>
              <div class="scan-inline">
                <strong>{{ row.scanTotal }}</strong>
                <span>累计扫码</span>
                <small>单码最高 {{ row.maxScanCount }}</small>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" link @click="openBatchDetail(row)">
                查看二维码
              </el-button>
              <el-button
                  type="primary"
                  link
                  :disabled="row.remainingCount <= 0"
                  @click="openGenerateDialog(row.batchId)"
              >
                继续生成
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
            :total="filteredBatchRows.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
            small
        />
      </div>
    </el-card>

    <el-drawer
        v-model="detailDrawerVisible"
        size="86%"
        class="qr-detail-drawer"
        destroy-on-close
    >
      <template #header>
        <div class="drawer-title" v-if="selectedBatchRow">
          <div>
            <span>{{ selectedBatchRow.productName }} 二维码明细</span>
            <small>{{ formatBatchCode(selectedBatchRow.batchCode) }} / {{ selectedBatchRow.manufacturerName || '未关联基地' }}</small>
          </div>
          <el-tag :type="selectedBatchRow.riskCount > 0 ? 'danger' : 'success'" round>
            {{ selectedBatchRow.riskCount > 0 ? `${selectedBatchRow.riskCount} 个风险码` : '状态正常' }}
          </el-tag>
        </div>
      </template>

      <div v-if="selectedBatchRow" class="drawer-content">
        <div class="drawer-summary">
          <div class="drawer-summary-item">
            <span>发码进度</span>
            <strong>{{ selectedBatchRow.generatedCount }} / {{ selectedBatchRow.batchQuantity || selectedBatchRow.generatedCount }}</strong>
          </div>
          <div class="drawer-summary-item">
            <span>累计扫码</span>
            <strong>{{ selectedBatchRow.scanTotal }}</strong>
          </div>
          <div class="drawer-summary-item">
            <span>激活 / 召回 / 过期</span>
            <strong>
              {{ selectedBatchRow.statusCounts.Active }} /
              {{ selectedBatchRow.statusCounts.Recalled }} /
              {{ selectedBatchRow.statusCounts.Expired }}
            </strong>
          </div>
          <div class="drawer-summary-item">
            <span>剩余可发码</span>
            <strong>{{ selectedBatchRow.remainingCount }}</strong>
          </div>
        </div>

        <div class="drawer-toolbar">
          <el-input
              v-model="detailKeyword"
              placeholder="搜索当前批次下的唯一码"
              clearable
              :prefix-icon="Search"
              class="drawer-search"
              @clear="detailCurrentPage = 1"
              @input="detailCurrentPage = 1"
          />
          <el-select
              v-model="detailStatus"
              placeholder="状态筛选"
              clearable
              class="drawer-status"
              @change="detailCurrentPage = 1"
          >
            <el-option label="全部状态" value="" />
            <el-option label="激活" value="Active" />
            <el-option label="召回" value="Recalled" />
            <el-option label="过期" value="Expired" />
          </el-select>
          <span class="drawer-count">共 <strong>{{ detailFilteredQrCodes.length }}</strong> 个二维码</span>
          <el-button
              type="primary"
              class="drawer-generate-btn"
              :disabled="selectedBatchRow.remainingCount <= 0"
              @click="openGenerateDialog(selectedBatchRow.batchId)"
          >
            <el-icon><Plus /></el-icon>
            继续生成
          </el-button>
        </div>

        <el-table
            :data="detailPagedQrCodes"
            stripe
            style="width: 100%"
            class="flat-table detail-table"
            row-key="qrId"
        >
          <el-table-column label="唯一码" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="code-cell">
                <el-text type="primary" tag="code">{{ row.uniqueCode }}</el-text>
                <el-tag v-if="isRiskCode(row.uniqueCode)" type="danger" size="small" round>风险</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small" round>
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="scanCount" label="扫码次数" width="100" align="center" />

          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>

          <el-table-column label="二维码" width="100" align="center">
            <template #default="{ row }">
              <el-popover
                  placement="left"
                  :width="180"
                  trigger="hover"
                  @show="() => onPopoverShow(row.uniqueCode)"
              >
                <template #reference>
                  <el-button type="primary" link size="small" class="qr-view-btn">
                    <el-icon><View /></el-icon>
                    查看
                  </el-button>
                </template>
                <div class="qr-popover-body">
                  <canvas :ref="(el) => setPopoverRef(row.uniqueCode, el)" class="qr-popover-canvas"></canvas>
                  <span class="qr-popover-code">{{ row.uniqueCode }}</span>
                </div>
              </el-popover>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="(command) => handleActionCommand(row, command)">
                <el-button type="primary" link class="action-trigger-btn">
                  操作
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="download">下载二维码</el-dropdown-item>
                    <el-dropdown-item
                        v-for="(item, index) in getAvailableStatusOptions(row)"
                        :key="item.value"
                        :command="`status:${item.value}`"
                        :divided="index === 0"
                    >
                      标记为{{ item.label }}
                    </el-dropdown-item>
                    <el-dropdown-item v-if="getAvailableStatusOptions(row).length === 0" divided disabled>
                      当前状态不可变更
                    </el-dropdown-item>
                    <el-dropdown-item divided command="delete">
                      删除二维码
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
              v-model:current-page="detailCurrentPage"
              v-model:page-size="detailPageSize"
              :page-sizes="[10, 20, 50]"
              :total="detailFilteredQrCodes.length"
              layout="total, sizes, prev, pager, next, jumper"
              background
              small
          />
        </div>
      </div>
    </el-drawer>

    <el-dialog
        v-model="dialogVisible"
        title="批量生成二维码"
        width="500px"
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
              filterable
              style="width: 100%"
          >
            <el-option
                v-for="batch in batchList"
                :key="batch.batchId"
                :label="buildBatchOptionLabel(batch)"
                :value="batch.batchId"
                :disabled="getBatchRemainingCount(batch) <= 0"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="selectedBatchForGenerate" label="剩余可发码">
          <el-tag
              :type="getBatchRemainingCount(selectedBatchForGenerate) > 0 ? 'success' : 'danger'"
              size="small"
              round
          >
            {{ getBatchRemainingCount(selectedBatchForGenerate) }} 个
          </el-tag>
        </el-form-item>

        <el-form-item label="生成数量" prop="count">
          <el-input-number
              v-model="formData.count"
              :min="1"
              :max="100"
              placeholder="请输入生成数量"
              style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleGenerate">
          生成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowDown, Search, View } from '@element-plus/icons-vue'
import { getQrCodeList, generateQrCode, updateQrCodeStatus, deleteQrCode } from '@/api/qrcode'
import { getProductList } from '@/api/product'
import { getTracePublicConfig } from '@/api/trace'
import { getWarningList } from '@/api/warning'
import QRCode from 'qrcode'

const route = useRoute()

const STATUS_OPTIONS = {
  Active: [{ value: 'Recalled', label: '召回' }, { value: 'Expired', label: '过期' }],
  Recalled: [],
  Expired: []
}

const qrCodeList = ref([])
const batchList = ref([])
const warningList = ref([])
const loading = ref(false)
const h5BaseUrl = ref(window.location.origin)

const keyword = ref('')
const filterStatus = ref('')
const riskFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const detailDrawerVisible = ref(false)
const selectedBatchId = ref(null)
const detailKeyword = ref('')
const detailStatus = ref('')
const detailCurrentPage = ref(1)
const detailPageSize = ref(10)

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)

const formData = reactive({
  batchId: null,
  count: 1
})

const formRules = {
  batchId: [{ required: true, message: '请选择产品批次', trigger: 'change' }],
  count: [{ required: true, message: '请输入生成数量', trigger: 'blur' }]
}

const popoverRefs = reactive({})
const renderedCodes = reactive({})

const selectedBatchForGenerate = computed(() =>
    batchList.value.find(item => String(item.batchId) === String(formData.batchId)) || null
)

const openWarningList = computed(() =>
    warningList.value.filter(item => isOpenWarning(item.status))
)

const riskCodeSet = computed(() => {
  return new Set(openWarningList.value.map(item => item.uniqueCode).filter(Boolean))
})

const batchRows = computed(() => {
  const rows = batchList.value.map(batch => buildBatchRow(batch))
  const knownBatchIds = new Set(batchList.value.map(batch => String(batch.batchId)))
  const orphanGroups = new Map()

  qrCodeList.value.forEach(qr => {
    const key = String(qr.batchId || 'unknown')
    if (knownBatchIds.has(key)) return
    if (!orphanGroups.has(key)) orphanGroups.set(key, [])
    orphanGroups.get(key).push(qr)
  })

  orphanGroups.forEach((qrs, key) => {
    rows.push(buildBatchRow({
      batchId: key,
      batchCode: '未知批次',
      productName: '未知产品',
      manufacturerName: '',
      origin: '',
      batchQuantity: qrs.length
    }, qrs))
  })

  return rows.sort((a, b) => {
    if (b.riskCount !== a.riskCount) return b.riskCount - a.riskCount
    if (b.scanTotal !== a.scanTotal) return b.scanTotal - a.scanTotal
    return b.latestSort.localeCompare(a.latestSort)
  })
})

const filteredBatchRows = computed(() => {
  let list = batchRows.value

  if (keyword.value) {
    const text = keyword.value.toLowerCase()
    list = list.filter(row => {
      const batchText = [
        row.productName,
        row.batchCode,
        row.manufacturerName,
        row.origin
      ].filter(Boolean).join(' ').toLowerCase()
      const codeMatched = row.qrCodes.some(qr => qr.uniqueCode && qr.uniqueCode.toLowerCase().includes(text))
      return batchText.includes(text) || codeMatched
    })
  }

  if (filterStatus.value) {
    list = list.filter(row => row.statusCounts[filterStatus.value] > 0)
  }

  if (riskFilter.value === 'risk') {
    list = list.filter(row => row.riskCount > 0)
  } else if (riskFilter.value === 'normal') {
    list = list.filter(row => row.riskCount === 0)
  }

  return list
})

const pagedBatchRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredBatchRows.value.slice(start, start + pageSize.value)
})

const selectedBatchRow = computed(() => {
  return batchRows.value.find(row => String(row.batchId) === String(selectedBatchId.value)) || null
})

const detailQrCodes = computed(() => {
  if (!selectedBatchRow.value) return []
  return [...selectedBatchRow.value.qrCodes].sort((a, b) => {
    return formatTimeForSort(b.createdAt).localeCompare(formatTimeForSort(a.createdAt))
  })
})

const detailFilteredQrCodes = computed(() => {
  let list = detailQrCodes.value

  if (detailKeyword.value) {
    const text = detailKeyword.value.toLowerCase()
    list = list.filter(item => item.uniqueCode && item.uniqueCode.toLowerCase().includes(text))
  }

  if (detailStatus.value) {
    list = list.filter(item => normalizeStatus(item.status) === detailStatus.value)
  }

  return list
})

const detailPagedQrCodes = computed(() => {
  const start = (detailCurrentPage.value - 1) * detailPageSize.value
  return detailFilteredQrCodes.value.slice(start, start + detailPageSize.value)
})

const statusSummary = computed(() => {
  const summary = { total: qrCodeList.value.length, active: 0, recalled: 0, expired: 0, risk: 0 }
  qrCodeList.value.forEach(item => {
    const status = normalizeStatus(item.status)
    if (status === 'Active') summary.active += 1
    if (status === 'Recalled') summary.recalled += 1
    if (status === 'Expired') summary.expired += 1
    if (riskCodeSet.value.has(item.uniqueCode)) summary.risk += 1
  })
  return summary
})

const getBaseUrl = () => {
  return h5BaseUrl.value || window.location.origin
}

const buildTraceUrl = (uniqueCode) => {
  try {
    const url = new URL(getBaseUrl())
    url.searchParams.set('code', uniqueCode)
    return url.toString()
  } catch (error) {
    const baseUrl = getBaseUrl().replace(/\/$/, '')
    const delimiter = baseUrl.includes('?') ? '&' : '?'
    return `${baseUrl}${delimiter}code=${encodeURIComponent(uniqueCode)}`
  }
}

const normalizeStatus = (status) => {
  return status === 'Sold' ? 'Active' : (status || 'Active')
}

const isOpenWarning = (status) => {
  return status === 'Pending' || status === '待处理' || status === 'InInspection' || status === '待质检'
}

const isRiskCode = (code) => {
  return riskCodeSet.value.has(code)
}

const getBatchQrCodes = (batchId) => {
  return qrCodeList.value.filter(item => String(item.batchId) === String(batchId))
}

const buildBatchRow = (batch, providedQrCodes = null) => {
  const qrCodes = (providedQrCodes || getBatchQrCodes(batch.batchId)).map(item => ({
    ...item,
    status: normalizeStatus(item.status)
  }))
  const statusCounts = { Active: 0, Recalled: 0, Expired: 0 }
  let scanTotal = 0
  let maxScanCount = 0
  let latestSort = ''

  qrCodes.forEach(item => {
    const status = normalizeStatus(item.status)
    if (statusCounts[status] !== undefined) statusCounts[status] += 1
    const scanCount = Number(item.scanCount || 0)
    scanTotal += scanCount
    maxScanCount = Math.max(maxScanCount, scanCount)
    const createdSort = formatTimeForSort(item.createdAt)
    if (createdSort > latestSort) latestSort = createdSort
  })

  const generatedCount = qrCodes.length
  const batchQuantity = Number(batch.batchQuantity || 0)
  const displayQuantity = batchQuantity > 0 ? batchQuantity : generatedCount
  const remainingCount = Math.max(displayQuantity - generatedCount, 0)
  const issuePercent = displayQuantity > 0 ? Math.min(100, Math.round((generatedCount / displayQuantity) * 100)) : 0
  const riskCount = qrCodes.filter(item => riskCodeSet.value.has(item.uniqueCode)).length

  return {
    batchId: batch.batchId,
    batch,
    productName: batch.productName || '未知产品',
    batchCode: batch.batchCode || '',
    manufacturerName: batch.manufacturerName || '',
    origin: batch.origin || '',
    batchQuantity: displayQuantity,
    generatedCount,
    remainingCount,
    issuePercent,
    statusCounts,
    scanTotal,
    maxScanCount,
    riskCount,
    latestSort,
    qrCodes
  }
}

const setPopoverRef = (code, el) => {
  if (el) {
    popoverRefs[code] = el
  }
}

const onPopoverShow = async (code) => {
  if (renderedCodes[code]) return
  await nextTick()
  const canvas = popoverRefs[code]
  if (!canvas) return

  const url = buildTraceUrl(code)
  try {
    await QRCode.toCanvas(canvas, url, {
      width: 140,
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' }
    })
    renderedCodes[code] = true
  } catch (err) {
    console.error('生成二维码失败:', err)
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

const getBatchRemainingCount = (batch) => {
  if (!batch) return 0
  const total = Number(batch.batchQuantity || 0)
  const generated = getBatchQrCodes(batch.batchId).length
  return Math.max(total - generated, 0)
}

const buildBatchOptionLabel = (batch) => {
  return `${batch.productName} - ${formatBatchCode(batch.batchCode)}（剩余 ${getBatchRemainingCount(batch)} 个）`
}

const downloadQrCode = async (row) => {
  const url = buildTraceUrl(row.uniqueCode)
  try {
    const canvas = document.createElement('canvas')
    await QRCode.toCanvas(canvas, url, {
      width: 300,
      margin: 2,
      color: { dark: '#000000', light: '#ffffff' }
    })
    const link = document.createElement('a')
    link.download = `${row.uniqueCode}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    ElMessage.success('二维码下载成功')
  } catch (err) {
    ElMessage.error('下载失败: ' + err.message)
  }
}

const getStatusLabel = (status) => {
  const labels = { Active: '激活', Recalled: '召回', Expired: '过期' }
  return labels[normalizeStatus(status)] || status
}

const getStatusType = (status) => {
  const types = { Active: 'success', Recalled: 'warning', Expired: 'danger' }
  return types[normalizeStatus(status)] || 'info'
}

const getAvailableStatusOptions = (row) => {
  return STATUS_OPTIONS[normalizeStatus(row.status)] || []
}

const openBatchDetail = (row, targetCode = '') => {
  selectedBatchId.value = row.batchId
  detailKeyword.value = targetCode
  detailStatus.value = ''
  detailCurrentPage.value = 1
  detailDrawerVisible.value = true
}

const focusCodeFromRoute = (code) => {
  if (!code) return
  const target = qrCodeList.value.find(item => item.uniqueCode === code)
  if (!target) return
  const row = batchRows.value.find(item => String(item.batchId) === String(target.batchId))
  if (row) {
    openBatchDetail(row, code)
  }
}

const loadBatchList = async () => {
  try {
    const res = await getProductList()
    if (res.code === 200) {
      batchList.value = res.data || []
    }
  } catch (error) {
    console.error('加载批次列表失败:', error)
  }
}

const loadWarningList = async (isSilent = false) => {
  try {
    const res = await getWarningList()
    if (res.code === 200) {
      warningList.value = res.data || []
    } else if (!isSilent) {
      console.warn(res.message || '预警数据加载失败')
    }
  } catch (error) {
    if (!isSilent) console.warn('预警数据加载失败:', error)
  }
}

const loadTracePublicConfig = async () => {
  try {
    const res = await getTracePublicConfig()
    if (res.success && res.h5BaseUrl) {
      h5BaseUrl.value = res.h5BaseUrl
    }
  } catch (error) {
    console.error('加载移动端入口配置失败:', error)
  }
}

const loadList = async (isSilent = false) => {
  if (!isSilent) loading.value = true
  try {
    const res = await getQrCodeList()
    if (res.code === 200) {
      qrCodeList.value = (res.data || []).map(item => ({
        ...item,
        status: normalizeStatus(item.status)
      }))
    } else if (!isSilent) {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    if (!isSilent) ElMessage.error('请求失败: ' + error.message)
  } finally {
    if (!isSilent) loading.value = false
  }
}

const openGenerateDialog = async (batchId = null) => {
  await loadBatchList()
  resetForm()
  if (batchId !== null && batchId !== undefined) {
    formData.batchId = batchId
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formData.batchId = null
  formData.count = 1
  if (formRef.value) formRef.value.resetFields()
}

const handleGenerate = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (selectedBatchForGenerate.value && formData.count > getBatchRemainingCount(selectedBatchForGenerate.value)) {
      ElMessage.warning(`超出当前批次剩余可发码数量，最多还能生成 ${getBatchRemainingCount(selectedBatchForGenerate.value)} 个`)
      return
    }
    submitLoading.value = true
    try {
      const res = await generateQrCode(formData)
      if (res.code === 200) {
        ElMessage.success(res.message || '生成成功')
        dialogVisible.value = false
        await loadBatchList()
        await loadList()
      } else {
        ElMessage.error(res.message || '生成失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleStatusChange = async (row, status) => {
  if (!getAvailableStatusOptions(row).some(item => item.value === status)) {
    ElMessage.warning('当前状态不允许变更为目标状态')
    return
  }
  try {
    const res = await updateQrCodeStatus({ qrId: row.qrId, status })
    if (res.code === 200) {
      ElMessage.success('状态更新成功')
      loadList()
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  }
}

const handleActionCommand = (row, command) => {
  if (command === 'download') {
    downloadQrCode(row)
    return
  }
  if (command === 'delete') {
    handleDelete(row)
    return
  }
  if (typeof command === 'string' && command.startsWith('status:')) {
    handleStatusChange(row, command.replace('status:', ''))
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除二维码 "${row.uniqueCode}" 吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteQrCode(row.qrId)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        await loadBatchList()
        await loadList()
        await loadWarningList(true)
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (error) {
      ElMessage.error('请求失败: ' + error.message)
    }
  }).catch(() => {})
}

let timer = null

watch(
    () => route.query.code,
    (code) => {
      const text = typeof code === 'string' ? code : ''
      keyword.value = text
      currentPage.value = 1
      if (text && qrCodeList.value.length > 0) {
        focusCodeFromRoute(text)
      }
    },
    { immediate: true }
)

onMounted(async () => {
  await loadTracePublicConfig()
  await Promise.all([loadBatchList(), loadList(), loadWarningList(true)])

  if (typeof route.query.code === 'string') {
    focusCodeFromRoute(route.query.code)
  }

  timer = setInterval(() => {
    loadList(true)
    loadWarningList(true)
  }, 3000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.qrcodes-container {
  padding: 0;
}

.qr-panel {
  position: relative;
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

.header-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-copy span {
  font-size: 19px;
  font-weight: 800;
  color: #1f2937;
}

.header-copy small {
  color: #8a94a6;
  font-size: 13px;
  font-weight: 400;
}

.generate-btn,
.drawer-generate-btn {
  border-radius: 16px;
  padding: 10px 18px;
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.18);
}

.entry-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.entry-tip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.06);
  border: 1px solid rgba(37, 99, 235, 0.08);
  font-size: 13px;
  color: #909399;
}

.entry-tip span {
  color: #409eff;
  font-weight: 600;
  word-break: break-all;
}

.sync-tip {
  color: #98a2b3;
  font-size: 12px;
}

.qr-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.stat-card {
  min-height: 118px;
  border-radius: 20px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.9));
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  color: #8a94a6;
  font-size: 12px;
}

.stat-card strong {
  font-size: 30px;
  line-height: 1;
}

.stat-card small {
  color: #8a94a6;
  font-size: 12px;
}

.stat-total strong { color: #2563eb; }
.stat-active strong { color: #1f9d6c; }
.stat-recalled strong { color: #d97706; }
.stat-expired strong { color: #ef5d5d; }
.stat-risk strong { color: #dc2626; }

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-input {
  width: 340px;
}

.filter-select {
  width: 150px;
}

.filter-summary {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}

.filter-summary strong {
  color: #409eff;
  font-weight: 700;
}

.batch-table :deep(.el-table__cell) {
  padding: 18px 0;
}

.batch-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.batch-cell,
.origin-cell,
.issue-cell,
.risk-scan-cell {
  display: flex;
  flex-direction: column;
}

.batch-cell {
  gap: 8px;
}

.batch-name {
  font-size: 15px;
  font-weight: 800;
  color: #1f2937;
}

.batch-code-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 220px;
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

.origin-cell {
  gap: 8px;
  align-items: flex-start;
}

.origin-text,
.muted-text {
  color: #98a2b3;
  font-size: 13px;
}

.issue-cell {
  width: 132px;
  margin: 0 auto;
  gap: 8px;
}

.issue-count {
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
  color: #8a94a6;
  font-size: 13px;
}

.issue-count strong {
  color: #2563eb;
  font-size: 18px;
}

.issue-cell small {
  color: #98a2b3;
  font-size: 12px;
}

.status-stack {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.risk-scan-cell {
  align-items: center;
  gap: 8px;
}

.scan-inline {
  display: grid;
  grid-template-columns: auto auto;
  gap: 2px 6px;
  align-items: baseline;
  justify-content: center;
  color: #98a2b3;
  font-size: 12px;
}

.scan-inline strong {
  color: #1f2937;
  font-size: 18px;
}

.scan-inline small {
  grid-column: 1 / -1;
  color: #98a2b3;
  font-size: 12px;
}

.scan-inline span {
  color: #98a2b3;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px 12px;
  flex-wrap: wrap;
}

.action-buttons .el-button + .el-button {
  margin-left: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 18px 0 4px;
}

.drawer-title {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.drawer-title span {
  display: block;
  font-size: 18px;
  font-weight: 800;
  color: #1f2937;
}

.drawer-title small {
  display: block;
  margin-top: 5px;
  color: #8a94a6;
  font-size: 12px;
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.drawer-summary-item {
  border-radius: 18px;
  padding: 15px;
  border: 1px solid #edf0f5;
  background: #fbfdff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drawer-summary-item span {
  color: #8a94a6;
  font-size: 12px;
}

.drawer-summary-item strong {
  color: #1f2937;
  font-size: 20px;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.drawer-search {
  width: 320px;
}

.drawer-status {
  width: 140px;
}

.drawer-count {
  margin-left: auto;
  color: #909399;
  font-size: 13px;
}

.drawer-count strong {
  color: #409eff;
}

.detail-table :deep(.el-table__cell) {
  padding: 14px 0;
}

.code-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.qr-view-btn {
  font-size: 13px;
}

.qr-popover-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.qr-popover-canvas {
  width: 140px !important;
  height: 140px !important;
  border-radius: 6px;
  border: 1px solid #eee;
}

.qr-popover-code {
  font-size: 11px;
  color: #909399;
  word-break: break-all;
  text-align: center;
}

.action-trigger-btn {
  min-width: 58px;
  padding-inline: 0;
}

:deep(.qr-detail-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 22px 24px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

:deep(.qr-detail-drawer .el-drawer__body) {
  padding: 20px 24px 24px;
}

@media (max-width: 1280px) {
  .qr-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .drawer-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .qr-stats,
  .drawer-summary {
    grid-template-columns: 1fr;
  }

  .filter-input,
  .filter-select,
  .drawer-search,
  .drawer-status,
  .drawer-generate-btn {
    width: 100%;
  }

  .filter-summary,
  .drawer-count {
    margin-left: 0;
    width: 100%;
  }
}
</style>
