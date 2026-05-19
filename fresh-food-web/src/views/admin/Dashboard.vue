<template>
  <div class="dashboard-container">
    <div class="dashboard-title">
      <h2>{{ isAdmin ? '系统管理控制台' : '基地专属运营看板' }}</h2>
      <p v-if="isAdmin" class="dashboard-subtitle">{{ dashboardSubtitle }}</p>
    </div>

    <div class="focus-layout">
      <el-card shadow="hover" class="focus-main-card">
        <div class="focus-main-head">
          <div class="focus-main-copy">
            <span class="focus-kicker">运营焦点</span>
            <h3 class="focus-title">{{ focusHeadline }}</h3>
            <p class="focus-desc">{{ riskLevel.desc }}</p>
          </div>
          <el-tag :type="riskTagType" size="large" round effect="dark" class="focus-tag">
            {{ riskLevel.label }}
          </el-tag>
        </div>

        <div class="focus-metrics">
          <div
              v-for="item in focusMetrics"
              :key="item.label"
              class="focus-metric"
              :class="item.tone"
          >
            <span class="focus-metric-label">{{ item.label }}</span>
            <strong class="focus-metric-value">{{ item.value }}</strong>
            <small v-if="item.desc" class="focus-metric-desc">{{ item.desc }}</small>
          </div>
        </div>

        <div v-if="!isAdmin" class="enterprise-todo-grid">
          <div v-for="item in enterpriseTodoCards" :key="item.label" class="enterprise-todo-card">
            <span class="todo-label">{{ item.label }}</span>
            <strong :class="item.tone ? `tone-${item.tone}` : ''">{{ item.value }}</strong>
            <small>{{ item.desc }}</small>
          </div>
        </div>
      </el-card>

      <div class="focus-side-grid">
        <div v-for="item in keyCards" :key="item.label" class="mini-metric-card">
          <div class="mini-metric-icon" :style="{ background: item.bg }">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="mini-metric-copy">
            <span class="mini-metric-label">{{ item.label }}</span>
            <strong class="mini-metric-value">{{ item.value }}</strong>
            <small v-if="isAdmin" class="mini-metric-desc">{{ item.desc }}</small>
          </div>
        </div>
      </div>
    </div>

    <div class="summary-strip">
      <div v-for="item in summaryStrip" :key="item.label" class="summary-strip-item">
        <span class="summary-strip-label">{{ item.label }}</span>
        <strong class="summary-strip-value">{{ item.value }}</strong>
      </div>
    </div>

    <el-row :gutter="18" class="chart-row">
      <el-col :xs="24" :lg="16">
        <div class="chart-column-stack">
          <el-card shadow="hover" class="chart-card trend-card">
            <template #header>
              <div class="chart-header">
                <div class="chart-header-copy">
                  <span class="chart-title">{{ isAdmin ? '近 7 日扫码趋势' : '我的近 7 日扫码趋势' }}</span>
                  <small v-if="isAdmin" class="chart-subtitle">峰值出现在 {{ peakTrend.label }}，当日共 {{ peakTrend.value }} 次扫码</small>
                </div>
              </div>
            </template>
            <div ref="lineChartRef" class="chart-box chart-box-line"></div>
          </el-card>

          <el-card shadow="hover" class="dashboard-card summary-card">
            <template #header>
              <div class="chart-header">
                <div class="chart-header-copy">
                  <span class="chart-title">{{ isAdmin ? '系统摘要' : '基地运营档案' }}</span>
                  <small v-if="isAdmin" class="chart-subtitle">全流程可追溯，让生鲜更放心</small>
                </div>
              </div>
            </template>
            <div class="info-grid">
              <div v-for="item in infoRows" :key="item.label" class="info-row">
                <span class="info-label">{{ item.label }}</span>
                <span class="info-value" :class="item.tone ? `tone-${item.tone}` : ''">{{ item.value }}</span>
                <small v-if="isAdmin && item.desc" class="info-desc">{{ item.desc }}</small>
              </div>
            </div>
          </el-card>
        </div>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="chart-card status-card">
          <template #header>
            <div class="chart-header">
              <div class="chart-header-copy">
                <span class="chart-title">{{ isAdmin ? '二维码状态分布' : '我的批次健康度' }}</span>
                <small v-if="isAdmin" class="chart-subtitle">当前占比最高：{{ dominantStatus.label }}</small>
              </div>
            </div>
          </template>

          <div v-if="isAdmin" ref="pieChartRef" class="chart-box chart-box-pie"></div>

          <div v-if="isAdmin" class="status-summary-list">
            <div v-for="item in summaryItems" :key="item.label" class="status-summary-row">
              <div class="status-summary-copy">
                <span class="status-summary-label">{{ item.label }}</span>
                <small class="status-summary-desc">{{ item.desc }}</small>
              </div>
              <span class="status-summary-value" :class="item.tone ? `tone-${item.tone}` : ''">
                {{ item.value }}
              </span>
            </div>
          </div>

          <div v-else-if="batchHealthRows.length" class="batch-health-list compact-health-list">
            <div v-for="item in batchHealthRows" :key="item.batchId" class="batch-health-row">
              <div class="batch-health-main">
                <strong>{{ item.productName }}</strong>
                <span>{{ formatBatchCode(item.batchCode) }} / {{ item.origin || '未知产地' }}</span>
              </div>
              <div class="batch-health-metrics">
                <div>
                  <span>溯源节点</span>
                  <strong :class="{ 'tone-warning': item.stageCount < standardStageCount }">
                    {{ item.stageCount }}/{{ standardStageCount }}
                  </strong>
                </div>
                <div>
                  <span>发码覆盖</span>
                  <strong :class="{ 'tone-warning': item.generatedQrCount < item.batchQuantity }">
                    {{ item.generatedQrCount }}/{{ item.batchQuantity }}
                  </strong>
                </div>
                <el-tag :type="item.healthType" round size="small">{{ item.healthLabel }}</el-tag>
              </div>
            </div>
          </div>

          <el-empty v-else description="当前基地暂无产品批次" :image-size="110" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, markRaw } from 'vue'
