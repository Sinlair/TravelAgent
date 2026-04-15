<script setup lang="ts">
import { computed } from 'vue'
import { Search, Brain, MapPin, CheckCircle2, AlertCircle, Wrench, Thermometer, CloudSun, Hotel, Navigation, History, Sparkles, Save, Info, Clock } from 'lucide-vue-next'
import type { ExecutionStage, TimelineEvent, TimelineEventStatus } from '../types/api'
import type { ConversationResultViewModel } from '../utils/conversationResult'

const props = withDefaults(defineProps<{
  resultView?: ConversationResultViewModel
  timeline?: TimelineEvent[]
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const emit = defineEmits<{
  retryStream: []
}>()

const copy = {
  zh: {
    title: '\u6267\u884c\u8be6\u60c5',
    empty: '\u53d1\u9001\u9700\u6c42\u540e\uff0c\u8fd9\u91cc\u4f1a\u663e\u793a\u65b9\u6848\u751f\u6210\u4e2d\u7684\u513f\u5173\u952e\u8282\u70b9\u3002',
    partial: '\u8fd8\u6ca1\u6536\u5230\u5b8c\u6574\u7684\u65f6\u95f4\u7ebf\u4e8b\u4ef6\u3002',
    retry: '\u91cd\u8fde\u5b9e\u65f6\u65f6\u95f4\u7ebf',
    disconnected: '\u5b9e\u65f6\u65f6\u95f4\u7ebf\u5df2\u4e2d\u65ad'
  },
  en: {
    title: 'Execution Details',
    empty: 'Key build steps will appear here after you send a request.',
    partial: 'The timeline is not fully populated yet.',
    retry: 'Retry live timeline',
    disconnected: 'Live execution updates disconnected'
  }
}

const timeline = computed(() => props.resultView?.timeline ?? props.timeline ?? [])
const timelineState = computed(() => props.resultView?.timelineState ?? (timeline.value.length ? 'success' : 'empty'))

function stageIcon(stage: ExecutionStage) {
  return {
    ANALYZE_QUERY: Brain,
    RECALL_MEMORY: History,
    SELECT_AGENT: Navigation,
    SPECIALIST: Sparkles,
    CALL_TOOL: Search,
    VALIDATE_PLAN: CheckCircle2,
    REPAIR_PLAN: Wrench,
    FINALIZE_MEMORY: Save,
    COMPLETED: CheckCircle2,
    ERROR: AlertCircle
  }[stage] ?? Info
}

function messageIcon(message: string) {
  if (message.includes('Amap')) return MapPin
  if (message.includes('weather')) return CloudSun
  if (message.includes('hotel')) return Hotel
  if (message.includes('transit')) return Navigation
  return null
}

function stageLabel(stage: ExecutionStage) {
  if (!props.preferChinese) {
    return stage
  }
  return {
    ANALYZE_QUERY: '\u7406\u89e3\u9700\u6c42',
    RECALL_MEMORY: '\u6574\u7406\u6761\u4ef6',
    SELECT_AGENT: '\u9009\u62e9\u5904\u7406\u65b9\u5f0f',
    SPECIALIST: '\u751f\u6210\u65b9\u6848',
    CALL_TOOL: '\u67e5\u8be2\u4fe1\u606f',
    VALIDATE_PLAN: '\u6821\u9a8c\u65b9\u6848',
    REPAIR_PLAN: '\u4fee\u6b63\u65b9\u6848',
    FINALIZE_MEMORY: '\u4fdd\u5b58\u7ed3\u679c',
    COMPLETED: '\u5b8c\u6210',
    ERROR: '\u5f02\u5e38'
  }[stage] ?? stage
}

function statusLabel(status: TimelineEventStatus) {
  if (!props.preferChinese) {
    return status
  }
  return {
    STARTED: '\u5f00\u59cb',
    COMPLETED: '\u5b8c\u6210',
    FAILED: '\u5931\u8d25',
    REPAIRED: '\u5df2\u4fee\u6b63'
  }[status] ?? status
}

function messageLabel(message: string) {
  if (!props.preferChinese) {
    return message
  }
  return {
    'Analyze user intent and missing slots': '\u6b63\u5728\u7406\u89e3\u4f60\u7684\u9700\u6c42\u548c\u7f3a\u5931\u6761\u4ef6',
    'Recall short-term window, summary, and long-term memory': '\u6b63\u5728\u6574\u7406\u8fd9\u6b21\u89c4\u5212\u9700\u8981\u7684\u5df2\u77e5\u4fe1\u606f',
    'Route request to the best agent': '\u6b63\u5728\u786e\u5b9a\u8fd9\u6b21\u5e94\u8be5\u5982\u4f55\u5904\u7406',
    'Execute specialist agent': '\u6b63\u5728\u751f\u6210\u6b63\u5f0f\u65b9\u6848',
    'Resolve itinerary places with Amap': '\u6b63\u5728\u6838\u5bf9\u884c\u7a0b\u91cc\u7684\u5730\u70b9\u4fe1\u606f',
    'Validate generated plan against budget, opening hours, and load': '\u6b63\u5728\u6821\u9a8c\u9884\u7b97\u3001\u5f00\u653e\u65f6\u95f4\u548c\u884c\u7a0b\u5f3a\u5ea6',
    'Repair plan against validation findings': '\u53d1\u73b0\u95ee\u9898\uff0c\u6b63\u5728\u81ea\u52a8\u4fee\u6b63\u65b9\u6848',
    'Build closest feasible alternative by relaxing constraints': '\u539f\u59cb\u7ea6\u675f\u8fc7\u7d27\uff0c\u6b63\u5728\u751f\u6210\u6700\u63a5\u8fd1\u7684\u53ef\u884c\u66ff\u4ee3\u65b9\u6848',
    'Retrieve destination knowledge from travel knowledge base': '\u6b63\u5728\u68c0\u7d22\u76ee\u7684\u5730\u77e5\u8bc6',
    'Fetch destination weather snapshot': '\u6b63\u5728\u83b7\u53d6\u76ee\u7684\u5730\u5929\u6c14\u5feb\u7167',
    'Resolve POI candidates with Amap': '\u6b63\u5728\u6821\u5bf9\u666f\u70b9\u4f4d\u7f6e',
    'Resolve hotel district center with Amap': '\u6b63\u5728\u786e\u8ba4\u4f4f\u5bbf\u533a\u57df',
    'Recommend hotels with Amap': '\u6b63\u5728\u7b5b\u9009\u66f4\u5408\u9002\u7684\u9152\u5e97',
    'Resolve transit route with Amap': '\u6b63\u5728\u8ba1\u7b97\u4e24\u5730\u8def\u7ebf',
    'Persist summary, task memory, and structured plan': '\u6b63\u5728\u4fdd\u5b58\u8fd9\u6b21\u89c4\u5212\u7ed3\u679c',
    'Execution finished': '\u8fd9\u6b21\u89c4\u5212\u5df2\u7ecf\u5b8c\u6210'
  }[message] ?? message
}

function detailLabel(key: string) {
  if (!props.preferChinese) {
    return {
      longTermCount: 'Long-term memory',
      agent: 'Agent',
      reason: 'Reason',
      routeReason: 'Route reason',
      destination: 'Destination',
      source: 'Source',
      attempt: 'Attempt',
      accepted: 'Accepted',
      failCount: 'Failures',
      warningCount: 'Warnings',
      repairCodes: 'Repair codes',
      hasSummary: 'Summary saved',
      hasPlan: 'Plan saved'
    }[key] ?? key
  }
  return {
    longTermCount: '\u957f\u671f\u8bb0\u5fc6\u547d\u4e2d',
    agent: '\u5904\u7406\u4ee3\u7406',
    reason: '\u539f\u56e0',
    routeReason: '\u8def\u7531\u539f\u56e0',
    destination: '\u76ee\u7684\u5730',
    source: '\u6765\u6e90',
    attempt: '\u7b2c\u51e0\u8f6e',
    accepted: '\u662f\u5426\u901a\u8fc7',
    failCount: '\u5931\u8d25\u9879',
    warningCount: '\u63d0\u9192\u9879',
    repairCodes: '\u4fee\u6b63\u7c7b\u578b',
    hasSummary: '\u5df2\u4fdd\u5b58\u6458\u8981',
    hasPlan: '\u5df2\u4fdd\u5b58\u65b9\u6848'
  }[key] ?? key
}

function detailValue(value: string | number | boolean | string[] | null) {
  if (Array.isArray(value)) {
    return value.join(' / ')
  }
  if (typeof value === 'boolean') {
    return props.preferChinese ? (value ? '\u662f' : '\u5426') : (value ? 'Yes' : 'No')
  }
  return value == null ? '' : String(value)
}

function detailEntries(details: TimelineEvent['details']) {
  return Object.entries(details ?? {})
    .filter(([, value]) => {
      if (Array.isArray(value)) {
        return value.length > 0
      }
      return value !== null && value !== ''
    })
    .map(([key, value]) => ({
      key,
      label: detailLabel(key),
      value: detailValue(value)
    }))
}
</script>

<template>
  <section class="panel timeline-panel">
    <div class="panel__header">
      <div class="panel__header-info">
        <div class="panel__icon-badge">
          <History :size="18" />
        </div>
        <h2>{{ preferChinese ? copy.zh.title : copy.en.title }}</h2>
      </div>
    </div>

    <div class="timeline-list">
      <div v-if="timelineState === 'error'" class="panel__empty">
        <AlertCircle :size="32" />
        <p>{{ preferChinese ? copy.zh.disconnected : copy.en.disconnected }}</p>
        <button type="button" class="message__feedback-choice message__feedback-choice--active" @click="emit('retryStream')">
          {{ preferChinese ? copy.zh.retry : copy.en.retry }}
        </button>
      </div>
      <div v-else-if="timeline.length === 0" class="panel__empty">
        <Clock :size="32" />
        <p>{{ timelineState === 'partial' ? (preferChinese ? copy.zh.partial : copy.en.partial) : (preferChinese ? copy.zh.empty : copy.en.empty) }}</p>
      </div>

      <article
        v-for="event in timeline"
        :key="event.id"
        class="timeline-item"
        :class="`timeline-item--${event.stage.toLowerCase()}`"
      >
        <div class="timeline-item__icon">
          <component :is="stageIcon(event.stage)" :size="14" />
        </div>
        <div class="timeline-item__content">
          <div class="timeline-item__header">
            <span class="timeline-item__stage">{{ stageLabel(event.stage) }}</span>
            <span class="timeline-item__detail-value">{{ statusLabel(event.status) }}</span>
            <time class="timeline-item__time">{{ new Date(event.endedAt).toLocaleTimeString() }}</time>
          </div>
          <p class="timeline-item__message">
            <component :is="messageIcon(event.message)" :size="12" v-if="messageIcon(event.message)" />
            {{ messageLabel(event.message) }}
          </p>

          <div v-if="Object.keys(event.details).length" class="timeline-item__details">
            <div
              v-for="(value, key) in event.details"
              :key="key"
              class="timeline-item__detail"
            >
              <span class="timeline-item__detail-key">{{ detailLabel(key) }}:</span>
              <strong class="timeline-item__detail-value">{{ detailValue(value) }}</strong>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
