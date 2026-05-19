<template>
  <div class="trace-page">
    <!-- 搜索区域：仅在未查询时显示（手动输入场景） -->
    <div v-if="!hasQueried && !loading" class="search-fallback">
      <div class="search-fallback-inner">
        <el-icon class="search-fallback-icon"><Search /></el-icon>
        <h2>生鲜食品溯源</h2>
        <p>请输入防伪码查询商品信息</p>
        <el-input
            v-model="searchCode"
            placeholder="输入防伪码"
            size="large"
            clearable
            @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button type="primary" @click="handleSearch" :loading="loading">
              查询
            </el-button>
          </template>
        </el-input>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <el-icon class="loading-spin"><Loading /></el-icon>
      <p>正在查询溯源信息…</p>
    </div>

    <!-- ===== 查询结果区域 ===== -->
    <template v-if="hasQueried && !loading">

      <!-- 查询失败 -->
      <div v-if="!isAuthentic" class="error-block">
        <el-icon class="error-icon"><WarningFilled /></el-icon>
        <h2>查询失败</h2>
        <p>{{ errorMessage || '未找到该防伪码的溯源信息' }}</p>
        <el-button type="primary" plain size="small" @click="resetQuery">重新查询</el-button>
      </div>

      <!-- 查询成功 -->
      <template v-if="isAuthentic && productInfo">

        <!-- 1. Hero Image -->
        <div class="hero-image">
          <el-image
              :src="productInfo.imageUrl"
              fit="cover"
              class="hero-img"
          >
            <template #error>
              <div class="hero-placeholder">
                <el-icon class="hero-placeholder-icon"><Picture /></el-icon>
                <span>暂无产品图片</span>
              </div>
            </template>
          </el-image>
        </div>

        <!-- 1.5 独立风控横幅（异常模式识别，与物理状态解耦共存） -->
        <div v-if="showRiskBanner()" class="risk-wrapper">
          <el-alert
              :title="riskMessage"
              :type="getRiskBannerType()"
              :closable="false"
              show-icon
          />
        </div>

        <!-- 2. 召回警告卡片 / 正品认证卡片 -->
        <div v-if="qrStatus === 'Recalled'" class="recall-card">
          <div class="recall-left">
            <el-icon class="recall-icon"><WarningFilled /></el-icon>
          </div>
          <div class="recall-right">
            <div class="recall-title">⚠️ 紧急召回警报</div>
            <div class="recall-desc">
              该批次产品存在质量隐患（如农药残留等），已被官方紧急召回，<strong>请立即停止食用并联系退货！</strong>
            </div>
          </div>
        </div>

        <!-- 2.1 过期卡片 -->
        <div v-else-if="qrStatus === 'Expired'" class="expired-card">
          <div class="expired-left">
            <el-icon class="expired-icon"><RemoveFilled /></el-icon>
          </div>
          <div class="expired-right">
            <div class="expired-title">❕ 商品已过期</div>
            <div class="expired-desc">
              该商品已过保质期，为保障您的健康，<strong>请勿食用。</strong>
            </div>
          </div>
        </div>

        <div v-else class="auth-card" :class="{ 'auth-warn': riskLevel !== 'normal' }">
          <div class="auth-left">
            <el-icon class="auth-icon"><SuccessFilled /></el-icon>
          </div>
          <div class="auth-right">
            <div class="auth-title">{{ riskLevel === 'normal' ? '正品认证' : '防伪核验提醒' }}</div>
            <div class="auth-desc" v-if="riskLevel === 'normal'">
              本品已通过防伪验证，系统累计记录
              <strong>{{ scanCount }}</strong> 次查询
            </div>
            <div class="auth-desc" v-else>
              {{ riskMessage }}
            </div>
            <div class="auth-warning-tip" :class="{ 'auth-warning-tip--muted': riskLevel === 'normal' }">
              <el-icon><WarningFilled /></el-icon>
              系统累计记录 {{ scanCount }} 次查询
            </div>
          </div>
        </div>

        <!-- 3. 产品信息卡片 -->
        <div class="info-card">
          <div class="info-card-title">产品信息</div>
          <div class="product-name">{{ productInfo.productName }}</div>
          <ul class="info-list">
            <li>
              <span class="info-label">生产批次</span>
              <span class="info-value">{{ productInfo.batchCode || '-' }}</span>
            </li>
            <li>
              <span class="info-label">产地</span>
              <span class="info-value">{{ productInfo.origin || '-' }}</span>
            </li>
            <li>
              <span class="info-label">生产日期</span>
              <span class="info-value">{{ formatDate(productInfo.productionDate) }}</span>
            </li>
            <li>
              <span class="info-label">保质期</span>
              <span class="info-value">{{ productInfo.shelfLifeDays ? productInfo.shelfLifeDays + ' 天' : '-' }}</span>
            </li>
          </ul>
        </div>

        <!-- 4. 消费者版溯源摘要 -->
        <div class="trace-summary-card">
          <div class="trace-summary-header">
            <div>
              <div class="trace-summary-title">溯源进度</div>
              <div class="trace-summary-subtitle">
                已完成 {{ completedStageCount }} / {{ stageFlow.length }} 个关键环节
              </div>
            </div>
            <span class="trace-summary-count">{{ traceRecords.length }} 条记录</span>
          </div>

          <div class="stage-flow">
            <div
                v-for="stage in stageFlow"
                :key="stage.key"
                class="stage-step"
                :class="{ 'is-done': completedStageKeys.includes(stage.key) }"
            >
              <span class="stage-dot"></span>
              <span class="stage-label">{{ stage.label }}</span>
            </div>
          </div>

          <div v-if="traceRecords.length === 0" class="trace-empty">
            当前批次尚未录入溯源节点，建议继续核验产品来源。
          </div>

          <div v-else class="trace-highlights">
            <div
                v-for="item in highlightedRecords"
                :key="item.key"
                class="highlight-card"
            >
              <span class="highlight-kicker">{{ item.title }}</span>
              <div class="highlight-stage">
                <el-tag :type="getTimelineType(item.record.nodeStage)" size="small" round>
                  {{ getStageName(item.record.nodeStage) }}
                </el-tag>
                <span>{{ formatTime(item.record.recordedAt) }}</span>
              </div>
              <p>{{ item.record.operationDetail || '暂无操作说明' }}</p>
              <div class="highlight-meta">
                <span v-if="item.record.location">地点：{{ item.record.location }}</span>
                <span v-if="item.record.operator">记录人：{{ item.record.operator }}</span>
              </div>
            </div>
          </div>

          <el-button
              v-if="traceRecords.length > 0"
              class="trace-toggle-btn"
              type="primary"
              plain
              round
              @click="showFullTimeline = !showFullTimeline"
          >
            {{ showFullTimeline ? '收起完整记录' : `查看完整溯源记录（${traceRecords.length} 条）` }}
          </el-button>
        </div>

        <!-- 5. 完整溯源记录：默认折叠，避免页面过长 -->
        <div v-if="showFullTimeline && traceRecords.length > 0" class="timeline-card">
          <div class="timeline-card-header">
            <span class="timeline-card-title">溯源时光轴</span>
            <span class="timeline-card-count">{{ traceRecords.length }} 条记录</span>
          </div>
          <el-timeline>
            <el-timeline-item
                v-for="(record, index) in traceRecords"
                :key="index"
                :timestamp="formatTime(record.recordedAt)"
                placement="top"
                :type="getTimelineType(record.nodeStage)"
                :icon="getTimelineIcon(record.nodeStage)"
            >
              <div class="tl-node">
                <div class="tl-node-header">
                  <el-tag :type="getTimelineType(record.nodeStage)" size="small" effect="dark" round>
                    {{ getStageName(record.nodeStage) }}
                  </el-tag>
                  <span v-if="record.location" class="tl-location">
                    <el-icon><Location /></el-icon>
                    {{ record.location }}
                  </span>
                </div>
                <p class="tl-detail">{{ record.operationDetail || '暂无说明' }}</p>
                <div class="tl-footer">
                  <span v-if="record.operator" class="tl-meta">
                    <el-icon><User /></el-icon>
                    {{ record.operator }}
                  </span>
                  <span v-if="record.temperatureData" class="tl-meta">
                    <span class="tl-temp-icon">🌡</span>
                    {{ record.temperatureData }}℃
                  </span>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 底部品牌 -->
        <div class="footer-brand">
          <span>— 生鲜食品溯源平台 —</span>
        </div>

      </template>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { queryTrace } from '@/api/trace'