import { Box, List, Grid, View } from '@element-plus/icons-vue'
import { getDashboardStats, getChartData } from '@/api/dashboard'
import { getProductList } from '@/api/product'
import { getRecordList } from '@/api/record'

const currentTime = ref('')
const normalizeStatus = (status) => status === 'Sold' ? 'Active' : status
const standardStageCount = 6

// ========== 角色判断 ==========
const userRole = ref('')
const currentUserName = ref('')
const isAdmin = computed(() => userRole.value === 'admin')

// ========== 统计数据 ==========
const stats = reactive({
  productCount: 0,
  recordCount: 0,
  qrCodeCount: 0,
  todayQuery: 0,
  pendingWarningCount: 0,
  recalledQrCount: 0,
  riskCodeCount: 0
})

const scanTrendSource = ref([])
const qrStatusSource = ref([])
const enterpriseBatchList = ref([])
const enterpriseRecordList = ref([])

const enterpriseName = computed(() => currentUserName.value || '当前基地')

const batchStageCountMap = computed(() => {
  const map = {}
  enterpriseRecordList.value.forEach(record => {
    const key = String(record.batchId)
    if (!map[key]) map[key] = new Set()
    if (record.nodeStage) map[key].add(record.nodeStage)
  })
  return map
})

const traceTodoBatchCount = computed(() =>
    enterpriseBatchList.value.filter(batch => getBatchStageCount(batch.batchId) < standardStageCount).length
)

const noQrBatchCount = computed(() =>
    enterpriseBatchList.value.filter(batch => Number(batch.generatedQrCount || 0) <= 0).length
)

const traceCompletionRate = computed(() => {
  const total = enterpriseBatchList.value.length * standardStageCount
  if (total <= 0) return 0
  const completed = enterpriseBatchList.value.reduce((sum, batch) => sum + getBatchStageCount(batch.batchId), 0)
  return Math.min(100, Math.round((completed / total) * 100))
})

const issueCoverageRate = computed(() => {
  const total = enterpriseBatchList.value.reduce((sum, batch) => sum + Number(batch.batchQuantity || 0), 0)
  if (total <= 0) return 0
  const generated = enterpriseBatchList.value.reduce((sum, batch) => sum + Number(batch.generatedQrCount || 0), 0)
  return Math.min(100, Math.round((generated / total) * 100))
})

