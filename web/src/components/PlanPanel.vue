<script setup lang="ts">
import { computed, ref } from 'vue'
import PlanMap from './PlanMap.vue'
import type {
  ConversationFeedback,
  ConversationFeedbackRequest,
  TravelKnowledgeSelection,
  TravelPlan,
  TravelPlanStop,
  TravelTransitLeg,
  TravelTransitStep
} from '../types/api'
import { downloadTravelScrapbook } from '../utils/travelScrapbook'
import { hotelPointId, stopPointId } from '../utils/travelPlan'

const props = defineProps<{
  travelPlan: TravelPlan | null
  feedback: ConversationFeedback | null
  feedbackSaving: boolean
  preferChinese: boolean
}>()

const emit = defineEmits<{
  feedback: [payload: ConversationFeedbackRequest]
}>()

const activePointId = ref('')

function checkClass(status: string) {
  return `plan-check--${status.toLowerCase()}`
}

function statusLabel(status: string) {
  return props.preferChinese
    ? ({ PASS: '通过', WARN: '提醒', FAIL: '冲突' }[status] ?? status)
    : status
}

function slotLabel(slot: string) {
  if (!props.preferChinese) {
    return { MORNING: 'Morning', AFTERNOON: 'Afternoon', EVENING: 'Evening' }[slot] ?? slot
  }
  return { MORNING: '上午', AFTERNOON: '下午', EVENING: '晚上' }[slot] ?? slot
}

function categoryLabel(category: string) {
  if (!props.preferChinese) {
    return category
  }
  return {
    Hotel: '住宿',
    'Intercity transport': '跨城交通',
    'Local transit': '本地通勤',
    Food: '餐饮',
    'Attractions and buffer': '景点与机动预算'
  }[category] ?? category
}

function modeLabel(mode: string) {
  if (!props.preferChinese) {
    return mode
  }
  return {
    SUBWAY: '地铁',
    BUS: '公交',
    WALK: '步行',
    TAXI: '打车',
    RAIL: '铁路'
  }[mode] ?? mode
}

function routeLine(route?: TravelTransitLeg | null) {
  if (!route) {
    return ''
  }
  return route.lineNames.length ? route.lineNames.join(' / ') : modeLabel(route.mode)
}

function routeMeta(route?: TravelTransitLeg | null) {
  if (!route) {
    return ''
  }
  if (props.preferChinese) {
    return `${route.durationMinutes} 分钟 · 步行 ${route.walkingMinutes} 分钟 · ${route.estimatedCost} 元`
  }
  return `${route.durationMinutes} min · walk ${route.walkingMinutes} min · ${route.estimatedCost} CNY`
}

function stepMeta(step: TravelTransitStep) {
  if (props.preferChinese) {
    return `${modeLabel(step.mode)} · ${step.durationMinutes} 分钟${step.stopCount ? ` · ${step.stopCount} 站` : ''}`
  }
  return `${modeLabel(step.mode)} · ${step.durationMinutes} min${step.stopCount ? ` · ${step.stopCount} stops` : ''}`
}

function coordinateText(longitude?: string, latitude?: string) {
  if (!longitude || !latitude) {
    return ''
  }
  return `${longitude}, ${latitude}`
}

function sourceLabel(source?: string) {
  if (!source) {
    return props.preferChinese ? '位置已确认' : 'Location verified'
  }
  if (!props.preferChinese) {
    return {
      'MCP.amap_input_tips': 'Place verified with Amap',
      'MCP.amap_geocode': 'Location confirmed with Amap',
      'MCP.amap_transit_route': 'Route planned with Amap',
      'RULE.fallback': 'Estimated result, please verify before departure'
    }[source] ?? source
  }
  return {
    'MCP.amap_input_tips': '已用高德匹配并确认地点',
    'MCP.amap_geocode': '已用高德确认位置',
    'MCP.amap_transit_route': '路线来自高德公交/地铁规划',
    'RULE.fallback': '当前为估算结果，建议出发前再确认'
  }[source] ?? `已校验：${source}`
}

function locationStatus(source?: string) {
  return sourceLabel(source)
}

function routeStatus(source?: string) {
  if (!source) {
    return props.preferChinese ? '路线已整理完成' : 'Route prepared'
  }
  return sourceLabel(source)
}

