import type {
  TravelBudgetItem,
  TravelConstraintCheck,
  TravelHotelRecommendation,
  TravelPlan,
  TravelPlanDay,
  TravelPlanStop
} from '../types/api'
import { buildMapPoints, buildRoutePolylines, dayLabel, stopCostTotal } from './travelPlan'
import { normalizeDisplayText } from './text'

const PAGE_WIDTH = 1440
const PAGE_PADDING = 72
const CONTENT_WIDTH = PAGE_WIDTH - PAGE_PADDING * 2
const SECTION_GAP = 24
const CARD_GAP = 24
const CARD_RADIUS = 28
const EXPORT_SCALE = 2
const MAX_CANVAS_HEIGHT = 30000

export type ScrapbookVariant = 'story' | 'brief'

type CardTone = 'warm' | 'cool' | 'neutral' | 'alert'
type PrepTone = 'good' | 'warn' | 'alert' | 'neutral'

interface ScrapbookCopy {
  brand: string
  tagline: string
  tripSnapshot: string
  routeSketch: string
  highlights: string
  stays: string
  budget: string
  prep: string
  itinerary: string
  duration: string
  estimatedTotal: string
  stayArea: string
  readiness: string
  noWeather: string
  noChecks: string
  noRecommendations: string
  routeLegendHotel: string
  routeLegendStop: string
  routeFallback: string
  generatedWith: string
  departureLocks: string
  dailyBrief: string
  dayMeta: (day: TravelPlanDay) => string
  stopMeta: (stop: TravelPlanStop) => string
  hotelPrice: (hotel: TravelHotelRecommendation) => string
  budgetAmount: (item: TravelBudgetItem) => string
  generatedOn: (value: string) => string
}

interface SnapshotCard {
  label: string
  value: string
  hint: string
  tone: CardTone
}

interface PrepItem {
  label: string
  body: string
  tone: PrepTone
}

interface ExecutionNote {
  label: string
  body: string
  tone: CardTone
}

interface TextBlockMetrics {
  lines: string[]
  height: number
  y: number
}

export async function downloadTravelScrapbook(
  plan: TravelPlan,
  preferChinese: boolean,
  variant: ScrapbookVariant = 'story'
) {
  const measureCanvas = document.createElement('canvas')
  measureCanvas.width = PAGE_WIDTH
  measureCanvas.height = 32
  const measureContext = measureCanvas.getContext('2d')
  if (!measureContext) {
    return
  }

  const renderer = variant === 'brief' ? renderBriefScrapbook : renderStoryScrapbook
  const requiredHeight = renderer(measureContext, plan, preferChinese, false)
  const scale = Math.min(EXPORT_SCALE, MAX_CANVAS_HEIGHT / requiredHeight)
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.floor(PAGE_WIDTH * scale))
  canvas.height = Math.max(1, Math.floor(requiredHeight * scale))

  const context = canvas.getContext('2d')
  if (!context) {
    return
  }

  context.scale(scale, scale)
  context.imageSmoothingEnabled = true
  renderer(context, plan, preferChinese, true, requiredHeight)
  await downloadCanvas(canvas, buildFileName(plan, preferChinese, variant))
}

function renderStoryScrapbook(
  context: CanvasRenderingContext2D,
  plan: TravelPlan,
  preferChinese: boolean,
  shouldDraw: boolean,
  measuredHeight?: number
) {
  const copy = getCopy(preferChinese)
  const heroTags = buildHeroTags(plan, copy, preferChinese, 'story')
  const snapshotCards = buildSnapshotCards(plan, copy, preferChinese)
  const prepItems = buildPrepItems(plan, preferChinese)
  const leftWidth = (CONTENT_WIDTH - CARD_GAP) / 2
  const rightWidth = leftWidth
  const routeHeight = 360

  const heroHeight = measureHeroCard(context, plan, heroTags)
  const highlightsHeight = measureHighlightsCard(context, plan, leftWidth)
  const staysHeight = measureStaysCard(context, plan, leftWidth, copy)
  const budgetHeight = measureBudgetCard(context, plan, rightWidth, preferChinese, copy)
  const prepHeight = measurePrepCard(context, prepItems, copy, CONTENT_WIDTH)
  const daysHeight = measureDaysSection(context, plan, copy, preferChinese)
  const snapshotHeight = 164

  const topRowHeight = Math.max(highlightsHeight, routeHeight)
  const middleRowHeight = Math.max(staysHeight, budgetHeight)
  const totalHeight = measuredHeight ?? (
    PAGE_PADDING +
    heroHeight +
    SECTION_GAP +
    snapshotHeight +
    SECTION_GAP +
    topRowHeight +
    SECTION_GAP +
    middleRowHeight +
    SECTION_GAP +
    prepHeight +
    SECTION_GAP +
    daysHeight +
    92 +
    PAGE_PADDING
  )

  if (!shouldDraw) {
    return totalHeight
  }

  drawBackground(context, totalHeight)

  let cursorY = PAGE_PADDING

  drawHeroCard(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, heroHeight, plan, copy, heroTags)
  cursorY += heroHeight + SECTION_GAP

  drawSnapshotRow(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, snapshotHeight, snapshotCards)
  cursorY += snapshotHeight + SECTION_GAP

  drawHighlightsCard(context, PAGE_PADDING, cursorY, leftWidth, topRowHeight, plan, copy)
  drawRouteCard(context, PAGE_PADDING + leftWidth + CARD_GAP, cursorY, rightWidth, topRowHeight, plan, copy)
  cursorY += topRowHeight + SECTION_GAP

  drawStaysCard(context, PAGE_PADDING, cursorY, leftWidth, middleRowHeight, plan, copy)
  drawBudgetCard(context, PAGE_PADDING + leftWidth + CARD_GAP, cursorY, rightWidth, middleRowHeight, plan, preferChinese, copy)
  cursorY += middleRowHeight + SECTION_GAP

  drawPrepCard(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, prepHeight, prepItems, copy)
  cursorY += prepHeight + SECTION_GAP

  drawDaysSection(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, plan, copy, preferChinese)
  drawFooter(context, PAGE_PADDING, totalHeight - 72, CONTENT_WIDTH, copy)

  return totalHeight
}

function renderBriefScrapbook(
  context: CanvasRenderingContext2D,
  plan: TravelPlan,
  preferChinese: boolean,
  shouldDraw: boolean,
  measuredHeight?: number
) {
  const copy = getCopy(preferChinese)
  const heroTags = buildHeroTags(plan, copy, preferChinese, 'brief')
  const snapshotCards = buildSnapshotCards(plan, copy, preferChinese)
  const prepItems = buildPrepItems(plan, preferChinese).slice(0, 4)
  const executionNotes = buildExecutionNotes(plan, preferChinese)
  const leftWidth = (CONTENT_WIDTH - CARD_GAP) / 2
  const rightWidth = leftWidth
  const routeHeight = 320

  const heroHeight = measureHeroCard(context, plan, heroTags)
  const snapshotHeight = 164
  const notesHeight = measureExecutionNotesCard(context, executionNotes, CONTENT_WIDTH)
  const prepHeight = measurePrepCard(context, prepItems, copy, leftWidth)
  const staysHeight = measureStaysCard(context, plan, leftWidth, copy)
  const budgetHeight = measureBudgetCard(context, plan, rightWidth, preferChinese, copy)
  const briefDaysHeight = measureBriefDaysSection(context, plan, copy, preferChinese)

  const upperRowHeight = Math.max(prepHeight, routeHeight)
  const lowerRowHeight = Math.max(staysHeight, budgetHeight)
  const totalHeight = measuredHeight ?? (
    PAGE_PADDING +
    heroHeight +
    SECTION_GAP +
    snapshotHeight +
    SECTION_GAP +
    notesHeight +
    SECTION_GAP +
    upperRowHeight +
    SECTION_GAP +
    lowerRowHeight +
    SECTION_GAP +
    briefDaysHeight +
    92 +
    PAGE_PADDING
  )

  if (!shouldDraw) {
    return totalHeight
  }

  drawBackground(context, totalHeight)

  let cursorY = PAGE_PADDING

  drawHeroCard(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, heroHeight, plan, copy, heroTags)
  cursorY += heroHeight + SECTION_GAP

  drawSnapshotRow(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, snapshotHeight, snapshotCards)
  cursorY += snapshotHeight + SECTION_GAP

  drawExecutionNotesCard(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, notesHeight, executionNotes, copy)
  cursorY += notesHeight + SECTION_GAP

  drawPrepCard(context, PAGE_PADDING, cursorY, leftWidth, upperRowHeight, prepItems, copy)
  drawRouteCard(context, PAGE_PADDING + leftWidth + CARD_GAP, cursorY, rightWidth, upperRowHeight, plan, copy)
  cursorY += upperRowHeight + SECTION_GAP

  drawStaysCard(context, PAGE_PADDING, cursorY, leftWidth, lowerRowHeight, plan, copy)
  drawBudgetCard(context, PAGE_PADDING + leftWidth + CARD_GAP, cursorY, rightWidth, lowerRowHeight, plan, preferChinese, copy)
  cursorY += lowerRowHeight + SECTION_GAP

  drawBriefDaysSection(context, PAGE_PADDING, cursorY, CONTENT_WIDTH, plan, copy, preferChinese)
  drawFooter(context, PAGE_PADDING, totalHeight - 72, CONTENT_WIDTH, copy)

  return totalHeight
}