const dashboardSubtitle = computed(() =>
    isAdmin.value
        ? '聚焦风险、趋势与关键业务数据'
        : `聚焦 ${enterpriseName.value} 的批次维护、溯源完整度与扫码表现`
)

const riskLevel = computed(() => {
  if (!isAdmin.value) {
    if (stats.recalledQrCount >= 5 || stats.riskCodeCount >= 5) {
      return { label: '高风险', desc: '本基地存在较多召回或风险码，请及时完善批次溯源信息，并联系管理员协同核验。', tone: 'danger' }
    }
    if (stats.recalledQrCount > 0 || stats.riskCodeCount > 0) {
      return { label: '需关注', desc: '本基地已有风险信号，建议先核对相关批次信息，必要时联系管理员处理预警或召回。', tone: 'warning' }
    }
    if (traceTodoBatchCount.value > 0 || noQrBatchCount.value > 0) {
      return { label: '待完善', desc: '当前无明显风险，但仍有批次需要补齐溯源节点或等待管理员发码。', tone: 'warning' }
    }
    return { label: '运营稳定', desc: '本基地批次维护较完整，当前没有明显风险信号。', tone: 'success' }
  }
  if (stats.pendingWarningCount >= 5 || stats.recalledQrCount >= 5) {
    return { label: '高风险', desc: '召回或风险待办较多，建议优先进入预警中心和质检召回模块处理。', tone: 'danger' }
  }
  if (stats.pendingWarningCount > 0 || stats.recalledQrCount > 0 || stats.riskCodeCount > 0) {
    return { label: '需关注', desc: '系统存在异常扫码或召回记录，建议继续跟踪二维码状态与批次处置。', tone: 'warning' }
  }
  return { label: '运行稳定', desc: '当前没有明显风险积压，系统处于相对平稳的运营状态。', tone: 'success' }
})

const riskTagType = computed(() => {
  const map = { danger: 'danger', warning: 'warning', success: 'success' }
  return map[riskLevel.value.tone] || 'info'
})

const focusHeadline = computed(() => {
  if (!isAdmin.value) {
    if (riskLevel.value.tone === 'danger') return '本基地存在较强风险信号，请先核对批次并联系管理员'
    if (riskLevel.value.label === '待完善') return '本基地运营数据待完善，请优先补齐批次和溯源信息'
    if (riskLevel.value.tone === 'warning') return '本基地存在异常迹象，建议关注风险批次和召回码'
    return '本基地当前运营平稳，可继续维护产品与溯源数据'
  }
  if (riskLevel.value.tone === 'danger') return '当前风险信号较强，需要优先处理召回与预警'
  if (riskLevel.value.tone === 'warning') return '当前存在异常迹象，建议优先关注风险闭环'
  return '当前业务运行平稳，可按常规节奏维护系统数据'
})

const focusMetrics = computed(() => {
  if (!isAdmin.value) {
    return [
      { label: '待补溯源批次', value: traceTodoBatchCount.value, desc: '基地可直接补录', tone: traceTodoBatchCount.value > 0 ? 'warning' : 'success' },
      { label: '召回二维码', value: stats.recalledQrCount, desc: '需关注对应批次', tone: stats.recalledQrCount > 0 ? 'danger' : 'success' },
      { label: '风险码数量', value: stats.riskCodeCount, desc: '请联系管理员核验', tone: stats.riskCodeCount > 0 ? 'warning' : 'success' }
    ]
  }
  return [
    { label: '待处理预警', value: stats.pendingWarningCount, tone: 'danger' },
    { label: '召回二维码', value: stats.recalledQrCount, tone: 'warning' },
    { label: '风险码数量', value: stats.riskCodeCount, tone: 'success' }
  ]
})