import {
  Search,
  SuccessFilled,
  WarningFilled,
  Location,
  Sell,
  Van,
  Operation,
  User,
  Picture,
  Loading,
  RemoveFilled
} from '@element-plus/icons-vue'

// ---- 响应式数据 ----
const searchCode = ref('')
const loading = ref(false)
const hasQueried = ref(false)
const isAuthentic = ref(false)
const errorMessage = ref('')
const productInfo = ref(null)
const traceRecords = ref([])
const scanCount = ref(0)
const qrStatus = ref('')
const riskLevel = ref('normal')
const riskMessage = ref('')
const showFullTimeline = ref(false)

const route = useRoute()
const normalizeQrStatus = (status) => status === 'Sold' ? 'Active' : (status || '')

const stageFlow = [
  { key: 'Production', label: '种植' },
  { key: 'Processing', label: '采摘' },
  { key: 'QualityCheck', label: '质检' },
  { key: 'Packaging', label: '包装' },
  { key: 'Transportation', label: '运输' },
  { key: 'Sales', label: '销售' }
]

const stageAliasMap = {
  Production: 'Production',
  Processing: 'Processing',
  QualityCheck: 'QualityCheck',
  Packaging: 'Packaging',
  Transportation: 'Transportation',
  Sales: 'Sales',
  生产: 'Production',
  种植: 'Production',
  加工: 'Processing',
  采摘: 'Processing',
  质检: 'QualityCheck',
  包装: 'Packaging',
  物流: 'Transportation',
  运输: 'Transportation',
  销售: 'Sales'
}