function getCopy(preferChinese: boolean): ScrapbookCopy {
  if (preferChinese) {
    return {
      brand: 'TravelAgent',
      tagline: '把聊天结果整理成一份可保存、可复盘、可转发的旅行手账。',
      tripSnapshot: '行程快照',
      routeSketch: '路线草图',
      highlights: '这次值得记住的重点',
      stays: '住宿建议',
      budget: '预算拆分',
      prep: '出发前先看',
      itinerary: '逐日行程',
      duration: '行程长度',
      estimatedTotal: '预计总花费',
      stayArea: '建议住宿区域',
      readiness: '执行把握',
      noWeather: '未提供天气快照',
      noChecks: '当前没有明显风险提醒，可以进入预订确认。',
      noRecommendations: '当前没有额外调整建议。',
      routeLegendHotel: '酒店',
      routeLegendStop: '景点 / 节点',
      routeFallback: '当前缺少足够坐标，先导出完整手账内容，地图草图会在下次补齐。',
      generatedWith: '由 TravelAgent 生成',
      departureLocks: '出发前锁定',
      dailyBrief: '执行版日程',
      dayMeta: (day: TravelPlanDay) => `${day.startTime} - ${day.endTime} · 活动 ${day.totalActivityMinutes} 分钟 · 通勤 ${day.totalTransitMinutes} 分钟 · 约 ${day.estimatedCost} 元`,
      stopMeta: (stop: TravelPlanStop) => `${slotLabel(stop.slot, true)} · ${normalizeDisplayText(stop.area)} · 停留 ${stop.durationMinutes} 分钟 · 约 ${stopCostTotal(stop)} 元`,
      hotelPrice: (hotel: TravelHotelRecommendation) => `${hotel.nightlyMin}-${hotel.nightlyMax} 元 / 晚`,
      budgetAmount: (item: TravelBudgetItem) => `${item.minAmount}-${item.maxAmount} 元`,
      generatedOn: (value: string) => `生成时间 ${formatDate(value, true)}`
    }
  }

  return {
    brand: 'TravelAgent',
    tagline: 'A long-form travel summary you can save, review, and send around.',
    tripSnapshot: 'Trip Snapshot',
    routeSketch: 'Route Sketch',
    highlights: 'Highlights Worth Remembering',
    stays: 'Stay Recommendations',
    budget: 'Budget Breakdown',
    prep: 'Before You Book',
    itinerary: 'Day By Day',
    duration: 'Trip Span',
    estimatedTotal: 'Estimated Total',
    stayArea: 'Suggested Base',
    readiness: 'Readiness',
    noWeather: 'No weather snapshot included',
    noChecks: 'No major risks flagged. You can move on to booking confirmation.',
    noRecommendations: 'No extra adjustment notes at the moment.',
    routeLegendHotel: 'Hotel',
    routeLegendStop: 'Stop',
    routeFallback: 'Not enough grounded coordinates yet. The full scrapbook still exports, and the route sketch will catch up once locations are confirmed.',
    generatedWith: 'Generated with TravelAgent',
    departureLocks: 'Lock Before You Go',
    dailyBrief: 'Run Sheet',
    dayMeta: (day: TravelPlanDay) => `${day.startTime} - ${day.endTime} · ${day.totalActivityMinutes} min activity · ${day.totalTransitMinutes} min transit · About ${day.estimatedCost} CNY`,
    stopMeta: (stop: TravelPlanStop) => `${slotLabel(stop.slot, false)} · ${normalizeDisplayText(stop.area)} · ${stop.durationMinutes} min stay · About ${stopCostTotal(stop)} CNY`,
    hotelPrice: (hotel: TravelHotelRecommendation) => `${hotel.nightlyMin}-${hotel.nightlyMax} CNY / night`,
    budgetAmount: (item: TravelBudgetItem) => `${item.minAmount}-${item.maxAmount} CNY`,
    generatedOn: (value: string) => `Generated ${formatDate(value, false)}`
  }
}

function buildHeroTags(
  plan: TravelPlan,
  copy: ScrapbookCopy,
  preferChinese: boolean,
  variant: ScrapbookVariant
) {
  return [
    getVariantLabel(variant, preferChinese),
    `${copy.duration}: ${plan.days.length}`,
    `${copy.estimatedTotal}: ${plan.estimatedTotalMin}-${plan.estimatedTotalMax}`,
    `${copy.stayArea}: ${normalizeDisplayText(plan.hotelArea)}`,
    copy.generatedOn(plan.updatedAt)
  ]
}

function buildSnapshotCards(plan: TravelPlan, copy: ScrapbookCopy, preferChinese: boolean): SnapshotCard[] {
  const totalStops = plan.days.reduce((sum, day) => sum + day.stops.length, 0)
  const readiness = getReadiness(plan, preferChinese)
  const budgetHint = plan.totalBudget == null
    ? (preferChinese ? '当前没有预算上限，适合继续补充约束。' : 'No budget cap yet, so tradeoffs are still flexible.')
    : plan.estimatedTotalMax <= plan.totalBudget
      ? (preferChinese ? `距离预算上限还有 ${plan.totalBudget - plan.estimatedTotalMax} 元余量。` : `${plan.totalBudget - plan.estimatedTotalMax} CNY headroom remains.`)
      : (preferChinese ? `当前预计超出预算 ${plan.estimatedTotalMax - plan.totalBudget} 元。` : `Currently ${plan.estimatedTotalMax - plan.totalBudget} CNY over the cap.`)

  return [
    {
      label: copy.duration,
      value: preferChinese ? `${plan.days.length} 天 / ${totalStops} 站` : `${plan.days.length} days / ${totalStops} stops`,
      hint: plan.days.map(day => normalizeDisplayText(day.theme)).join(' · '),
      tone: 'warm'
    },
    {
      label: copy.estimatedTotal,
      value: preferChinese ? `${plan.estimatedTotalMin}-${plan.estimatedTotalMax} 元` : `${plan.estimatedTotalMin}-${plan.estimatedTotalMax} CNY`,
      hint: budgetHint,
      tone: plan.totalBudget != null && plan.estimatedTotalMax > plan.totalBudget ? 'alert' : 'cool'
    },
    {
      label: copy.readiness,
      value: readiness.value,
      hint: readiness.hint,
      tone: readiness.tone
    }
  ]
}