const enterpriseTodoCards = computed(() => [
  {
    label: '我能直接处理',
    value: `${traceTodoBatchCount.value} 个批次`,
    desc: '进入溯源记录管理，补齐生产、采摘、质检等节点。',
    tone: traceTodoBatchCount.value > 0 ? 'warning' : 'success'
  },
  {
    label: '需要管理员协同',
    value: `${noQrBatchCount.value} 个批次`,
    desc: '这些批次尚未生成二维码，需联系管理员发码。',
    tone: noQrBatchCount.value > 0 ? 'primary' : 'success'
  },
  {
    label: '风险关注',
    value: `${stats.riskCodeCount + stats.recalledQrCount} 个`,
    desc: '风险或召回状态不由基地直接关闭，需配合管理员核验。',
    tone: stats.riskCodeCount + stats.recalledQrCount > 0 ? 'danger' : 'success'
  }
])

const keyCards = computed(() => [
  {
    label: isAdmin.value ? '今日查询' : '今日被查',
    value: `${stats.todayQuery} 次`,
    desc: '消费者当日扫码总量',
    icon: markRaw(View),
    bg: 'linear-gradient(135deg, #3b82f6, #60a5fa)'
  },
  {
    label: isAdmin.value ? '产品批次' : '我的批次',
    value: stats.productCount,
    desc: '当前可管理批次总数',
    icon: markRaw(Box),
    bg: 'linear-gradient(135deg, #0f9d6c, #22c55e)'
  },
  {
    label: isAdmin.value ? '溯源记录' : '溯源完成率',
    value: isAdmin.value ? stats.recordCount : `${traceCompletionRate.value}%`,
    desc: isAdmin.value ? '已录入的全链路节点记录' : '标准节点整体完成度',
    icon: markRaw(List),
    bg: 'linear-gradient(135deg, #d97706, #f59e0b)'
  },
  {
    label: isAdmin.value ? '二维码数量' : '我的二维码',
    value: stats.qrCodeCount,
    desc: '系统中已生成的二维码总量',
    icon: markRaw(Grid),
    bg: 'linear-gradient(135deg, #ef5d5d, #fb7185)'
  }
])

const dataScale = computed(() => stats.productCount + stats.recordCount + stats.qrCodeCount)

const summaryStrip = computed(() => {
  if (!isAdmin.value) {
    return [
      { label: '当前基地', value: enterpriseName.value },
      { label: '溯源待补', value: `${traceTodoBatchCount.value} 个批次` },
      { label: '发码覆盖率', value: `${issueCoverageRate.value}%` },
      { label: '最近更新', value: currentTime.value || '加载中' }
    ]
  }
  return [
    { label: '数据规模', value: `${dataScale.value}` },
    { label: '当前视角', value: '管理员全局' },
    { label: '风险等级', value: riskLevel.value.label },
    { label: '最近更新', value: currentTime.value || '加载中' }
  ]
})

const peakTrend = computed(() => {
  if (!scanTrendSource.value.length) {
    return { label: '近 7 日', value: 0 }
  }
  const peakItem = scanTrendSource.value.reduce((prev, current) =>
      Number(current.cnt || 0) > Number(prev.cnt || 0) ? current : prev
  )
  const date = new Date(peakItem.day)
  return {
    label: Number.isNaN(date.getTime()) ? '近 7 日' : `${date.getMonth() + 1}/${date.getDate()}`,
    value: Number(peakItem.cnt || 0)
  }
})

const dominantStatus = computed(() => {
  if (!qrStatusSource.value.length) {
    return { label: '暂无数据', count: 0 }
  }
  const topStatus = qrStatusSource.value.reduce((prev, current) =>
      Number(current.cnt || 0) > Number(prev.cnt || 0) ? current : prev
  )
  return {
    label: statusNameMap[normalizeStatus(topStatus.status)] || normalizeStatus(topStatus.status) || '未知',
    count: Number(topStatus.cnt || 0)
  }
})