const completedStageKeys = computed(() => {
  const keys = new Set()
  traceRecords.value.forEach(record => {
    const key = stageAliasMap[record.nodeStage]
    if (key) keys.add(key)
  })
  return Array.from(keys)
})

const completedStageCount = computed(() => completedStageKeys.value.length)

const highlightedRecords = computed(() => {
  if (!traceRecords.value.length) return []
  const first = traceRecords.value[0]
  const latest = traceRecords.value[traceRecords.value.length - 1]
  if (traceRecords.value.length === 1) {
    return [{ key: 'latest', title: '最新节点', record: latest }]
  }
  return [
    { key: 'start', title: '起始节点', record: first },
    { key: 'latest', title: '最新节点', record: latest }
  ]
})

// ---- 生命周期 ----
onMounted(() => {
  const code = route.query.code
  if (code) {
    searchCode.value = code
    handleSearch()
  }
})

// ---- 工具函数 ----

/** 格式化日期（LocalDate 数组 [2026,3,5] 或字符串） */
const formatDate = (val) => {
  if (!val) return '-'
  if (Array.isArray(val)) {
    const [y, m, d] = val
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }
  return String(val).substring(0, 10)
}

/** 格式化日期时间（LocalDateTime 数组或字符串） */
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  if (Array.isArray(timeStr)) {
    const [year, month, day, hour, minute] = timeStr
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
  }
  return timeStr.replace('T', ' ').substring(0, 16)
}

/** 重置查询 */
const resetQuery = () => {
  hasQueried.value = false
  isAuthentic.value = false
  productInfo.value = null
  traceRecords.value = []
  scanCount.value = 0
  qrStatus.value = ''
  riskLevel.value = 'normal'
  riskMessage.value = ''
  showFullTimeline.value = false
  errorMessage.value = ''
}

