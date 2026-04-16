<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { FeedbackBreakdownItem, FeedbackLoopSummaryResponse } from '../types/api'

type FeedbackFilters = {
  destination?: string
  agentType?: string
  targetScope?: string
  reasonLabel?: string
}

const props = withDefaults(defineProps<{
  summary: FeedbackLoopSummaryResponse | null
  loading: boolean
  stale: boolean
  errorMessage: string
  preferChinese?: boolean
  initialLimit: number
  initialFilters?: FeedbackFilters
}>(), {
  preferChinese: true,
  initialFilters: () => ({})
})

const emit = defineEmits<{
  refresh: [payload: { limit: number } & FeedbackFilters]
}>()

const form = reactive({
  limit: props.initialLimit,
  destination: props.initialFilters?.destination ?? '',
  agentType: props.initialFilters?.agentType ?? '',
  targetScope: props.initialFilters?.targetScope ?? '',
  reasonLabel: props.initialFilters?.reasonLabel ?? ''
})

watch(() => props.initialLimit, value => {
  form.limit = value
})

watch(() => props.initialFilters, value => {
  form.destination = value?.destination ?? ''
  form.agentType = value?.agentType ?? ''
  form.targetScope = value?.targetScope ?? ''
  form.reasonLabel = value?.reasonLabel ?? ''
}, { deep: true })

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '运营',
      title: '反馈运营面板',
      hint: '按城市、agent、反馈范围和原因标签筛一遍，就能快速看到最近内部测试最容易失真的区域。',
      refresh: '刷新',
      loading: '加载中...',
      stale: '有新的反馈写入，当前视图可能已经过时。',
      generatedAt: '生成时间',
      sample: '样本数',
      accepted: '直接接受率',
      usable: '可用率',
      coverage: '结构化方案覆盖率',
      reasons: '高频原因',
      destinations: '高频目的地',
      agents: '高频 Agent',
      findings: '关键发现',
      noFindings: '当前筛选范围内还没有明显的失败模式。',
      destination: '目的地',
      destinationPlaceholder: '例如 Hangzhou / 杭州',
      agentType: 'Agent',
      targetScope: '反馈范围',
      reasonLabel: '原因标签',
      reasonPlaceholder: '例如 not_useful',
      any: '全部',
      answer: '答案',
      plan: '行程',
      overall: '整体',
      sampleWord: '样本'
    }
  : {
      eyebrow: 'Operations',
      title: 'Feedback Operations Panel',
      hint: 'Filter by city, agent, target scope, and reason label to isolate the most harmful internal-beta failures quickly.',
      refresh: 'Refresh',
      loading: 'Loading...',
      stale: 'New feedback arrived. This view may be stale.',
      generatedAt: 'Generated',
      sample: 'Samples',
      accepted: 'Accepted',
      usable: 'Usable',
      coverage: 'Structured plan coverage',
      reasons: 'Top reasons',
      destinations: 'Top destinations',
      agents: 'Top agents',
      findings: 'Key findings',
      noFindings: 'No strong failure pattern is visible in the current filter.',
      destination: 'Destination',
      destinationPlaceholder: 'Example: Hangzhou',
      agentType: 'Agent',
      targetScope: 'Scope',
      reasonLabel: 'Reason label',
      reasonPlaceholder: 'Example: not_useful',
      any: 'Any',
      answer: 'Answer',
      plan: 'Plan',
      overall: 'Overall',
      sampleWord: 'Sample'
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
  emit('refresh', {
    limit: Number(form.limit),
    destination: form.destination || undefined,
    agentType: form.agentType || undefined,
    targetScope: form.targetScope || undefined,
    reasonLabel: form.reasonLabel || undefined
  })
}
</script>