const summaryItems = computed(() => {
  const base = [
    {
      label: '7 日峰值',
      value: `${peakTrend.value.value} 次`,
      desc: `${peakTrend.value.label} 出现最大扫码峰值`,
      tone: peakTrend.value.value > 0 ? 'primary' : ''
    },
    {
      label: '主要二维码状态',
      value: dominantStatus.value.label,
      desc: dominantStatus.value.count > 0 ? `${dominantStatus.value.count} 个二维码处于该状态` : '当前暂无二维码状态数据'
    },
    {
      label: '风险码数量',
      value: `${stats.riskCodeCount} 个`,
      desc: isAdmin.value ? '被识别为异常扫码模式的二维码数量' : '基地侧只做关注和配合，关闭风险需管理员处理',
      tone: stats.riskCodeCount > 0 ? 'warning' : 'success'
    }
  ]
  if (!isAdmin.value) {
    return [
      ...base,
      {
        label: '当前建议',
        value: enterpriseSuggestion.value.title,
        desc: enterpriseSuggestion.value.desc,
        tone: enterpriseSuggestion.value.tone
      }
    ]
  }
  return [
    ...base,
    {
      label: '待处理预警',
      value: `${stats.pendingWarningCount} 条`,
      desc: stats.pendingWarningCount > 0 ? '建议优先进入预警中心处理' : '当前没有积压预警',
      tone: stats.pendingWarningCount > 0 ? 'danger' : 'success'
    }
  ]
})

const enterpriseSuggestion = computed(() => {
  if (traceTodoBatchCount.value > 0) {
    return { title: '补齐溯源', desc: '优先进入溯源记录管理，完善未完成批次节点。', tone: 'warning' }
  }
  if (noQrBatchCount.value > 0) {
    return { title: '申请发码', desc: '部分批次尚未生成二维码，请联系管理员发码。', tone: 'primary' }
  }
  if (stats.riskCodeCount > 0 || stats.recalledQrCount > 0) {
    return { title: '配合核验', desc: '关注风险批次，等待管理员或质检结论。', tone: 'danger' }
  }
  return { title: '保持维护', desc: '当前批次状态较稳定，继续按流程录入后续节点。', tone: 'success' }
})

const infoRows = computed(() => {
  if (!isAdmin.value) {
    return [
      { label: '基地名称', value: enterpriseName.value },
      { label: '当前账号', value: userRole.value === 'enterprise' ? '基地账号' : '未知角色' },
      { label: '可管理批次', value: `${stats.productCount}`, desc: '仅统计本基地批次', tone: 'primary' },
      { label: '溯源完成率', value: `${traceCompletionRate.value}%`, desc: `${traceTodoBatchCount.value} 个批次仍需补录`, tone: traceCompletionRate.value >= 80 ? 'success' : 'warning' },
      { label: '发码覆盖率', value: `${issueCoverageRate.value}%`, desc: `${noQrBatchCount.value} 个批次尚未发码`, tone: issueCoverageRate.value >= 80 ? 'success' : 'primary' },
      { label: '当前建议', value: enterpriseSuggestion.value.title, desc: enterpriseSuggestion.value.desc, tone: enterpriseSuggestion.value.tone }
    ]
  }
  return [
    { label: '系统名称', value: '生鲜食品溯源管理平台' },
    { label: '当前时间', value: currentTime.value || '加载中' },
    { label: '前端框架', value: 'Vue 3 + Element Plus' },
    { label: '后端框架', value: 'Spring Boot 2.7' },
    { label: '数据规模', value: `${dataScale.value}`, desc: '批次、记录与二维码总量', tone: 'primary' },
    { label: '当前判断', value: riskLevel.value.label, desc: riskLevel.value.desc, tone: riskLevel.value.tone }
  ]
})

const batchHealthRows = computed(() => {
  return enterpriseBatchList.value.map(batch => {
    const stageCount = getBatchStageCount(batch.batchId)
    const generatedQrCount = Number(batch.generatedQrCount || 0)
    const batchQuantity = Number(batch.batchQuantity || 0)
    const incompleteTrace = stageCount < standardStageCount
    const incompleteQr = generatedQrCount < batchQuantity
    let healthLabel = '完整'
    let healthType = 'success'
    if (incompleteTrace) {
      healthLabel = '待补溯源'
      healthType = 'warning'
    } else if (incompleteQr) {
      healthLabel = '待发码'
      healthType = 'primary'
    }
    return {
      ...batch,
      stageCount,
      generatedQrCount,
      batchQuantity,
      healthLabel,
      healthType
    }
  }).sort((a, b) => {
    if (a.stageCount !== b.stageCount) return a.stageCount - b.stageCount
    return a.generatedQrCount - b.generatedQrCount
  }).slice(0, 5)
})