function buildPrepItems(plan: TravelPlan, preferChinese: boolean): PrepItem[] {
  const items: PrepItem[] = []

  items.push(
    ...(plan.adjustmentSuggestions ?? []).slice(0, 2).map((item, index) => ({
      label: preferChinese ? `规划建议 0${index + 1}` : `Planner Note 0${index + 1}`,
      body: normalizeDisplayText(item),
      tone: 'neutral' as const
    }))
  )

  items.push(
    ...plan.checks.slice(0, 4).map(check => ({
      label: localizeCheckStatus(check, preferChinese),
      body: normalizeDisplayText(check.message),
      tone: getCheckTone(check.status)
    }))
  )

  return items.slice(0, 6)
}

function buildExecutionNotes(plan: TravelPlan, preferChinese: boolean): ExecutionNote[] {
  const notes: ExecutionNote[] = []
  const primaryHotel = plan.hotels[0]
  const firstCheck = plan.checks.find(check => check.status !== 'PASS')
  const firstDay = plan.days[0]
  const firstStop = firstDay?.stops[0]

  if (primaryHotel) {
    notes.push({
      label: preferChinese ? '住宿基点' : 'Base Stay',
      body: [
        normalizeDisplayText(primaryHotel.name),
        normalizeDisplayText(primaryHotel.area),
        preferChinese
          ? `${primaryHotel.nightlyMin}-${primaryHotel.nightlyMax} 元 / 晚`
          : `${primaryHotel.nightlyMin}-${primaryHotel.nightlyMax} CNY / night`
      ].filter(Boolean).join(' · '),
      tone: 'warm'
    })
  }

  notes.push({
    label: preferChinese ? '预算信号' : 'Budget Signal',
    body: plan.totalBudget == null
      ? (preferChinese ? '当前没有预算上限，适合继续补充约束。' : 'No budget cap yet, so tradeoffs are still flexible.')
      : plan.estimatedTotalMax <= plan.totalBudget
        ? (preferChinese
          ? `当前预计 ${plan.estimatedTotalMin}-${plan.estimatedTotalMax} 元，仍在预算内。`
          : `Estimated at ${plan.estimatedTotalMin}-${plan.estimatedTotalMax} CNY and still within budget.`)
        : (preferChinese
          ? `当前预计超出预算 ${plan.estimatedTotalMax - plan.totalBudget} 元，建议先压缩高成本环节。`
          : `Currently ${plan.estimatedTotalMax - plan.totalBudget} CNY over budget. Trim the highest-cost items first.`),
    tone: plan.totalBudget != null && plan.estimatedTotalMax > plan.totalBudget ? 'alert' : 'cool'
  })

  if (firstCheck) {
    notes.push({
      label: preferChinese ? '风险提醒' : 'Risk Signal',
      body: normalizeDisplayText(firstCheck.message),
      tone: firstCheck.status === 'FAIL' ? 'alert' : 'neutral'
    })
  }

  if (firstDay) {
    notes.push({
      label: preferChinese ? '第一天锚点' : 'Day One Anchor',
      body: [
        `${dayLabel(firstDay, preferChinese)} · ${normalizeDisplayText(firstDay.theme)}`,
        firstStop ? `${firstStop.startTime} ${normalizeDisplayText(firstStop.name)}` : ''
      ].filter(Boolean).join(' · '),
      tone: 'cool'
    })
  }

  if (plan.weatherSnapshot?.description && notes.length < 4) {
    notes.push({
      label: preferChinese ? '天气提示' : 'Weather',
      body: [
        normalizeDisplayText(plan.weatherSnapshot.city ?? ''),
        normalizeDisplayText(plan.weatherSnapshot.description ?? ''),
        plan.weatherSnapshot.temperature ? `${plan.weatherSnapshot.temperature}${preferChinese ? '℃' : ' C'}` : ''
      ].filter(Boolean).join(' · '),
      tone: 'neutral'
    })
  }

  if (!notes.length) {
    notes.push({
      label: preferChinese ? '继续推进' : 'Keep Moving',
      body: preferChinese ? '当前没有明显风险，下一步可以确认酒店、票务和出发时间。' : 'No major risks flagged. Move on to hotels, tickets, and departure timing.',
      tone: 'cool'
    })
  }

  return notes.slice(0, 4)
}

function getVariantLabel(variant: ScrapbookVariant, preferChinese: boolean) {
  if (variant === 'brief') {
    return preferChinese ? '执行版手账' : 'Trip Brief'
  }
  return preferChinese ? '分享版手账' : 'Share Story'
}

function getReadiness(plan: TravelPlan, preferChinese: boolean) {
  const failCount = plan.checks.filter(check => check.status === 'FAIL').length
  const warnCount = plan.checks.filter(check => check.status === 'WARN').length

  if (failCount > 0) {
    return {
      value: preferChinese ? '建议先调整' : 'Adjust First',
      hint: preferChinese ? `有 ${failCount} 项冲突需要先处理。` : `${failCount} blocking issues should be resolved first.`,
      tone: 'alert' as const
    }
  }

  if (warnCount > 0 || plan.constraintRelaxed) {
    return {
      value: preferChinese ? '可用但需留意' : 'Usable With Caveats',
      hint: preferChinese ? `有 ${warnCount} 项提醒，适合预订前再过一遍。` : `${warnCount} reminders are worth reviewing before booking.`,
      tone: 'neutral' as const
    }
  }

  return {
    value: preferChinese ? '可以继续推进' : 'Ready To Move',
    hint: preferChinese ? '没有明显冲突，可以开始确认酒店和门票。' : 'No major conflicts detected. You can confirm hotels and tickets next.',
    tone: 'cool' as const
  }
}

function localizeCheckStatus(check: TravelConstraintCheck, preferChinese: boolean) {
  if (preferChinese) {
    if (check.status === 'FAIL') {
      return '需要处理'
    }
    if (check.status === 'WARN') {
      return '需要确认'
    }
    return '已通过'
  }

  if (check.status === 'FAIL') {
    return 'Needs Fix'
  }
  if (check.status === 'WARN') {
    return 'Needs Review'
  }
  return 'Passed'
}

function getCheckTone(status: TravelConstraintCheck['status']): PrepTone {
  if (status === 'FAIL') {
    return 'alert'
  }
  if (status === 'WARN') {
    return 'warn'
  }
  return 'good'
}

function measureHeroCard(context: CanvasRenderingContext2D, plan: TravelPlan, tags: string[]) {
  context.font = '700 56px "Space Grotesk", "Noto Sans SC", sans-serif'
  const titleHeight = measureTextBlock(context, normalizeDisplayText(plan.title), CONTENT_WIDTH - 96, 1.18).height
  context.font = '24px "Noto Sans SC", "Segoe UI", sans-serif'
  const summaryHeight = measureTextBlock(context, normalizeDisplayText(plan.summary), CONTENT_WIDTH - 96, 1.5).height
  const tagsHeight = measureChipGroup(context, tags, CONTENT_WIDTH - 96, 38, '600 17px "IBM Plex Mono", "Segoe UI", monospace')
  return 64 + titleHeight + 20 + summaryHeight + 24 + tagsHeight + 56
}

function measureHighlightsCard(context: CanvasRenderingContext2D, plan: TravelPlan, width: number) {
  const contentWidth = width - 56
  let height = 94
  context.font = '600 18px "Noto Sans SC", "Segoe UI", sans-serif'
  for (const item of plan.highlights.slice(0, 5)) {
    height += measureTextBlock(context, normalizeDisplayText(item), contentWidth - 24, 1.45).height + 20
  }
  return Math.max(height + 20, 260)
}