<template>
  <section class="panel feedback-loop-panel">
    <div class="panel__header feedback-loop-panel__header">
      <div>
        <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
        <h2>{{ copy.title }}</h2>
        <p class="feedback-loop-panel__hint">{{ copy.hint }}</p>
      </div>
      <button class="feedback-loop-panel__action" :disabled="loading" @click="refresh">
        {{ loading ? copy.loading : copy.refresh }}
      </button>
    </div>

    <div class="feedback-loop-panel__filters">
      <label class="feedback-loop-panel__field">
        <span>{{ copy.destination }}</span>
        <input v-model="form.destination" :placeholder="copy.destinationPlaceholder" />
      </label>
      <label class="feedback-loop-panel__field">
        <span>{{ copy.agentType }}</span>
        <select v-model="form.agentType">
          <option value="">{{ copy.any }}</option>
          <option value="TRAVEL_PLANNER">TRAVEL_PLANNER</option>
          <option value="WEATHER">WEATHER</option>
          <option value="GEO">GEO</option>
          <option value="GENERAL">GENERAL</option>
        </select>
      </label>
      <label class="feedback-loop-panel__field">
        <span>{{ copy.targetScope }}</span>
        <select v-model="form.targetScope">
          <option value="">{{ copy.any }}</option>
          <option value="ANSWER">{{ copy.answer }}</option>
          <option value="PLAN">{{ copy.plan }}</option>
          <option value="OVERALL">{{ copy.overall }}</option>
        </select>
      </label>
      <label class="feedback-loop-panel__field">
        <span>{{ copy.reasonLabel }}</span>
        <input v-model="form.reasonLabel" :placeholder="copy.reasonPlaceholder" />
      </label>
      <label class="feedback-loop-panel__field">
        <span>{{ copy.sample }}</span>
        <select v-model="form.limit">
          <option :value="50">50</option>
          <option :value="100">100</option>
          <option :value="200">200</option>
          <option :value="500">500</option>
        </select>
      </label>
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
        <span>{{ copy.sampleWord }}: {{ summary.sampleCount }} / {{ summary.limitApplied }}</span>
      </div>

      <div class="feedback-loop-panel__grid">
        <section class="feedback-loop-panel__section">
          <div class="feedback-loop-panel__section-title">{{ copy.reasons }}</div>
          <div class="feedback-loop-panel__list">
            <article v-for="item in summary.topReasonCodes.slice(0, 4)" :key="`reason-${item.key}`" class="feedback-loop-panel__list-item">
              <strong>{{ item.key }}</strong>
              <p>{{ breakdownLabel(item) }}</p>
            </article>
          </div>
        </section>

        <section class="feedback-loop-panel__section">
          <div class="feedback-loop-panel__section-title">{{ copy.destinations }}</div>
          <div class="feedback-loop-panel__list">
            <article v-for="item in summary.topDestinations.slice(0, 4)" :key="`destination-${item.key}`" class="feedback-loop-panel__list-item">
              <strong>{{ item.key }}</strong>
              <p>{{ breakdownLabel(item) }}</p>
            </article>
          </div>
        </section>

        <section class="feedback-loop-panel__section">
          <div class="feedback-loop-panel__section-title">{{ copy.agents }}</div>
          <div class="feedback-loop-panel__list">
            <article v-for="item in summary.topAgentTypes.slice(0, 4)" :key="`agent-${item.key}`" class="feedback-loop-panel__list-item">
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
              {{ copy.sampleWord }} {{ item.totalCount }}
              | {{ copy.usable }} {{ formatRate(item.usableRatePct) }}
            </p>
            <p>{{ item.recommendation }}</p>
          </article>
        </div>
        <div v-else class="timeline-empty">
          {{ copy.noFindings }}
        </div>
      </section>
    </div>

    <div v-else class="timeline-empty">
      {{ copy.noFindings }}
    </div>
  </section>
</template>

<style scoped>
.feedback-loop-panel {
  display: grid;
  gap: 14px;
}

.feedback-loop-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.feedback-loop-panel__hint,
.feedback-loop-panel__meta,
.feedback-loop-panel__finding p,
.feedback-loop-panel__list-item p {
  margin: 0;
  color: var(--muted);
  line-height: 1.5;
}

.feedback-loop-panel__action,
.feedback-loop-panel__field input,
.feedback-loop-panel__field select {
  border-radius: 12px;
  border: 1px solid rgba(24, 50, 74, 0.1);
  background: rgba(255, 255, 255, 0.92);
  color: var(--ink);
  font: inherit;
}

.feedback-loop-panel__action {
  padding: 10px 14px;
  cursor: pointer;
}

.feedback-loop-panel__filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.feedback-loop-panel__field {
  display: grid;
  gap: 6px;
}

.feedback-loop-panel__field span {
  color: var(--muted);
  font-size: 0.8rem;
}

.feedback-loop-panel__field input,
.feedback-loop-panel__field select {
  padding: 9px 10px;
}

.feedback-loop-panel__stale {
  margin: 0;
  color: var(--accent-deep);
}

.feedback-loop-panel__body,
.feedback-loop-panel__grid,
.feedback-loop-panel__list,
.feedback-loop-panel__finding-list {
  display: grid;
  gap: 12px;
}

.feedback-loop-panel__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.feedback-loop-panel__stat,
.feedback-loop-panel__list-item,
.feedback-loop-panel__finding,
.feedback-loop-panel__section {
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(24, 50, 74, 0.08);
  background: rgba(255, 255, 255, 0.84);
}

.feedback-loop-panel__stat span,
.feedback-loop-panel__section-title {
  color: var(--muted);
  font-size: 0.8rem;
}

.feedback-loop-panel__finding-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

@media (max-width: 1100px) {
  .feedback-loop-panel__filters,
  .feedback-loop-panel__stats,
  .feedback-loop-panel__grid {
    grid-template-columns: 1fr;
  }

  .feedback-loop-panel__header {
    flex-direction: column;
  }
}
</style>