const getBatchStageCount = (batchId) => {
  return batchStageCountMap.value[String(batchId)]?.size || 0
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

const loadStats = async () => {
  try {
    const res = await getDashboardStats()
    if (res.code === 200 && res.data) {
      stats.productCount = res.data.productCount || 0
      stats.recordCount = res.data.recordCount || 0
      stats.qrCodeCount = res.data.qrCodeCount || 0
      stats.todayQuery = res.data.todayQuery || 0
      stats.pendingWarningCount = res.data.pendingWarningCount || 0
      stats.recalledQrCount = res.data.recalledQrCount || 0
      stats.riskCodeCount = res.data.riskCodeCount || 0
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const lineChartRef = ref(null)
const pieChartRef = ref(null)
let lineChart = null
let pieChart = null
let echartsModule = null

const loadEcharts = async () => {
  if (echartsModule) return echartsModule

  const [core, charts, components, renderers] = await Promise.all([
    import('echarts/core'),
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/renderers')
  ])

  core.use([
    charts.LineChart,
    charts.PieChart,
    components.GridComponent,
    components.LegendComponent,
    components.TooltipComponent,
    renderers.CanvasRenderer
  ])
  echartsModule = core
  return echartsModule
}

const getLast7Days = () => {
  const days = []
  for (let i = 6; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    days.push(`${date.getMonth() + 1}/${date.getDate()}`)
  }
  return days
}

const loadEnterpriseOperations = async () => {
  if (isAdmin.value) return
  try {
    const [batchRes, recordRes] = await Promise.all([getProductList(), getRecordList()])
    if (batchRes.code === 200) {
      enterpriseBatchList.value = batchRes.data || []
    }
    if (recordRes.code === 200) {
      enterpriseRecordList.value = recordRes.data || []
    }
  } catch (error) {
    console.error('获取基地运营数据失败:', error)
  }
}

const initLineChart = (scanTrend, echarts) => {
  if (!lineChartRef.value) return
  lineChart = echarts.init(lineChartRef.value)

  const dateLabels = getLast7Days()
  const trendMap = {}
  if (scanTrend?.length) {
    scanTrend.forEach(item => {
      const d = new Date(item.day)
      const key = `${d.getMonth() + 1}/${d.getDate()}`
      trendMap[key] = Number(item.cnt) || 0
    })
  }
  const trendData = dateLabels.map(label => trendMap[label] || 0)

  lineChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(36, 44, 58, 0.9)',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 12 }
    },
    grid: { top: 18, right: 14, bottom: 20, left: 38 },
    xAxis: {
      type: 'category',
      data: dateLabels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#d7deea' } },
      axisLabel: { color: '#8a95a6', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#edf2f7', type: 'dashed' } },
      axisLabel: { color: '#8a95a6', fontSize: 11 }
    },
    series: [
      {
        name: '扫码次数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#409eff' },
        itemStyle: { color: '#409eff', borderWidth: 2, borderColor: '#ffffff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.26)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.03)' }
          ])
        },
        data: trendData
      }
    ]
  })
}

const statusNameMap = { 'Active': '激活', 'Expired': '过期', 'Recalled': '召回' }
const statusColorMap = { 'Active': '#67c23a', 'Expired': '#f56c6c', 'Recalled': '#e6a23c' }

const initPieChart = (qrStatusDist, echarts) => {
  if (!pieChartRef.value || !isAdmin.value) return
  pieChart = echarts.init(pieChartRef.value)

  const pieData = qrStatusDist?.length
      ? qrStatusDist.map(item => ({
        value: Number(item.cnt) || 0,
        name: statusNameMap[normalizeStatus(item.status)] || normalizeStatus(item.status) || '未知',
        itemStyle: { color: statusColorMap[normalizeStatus(item.status)] || '#909399' }
      }))
      : [
        { value: 0, name: '暂无数据', itemStyle: { color: '#cbd5e1' } }
      ]

  pieChart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(36, 44, 58, 0.9)',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 12,
      textStyle: { color: '#758195', fontSize: 11 }
    },
    series: [
      {
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '40%'],
        label: {
          show: true,
          formatter: '{d}%',
          fontSize: 11,
          color: '#667085'
        },
        labelLine: { length: 8, length2: 10 },
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 3
        },
        data: pieData
      }
    ]
  })
}