function measureStaysCard(
  context: CanvasRenderingContext2D,
  plan: TravelPlan,
  width: number,
  copy: ScrapbookCopy
) {
  const contentWidth = width - 56
  let height = 94
  context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
  for (const hotel of plan.hotels.slice(0, 3)) {
    const titleHeight = measureTextBlock(context, normalizeDisplayText(hotel.name), contentWidth, 1.2).height
    context.font = '16px "IBM Plex Mono", "Segoe UI", monospace'
    const priceHeight = measureTextBlock(context, copy.hotelPrice(hotel), contentWidth, 1.25).height
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    const reasonHeight = measureTextBlock(context, normalizeDisplayText(hotel.rationale), contentWidth, 1.45).height
    height += titleHeight + priceHeight + reasonHeight + 42
  }
  return Math.max(height + 20, 280)
}

function measureBudgetCard(
  context: CanvasRenderingContext2D,
  plan: TravelPlan,
  width: number,
  preferChinese: boolean,
  copy: ScrapbookCopy
) {
  const contentWidth = width - 56
  let height = 94
  context.font = '700 18px "Noto Sans SC", "Segoe UI", sans-serif'
  for (const item of plan.budget) {
    const titleHeight = measureTextBlock(context, budgetCategoryLabel(item.category, preferChinese), contentWidth, 1.25).height
    context.font = '16px "IBM Plex Mono", "Segoe UI", monospace'
    const amountHeight = measureTextBlock(context, copy.budgetAmount(item), contentWidth, 1.25).height
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    const reasonHeight = measureTextBlock(context, normalizeDisplayText(item.rationale), contentWidth, 1.45).height
    height += titleHeight + amountHeight + reasonHeight + 36
  }
  return Math.max(height + 20, 280)
}

function measurePrepCard(
  context: CanvasRenderingContext2D,
  items: PrepItem[],
  copy: ScrapbookCopy,
  width: number
) {
  if (!items.length) {
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    return 110 + measureTextBlock(context, copy.noChecks, width - 56, 1.45).height
  }

  let height = 94
  for (const item of items) {
    context.font = '700 14px "IBM Plex Mono", "Segoe UI", monospace'
    const badgeWidth = Math.min(Math.max(context.measureText(item.label.toUpperCase()).width + 28, 112), width - 56)
    const bodyWidth = Math.max(width - 56 - badgeWidth - 16, 120)
    const labelHeight = 30
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    const bodyHeight = measureTextBlock(context, item.body, bodyWidth, 1.45).height
    height += labelHeight + bodyHeight + 30
  }
  return height + 20
}

function measureDaysSection(
  context: CanvasRenderingContext2D,
  plan: TravelPlan,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  let height = 66
  const dayWidth = CONTENT_WIDTH
  for (const day of plan.days) {
    height += measureDayCard(context, dayWidth, day, copy, preferChinese) + SECTION_GAP
  }
  return height
}

function drawBackground(context: CanvasRenderingContext2D, height: number) {
  const gradient = context.createLinearGradient(0, 0, PAGE_WIDTH, height)
  gradient.addColorStop(0, '#f7efe4')
  gradient.addColorStop(0.52, '#f6f2eb')
  gradient.addColorStop(1, '#ebf0ec')
  context.fillStyle = gradient
  context.fillRect(0, 0, PAGE_WIDTH, height)

  context.fillStyle = 'rgba(214, 98, 58, 0.08)'
  context.beginPath()
  context.arc(180, 180, 120, 0, Math.PI * 2)
  context.fill()

  context.fillStyle = 'rgba(15, 123, 115, 0.08)'
  context.beginPath()
  context.arc(PAGE_WIDTH - 180, 260, 180, 0, Math.PI * 2)
  context.fill()

  context.fillStyle = 'rgba(23, 48, 66, 0.04)'
  context.fillRect(PAGE_PADDING, PAGE_PADDING, CONTENT_WIDTH, height - PAGE_PADDING * 2)
}

function drawHeroCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  plan: TravelPlan,
  copy: ScrapbookCopy,
  tags: string[]
) {
  drawCard(context, x, y, width, height, '#fff7ee', '#fefcf8')

  context.fillStyle = '#a14a2b'
  context.font = '700 18px "IBM Plex Mono", "Segoe UI", monospace'
  context.fillText(copy.brand.toUpperCase(), x + 48, y + 48)

  context.fillStyle = '#153546'
  context.font = '700 56px "Space Grotesk", "Noto Sans SC", sans-serif'
  const titleMetrics = drawTextBlock(context, normalizeDisplayText(plan.title), x + 48, y + 82, width - 96, 1.18)

  context.fillStyle = '#506067'
  context.font = '24px "Noto Sans SC", "Segoe UI", sans-serif'
  const summaryMetrics = drawTextBlock(
    context,
    normalizeDisplayText(plan.summary) || copy.tagline,
    x + 48,
    titleMetrics.y + 20,
    width - 96,
    1.5
  )

  drawChipGroup(
    context,
    tags,
    x + 48,
    summaryMetrics.y + 24,
    width - 96,
    38,
    '600 17px "IBM Plex Mono", "Segoe UI", monospace',
    '#fff8f1',
    '#a14a2b',
    'rgba(161, 74, 43, 0.12)'
  )
}

function drawSnapshotRow(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  cards: SnapshotCard[]
) {
  const cardWidth = (width - CARD_GAP * (cards.length - 1)) / cards.length

  cards.forEach((card, index) => {
    const cardX = x + index * (cardWidth + CARD_GAP)
    const tone = getSnapshotTone(card.tone)
    drawCard(context, cardX, y, cardWidth, height, tone.start, tone.end)

    context.fillStyle = tone.eyebrow
    context.font = '700 15px "IBM Plex Mono", "Segoe UI", monospace'
    context.fillText(card.label.toUpperCase(), cardX + 24, y + 38)

    context.fillStyle = '#183649'
    context.font = '700 30px "Space Grotesk", "Noto Sans SC", sans-serif'
    const valueMetrics = drawTextBlock(context, card.value, cardX + 24, y + 68, cardWidth - 48, 1.18)

    context.fillStyle = '#5b676d'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    drawTextBlock(context, card.hint, cardX + 24, valueMetrics.y + 12, cardWidth - 48, 1.45)
  })
}

function drawHighlightsCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  plan: TravelPlan,
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#ffffff', '#fff8ef')
  drawCardTitle(context, x + 28, y + 34, copy.highlights, '#a14a2b')

  let cursorY = y + 84
  const contentWidth = width - 56
  plan.highlights.slice(0, 5).forEach((item, index) => {
    drawNumberBullet(context, x + 28, cursorY + 4, index + 1, '#d6623a')
    context.fillStyle = '#21333d'
    context.font = '600 18px "Noto Sans SC", "Segoe UI", sans-serif'
    const metrics = drawTextBlock(context, normalizeDisplayText(item), x + 64, cursorY, contentWidth - 36, 1.45)
    cursorY = metrics.y + 16
  })
}

function drawRouteCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  plan: TravelPlan,
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#f4faf8', '#fbfdfc')
  drawCardTitle(context, x + 28, y + 34, copy.routeSketch, '#0f7b73')
  drawRouteSketch(context, x + 28, y + 76, width - 56, height - 130, plan, copy)
  drawLegend(context, x + 28, y + height - 34, copy)
}

function drawStaysCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  plan: TravelPlan,
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#fffdf8', '#fdf8f0')
  drawCardTitle(context, x + 28, y + 34, copy.stays, '#183649')

  let cursorY = y + 82
  const contentWidth = width - 56

  plan.hotels.slice(0, 3).forEach((hotel, index) => {
    if (index > 0) {
      context.strokeStyle = 'rgba(24, 54, 73, 0.08)'
      context.lineWidth = 1
      context.beginPath()
      context.moveTo(x + 28, cursorY - 14)
      context.lineTo(x + width - 28, cursorY - 14)
      context.stroke()
    }

    context.fillStyle = '#183649'
    context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
    const titleMetrics = drawTextBlock(context, normalizeDisplayText(hotel.name), x + 28, cursorY, contentWidth, 1.2)

    context.fillStyle = '#a14a2b'
    context.font = '600 16px "IBM Plex Mono", "Segoe UI", monospace'
    const priceMetrics = drawTextBlock(context, copy.hotelPrice(hotel), x + 28, titleMetrics.y + 10, contentWidth, 1.25)

    context.fillStyle = '#516168'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    const reasonMetrics = drawTextBlock(context, normalizeDisplayText(hotel.rationale), x + 28, priceMetrics.y + 10, contentWidth, 1.45)

    const tagText = normalizeDisplayText(hotel.area) || normalizeDisplayText(plan.hotelArea)
    drawChipGroup(
      context,
      [tagText],
      x + 28,
      reasonMetrics.y + 12,
      contentWidth,
      32,
      '600 15px "Noto Sans SC", "Segoe UI", sans-serif',
      '#f6f2ea',
      '#183649',
      'rgba(24, 54, 73, 0.08)'
    )

    cursorY = reasonMetrics.y + 54
  })
}

function drawBudgetCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  plan: TravelPlan,
  preferChinese: boolean,
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#f7fafc', '#ffffff')
  drawCardTitle(context, x + 28, y + 34, copy.budget, '#183649')

  let cursorY = y + 82
  const contentWidth = width - 56

  plan.budget.forEach((item, index) => {
    if (index > 0) {
      context.strokeStyle = 'rgba(24, 54, 73, 0.08)'
      context.beginPath()
      context.moveTo(x + 28, cursorY - 14)
      context.lineTo(x + width - 28, cursorY - 14)
      context.stroke()
    }

    context.fillStyle = '#183649'
    context.font = '700 18px "Noto Sans SC", "Segoe UI", sans-serif'
    const titleMetrics = drawTextBlock(
      context,
      budgetCategoryLabel(item.category, preferChinese),
      x + 28,
      cursorY,
      contentWidth,
      1.25
    )

    context.fillStyle = '#0f7b73'
    context.font = '600 16px "IBM Plex Mono", "Segoe UI", monospace'
    const amountMetrics = drawTextBlock(context, copy.budgetAmount(item), x + 28, titleMetrics.y + 8, contentWidth, 1.25)

    context.fillStyle = '#5b676d'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    const reasonMetrics = drawTextBlock(context, normalizeDisplayText(item.rationale), x + 28, amountMetrics.y + 10, contentWidth, 1.45)

    cursorY = reasonMetrics.y + 24
  })
}

function drawPrepCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  items: PrepItem[],
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#fffaf5', '#fffdfb')
  drawCardTitle(context, x + 28, y + 34, copy.prep, '#a14a2b')

  if (!items.length) {
    context.fillStyle = '#58666c'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    drawTextBlock(context, copy.noChecks, x + 28, y + 84, width - 56, 1.45)
    return
  }

  let cursorY = y + 84
  const contentWidth = width - 56

  items.forEach(item => {
    const tone = getPrepColors(item.tone)
    context.font = '700 14px "IBM Plex Mono", "Segoe UI", monospace'
    const badgeText = item.label.toUpperCase()
    const badgeWidth = Math.min(Math.max(context.measureText(badgeText).width + 28, 112), width - 56)
    context.fillStyle = tone.badge
    roundRect(context, x + 28, cursorY, badgeWidth, 30, 15)
    context.fill()

    context.fillStyle = tone.badgeText
    context.fillText(badgeText, x + 42, cursorY + 20)

    context.fillStyle = '#233541'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    const bodyStartX = x + 28 + badgeWidth + 16
    const bodyMetrics = drawTextBlock(context, item.body, bodyStartX, cursorY + 2, x + width - 28 - bodyStartX, 1.45)
    cursorY = bodyMetrics.y + 18
  })
}

function measureExecutionNotesCard(
  context: CanvasRenderingContext2D,
  notes: ExecutionNote[],
  width: number
) {
  const contentWidth = width - 56
  const cardWidth = (contentWidth - CARD_GAP) / 2
  const rowGap = 14
  let bodyHeight = 0

  for (let index = 0; index < notes.length; index += 2) {
    const leftHeight = measureExecutionNoteTile(context, cardWidth, notes[index])
    const rightHeight = notes[index + 1] ? measureExecutionNoteTile(context, cardWidth, notes[index + 1]) : 0
    bodyHeight += Math.max(leftHeight, rightHeight)
    if (index + 2 < notes.length) {
      bodyHeight += rowGap
    }
  }

  return 94 + bodyHeight + 24
}

function measureExecutionNoteTile(
  context: CanvasRenderingContext2D,
  width: number,
  note: ExecutionNote
) {
  context.font = '700 14px "IBM Plex Mono", "Segoe UI", monospace'
  const labelHeight = measureTextBlock(context, note.label, width - 36, 1.25).height
  context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
  const bodyHeight = measureTextBlock(context, note.body, width - 36, 1.45).height
  return 22 + labelHeight + 12 + bodyHeight + 22
}

function drawExecutionNotesCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  notes: ExecutionNote[],
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#fff9f2', '#fffefd')
  drawCardTitle(context, x + 28, y + 34, copy.departureLocks, '#a14a2b')

  const contentWidth = width - 56
  const cardWidth = (contentWidth - CARD_GAP) / 2
  let cursorY = y + 82

  for (let index = 0; index < notes.length; index += 2) {
    const left = notes[index]
    const right = notes[index + 1]
    const leftHeight = measureExecutionNoteTile(context, cardWidth, left)
    const rightHeight = right ? measureExecutionNoteTile(context, cardWidth, right) : 0
    const rowHeight = Math.max(leftHeight, rightHeight)

    drawExecutionNoteTile(context, x + 28, cursorY, cardWidth, rowHeight, left)
    if (right) {
      drawExecutionNoteTile(context, x + 28 + cardWidth + CARD_GAP, cursorY, cardWidth, rowHeight, right)
    }
    cursorY += rowHeight + 14
  }
}

function drawExecutionNoteTile(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  note: ExecutionNote
) {
  const tone = getSnapshotTone(note.tone)
  drawCard(context, x, y, width, height, tone.start, tone.end)

  context.fillStyle = tone.eyebrow
  context.font = '700 14px "IBM Plex Mono", "Segoe UI", monospace'
  const labelMetrics = drawTextBlock(context, note.label.toUpperCase(), x + 18, y + 24, width - 36, 1.25)

  context.fillStyle = '#203542'
  context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
  drawTextBlock(context, note.body, x + 18, labelMetrics.y + 10, width - 36, 1.45)
}

function drawDaysSection(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  plan: TravelPlan,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  context.fillStyle = '#183649'
  context.font = '700 30px "Space Grotesk", "Noto Sans SC", sans-serif'
  context.fillText(copy.itinerary, x, y + 32)

  let cursorY = y + 66
  for (const day of plan.days) {
    const dayHeight = measureDayCard(context, width, day, copy, preferChinese)
    drawDayCard(context, x, cursorY, width, dayHeight, day, copy, preferChinese)
    cursorY += dayHeight + SECTION_GAP
  }
}

function measureBriefDaysSection(
  context: CanvasRenderingContext2D,
  plan: TravelPlan,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  let height = 66
  for (const day of plan.days) {
    height += measureBriefDayCard(context, CONTENT_WIDTH, day, copy, preferChinese) + 18
  }
  return height
}

function drawBriefDaysSection(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  plan: TravelPlan,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  context.fillStyle = '#183649'
  context.font = '700 30px "Space Grotesk", "Noto Sans SC", sans-serif'
  context.fillText(copy.dailyBrief, x, y + 32)

  let cursorY = y + 66
  for (const day of plan.days) {
    const cardHeight = measureBriefDayCard(context, width, day, copy, preferChinese)
    drawBriefDayCard(context, x, cursorY, width, cardHeight, day, copy, preferChinese)
    cursorY += cardHeight + 18
  }
}

