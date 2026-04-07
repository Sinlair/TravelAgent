<script setup lang="ts">
import type { ExecutionStage, TimelineEvent } from '../types/api'

const props = withDefaults(defineProps<{
  timeline: TimelineEvent[]
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const stageLabel = (stage: ExecutionStage) => {
  if (!props.preferChinese) {
    return stage
  }
  return {
    ANALYZE_QUERY: '理解需求',
    RECALL_MEMORY: '整理条件',
    SELECT_AGENT: '选择处理方式',
    SPECIALIST: '生成方案',
    CALL_TOOL: '查询信息',
    VALIDATE_PLAN: '校验方案',
    REPAIR_PLAN: '修正方案',
    FINALIZE_MEMORY: '保存结果',
    COMPLETED: '完成',
    ERROR: '异常'
  }[stage] ?? stage
}

function messageLabel(message: string) {
  if (!props.preferChinese) {
    return message
  }
  return {
    'Analyze user intent and missing slots': '正在理解你的需求和缺失条件',
    'Recall short-term window, summary, and long-term memory': '正在整理这次规划需要的已知信息',
    'Route request to the best agent': '正在确定这次应该如何处理',
    'Execute specialist agent': '正在生成正式方案',
    'Resolve itinerary places with Amap': '正在核对行程中的地点信息',
    'Validate generated plan against budget, opening hours, and load': '正在校验预算、开放时间和行程强度',
    'Repair plan against validation findings': '发现问题，正在自动修正方案',
    'Build closest feasible alternative by relaxing constraints': '原始约束过紧，正在生成最接近的可行替代方案',
    'Retrieve destination knowledge from travel knowledge base': '正在检索目的地知识',
    'Fetch destination weather snapshot': '正在获取目的地天气快照',
    'Resolve POI candidates with Amap': '正在校对景点位置',
    'Resolve hotel district center with Amap': '正在确认住宿区域',
    'Recommend hotels with Amap': '正在筛选更合适的酒店',
    'Resolve transit route with Amap': '正在计算两地路线',
    'Persist summary, task memory, and structured plan': '正在保存这次规划结果',
    'Execution finished': '这次规划已经完成'
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
    longTermCount: '长期记忆命中',
    agent: '处理代理',
    reason: '原因',
    routeReason: '路由原因',
    destination: '目的地',
    source: '来源',
    attempt: '第几轮',
    accepted: '是否通过',
    failCount: '失败项',
    warningCount: '提醒项',
    repairCodes: '修正类型',
    hasSummary: '已保存摘要',
    hasPlan: '已保存方案'
  }[key] ?? key
}

function detailValue(value: string | number | boolean | string[] | null) {
  if (Array.isArray(value)) {
    return value.join(' / ')
  }
  if (typeof value === 'boolean') {
    return props.preferChinese ? (value ? '是' : '否') : (value ? 'Yes' : 'No')
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
      <div>
        <p class="panel__eyebrow">{{ preferChinese ? '过程' : 'Execution Trail' }}</p>
        <h2>{{ preferChinese ? '这次方案是怎么生成的' : 'How This Plan Was Built' }}</h2>
      </div>
    </div>

    <div class="timeline-list">
      <article v-for="event in timeline" :key="event.id" class="timeline-item">
        <div class="timeline-item__stage">{{ stageLabel(event.stage) }}</div>
        <div>
          <strong>{{ messageLabel(event.message) }}</strong>
          <div v-if="detailEntries(event.details).length" class="timeline-item__details">
            <span
              v-for="detail in detailEntries(event.details)"
              :key="`${event.id}-${detail.key}`"
              class="timeline-item__pill"
            >
              {{ detail.label }}: {{ detail.value }}
            </span>
          </div>
          <p>{{ new Date(event.createdAt).toLocaleString(preferChinese ? 'zh-CN' : undefined) }}</p>
        </div>
      </article>

      <div v-if="timeline.length === 0" class="timeline-empty">
        {{ preferChinese ? '发送需求后，这里会展示方案生成过程。' : 'Execution details will appear here after you send a request.' }}
      </div>
    </div>
  </section>
</template>