const handleResize = () => {
  lineChart?.resize()
  pieChart?.resize()
}

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

let timer = null

onMounted(async () => {
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) {
    const user = JSON.parse(userInfo)
    userRole.value = user.role || 'admin'
    currentUserName.value = user.fullName || user.username || ''
  }

  updateTime()
  timer = setInterval(updateTime, 1000)
  await loadEnterpriseOperations()
  await loadStats()

  let scanTrend = []
  let qrStatusDist = []
  try {
    const chartRes = await getChartData()
    if (chartRes.code === 200 && chartRes.data) {
      scanTrend = chartRes.data.scanTrend || []
      qrStatusDist = chartRes.data.qrStatusDist || []
      scanTrendSource.value = scanTrend
      qrStatusSource.value = qrStatusDist
    }
  } catch (error) {
    console.error('获取图表数据失败:', error)
  }

  await nextTick()
  loadEcharts()
      .then(echarts => {
        initLineChart(scanTrend, echarts)
        initPieChart(qrStatusDist, echarts)
      })
      .catch(error => {
        console.error('图表资源加载失败:', error)
      })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  lineChart?.dispose()
  pieChart?.dispose()
  window.removeEventListener('resize', handleResize)
})

</script>

<style scoped>
.dashboard-container {
  padding: 0;
}

.dashboard-title {
  margin-bottom: 18px;
}

.dashboard-title h2 {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 700;
  color: #172033;
  letter-spacing: -0.02em;
}

.dashboard-subtitle {
  margin: 0;
  font-size: 14px;
  color: #7b8797;
}

.focus-layout {
  display: grid;
  grid-template-columns: 1.2fr 0.9fr;
  gap: 18px;
  margin-bottom: 16px;
}

.focus-main-card {
  border-radius: 22px;
  border: 1px solid rgba(239, 93, 93, 0.1);
  background:
    radial-gradient(circle at top right, rgba(239, 93, 93, 0.08), transparent 26%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 252, 255, 0.96));
}

.focus-main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.focus-main-copy {
  min-width: 0;
}

.focus-kicker {
  display: inline-block;
  margin-bottom: 10px;
  font-size: 12px;
  color: #8893a3;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.focus-title {
  margin: 0 0 8px;
  font-size: 24px;
  line-height: 1.35;
  color: #152033;
}

.focus-desc {
  margin: 0;
  max-width: 620px;
  font-size: 14px;
  color: #6b7788;
  line-height: 1.7;
}

.focus-tag {
  margin-top: 4px;
}

.focus-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 22px;
}

.focus-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.focus-metric-label {
  font-size: 12px;
  color: #8a95a6;
}

.focus-metric-value {
  font-size: 28px;
  line-height: 1.2;
  color: #162033;
}

.focus-metric-desc {
  font-size: 12px;
  color: #8a95a6;
  line-height: 1.45;
}

.focus-metric.danger .focus-metric-value {
  color: #dc2626;
}

.focus-metric.warning .focus-metric-value {
  color: #d97706;
}

.focus-metric.success .focus-metric-value {
  color: #14915f;
}

.focus-metric.primary .focus-metric-value {
  color: #2563eb;
}

.enterprise-todo-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.enterprise-todo-card {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.todo-label {
  font-size: 12px;
  color: #8a95a6;
}

.enterprise-todo-card strong {
  font-size: 20px;
  color: #1d2635;
  line-height: 1.2;
}

.enterprise-todo-card small {
  font-size: 12px;
  color: #7b8797;
  line-height: 1.55;
}

.focus-side-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.mini-metric-card {
  display: flex;
  gap: 14px;
  padding: 18px 18px 16px;
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.04);
}

.mini-metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  font-size: 20px;
}

.mini-metric-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.mini-metric-label {
  font-size: 12px;
  color: #8a95a6;
}

.mini-metric-value {
  font-size: 24px;
  line-height: 1.15;
  color: #182234;
}