function measureBriefDayCard(
  context: CanvasRenderingContext2D,
  width: number,
  day: TravelPlanDay,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  const contentWidth = width - 56
  context.font = '700 26px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleHeight = measureTextBlock(context, normalizeDisplayText(day.theme), contentWidth, 1.2).height
  const chipHeight = measureChipGroup(
    context,
    [
      dayLabel(day, preferChinese),
      `${day.startTime} - ${day.endTime}`,
      preferChinese ? `约 ${day.estimatedCost} 元` : `About ${day.estimatedCost} CNY`
    ],
    contentWidth,
    30,
    '600 14px "IBM Plex Mono", "Segoe UI", monospace'
  )

  let bodyHeight = 34 + titleHeight + 16 + chipHeight + 20
  for (const stop of day.stops) {
    bodyHeight += measureBriefStopLine(context, contentWidth, stop, preferChinese) + 10
  }
  if (day.returnToHotel) {
    bodyHeight += 48
  }
  return bodyHeight + 18
}

function drawBriefDayCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  day: TravelPlanDay,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  drawCard(context, x, y, width, height, '#ffffff', '#f9fbfc')

  context.fillStyle = '#183649'
  context.font = '700 26px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleMetrics = drawTextBlock(context, normalizeDisplayText(day.theme), x + 28, y + 36, width - 56, 1.2)

  drawChipGroup(
    context,
    [
      dayLabel(day, preferChinese),
      `${day.startTime} - ${day.endTime}`,
      preferChinese ? `约 ${day.estimatedCost} 元` : `About ${day.estimatedCost} CNY`
    ],
    x + 28,
    titleMetrics.y + 12,
    width - 56,
    30,
    '600 14px "IBM Plex Mono", "Segoe UI", monospace',
    '#f3f6f7',
    '#183649',
    'rgba(24, 54, 73, 0.08)'
  )

  const chipHeight = measureChipGroup(
    context,
    [
      dayLabel(day, preferChinese),
      `${day.startTime} - ${day.endTime}`,
      preferChinese ? `约 ${day.estimatedCost} 元` : `About ${day.estimatedCost} CNY`
    ],
    width - 56,
    30,
    '600 14px "IBM Plex Mono", "Segoe UI", monospace'
  )

  let cursorY = titleMetrics.y + 22 + chipHeight

  for (const stop of day.stops) {
    const lineHeight = measureBriefStopLine(context, width - 56, stop, preferChinese)
    drawBriefStopLine(context, x + 28, cursorY, width - 56, lineHeight, stop, preferChinese)
    cursorY += lineHeight + 10
  }

  if (day.returnToHotel) {
    context.fillStyle = '#0f7b73'
    context.font = '700 13px "IBM Plex Mono", "Segoe UI", monospace'
    context.fillText(preferChinese ? '返程' : 'RETURN', x + 28, cursorY + 14)

    context.fillStyle = '#5a676e'
    context.font = '15px "Noto Sans SC", "Segoe UI", sans-serif'
    drawTextBlock(
      context,
      `${normalizeDisplayText(day.returnToHotel.toName)} · ${normalizeDisplayText(day.returnToHotel.summary)}`,
      x + 92,
      cursorY + 2,
      width - 120,
      1.4
    )
  }
}

function measureBriefStopLine(
  context: CanvasRenderingContext2D,
  width: number,
  stop: TravelPlanStop,
  preferChinese: boolean
) {
  const contentWidth = width - 108
  context.font = '700 18px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleHeight = measureTextBlock(context, normalizeDisplayText(stop.name), contentWidth, 1.2).height
  context.font = '15px "Noto Sans SC", "Segoe UI", sans-serif'
  const metaHeight = measureTextBlock(context, buildBriefStopMeta(stop, preferChinese), contentWidth, 1.35).height
  return Math.max(40, titleHeight + metaHeight + 8)
}

function drawBriefStopLine(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  stop: TravelPlanStop,
  preferChinese: boolean
) {
  context.fillStyle = 'rgba(247, 241, 233, 0.88)'
  roundRect(context, x, y, width, height, 18)
  context.fill()

  context.fillStyle = '#a14a2b'
  context.font = '700 13px "IBM Plex Mono", "Segoe UI", monospace'
  context.fillText(stop.startTime, x + 16, y + 20)

  context.fillStyle = '#183649'
  context.font = '700 18px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleMetrics = drawTextBlock(context, normalizeDisplayText(stop.name), x + 92, y + 18, width - 108, 1.2)

  context.fillStyle = '#5b676d'
  context.font = '15px "Noto Sans SC", "Segoe UI", sans-serif'
  drawTextBlock(context, buildBriefStopMeta(stop, preferChinese), x + 92, titleMetrics.y + 6, width - 108, 1.35)
}

function buildBriefStopMeta(stop: TravelPlanStop, preferChinese: boolean) {
  return [
    slotLabel(stop.slot, preferChinese),
    normalizeDisplayText(stop.area),
    preferChinese ? `停留 ${stop.durationMinutes} 分钟` : `${stop.durationMinutes} min`,
    preferChinese ? `约 ${stopCostTotal(stop)} 元` : `About ${stopCostTotal(stop)} CNY`
  ].filter(Boolean).join(' · ')
}

function measureDayCard(
  context: CanvasRenderingContext2D,
  width: number,
  day: TravelPlanDay,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  const contentWidth = width - 56
  context.font = '700 30px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleHeight = measureTextBlock(context, `${dayLabel(day, preferChinese)} · ${normalizeDisplayText(day.theme)}`, contentWidth, 1.2).height
  context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
  const metaHeight = measureTextBlock(context, copy.dayMeta(day), contentWidth, 1.45).height

  let bodyHeight = 48 + titleHeight + metaHeight + 26
  for (const stop of day.stops) {
    bodyHeight += measureStopBlock(context, width - 56, stop, copy) + 14
  }
  if (day.returnToHotel) {
    bodyHeight += 76
  }
  return bodyHeight + 28
}

function drawDayCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  day: TravelPlanDay,
  copy: ScrapbookCopy,
  preferChinese: boolean
) {
  drawCard(context, x, y, width, height, '#ffffff', '#f9fbfc')

  context.fillStyle = '#a14a2b'
  context.font = '700 15px "IBM Plex Mono", "Segoe UI", monospace'
  context.fillText(dayLabel(day, preferChinese).toUpperCase(), x + 28, y + 36)

  context.fillStyle = '#183649'
  context.font = '700 30px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleMetrics = drawTextBlock(context, normalizeDisplayText(day.theme), x + 28, y + 58, width - 56, 1.2)

  context.fillStyle = '#58666c'
  context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
  const metaMetrics = drawTextBlock(context, copy.dayMeta(day), x + 28, titleMetrics.y + 10, width - 56, 1.45)

  let cursorY = metaMetrics.y + 18
  for (const stop of day.stops) {
    const stopHeight = measureStopBlock(context, width - 56, stop, copy)
    drawStopBlock(context, x + 28, cursorY, width - 56, stopHeight, stop, copy)
    cursorY += stopHeight + 14
  }

  if (day.returnToHotel) {
    drawReturnBlock(context, x + 28, cursorY, width - 56, day.returnToHotel.toName, day.returnToHotel.summary)
  }
}

function measureStopBlock(
  context: CanvasRenderingContext2D,
  width: number,
  stop: TravelPlanStop,
  copy: ScrapbookCopy
) {
  const contentWidth = width - 40
  context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleHeight = measureTextBlock(context, normalizeDisplayText(stop.name), contentWidth, 1.2).height
  const metaHeight = measureChipGroup(
    context,
    [copy.stopMeta(stop), `${stop.startTime} - ${stop.endTime}`],
    contentWidth,
    30,
    '600 14px "IBM Plex Mono", "Segoe UI", monospace'
  )
  context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
  const rationaleHeight = measureTextBlock(context, normalizeDisplayText(stop.rationale), contentWidth, 1.45).height
  const routeHeight = stop.routeFromPrevious
    ? measureTextBlock(context, normalizeDisplayText(stop.routeFromPrevious.summary), contentWidth, 1.4).height + 24
    : 0
  return 26 + titleHeight + 12 + metaHeight + 12 + rationaleHeight + routeHeight + 18
}

