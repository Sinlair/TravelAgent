<script setup lang="ts">
import { computed } from 'vue'
import { RefreshCcw, CheckCircle2, Circle, History, ArrowRightLeft, CalendarDays } from 'lucide-vue-next'
import type { TravelPlan, TravelPlanVersionDiffResponse } from '../types/api'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  recentVersionDiff: TravelPlanVersionDiffResponse | null
  sending?: boolean
  preferChinese?: boolean
}>(), {
  sending: false,
  preferChinese: true
})

const emit = defineEmits<{
  replanHotel: []
  replanDay: [dayNumber: number]
  toggleChecklist: [itemKey: string, confirmed: boolean]
}>()

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '执行',
      title: '落地与修正',
      refreshedTitle: '最近一次局部刷新',
      checklistTitle: '出发前清单',
      checklistHint: (pending: number) => `还有 ${pending} 项待确认`,
      checklistReady: '清单已经确认完毕',
      confirm: '标记已确认',
      undo: '恢复待确认',
      replanTitle: '局部重算',
      replanBody: '只刷新受影响的部分，尽量保留其余行程不动。',
      replanHotel: '重算住宿区域',
      replanDay: (dayNumber: number) => `重算第 ${dayNumber} 天`,
      replanBusy: '正在重算...',
      diffTitle: '最近两个版本变化',
      dates: '日期',
      hotel: '住宿区域',
      budget: '预算',
      stops: '关键停靠变化',
      none: '暂无'
    }
  : {
      eyebrow: 'Execution',
      title: 'Commit And Adjust',
      refreshedTitle: 'Latest scoped refresh',
      checklistTitle: 'Pre-departure checklist',
      checklistHint: (pending: number) => `${pending} items still pending`,
      checklistReady: 'Checklist fully confirmed',
      confirm: 'Mark confirmed',
      undo: 'Mark pending',
      replanTitle: 'Scoped replan',
      replanBody: 'Refresh only the affected section and keep the rest of the trip stable.',
      replanHotel: 'Replan hotel area',
      replanDay: (dayNumber: number) => `Replan Day ${dayNumber}`,
      replanBusy: 'Replanning...',
      diffTitle: 'Latest version comparison',
      dates: 'Dates',
      hotel: 'Hotel area',
      budget: 'Budget',
      stops: 'Key stop changes',
      none: 'None'
    }))

const pendingChecklistCount = computed(() =>
  props.travelPlan?.checklist?.filter(item => !item.confirmed).length ?? 0
)

const refreshedSummary = computed(() => {
  const sections = props.travelPlan?.refreshedSections ?? []
  if (!sections.length) {
    return null
  }
  return sections.map(section => {
    if (section.startsWith('DAY:')) {
      const dayNumber = section.split(':')[1]
      return props.preferChinese ? `第 ${dayNumber} 天已刷新` : `Day ${dayNumber} refreshed`
    }
    if (section === 'HOTEL_AREA') {
      return props.preferChinese ? '住宿区域已刷新' : 'Hotel area refreshed'
    }
    return section
  }).join(' / ')
})
</script>