.mini-metric-desc {
  font-size: 12px;
  color: #7b8797;
  line-height: 1.5;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-strip-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.summary-strip-label {
  font-size: 13px;
  color: #7b8797;
}

.summary-strip-value {
  font-size: 14px;
  font-weight: 700;
  color: #1d2635;
}

.chart-row {
  align-items: stretch;
  margin-bottom: 16px;
}

.chart-row :deep(.el-col) {
  display: flex;
}

.chart-column-stack {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  height: 100%;
}

.chart-card,
.dashboard-card {
  border-radius: 22px;
  margin-bottom: 18px;
}

.chart-column-stack .chart-card,
.chart-column-stack .dashboard-card,
.status-card {
  margin-bottom: 0;
}

.trend-card {
  flex: 0 0 auto;
}

.summary-card {
  flex: 1;
}

.summary-card :deep(.el-card__body) {
  height: calc(100% - 72px);
}

.status-card {
  width: 100%;
  height: 100%;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-header-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chart-title {
  font-size: 17px;
  font-weight: 700;
  color: #162033;
}

.chart-subtitle {
  font-size: 12px;
  color: #8591a3;
  line-height: 1.5;
}

.chart-box-line {
  width: 100%;
  height: 220px;
}

.chart-box-pie {
  width: 100%;
  height: 180px;
}

.status-card :deep(.el-card__body) {
  padding-top: 18px;
}

.status-summary-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
}

.status-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f7f9fc 100%);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.status-summary-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.status-summary-label {
  font-size: 13px;
  font-weight: 600;
  color: #243044;
}

.status-summary-desc {
  font-size: 12px;
  color: #8a95a6;
  line-height: 1.45;
}

.status-summary-value {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 700;
  color: #1d2635;
}

.status-summary-value.tone-primary,
.info-value.tone-primary,
.enterprise-todo-card strong.tone-primary,
.batch-health-row .tone-primary {
  color: #2563eb;
}

.status-summary-value.tone-success,
.info-value.tone-success,
.enterprise-todo-card strong.tone-success,
.batch-health-row .tone-success {
  color: #14915f;
}

.status-summary-value.tone-warning,
.info-value.tone-warning,
.enterprise-todo-card strong.tone-warning,
.batch-health-row .tone-warning {
  color: #d97706;
}

.status-summary-value.tone-danger,
.info-value.tone-danger,
.enterprise-todo-card strong.tone-danger,
.batch-health-row .tone-danger {
  color: #dc2626;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.info-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.info-label {
  font-size: 12px;
  color: #8a95a6;
}

.info-value {
  font-size: 20px;
  font-weight: 700;
  color: #1d2635;
  line-height: 1.3;
}

.info-desc {
  font-size: 12px;
  color: #7b8797;
  line-height: 1.5;
}

.batch-health-card {
  margin-top: 16px;
}

.batch-health-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.compact-health-list {
  gap: 10px;
}

.batch-health-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, #ffffff 0%, #f9fbfd 100%);
}

.batch-health-main {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.batch-health-main strong {
  font-size: 15px;
  color: #172033;
}

.batch-health-main span {
  font-size: 12px;
  color: #8a95a6;
}

.batch-health-metrics {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-shrink: 0;
}

.batch-health-metrics > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 76px;
}

.batch-health-metrics span {
  font-size: 12px;
  color: #8a95a6;
}

.batch-health-metrics strong {
  font-size: 16px;
  color: #1d2635;
}

.compact-health-list .batch-health-row {
  align-items: flex-start;
  flex-direction: column;
  gap: 12px;
  padding: 13px 14px;
}

.compact-health-list .batch-health-main {
  width: 100%;
}

.compact-health-list .batch-health-metrics {
  width: 100%;
  justify-content: space-between;
  gap: 10px;
}

.compact-health-list .batch-health-metrics > div {
  min-width: auto;
}

@media (max-width: 1440px) {
  .focus-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1100px) {
  .summary-strip,
  .focus-side-grid,
  .info-grid,
  .enterprise-todo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .batch-health-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .batch-health-metrics {
    width: 100%;
    justify-content: space-between;
  }
}

@media (max-width: 768px) {
  .focus-metrics,
  .summary-strip,
  .focus-side-grid,
  .info-grid,
  .enterprise-todo-grid {
    grid-template-columns: 1fr;
  }

  .focus-main-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .chart-box-line,
  .chart-box-pie {
    height: 200px;
  }

  .dashboard-title h2 {
    font-size: 24px;
  }

  .batch-health-metrics {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