// ---- 核心查询 ----
const handleSearch = async () => {
  if (!searchCode.value.trim()) {
    ElMessage.warning('请输入防伪码')
    return
  }

  loading.value = true
  hasQueried.value = false
  isAuthentic.value = false
  errorMessage.value = ''
  productInfo.value = null
  traceRecords.value = []
  scanCount.value = 0
  qrStatus.value = ''
  riskLevel.value = 'normal'
  riskMessage.value = ''
  showFullTimeline.value = false

  try {
    const response = await queryTrace(searchCode.value.trim())

    hasQueried.value = true

    if (response.success) {
      isAuthentic.value = true
      scanCount.value = response.scanCount || 0
      qrStatus.value = normalizeQrStatus(response.qrCodeStatus)
      riskLevel.value = response.riskLevel || 'normal'
      riskMessage.value = response.riskMessage || ''

      // ★ 直接使用后端返回的 product 对象，字段名与 ProductBatch 实体一致
      //   batchCode, productName, origin, productionDate, shelfLifeDays, imageUrl
      if (response.product) {
        productInfo.value = response.product
      }

      if (response.traceRecords && response.traceRecords.length > 0) {
        traceRecords.value = response.traceRecords
      }
    } else {
      isAuthentic.value = false
      errorMessage.value = response.message || '查询失败'
      ElMessage.error(errorMessage.value)
    }
  } catch (error) {
    hasQueried.value = true
    isAuthentic.value = false
    errorMessage.value = error.message || '网络请求失败'
    ElMessage.error('查询失败：' + errorMessage.value)
  } finally {
    loading.value = false
  }
}

const showRiskBanner = () => {
  return riskLevel.value === 'attention' || riskLevel.value === 'warning'
}

const getRiskBannerType = () => {
  return riskLevel.value === 'warning' ? 'warning' : 'info'
}

// ---- 时间轴辅助 ----
// ---- 时间轴辅助 ----
const getTimelineType = (stage) => {
  const map = {
    'Production': 'primary', 'Processing': 'success',
    'QualityCheck': 'warning', 'Packaging': 'primary', // 补充了这两个
    'Transportation': 'warning', 'Sales': 'danger',
    '生产': 'primary', '加工': 'success',
    '质检': 'warning', '包装': 'primary', '物流': 'warning', '销售': 'danger'
  }
  return map[stage] || 'info'
}

const getTimelineIcon = (stage) => {
  const map = {
    'Production': Operation, 'Processing': Operation,
    'QualityCheck': Operation, 'Packaging': Operation, // 补充了这两个
    'Transportation': Van, 'Sales': Sell,
    '生产': Operation, '加工': Operation,
    '质检': Operation, '包装': Operation, '物流': Van, '销售': Sell
  }
  return map[stage] || Location
}

const getStageName = (stage) => {
  const map = {
    'Production': '种植', 'Processing': '采摘',
    'QualityCheck': '质检', 'Packaging': '包装',
    'Transportation': '运输', 'Sales': '销售',
    '生产': '种植', '加工': '采摘', '物流': '运输'
  }
  return map[stage] || stage || '未知'
}
</script>

<style scoped>
/* ===========================
   Mobile-First H5 Layout
   =========================== */

.trace-page {
  max-width: 480px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f5f7fa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
  'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  padding-bottom: 40px;
  overflow-x: hidden;
}

/* ---------- 搜索回退区域 ---------- */
.search-fallback {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 32px 20px;
}
.search-fallback-inner {
  background: #fff;
  border-radius: 20px;
  padding: 48px 28px 36px;
  text-align: center;
  width: 100%;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);
}
.search-fallback-icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 12px;
}
.search-fallback-inner h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0 0 6px;
}
.search-fallback-inner p {
  font-size: 13px;
  color: #8e8e93;
  margin: 0 0 24px;
}