function knowledgeRouteLabel(source?: string) {
  if (!source) {
    return props.preferChinese ? '未记录' : 'Not recorded'
  }
  if (!props.preferChinese) {
    return {
      'vector-store': 'Vector store',
      'local-fallback': 'Local fallback'
    }[source] ?? source
  }
  return {
    'vector-store': '向量检索',
    'local-fallback': '本地回退'
  }[source] ?? source
}

function topicLabel(topic?: string) {
  if (!topic) {
    return props.preferChinese ? '未标注' : 'Unlabeled'
  }
  if (!props.preferChinese) {
    return {
      scenic: 'Scenic',
      food: 'Food',
      hotel: 'Hotel',
      transit: 'Transit',
      activity: 'Activity',
      nightlife: 'Nightlife'
    }[topic] ?? topic
  }
  return {
    scenic: '景点',
    food: '餐饮',
    hotel: '住宿',
    transit: '交通',
    activity: '活动',
    nightlife: '夜生活'
  }[topic] ?? topic
}

function tripStyleLabel(style?: string) {
  if (!style) {
    return props.preferChinese ? '未标注' : 'Unlabeled'
  }
  if (!props.preferChinese) {
    return {
      relaxed: 'Relaxed',
      family: 'Family',
      nightlife: 'Nightlife',
      museum: 'Museum',
      shopping: 'Shopping',
      foodie: 'Foodie',
      heritage: 'Heritage',
      outdoors: 'Outdoors',
      budget: 'Budget'
    }[style] ?? style
  }
  return {
    relaxed: '轻松',
    family: '亲子',
    nightlife: '夜生活',
    museum: '博物馆',
    shopping: '购物',
    foodie: '吃货',
    heritage: '人文古迹',
    outdoors: '户外',
    budget: '预算友好'
  }[style] ?? style
}

function schemaSubtypeLabel(schemaSubtype?: string) {
  if (!schemaSubtype) {
    return props.preferChinese ? '通用知识' : 'General hint'
  }
  if (!props.preferChinese) {
    return {
      hotel_area: 'Stay area',
      hotel_listing: 'Hotel example',
      transit_arrival: 'Arrival advice',
      transit_hub: 'Transfer hub',
      transit_district: 'District movement'
    }[schemaSubtype] ?? schemaSubtype
  }
  return {
    hotel_area: '住宿片区建议',
    hotel_listing: '酒店示例',
    transit_arrival: '到达建议',
    transit_hub: '换乘枢纽',
    transit_district: '片区移动'
  }[schemaSubtype] ?? schemaSubtype
}

function qualityLabel(score?: number) {
  if (!score || score <= 0) {
    return props.preferChinese ? '待评估' : 'Unrated'
  }
  if (props.preferChinese) {
    if (score >= 40) return `高相关 ${score}`
    if (score >= 26) return `较强 ${score}`
    return `可用 ${score}`
  }
  if (score >= 40) return `High ${score}`
  if (score >= 26) return `Strong ${score}`
  return `Usable ${score}`
}

function qualityClass(score?: number) {
  if (!score || score <= 0) {
    return 'plan-pill--muted'
  }
  if (score >= 40) {
    return 'plan-pill--high'
  }
  if (score >= 26) {
    return 'plan-pill--medium'
  }
  return 'plan-pill--muted'
}

