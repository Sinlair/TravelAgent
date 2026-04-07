<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FeedbackBreakdownItem, FeedbackLoopSummaryResponse } from '../types/api'

const props = defineProps<{
  summary: FeedbackLoopSummaryResponse | null
  loading: boolean
  stale: boolean
  errorMessage: string
  preferChinese: boolean
  initialLimit: number
}>()

const emit = defineEmits<{
  refresh: [limit: number]
}>()

const selectedLimit = ref(props.initialLimit)

watch(() => props.initialLimit, (value) => {
  selectedLimit.value = value
})

const copy = computed(() => ({
  eyebrow: props.preferChinese ? '反馈闭环' : 'Feedback Loop',
  title: props.preferChinese ? '按需分析最近反馈' : 'Analyze Recent Feedback On Demand',
  hint: props.preferChinese
    ? '这里只会在你手动刷新时拉取一次汇总，不会自动跑后台任务。'
    : 'This view only refreshes when you ask for it. No scheduled background job is running.',
  empty: props.preferChinese
    ? '还没有加载反馈洞察。手动刷新后，这里会显示接受率、失败模式和建议动作。'
    : 'No feedback analysis loaded yet. Refresh manually to see acceptance rates, failure patterns, and next actions.',
  refresh: props.preferChinese ? '手动刷新' : 'Refresh',
  loading: props.preferChinese ? '分析中...' : 'Loading...',
  stale: props.preferChinese ? '已有新反馈，当前视图可能过时' : 'New feedback arrived. This view may be stale.',
  generatedAt: props.preferChinese ? '生成时间' : 'Generated',
  sample: props.preferChinese ? '样本数' : 'Samples',
  accepted: props.preferChinese ? '直接接受' : 'Accepted',
  usable: props.preferChinese ? '可用率' : 'Usable',
  coverage: props.preferChinese ? '结构化方案覆盖率' : 'Structured plan coverage',
  reasons: props.preferChinese ? '主要原因码' : 'Top reason codes',
  destinations: props.preferChinese ? '主要目的地' : 'Top destinations',
  findings: props.preferChinese ? '关键发现' : 'Key findings',
  noFindings: props.preferChinese ? '当前样本里还没有明显风险模式。' : 'No strong risk patterns surfaced in the current sample.',
  sampleOfLimit: props.preferChinese ? '已分析样本' : 'Sampled'
}))

function formatRate(value: number) {
  return `${value.toFixed(2)}%`
}

function breakdownLabel(item: FeedbackBreakdownItem) {
  return props.preferChinese
    ? `总计 ${item.totalCount} / 可用 ${formatRate(item.usableRatePct)}`
    : `${item.totalCount} total / ${formatRate(item.usableRatePct)} usable`
}

function refresh() {
  emit('refresh', Number(selectedLimit.value))
}

const destinationItems = computed(() => props.summary?.topDestinations.slice(0, 4) ?? [])
const reasonItems = computed(() => props.summary?.topReasonCodes.slice(0, 4) ?? [])
</script>

<template>
  <section class="panel feedback-loop-panel">
    <div class="panel__header feedback-loop-panel__header">
      <div>
        <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
        <h2>{{ copy.title }}</h2>
        <p class="feedback-loop-panel__hint">{{ copy.hint }}</p>
      </div>
      <div class="feedback-loop-panel__controls">
        <select v-model="selectedLimit" class="feedback-loop-panel__select" :disabled="loading">
          <option :value="50">50</option>
          <option :value="100">100</option>
          <option :value="200">200</option>
          <option :value="500">500</option>
        </select>
        <button class="feedback-loop-panel__action" :disabled="loading" @click="refresh">
          {{ loading ? copy.loading : copy.refresh }}
        </button>
      </div>
    </div>

    <p v-if="stale && summary" class="feedback-loop-panel__stale">{{ copy.stale }}</p>
    <p v-if="errorMessage" class="composer__error">{{ errorMessage }}</p>

    <div v-if="summary" class="feedback-loop-panel__body">
      <div class="feedback-loop-panel__stats">
        <article class="feedback-loop-panel__stat">
          <span>{{ copy.accepted }}</span>
          <strong>{{ formatRate(summary.acceptedRatePct) }}</strong>
        </article>
        <article class="feedback-loop-panel__stat">
          <span>{{ copy.usable }}</span>
          <strong>{{ formatRate(summary.usableRatePct) }}</strong>
        </article>
        <article class="feedback-loop-panel__stat">
          <span>{{ copy.coverage }}</span>
          <strong>{{ formatRate(summary.structuredPlanCoveragePct) }}</strong>
        </article>
        <article class="feedback-loop-panel__stat">
          <span>{{ copy.sample }}</span>
          <strong>{{ summary.sampleCount }}</strong>
        </article>
      </div>

      <div class="feedback-loop-panel__meta">
        <span>{{ copy.generatedAt }}: {{ new Date(summary.generatedAt).toLocaleString() }}</span>
        <span>{{ copy.sampleOfLimit }}: {{ summary.sampleCount }} / {{ summary.limitApplied }}</span>
      </div>

      <div class="feedback-loop-panel__grid">
        <section class="feedback-loop-panel__section">
          <div class="feedback-loop-panel__section-title">{{ copy.reasons }}</div>
          <div class="feedback-loop-panel__list">
            <article v-for="item in reasonItems" :key="`reason-${item.key}`" class="feedback-loop-panel__list-item">
              <strong>{{ item.key }}</strong>
              <p>{{ breakdownLabel(item) }}</p>
            </article>
          </div>
        </section>

        <section class="feedback-loop-panel__section">
          <div class="feedback-loop-panel__section-title">{{ copy.destinations }}</div>
          <div class="feedback-loop-panel__list">
            <article v-for="item in destinationItems" :key="`destination-${item.key}`" class="feedback-loop-panel__list-item">
              <strong>{{ item.key }}</strong>
              <p>{{ breakdownLabel(item) }}</p>
            </article>
          </div>
        </section>
      </div>

      <section class="feedback-loop-panel__section">
        <div class="feedback-loop-panel__section-title">{{ copy.findings }}</div>
        <div v-if="summary.keyFindings.length" class="feedback-loop-panel__finding-list">
          <article v-for="item in summary.keyFindings" :key="`${item.type}-${item.key}`" class="feedback-loop-panel__finding">
            <div class="feedback-loop-panel__finding-header">
              <strong>{{ item.key }}</strong>
              <span class="plan-insight-card__badge">{{ item.type }}</span>
            </div>
            <p>
              {{ props.preferChinese ? '样本' : 'Sample' }} {{ item.totalCount }}
              | {{ props.preferChinese ? '可用率' : 'Usable' }} {{ formatRate(item.usableRatePct) }}
            </p>
            <p>{{ item.recommendation }}</p>
          </article>
        </div>
        <div v-else class="timeline-empty">{{ copy.noFindings }}</div>
      </section>
    </div>

    <div v-else class="timeline-empty">
      {{ copy.empty }}
    </div>
  </section>
</template>