/* ---------- 加载态 ---------- */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: #8e8e93;
  gap: 16px;
}
.loading-spin {
  font-size: 36px;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.loading-state p {
  font-size: 14px;
  margin: 0;
}

/* ---------- 错误态 ---------- */
.error-block {
  text-align: center;
  padding: 80px 24px 40px;
}
.error-icon {
  font-size: 64px;
  color: #f56c6c;
}
.error-block h2 {
  font-size: 20px;
  color: #303133;
  margin: 16px 0 8px;
}
.error-block p {
  color: #909399;
  font-size: 14px;
  margin: 0 0 24px;
}

/* ---------- Hero Image ---------- */
.hero-image {
  width: 100%;
  background: #e9ecef;
}
.hero-img {
  width: 100%;
  height: 260px;
  display: block;
}
.hero-placeholder {
  width: 100%;
  height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #c3e8d0 0%, #a8d8ea 100%);
  color: #fff;
  gap: 10px;
}
.hero-placeholder-icon {
  font-size: 56px;
  opacity: 0.7;
}
.hero-placeholder span {
  font-size: 13px;
  opacity: 0.8;
}

/* ---------- 风控横幅包装器 ---------- */
.risk-wrapper {
  margin: 15px 16px 20px;
}
.risk-wrapper :deep(.el-alert) {
  border-radius: 8px;
  font-weight: bold;
  padding: 12px 16px;
}
.risk-wrapper :deep(.el-alert--warning) {
  background-color: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #f3d19e;
}
.risk-wrapper :deep(.el-alert--info) {
  background-color: #f4f4f5;
  color: #606266;
  border: 1px solid #dcdfe6;
}
.risk-wrapper :deep(.el-alert__icon) {
  font-size: 18px;
  width: 18px;
}

/* ---------- 认证卡片 ---------- */
.auth-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: #fff;
  margin: 15px 16px 0;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
  position: relative;
  z-index: 2;
}
.auth-left {
  flex-shrink: 0;
}
.auth-icon {
  font-size: 42px;
  color: #67c23a;
}
.auth-card.auth-warn .auth-icon {
  color: #e6a23c;
}
.auth-right {
  flex: 1;
  min-width: 0;
}
.auth-title {
  font-size: 18px;
  font-weight: 700;
  color: #1d1d1f;
  line-height: 1.3;
}
.auth-desc {
  font-size: 13px;
  color: #8e8e93;
  margin-top: 4px;
  line-height: 1.5;
}
.auth-desc strong {
  color: #67c23a;
  font-weight: 600;
}
.auth-card.auth-warn .auth-desc strong {
  color: #e6a23c;
}
.auth-warning-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 4px 10px;
  border-radius: 20px;
}

.auth-warning-tip--muted {
  color: #909399;
  background: #f4f4f5;
}

/* ---------- 产品信息卡片 ---------- */
.info-card {
  background: #fff;
  margin: 16px 16px 0;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.info-card-title {
  font-size: 12px;
  color: #8e8e93;
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 12px;
}
.product-name {
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 18px;
  line-height: 1.3;
  word-break: break-all;
}
.info-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.info-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 13px 0;
  border-bottom: 1px solid #f2f2f7;
}
.info-list li:last-child {
  border-bottom: none;
}
.info-label {
  font-size: 14px;
  color: #8e8e93;
  flex-shrink: 0;
}
.info-value {
  font-size: 14px;
  color: #1d1d1f;
  text-align: right;
  word-break: break-all;
}