function isLowQualityKnowledge(item: TravelKnowledgeSelection) {
  const title = (item.title || '').trim()
  const content = (item.content || '').trim()
  const score = item.qualityScore ?? 0
  if (!title || !content) {
    return true
  }
  if (score > 0 && score < 20) {
    return true
  }
  if (/^(?:\.|#|@)/.test(title)) {
    return true
  }
  if (/traceback|unicode(?:encode|decode)error|mw-parser-output|display\s*:\s*none/i.test(`${title} ${content}`)) {
    return true
  }
  return false
}

function activateHotel(index: number) {
  activePointId.value = hotelPointId(index)
}

function activateStop(dayNumber: number, stop: TravelPlanStop) {
  activePointId.value = stopPointId(dayNumber, stop.slot, stop.name)
}

function activatePrimaryHotel() {
  activePointId.value = hotelPointId(1)
}

async function exportScrapbook() {
  if (!props.travelPlan) {
    return
  }
  await downloadTravelScrapbook(props.travelPlan, props.preferChinese)
}

function submitFeedback(label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED') {
  const reasonCode = {
    ACCEPTED: 'used_as_is',
    PARTIAL: 'edited_before_use',
    REJECTED: 'not_useful'
  }[label]
  emit('feedback', { label, reasonCode })
}

const feedbackLabel = computed(() => {
  if (!props.feedback) {
    return props.preferChinese ? '未记录' : 'Not recorded'
  }
  return {
    ACCEPTED: props.preferChinese ? '已接受' : 'Accepted',
    PARTIAL: props.preferChinese ? '部分接受' : 'Partially accepted',
    REJECTED: props.preferChinese ? '已拒绝' : 'Rejected'
  }[props.feedback.label]
})

const feedbackReason = computed(() => {
  if (!props.feedback?.reasonCode) {
    return ''
  }
  return {
    used_as_is: props.preferChinese ? '基本无需修改' : 'Used as-is',
    edited_before_use: props.preferChinese ? '使用前做了调整' : 'Edited before use',
    not_useful: props.preferChinese ? '当前不打算采用' : 'Not useful enough'
  }[props.feedback.reasonCode] ?? props.feedback.reasonCode
})

const feedbackCopy = computed(() => ({
  title: props.preferChinese ? '结果反馈' : 'Recommendation Feedback',
  status: props.preferChinese ? '当前状态' : 'Current status',
  hint: props.preferChinese
    ? '这一步是数据飞轮的第一层：告诉系统这次推荐有没有真正帮到你。'
    : 'This is the first layer of the data flywheel: tell the system whether this recommendation actually helped.',
  accept: props.preferChinese ? '直接采用' : 'Use As-Is',
  partial: props.preferChinese ? '部分采用' : 'Use with Edits',
  reject: props.preferChinese ? '暂不采用' : 'Reject'
}))

const knowledgeSelections = computed(() => props.travelPlan?.knowledgeRetrieval?.selections ?? [])

const visibleKnowledgeSelections = computed(() => knowledgeSelections.value.filter(item => !isLowQualityKnowledge(item)))

const suppressedKnowledgeSelections = computed(() => knowledgeSelections.value.filter(isLowQualityKnowledge))

const knowledgeGroups = computed(() => {
  const groups = new Map<string, TravelKnowledgeSelection[]>()
  for (const item of visibleKnowledgeSelections.value) {
    const topic = item.topic || 'other'
    const list = groups.get(topic) ?? []
    list.push(item)
    groups.set(topic, list)
  }

  const preferredOrder = props.travelPlan?.knowledgeRetrieval?.inferredTopics ?? []
  const orderedTopics = [
    ...preferredOrder,
    ...Array.from(groups.keys()).filter(topic => !preferredOrder.includes(topic))
  ]

  return orderedTopics
    .filter(topic => groups.has(topic))
    .map(topic => ({
      topic,
      items: [...(groups.get(topic) ?? [])].sort((left, right) => (right.qualityScore ?? 0) - (left.qualityScore ?? 0))
    }))
})

const knowledgeSummary = computed(() => {
  const selections = visibleKnowledgeSelections.value
  const totalQuality = selections.reduce((sum, item) => sum + (item.qualityScore ?? 0), 0)
  const averageQuality = selections.length ? Math.round(totalQuality / selections.length) : 0
  const subtypes = new Set(selections.map(item => item.schemaSubtype).filter(Boolean))
  return {
    count: selections.length,
    averageQuality,
    topicCount: knowledgeGroups.value.length,
    subtypeCount: subtypes.size,
    suppressedCount: suppressedKnowledgeSelections.value.length
  }
})

const copy = computed(() => ({
  summary: props.preferChinese ? '方案概览' : 'Summary',
  budget: props.preferChinese ? '预计总花费' : 'Estimated Total',
  hotelArea: props.preferChinese ? '建议住宿区域' : 'Recommended Hotel Area',
  adjustments: props.preferChinese ? '最近可行替代方案说明' : 'Closest Feasible Alternative',
  adjustmentsBody: props.preferChinese
    ? '系统为满足约束自动放宽了部分条件，下面是实际发生的调整。'
    : 'The planner relaxed a few constraints to keep the itinerary feasible. These are the exact adjustments it made.',
  weather: props.preferChinese ? '天气快照' : 'Weather Snapshot',
  weatherValidity: props.preferChinese
    ? '这是当前时点天气快照，不代表未来多日预报。'
    : 'This weather hint is a point-in-time snapshot, not a multi-day forecast.',
  weatherReportedAt: props.preferChinese ? '快照时间' : 'Snapshot time',
  knowledge: props.preferChinese ? '引用的目的地知识' : 'Retrieved Knowledge',
  knowledgeSummary: props.preferChinese ? '检索摘要' : 'Retrieval Summary',
  knowledgeCount: props.preferChinese ? '命中片段' : 'Matched hints',
  suppressedCount: props.preferChinese ? '已抑制低质量片段' : 'Suppressed noisy hints',
  knowledgeTopicCount: props.preferChinese ? '覆盖主题' : 'Covered topics',
  knowledgeSubtypeCount: props.preferChinese ? '知识类型' : 'Hint types',
  averageQuality: props.preferChinese ? '平均质量' : 'Average quality',
  knowledgeRoute: props.preferChinese ? '检索路径' : 'Retrieval path',
  inferredTopics: props.preferChinese ? '推断主题' : 'Inferred topics',
  inferredTripStyles: props.preferChinese ? '推断风格' : 'Inferred styles',
  contentSource: props.preferChinese ? '内容来源' : 'Content source',
  cityMatch: props.preferChinese ? '城市匹配' : 'City match',
  topicMatch: props.preferChinese ? '主题匹配' : 'Topic match',
  tripStyleMatch: props.preferChinese ? '风格匹配' : 'Style match',
  schemaSubtype: props.preferChinese ? '知识类型' : 'Hint type',
  noisyKnowledge: props.preferChinese ? '已弱化的低质量知识' : 'Suppressed Noisy Knowledge',
  hotels: props.preferChinese ? '酒店建议' : 'Hotel Picks',
  checks: props.preferChinese ? '重点提醒' : 'Constraint Checks',
  breakdown: props.preferChinese ? '预算拆分' : 'Budget Breakdown',
  days: props.preferChinese ? '每日路线' : 'Daily Route',
  emptyTitle: props.preferChinese ? '还没有生成结构化方案' : 'No structured plan yet',
  emptyBody: props.preferChinese
    ? '给出目的地、天数和预算后，我会把酒店、路线、费用和地图一起整理出来。'
    : 'Share the destination, trip length, and budget and I will structure the hotels, route, costs, and map together.',
  export: props.preferChinese ? '生成旅行手账图' : 'Export Scrapbook',
  locationDetails: props.preferChinese ? '查看定位细节' : 'View location details',
  routeDetails: props.preferChinese ? '查看详细路线' : 'View route details'
}))
</script>

<template>
  <section class="panel plan-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">{{ preferChinese ? '方案' : 'Plan' }}</p>
        <h2>{{ preferChinese ? '这次出行建议' : 'Suggested Plan' }}</h2>
      </div>
      <button v-if="travelPlan" class="plan-export" @click="exportScrapbook">{{ copy.export }}</button>
    </div>

    <div v-if="!travelPlan" class="plan-empty">
      <h3>{{ copy.emptyTitle }}</h3>
      <p>{{ copy.emptyBody }}</p>
    </div>

    <template v-else>
      <div class="plan-summary">
        <div>
          <p class="plan-summary__label">{{ copy.summary }}</p>
          <h3>{{ travelPlan.summary }}</h3>
          <p class="plan-summary__body">{{ travelPlan.hotelAreaReason }}</p>
        </div>
        <div class="plan-summary__stats">
          <div>
            <span>{{ copy.budget }}</span>
            <strong>{{ travelPlan.estimatedTotalMin }}-{{ travelPlan.estimatedTotalMax }} {{ preferChinese ? '元' : 'CNY' }}</strong>
          </div>
          <div>
            <span>{{ copy.hotelArea }}</span>
            <strong>{{ travelPlan.hotelArea }}</strong>
          </div>
        </div>
      </div>

      <div class="plan-section">
        <div class="plan-section__title">{{ feedbackCopy.title }}</div>
        <article class="plan-insight-card plan-feedback-card">
          <div class="plan-insight-card__header">
            <strong>{{ feedbackCopy.status }}</strong>
            <span class="plan-insight-card__badge">{{ feedbackLabel }}</span>
          </div>
          <p class="plan-amap__note">{{ feedbackCopy.hint }}</p>
          <p v-if="feedbackReason" class="plan-amap__note">{{ feedbackReason }}</p>
          <div class="plan-feedback-actions">
            <button class="plan-feedback-button" :disabled="feedbackSaving" @click="submitFeedback('ACCEPTED')">
              {{ feedbackSaving ? '...' : feedbackCopy.accept }}
            </button>
            <button class="plan-feedback-button" :disabled="feedbackSaving" @click="submitFeedback('PARTIAL')">
              {{ feedbackSaving ? '...' : feedbackCopy.partial }}
            </button>
            <button class="plan-feedback-button plan-feedback-button--muted" :disabled="feedbackSaving" @click="submitFeedback('REJECTED')">
              {{ feedbackSaving ? '...' : feedbackCopy.reject }}
            </button>
          </div>
        </article>
      </div>

      <PlanMap
        :travel-plan="travelPlan"
        :prefer-chinese="preferChinese"
        :active-point-id="activePointId"
        @select-point="activePointId = $event"
      />

      <div class="plan-highlights">
        <span v-for="item in travelPlan.highlights" :key="item">{{ item }}</span>
      </div>

      <div v-if="travelPlan.constraintRelaxed && travelPlan.adjustmentSuggestions?.length" class="plan-section">
        <div class="plan-section__title">{{ copy.adjustments }}</div>
        <article class="plan-insight-card plan-insight-card--warn">
          <p class="plan-amap__note">{{ copy.adjustmentsBody }}</p>
          <ul class="plan-adjustments">
            <li v-for="item in travelPlan.adjustmentSuggestions" :key="item">{{ item }}</li>
          </ul>
        </article>
      </div>

      <div v-if="travelPlan.weatherSnapshot" class="plan-section">
        <div class="plan-section__title">{{ copy.weather }}</div>
        <article class="plan-insight-card">
          <div class="plan-insight-card__header">
            <strong>{{ travelPlan.weatherSnapshot.city || travelPlan.hotelArea }}</strong>
            <span class="plan-insight-card__badge">{{ preferChinese ? '实时快照' : 'Point-in-time snapshot' }}</span>
          </div>
          <div class="plan-amap__grid">
            <span v-if="travelPlan.weatherSnapshot.description" class="plan-amap__pill">
              {{ preferChinese ? '天气' : 'Weather' }}: {{ travelPlan.weatherSnapshot.description }}
            </span>
            <span v-if="travelPlan.weatherSnapshot.temperature" class="plan-amap__pill">
              {{ preferChinese ? '温度' : 'Temperature' }}: {{ travelPlan.weatherSnapshot.temperature }} C
            </span>
            <span v-if="travelPlan.weatherSnapshot.windDirection || travelPlan.weatherSnapshot.windPower" class="plan-amap__pill">
              {{ preferChinese ? '风向/风力' : 'Wind' }}:
              {{ travelPlan.weatherSnapshot.windDirection || '-' }} / {{ travelPlan.weatherSnapshot.windPower || '-' }}
            </span>
            <span v-if="travelPlan.weatherSnapshot.reportTime" class="plan-amap__pill">
              {{ copy.weatherReportedAt }}: {{ travelPlan.weatherSnapshot.reportTime }}
            </span>
          </div>
          <p class="plan-amap__note">{{ copy.weatherValidity }}</p>
        </article>
      </div>

      <div v-if="travelPlan.knowledgeRetrieval?.selections?.length" class="plan-section">
        <div class="plan-section__title">{{ copy.knowledge }}</div>
        <div class="plan-knowledge-shell">
          <article class="plan-insight-card">
            <div class="plan-insight-card__header">
              <strong>{{ copy.knowledgeSummary }}</strong>
              <span class="plan-insight-card__badge">{{ knowledgeRouteLabel(travelPlan.knowledgeRetrieval.retrievalSource) }}</span>
            </div>
            <div class="plan-context-stats">
              <div class="plan-context-stat">
                <span>{{ copy.knowledgeCount }}</span>
                <strong>{{ knowledgeSummary.count }}</strong>
              </div>
              <div class="plan-context-stat">
                <span>{{ copy.knowledgeTopicCount }}</span>
                <strong>{{ knowledgeSummary.topicCount }}</strong>
              </div>
              <div class="plan-context-stat">
                <span>{{ copy.knowledgeSubtypeCount }}</span>
                <strong>{{ knowledgeSummary.subtypeCount }}</strong>
              </div>
              <div class="plan-context-stat">
                <span>{{ copy.averageQuality }}</span>
                <strong>{{ knowledgeSummary.averageQuality }}</strong>
              </div>
              <div v-if="knowledgeSummary.suppressedCount" class="plan-context-stat">
                <span>{{ copy.suppressedCount }}</span>
                <strong>{{ knowledgeSummary.suppressedCount }}</strong>
              </div>
            </div>
            <div class="plan-amap__grid">
              <span
                v-for="topic in travelPlan.knowledgeRetrieval.inferredTopics"
                :key="topic"
                class="plan-amap__pill"
              >
                {{ copy.inferredTopics }}: {{ topicLabel(topic) }}
              </span>
              <span
                v-for="style in travelPlan.knowledgeRetrieval.inferredTripStyles || []"
                :key="style"
                class="plan-amap__pill"
              >
                {{ copy.inferredTripStyles }}: {{ tripStyleLabel(style) }}
              </span>
            </div>
          </article>
          <div class="plan-knowledge-groups">
            <section
              v-for="group in knowledgeGroups"
              :key="group.topic"
              class="plan-knowledge-group"
            >
              <div class="plan-knowledge-group__header">
                <strong>{{ topicLabel(group.topic) }}</strong>
                <span>{{ group.items.length }}</span>
              </div>
              <div class="plan-knowledge-list">
                <article
                  v-for="item in group.items"
                  :key="`${item.city}-${item.topic}-${item.title}`"
                  class="plan-knowledge-card"
                >
                  <div class="plan-insight-card__header">
                    <div class="plan-knowledge-card__headline">
                      <strong>{{ item.title }}</strong>
                      <p>{{ item.content }}</p>
                    </div>
                    <div class="plan-knowledge-card__badges">
                      <span class="plan-insight-card__badge">{{ schemaSubtypeLabel(item.schemaSubtype) }}</span>
                      <span class="plan-amap__pill" :class="qualityClass(item.qualityScore)">{{ qualityLabel(item.qualityScore) }}</span>
                    </div>
                  </div>
                  <div class="plan-amap__grid">
                    <span v-if="item.matchedCity" class="plan-amap__pill">{{ copy.cityMatch }}: {{ item.matchedCity }}</span>
                    <span v-if="item.matchedTopic" class="plan-amap__pill">{{ copy.topicMatch }}: {{ topicLabel(item.matchedTopic) }}</span>
                    <span v-for="style in item.matchedTripStyles || []" :key="`${item.title}-${style}`" class="plan-amap__pill">
                      {{ copy.tripStyleMatch }}: {{ tripStyleLabel(style) }}
                    </span>
                    <span v-if="item.schemaSubtype" class="plan-amap__pill">{{ copy.schemaSubtype }}: {{ schemaSubtypeLabel(item.schemaSubtype) }}</span>
                    <span v-if="item.source" class="plan-amap__pill">{{ copy.contentSource }}: {{ item.source }}</span>
                  </div>
                </article>
              </div>
            </section>
          </div>
          <details v-if="suppressedKnowledgeSelections.length" class="plan-noisy-knowledge">
            <summary>{{ copy.noisyKnowledge }} ({{ suppressedKnowledgeSelections.length }})</summary>
            <div class="plan-knowledge-list">
              <article
                v-for="item in suppressedKnowledgeSelections"
                :key="`${item.city}-${item.topic}-${item.title}-suppressed`"
                class="plan-knowledge-card plan-knowledge-card--muted"
              >
                <div class="plan-insight-card__header">
                  <div class="plan-knowledge-card__headline">
                    <strong>{{ item.title }}</strong>
                    <p>{{ item.content }}</p>
                  </div>
                  <div class="plan-knowledge-card__badges">
                    <span class="plan-amap__pill plan-pill--muted">{{ qualityLabel(item.qualityScore) }}</span>
                  </div>
                </div>
              </article>
            </div>
          </details>
        </div>
      </div>

      <div v-if="travelPlan.hotels?.length" class="plan-section">
        <div class="plan-section__title">{{ copy.hotels }}</div>
        <div class="plan-hotel-list">
          <article
            v-for="(hotel, index) in travelPlan.hotels"
            :key="hotel.name"
            class="plan-hotel-card"
            @mouseenter="activateHotel(index + 1)"
          >
            <div>
              <strong>{{ hotel.name }}</strong>
              <p>{{ hotel.area }} · {{ hotel.address }}</p>
              <p>{{ hotel.rationale }}</p>
              <p class="plan-amap__note">{{ locationStatus(hotel.source) }}</p>
              <details v-if="hotel.longitude && hotel.latitude" class="plan-amap__details">
                <summary>{{ copy.locationDetails }}</summary>
                <div class="plan-amap">
                  <div class="plan-amap__grid">
                    <span class="plan-amap__pill">{{ coordinateText(hotel.longitude, hotel.latitude) }}</span>
                  </div>
                </div>
              </details>
            </div>
            <span>{{ hotel.nightlyMin }}-{{ hotel.nightlyMax }} {{ preferChinese ? '元/晚' : 'CNY/night' }}</span>
          </article>
        </div>
      </div>

      <div class="plan-section">
        <div class="plan-section__title">{{ copy.checks }}</div>
        <div class="plan-checks">
          <article v-for="check in travelPlan.checks" :key="check.code" class="plan-check" :class="checkClass(check.status)">
            <strong>{{ statusLabel(check.status) }}</strong>
            <p>{{ check.message }}</p>
          </article>
        </div>
      </div>

      <div class="plan-section">
        <div class="plan-section__title">{{ copy.breakdown }}</div>
        <div class="plan-budget-list">
          <article v-for="item in travelPlan.budget" :key="item.category" class="plan-budget-item">
            <div>
              <strong>{{ categoryLabel(item.category) }}</strong>
              <p>{{ item.rationale }}</p>
            </div>
            <span>{{ item.minAmount }}-{{ item.maxAmount }} {{ preferChinese ? '元' : 'CNY' }}</span>
          </article>
        </div>
      </div>

      <div class="plan-section">
        <div class="plan-section__title">{{ copy.days }}</div>
        <div class="plan-days">
          <article v-for="day in travelPlan.days" :key="day.dayNumber" class="plan-day">
            <div class="plan-day__header">
              <div>
                <p>{{ preferChinese ? `第 ${day.dayNumber} 天` : `Day ${day.dayNumber}` }}</p>
                <h4>{{ day.theme }}</h4>
              </div>
              <div class="plan-day__meta">
                <span>{{ preferChinese ? `游玩 ${day.totalActivityMinutes} 分钟` : `Activity ${day.totalActivityMinutes} min` }}</span>
                <span>{{ preferChinese ? `通勤 ${day.totalTransitMinutes} 分钟` : `Transit ${day.totalTransitMinutes} min` }}</span>
                <span>{{ preferChinese ? `花费 ${day.estimatedCost} 元` : `Cost ${day.estimatedCost} CNY` }}</span>
              </div>
            </div>

            <div class="plan-stops">
              <article
                v-for="stop in day.stops"
                :key="`${day.dayNumber}-${stop.slot}-${stop.name}`"
                class="plan-stop"
                @mouseenter="activateStop(day.dayNumber, stop)"
              >
                <div class="plan-stop__slot">{{ slotLabel(stop.slot) }}</div>
                <div class="plan-stop__body">
                  <strong>{{ stop.startTime }}-{{ stop.endTime }} {{ stop.name }}</strong>
                  <p>{{ stop.area }}<template v-if="stop.address"> · {{ stop.address }}</template></p>
                  <p>{{ stop.rationale }}</p>

                  <div v-if="stop.poiMatch || (stop.longitude && stop.latitude)" class="plan-amap">
                    <div class="plan-amap__title">{{ preferChinese ? '地点确认' : 'Location Check' }}</div>
                    <p class="plan-amap__note">{{ locationStatus(stop.poiMatch?.source) }}</p>
                    <details class="plan-amap__details">
                      <summary>{{ copy.locationDetails }}</summary>
                      <div class="plan-amap__grid">
                        <span v-if="stop.poiMatch?.matchedName" class="plan-amap__pill">
                          {{ preferChinese ? '匹配地点' : 'Matched' }}: {{ stop.poiMatch.matchedName }}
                        </span>
                        <span v-if="stop.poiMatch?.district" class="plan-amap__pill">
                          {{ preferChinese ? '所在区域' : 'District' }}: {{ stop.poiMatch.district }}
                        </span>
                        <span v-if="stop.longitude && stop.latitude" class="plan-amap__pill">
                          {{ preferChinese ? '坐标' : 'Coordinates' }}: {{ coordinateText(stop.longitude, stop.latitude) }}
                        </span>
                      </div>
                      <p v-if="stop.poiMatch?.address" class="plan-amap__note">
                        {{ preferChinese ? '高德地址' : 'Amap Address' }}: {{ stop.poiMatch.address }}
                      </p>
                      <p v-if="(stop.poiMatch?.candidateNames?.length ?? 0) > 1" class="plan-amap__note">
                        {{ preferChinese ? '其他候选地点' : 'Other candidates' }}: {{ stop.poiMatch?.candidateNames?.join(' / ') }}
                      </p>
                    </details>
                  </div>

                  <div v-if="stop.costBreakdown" class="plan-stop__costs">
                    <span>{{ preferChinese ? `门票 ${stop.costBreakdown.ticketCost}` : `Ticket ${stop.costBreakdown.ticketCost}` }}</span>
                    <span>{{ preferChinese ? `餐饮 ${stop.costBreakdown.foodCost}` : `Food ${stop.costBreakdown.foodCost}` }}</span>
                    <span>{{ preferChinese ? `通勤 ${stop.costBreakdown.localTransitCost}` : `Transit ${stop.costBreakdown.localTransitCost}` }}</span>
                    <span>{{ preferChinese ? `其他 ${stop.costBreakdown.otherCost}` : `Other ${stop.costBreakdown.otherCost}` }}</span>
                  </div>

                  <div v-if="stop.routeFromPrevious" class="plan-route">
                    <div class="plan-route__summary">
                      <strong>{{ stop.routeFromPrevious.fromName }} → {{ stop.routeFromPrevious.toName }}</strong>
                      <p>{{ routeLine(stop.routeFromPrevious) }} · {{ routeMeta(stop.routeFromPrevious) }}</p>
                      <p class="plan-amap__note">{{ routeStatus(stop.routeFromPrevious.source) }}</p>
                      <p>{{ stop.routeFromPrevious.summary }}</p>
                    </div>
                    <details class="plan-route__details">
                      <summary>{{ copy.routeDetails }}</summary>
                      <div class="plan-route__steps">
                        <article v-for="step in stop.routeFromPrevious.steps" :key="`${step.mode}-${step.lineName}-${step.fromName}-${step.toName}`" class="plan-route__step">
                          <strong>{{ step.lineName || step.title }}</strong>
                          <p>{{ step.fromName || step.instruction }}<template v-if="step.toName"> → {{ step.toName }}</template></p>
                          <span>{{ stepMeta(step) }}</span>
                        </article>
                      </div>
                    </details>
                  </div>
                </div>
              </article>

              <article
                v-if="day.returnToHotel"
                class="plan-stop plan-stop--return"
                @mouseenter="activatePrimaryHotel()"
              >
                <div class="plan-stop__slot">{{ preferChinese ? '返程' : 'Return' }}</div>
                <div class="plan-stop__body">
                  <strong>{{ day.returnToHotel.fromName }} → {{ day.returnToHotel.toName }}</strong>
                  <p>{{ routeLine(day.returnToHotel) }} · {{ routeMeta(day.returnToHotel) }}</p>
                  <p class="plan-amap__note">{{ routeStatus(day.returnToHotel.source) }}</p>
                  <p>{{ day.returnToHotel.summary }}</p>
                </div>
              </article>
            </div>
          </article>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.plan-feedback-card {
  gap: 0.9rem;
}

.plan-feedback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 0.35rem;
}

.plan-feedback-button {
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: #fff;
  color: #0f172a;
  border-radius: 999px;
  padding: 0.65rem 1rem;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: transform 120ms ease, border-color 120ms ease, background 120ms ease;
}

.plan-feedback-button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(15, 118, 110, 0.55);
  background: rgba(15, 118, 110, 0.06);
}

.plan-feedback-button:disabled {
  opacity: 0.6;
  cursor: wait;
}

.plan-feedback-button--muted:hover:not(:disabled) {
  border-color: rgba(148, 163, 184, 0.8);
  background: rgba(148, 163, 184, 0.12);
}
</style>