<template>
  <section v-if="travelPlan" class="panel execution-panel">
    <div class="panel__header">
      <div class="panel__header-info">
        <div class="panel__icon-badge">
          <RefreshCcw :size="18" />
        </div>
        <div>
          <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
          <h2>{{ copy.title }}</h2>
        </div>
      </div>
    </div>

    <article v-if="refreshedSummary" class="execution-panel__banner">
      <div class="execution-panel__banner-head">
        <RefreshCcw :size="14" />
        <strong>{{ copy.refreshedTitle }}</strong>
      </div>
      <p>{{ refreshedSummary }}</p>
    </article>

    <section class="execution-panel__section">
      <div class="execution-panel__section-head">
        <div>
          <strong>{{ copy.checklistTitle }}</strong>
          <p>{{ pendingChecklistCount ? copy.checklistHint(pendingChecklistCount) : copy.checklistReady }}</p>
        </div>
        <CalendarDays :size="16" />
      </div>

      <div class="execution-panel__checklist">
        <article
          v-for="item in travelPlan.checklist ?? []"
          :key="item.key"
          class="execution-panel__checklist-item"
          :class="{ 'execution-panel__checklist-item--confirmed': item.confirmed }"
        >
          <div class="execution-panel__checklist-copy">
            <div class="execution-panel__checklist-title">
              <component :is="item.confirmed ? CheckCircle2 : Circle" :size="14" />
              <strong>{{ item.title }}</strong>
            </div>
            <p>{{ item.details }}</p>
          </div>
          <button
            type="button"
            class="execution-panel__action"
            @click="emit('toggleChecklist', item.key, !item.confirmed)"
          >
            {{ item.confirmed ? copy.undo : copy.confirm }}
          </button>
        </article>
      </div>
    </section>

    <section class="execution-panel__section">
      <div class="execution-panel__section-head">
        <div>
          <strong>{{ copy.replanTitle }}</strong>
          <p>{{ copy.replanBody }}</p>
        </div>
        <ArrowRightLeft :size="16" />
      </div>

      <div class="execution-panel__actions">
        <button
          type="button"
          class="execution-panel__primary"
          :disabled="sending"
          @click="emit('replanHotel')"
        >
          {{ sending ? copy.replanBusy : copy.replanHotel }}
        </button>
        <button
          v-for="day in travelPlan.days"
          :key="day.dayNumber"
          type="button"
          class="execution-panel__secondary"
          :disabled="sending"
          @click="emit('replanDay', day.dayNumber)"
        >
          {{ sending ? copy.replanBusy : copy.replanDay(day.dayNumber) }}
        </button>
      </div>
    </section>

    <section v-if="recentVersionDiff" class="execution-panel__section">
      <div class="execution-panel__section-head">
        <div>
          <strong>{{ copy.diffTitle }}</strong>
          <p>{{ recentVersionDiff.previousCreatedAt }} -> {{ recentVersionDiff.latestCreatedAt }}</p>
        </div>
        <History :size="16" />
      </div>

      <div class="execution-panel__diff-grid">
        <article class="execution-panel__diff-card">
          <span>{{ copy.dates }}</span>
          <strong>{{ recentVersionDiff.dateSummary }}</strong>
        </article>
        <article class="execution-panel__diff-card">
          <span>{{ copy.hotel }}</span>
          <strong>{{ recentVersionDiff.hotelSummary }}</strong>
        </article>
        <article class="execution-panel__diff-card">
          <span>{{ copy.budget }}</span>
          <strong>{{ recentVersionDiff.budgetSummary }}</strong>
        </article>
      </div>

      <div class="execution-panel__diff-list">
        <span>{{ copy.stops }}</span>
        <article
          v-for="item in recentVersionDiff.stopHighlights"
          :key="item"
          class="execution-panel__diff-item"
        >
          {{ item }}
        </article>
        <div v-if="!recentVersionDiff.stopHighlights.length" class="timeline-empty">
          {{ copy.none }}
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.execution-panel {
  display: grid;
  gap: 14px;
}

.execution-panel__banner,
.execution-panel__section {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(24, 50, 74, 0.08);
  background: rgba(255, 255, 255, 0.84);
}

.execution-panel__banner {
  background: linear-gradient(145deg, rgba(246, 250, 255, 0.96), rgba(255, 249, 242, 0.94));
}

.execution-panel__banner-head,
.execution-panel__section-head,
.execution-panel__checklist-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.execution-panel__section-head {
  justify-content: space-between;
}

.execution-panel__section-head p,
.execution-panel__banner p,
.execution-panel__checklist-copy p {
  margin: 0;
  color: var(--muted);
  line-height: 1.5;
}

.execution-panel__checklist,
.execution-panel__actions,
.execution-panel__diff-grid,
.execution-panel__diff-list {
  display: grid;
  gap: 10px;
}

.execution-panel__checklist-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(24, 50, 74, 0.08);
  background: rgba(250, 251, 253, 0.82);
}

.execution-panel__checklist-item--confirmed {
  border-color: rgba(15, 123, 115, 0.18);
  background: rgba(245, 252, 251, 0.9);
}

.execution-panel__action,
.execution-panel__primary,
.execution-panel__secondary {
  border: 1px solid rgba(24, 50, 74, 0.1);
  border-radius: 999px;
  padding: 9px 12px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--ink);
  font: inherit;
  cursor: pointer;
}

.execution-panel__primary {
  background: linear-gradient(135deg, var(--accent), #a93d1d);
  color: #fff;
  border: none;
}

.execution-panel__diff-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.execution-panel__diff-card,
.execution-panel__diff-item {
  padding: 12px;
  border-radius: 14px;
  background: rgba(247, 249, 252, 0.86);
  border: 1px solid rgba(24, 50, 74, 0.08);
}

.execution-panel__diff-card span,
.execution-panel__diff-list > span {
  color: var(--muted);
  font-size: 0.8rem;
}

.execution-panel__diff-card strong {
  display: block;
  margin-top: 6px;
}

@media (max-width: 900px) {
  .execution-panel__checklist-item {
    grid-template-columns: 1fr;
  }

  .execution-panel__diff-grid {
    grid-template-columns: 1fr;
  }
}
</style>