/* ---------- 消费者版溯源摘要 ---------- */
.trace-summary-card {
  background: #fff;
  margin: 16px 16px 0;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.trace-summary-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.trace-summary-title {
  font-size: 12px;
  color: #8e8e93;
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 6px;
}

.trace-summary-subtitle {
  font-size: 17px;
  font-weight: 700;
  color: #1d1d1f;
}

.trace-summary-count {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f4f4f5;
  color: #8e8e93;
  font-size: 12px;
}

.stage-flow {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.stage-step {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 8px 9px;
  border-radius: 12px;
  background: #f7f8fa;
  color: #a0a4ad;
  font-size: 12px;
}

.stage-step.is-done {
  background: #f0f9eb;
  color: #67c23a;
}

.stage-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.stage-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-empty {
  padding: 16px;
  border-radius: 12px;
  background: #fdf6ec;
  color: #a36b16;
  font-size: 13px;
  line-height: 1.6;
}

.trace-highlights {
  display: grid;
  gap: 12px;
}

.highlight-card {
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, #fbfcfe 0%, #f7f9fc 100%);
  border: 1px solid #eef1f6;
}

.highlight-kicker {
  display: block;
  margin-bottom: 10px;
  color: #8e8e93;
  font-size: 12px;
}

.highlight-stage {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #a0a4ad;
  font-size: 12px;
}

.highlight-card p {
  margin: 10px 0 8px;
  color: #303133;
  font-size: 14px;
  line-height: 1.6;
}

.highlight-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #8e8e93;
  font-size: 12px;
}

.trace-toggle-btn {
  width: 100%;
  margin-top: 16px;
}

/* ---------- 溯源时光轴 ---------- */
.timeline-card {
  background: #fff;
  margin: 16px 16px 0;
  padding: 20px 20px 8px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.timeline-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.timeline-card-title {
  font-size: 12px;
  color: #8e8e93;
  text-transform: uppercase;
  letter-spacing: 2px;
}
.timeline-card-count {
  font-size: 12px;
  color: #c0c4cc;
}

/* timeline 节点内容 */
.tl-node {
  padding: 2px 0 8px;
}
.tl-node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tl-location {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: #909399;
}
.tl-detail {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  margin: 10px 0 8px;
  word-break: break-word;
}
.tl-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 4px;
}
.tl-meta {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #c0c4cc;
}

.tl-temp-icon {
  font-size: 12px;
  line-height: 1;
}

/* Element-plus timeline 微调 */
.timeline-card :deep(.el-timeline-item__timestamp) {
  font-size: 12px;
  color: #c0c4cc;
}
.timeline-card :deep(.el-timeline-item__wrapper) {
  padding-left: 24px;
}
.timeline-card :deep(.el-timeline) {
  padding-left: 0;
}

/* ---------- 底部品牌 ---------- */
.footer-brand {
  text-align: center;
  padding: 32px 16px 8px;
  font-size: 12px;
  color: #c0c4cc;
  letter-spacing: 1px;
}

/* ---------- 召回警告卡片 ---------- */
.recall-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: linear-gradient(135deg, #fff5f5 0%, #fff8e6 100%);
  margin: 15px 16px 0;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(245, 108, 108, 0.18);
  position: relative;
  z-index: 2;
  border: 1.5px solid #f56c6c;
}
.recall-left {
  flex-shrink: 0;
}
.recall-icon {
  font-size: 42px;
  color: #f56c6c;
}
.recall-right {
  flex: 1;
  min-width: 0;
}
.recall-title {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
  line-height: 1.3;
}
.recall-desc {
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
  line-height: 1.7;
}
.recall-desc strong {
  color: #f56c6c;
  font-weight: 700;
}

/* ---------- 过期卡片 (Expired) ---------- */
.expired-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: linear-gradient(135deg, #f4f4f5 0%, #e9e9eb 100%);
  margin: 15px 16px 0;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(144, 147, 153, 0.18);
  position: relative;
  z-index: 2;
  border: 1.5px solid #909399;
}
.expired-left {
  flex-shrink: 0;
}
.expired-icon {
  font-size: 42px;
  color: #909399;
}
.expired-right {
  flex: 1;
  min-width: 0;
}
.expired-title {
  font-size: 18px;
  font-weight: 700;
  color: #909399;
  line-height: 1.3;
}
.expired-desc {
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
  line-height: 1.7;
}
.expired-desc strong {
  color: #909399;
  font-weight: 700;
}

/* ---------- 高频异常卡片 (Abnormal) ---------- */
.abnormal-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: linear-gradient(135deg, #fdf6ec 0%, #fef0c7 100%);
  margin: 15px 16px 0;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(230, 162, 60, 0.18);
  position: relative;
  z-index: 2;
  border: 1.5px solid #e6a23c;
}
.abnormal-left {
  flex-shrink: 0;
}
.abnormal-icon {
  font-size: 42px;
  color: #e6a23c;
}
.abnormal-right {
  flex: 1;
  min-width: 0;
}
.abnormal-title {
  font-size: 18px;
  font-weight: 700;
  color: #e6a23c;
  line-height: 1.3;
}
.abnormal-desc {
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
  line-height: 1.7;
}
.abnormal-desc strong {
  color: #e6a23c;
  font-weight: 700;
}
</style>