function drawStopBlock(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  stop: TravelPlanStop,
  copy: ScrapbookCopy
) {
  drawCard(context, x, y, width, height, '#fffaf3', '#ffffff')
  context.fillStyle = '#d6623a'
  context.beginPath()
  context.arc(x + 18, y + 26, 6, 0, Math.PI * 2)
  context.fill()

  context.fillStyle = '#183649'
  context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
  const titleMetrics = drawTextBlock(context, normalizeDisplayText(stop.name), x + 30, y + 18, width - 54, 1.2)

  drawChipGroup(
    context,
    [copy.stopMeta(stop), `${stop.startTime} - ${stop.endTime}`],
    x + 30,
    titleMetrics.y + 10,
    width - 54,
    30,
    '600 14px "IBM Plex Mono", "Segoe UI", monospace',
    '#f8f1e8',
    '#8b522b',
    'rgba(139, 82, 43, 0.12)'
  )

  const metaHeight = measureChipGroup(
    context,
    [copy.stopMeta(stop), `${stop.startTime} - ${stop.endTime}`],
    width - 54,
    30,
    '600 14px "IBM Plex Mono", "Segoe UI", monospace'
  )

  context.fillStyle = '#57676e'
  context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
  const rationaleMetrics = drawTextBlock(
    context,
    normalizeDisplayText(stop.rationale),
    x + 30,
    titleMetrics.y + 16 + metaHeight,
    width - 54,
    1.45
  )

  if (stop.routeFromPrevious) {
    context.fillStyle = '#0f7b73'
    context.font = '600 15px "IBM Plex Mono", "Segoe UI", monospace'
    context.fillText('ROUTE', x + 30, rationaleMetrics.y + 16)

    context.fillStyle = '#57676e'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    drawTextBlock(
      context,
      normalizeDisplayText(stop.routeFromPrevious.summary),
      x + 30,
      rationaleMetrics.y + 24,
      width - 54,
      1.4
    )
  }
}

function drawReturnBlock(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  returnName: string,
  summary: string
) {
  drawCard(context, x, y, width, 76, '#f5faf8', '#ffffff')
  context.fillStyle = '#0f7b73'
  context.font = '700 14px "IBM Plex Mono", "Segoe UI", monospace'
  context.fillText('RETURN', x + 20, y + 26)

  context.fillStyle = '#183649'
  context.font = '700 18px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(normalizeDisplayText(returnName), x + 20, y + 48)

  context.fillStyle = '#5d676d'
  context.font = '15px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(ellipsize(context, normalizeDisplayText(summary), width - 200), x + 180, y + 48)
}

function drawFooter(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  copy: ScrapbookCopy
) {
  context.strokeStyle = 'rgba(24, 54, 73, 0.08)'
  context.lineWidth = 1
  context.beginPath()
  context.moveTo(x, y - 18)
  context.lineTo(x + width, y - 18)
  context.stroke()

  context.fillStyle = '#69767c'
  context.font = '600 15px "IBM Plex Mono", "Segoe UI", monospace'
  context.fillText(copy.generatedWith, x, y + 10)
}

function drawRouteSketch(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  plan: TravelPlan,
  copy: ScrapbookCopy
) {
  const points = buildMapPoints(plan)
  const lines = buildRoutePolylines(plan)

  drawMapGrid(context, x, y, width, height)

  if (!points.length) {
    context.fillStyle = '#617076'
    context.font = '16px "Noto Sans SC", "Segoe UI", sans-serif'
    drawTextBlock(context, copy.routeFallback, x + 20, y + 32, width - 40, 1.45)
    return
  }

  const longitudes = points.map(point => point.longitude)
  const latitudes = points.map(point => point.latitude)
  const minLng = Math.min(...longitudes)
  const maxLng = Math.max(...longitudes)
  const minLat = Math.min(...latitudes)
  const maxLat = Math.max(...latitudes)
  const pad = 30

  const project = (longitude: number, latitude: number) => ({
    x: x + pad + ((longitude - minLng) / Math.max(maxLng - minLng, 0.001)) * (width - pad * 2),
    y: y + height - pad - ((latitude - minLat) / Math.max(maxLat - minLat, 0.001)) * (height - pad * 2)
  })

  context.strokeStyle = 'rgba(15, 123, 115, 0.32)'
  context.lineWidth = 3
  for (const line of lines) {
    context.beginPath()
    line.forEach(([longitude, latitude], index) => {
      const projected = project(longitude, latitude)
      if (index === 0) {
        context.moveTo(projected.x, projected.y)
      } else {
        context.lineTo(projected.x, projected.y)
      }
    })
    context.stroke()
  }

  context.font = '600 13px "IBM Plex Mono", "Segoe UI", monospace'
  for (const point of points) {
    const projected = project(point.longitude, point.latitude)
    context.fillStyle = point.kind === 'hotel' ? '#0f7b73' : '#d6623a'
    context.beginPath()
    context.arc(projected.x, projected.y, point.kind === 'hotel' ? 8 : 6, 0, Math.PI * 2)
    context.fill()

    context.fillStyle = '#183649'
    context.fillText(point.label, projected.x + 10, projected.y + 4)
  }
}

function drawMapGrid(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number) {
  context.fillStyle = 'rgba(255, 255, 255, 0.62)'
  roundRect(context, x, y, width, height, 24)
  context.fill()

  context.strokeStyle = 'rgba(24, 54, 73, 0.06)'
  context.lineWidth = 1
  for (let index = 1; index <= 4; index += 1) {
    const lineX = x + (width / 5) * index
    const lineY = y + (height / 5) * index
    context.beginPath()
    context.moveTo(lineX, y + 12)
    context.lineTo(lineX, y + height - 12)
    context.stroke()
    context.beginPath()
    context.moveTo(x + 12, lineY)
    context.lineTo(x + width - 12, lineY)
    context.stroke()
  }
}

function drawLegend(context: CanvasRenderingContext2D, x: number, y: number, copy: ScrapbookCopy) {
  context.fillStyle = '#0f7b73'
  context.beginPath()
  context.arc(x + 8, y, 6, 0, Math.PI * 2)
  context.fill()
  context.fillStyle = '#58666c'
  context.font = '14px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(copy.routeLegendHotel, x + 20, y + 5)

  context.fillStyle = '#d6623a'
  context.beginPath()
  context.arc(x + 126, y, 6, 0, Math.PI * 2)
  context.fill()
  context.fillStyle = '#58666c'
  context.fillText(copy.routeLegendStop, x + 138, y + 5)
}

function drawNumberBullet(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  value: number,
  fill: string
) {
  context.fillStyle = fill
  context.beginPath()
  context.arc(x, y, 14, 0, Math.PI * 2)
  context.fill()

  context.fillStyle = '#ffffff'
  context.font = '700 14px "IBM Plex Mono", "Segoe UI", monospace'
  context.textAlign = 'center'
  context.fillText(String(value), x, y + 5)
  context.textAlign = 'left'
}

function drawCardTitle(context: CanvasRenderingContext2D, x: number, y: number, value: string, color: string) {
  context.fillStyle = color
  context.font = '700 24px "Space Grotesk", "Noto Sans SC", sans-serif'
  context.fillText(value, x, y)
}

function drawCard(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  startColor: string,
  endColor: string
) {
  const gradient = context.createLinearGradient(x, y, x + width, y + height)
  gradient.addColorStop(0, startColor)
  gradient.addColorStop(1, endColor)
  context.fillStyle = gradient
  roundRect(context, x, y, width, height, CARD_RADIUS)
  context.fill()

  context.strokeStyle = 'rgba(24, 54, 73, 0.08)'
  context.lineWidth = 1
  roundRect(context, x, y, width, height, CARD_RADIUS)
  context.stroke()
}

function drawChipGroup(
  context: CanvasRenderingContext2D,
  chips: string[],
  x: number,
  y: number,
  maxWidth: number,
  chipHeight: number,
  font: string,
  background: string,
  color: string,
  borderColor: string
) {
  context.font = font
  let cursorX = x
  let cursorY = y

  for (const chip of chips.filter(Boolean)) {
    const label = ellipsize(context, chip, Math.max(maxWidth - 36, 120))
    const chipWidth = Math.min(context.measureText(label).width + 28, maxWidth)
    if (cursorX > x && cursorX + chipWidth > x + maxWidth) {
      cursorX = x
      cursorY += chipHeight + 10
    }

    context.fillStyle = background
    roundRect(context, cursorX, cursorY, chipWidth, chipHeight, chipHeight / 2)
    context.fill()
    context.strokeStyle = borderColor
    context.lineWidth = 1
    roundRect(context, cursorX, cursorY, chipWidth, chipHeight, chipHeight / 2)
    context.stroke()

    context.fillStyle = color
    context.fillText(label, cursorX + 14, cursorY + chipHeight / 2 + 6)
    cursorX += chipWidth + 10
  }
}

function measureChipGroup(
  context: CanvasRenderingContext2D,
  chips: string[],
  maxWidth: number,
  chipHeight: number,
  font: string
) {
  const visibleChips = chips.filter(Boolean)
  if (!visibleChips.length) {
    return 0
  }

  context.font = font
  let cursorX = 0
  let rows = 1

  for (const chip of visibleChips) {
    const chipWidth = Math.min(context.measureText(chip).width + 28, maxWidth)
    if (cursorX > 0 && cursorX + chipWidth > maxWidth) {
      cursorX = 0
      rows += 1
    }
    cursorX += chipWidth + 10
  }

  return rows * chipHeight + (rows - 1) * 10
}

function drawTextBlock(
  context: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  maxWidth: number,
  lineHeightRatio: number
) {
  const metrics = measureTextBlock(context, text, maxWidth, lineHeightRatio)
  const lineHeight = metrics.lines.length ? metrics.height / metrics.lines.length : measureFontHeight(context.font) * lineHeightRatio

  metrics.lines.forEach((line, index) => {
    context.fillText(line, x, y + index * lineHeight)
  })

  return {
    ...metrics,
    y: y + metrics.height
  }
}

function measureTextBlock(
  context: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  lineHeightRatio: number
): TextBlockMetrics {
  const content = normalizeDisplayText(text)
  if (!content) {
    return { lines: [], height: 0, y: 0 }
  }

  const lines = wrapText(context, content, maxWidth)
  const lineHeight = measureFontHeight(context.font) * lineHeightRatio
  return {
    lines,
    height: lines.length * lineHeight,
    y: 0
  }
}

function wrapText(context: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return []
  }

  const tokens = normalized.match(/[\u4e00-\u9fff]|[^\s\u4e00-\u9fff]+|\s+/g) ?? [normalized]
  const lines: string[] = []
  let currentLine = ''

  for (const token of tokens) {
    const candidate = `${currentLine}${token}`
    if (!currentLine || context.measureText(candidate).width <= maxWidth) {
      currentLine = candidate
      continue
    }

    lines.push(currentLine.trim())
    currentLine = token.trimStart()
  }

  if (currentLine.trim()) {
    lines.push(currentLine.trim())
  }

  return lines
}

function ellipsize(context: CanvasRenderingContext2D, text: string, maxWidth: number) {
  if (context.measureText(text).width <= maxWidth) {
    return text
  }

  let value = text
  while (value.length > 1 && context.measureText(`${value}...`).width > maxWidth) {
    value = value.slice(0, -1)
  }
  return `${value}...`
}

function measureFontHeight(font: string) {
  const match = font.match(/(\d+)px/)
  return match ? Number.parseInt(match[1], 10) : 16
}

function getSnapshotTone(tone: CardTone) {
  if (tone === 'warm') {
    return { start: '#fff7ef', end: '#fffdf8', eyebrow: '#a14a2b' }
  }
  if (tone === 'cool') {
    return { start: '#f3fbf8', end: '#fbfefd', eyebrow: '#0f7b73' }
  }
  if (tone === 'alert') {
    return { start: '#fff2ee', end: '#fffaf8', eyebrow: '#b54329' }
  }
  return { start: '#f7f9fb', end: '#ffffff', eyebrow: '#183649' }
}

function getPrepColors(tone: PrepTone) {
  if (tone === 'good') {
    return { badge: 'rgba(15, 123, 115, 0.12)', badgeText: '#0f7b73' }
  }
  if (tone === 'warn') {
    return { badge: 'rgba(214, 164, 70, 0.16)', badgeText: '#9b6b13' }
  }
  if (tone === 'alert') {
    return { badge: 'rgba(214, 98, 58, 0.16)', badgeText: '#b54329' }
  }
  return { badge: 'rgba(24, 54, 73, 0.08)', badgeText: '#183649' }
}

function budgetCategoryLabel(category: string, preferChinese: boolean) {
  const key = category.toUpperCase()
  const zhLabels: Record<string, string> = {
    HOTEL: '住宿',
    STAY: '住宿',
    TICKET: '门票',
    FOOD: '餐饮',
    TRANSIT: '市内交通',
    LOCAL_TRANSIT: '市内交通',
    INTERCITY_TRANSIT: '城际交通',
    SHOPPING: '购物',
    OTHER: '其他'
  }
  const enLabels: Record<string, string> = {
    HOTEL: 'Stay',
    STAY: 'Stay',
    TICKET: 'Tickets',
    FOOD: 'Food',
    TRANSIT: 'Local Transit',
    LOCAL_TRANSIT: 'Local Transit',
    INTERCITY_TRANSIT: 'Intercity Transit',
    SHOPPING: 'Shopping',
    OTHER: 'Other'
  }

  const normalized = normalizeDisplayText(category).replace(/_/g, ' ')
  return preferChinese ? (zhLabels[key] ?? normalized) : (enLabels[key] ?? normalized)
}

function slotLabel(slot: TravelPlanStop['slot'], preferChinese: boolean) {
  if (preferChinese) {
    if (slot === 'MORNING') {
      return '上午'
    }
    if (slot === 'AFTERNOON') {
      return '下午'
    }
    return '晚上'
  }

  if (slot === 'MORNING') {
    return 'Morning'
  }
  if (slot === 'AFTERNOON') {
    return 'Afternoon'
  }
  return 'Evening'
}

function buildFileName(plan: TravelPlan, preferChinese: boolean, variant: ScrapbookVariant) {
  const baseName = sanitizeFileName(normalizeDisplayText(plan.title) || (preferChinese ? '旅行手账' : 'travel-scrapbook'))
  const suffix = variant === 'brief'
    ? (preferChinese ? '执行版' : 'brief')
    : (preferChinese ? '分享版' : 'story')
  return `${baseName || 'travel-scrapbook'}-${suffix}.png`
}

function sanitizeFileName(value: string) {
  return value.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '').trim()
}

async function downloadCanvas(canvas: HTMLCanvasElement, fileName: string) {
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/png'))
  if (!blob) {
    return
  }

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.download = fileName
  link.href = url
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function formatDate(value: string, preferChinese: boolean) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return preferChinese
    ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    : `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function roundRect(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
) {
  const clamped = Math.min(radius, width / 2, height / 2)
  context.beginPath()
  context.moveTo(x + clamped, y)
  context.arcTo(x + width, y, x + width, y + height, clamped)
  context.arcTo(x + width, y + height, x, y + height, clamped)
  context.arcTo(x, y + height, x, y, clamped)
  context.arcTo(x, y, x + width, y, clamped)
  context.closePath()
}
