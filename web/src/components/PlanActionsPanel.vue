<script setup lang="ts">
import { computed } from 'vue'
import type { TravelPlan } from '../types/api'
import { downloadTravelScrapbook } from '../utils/travelScrapbook'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '亮点功能',
      title: '旅行手账',
      exportTitle: '生成旅行手账',
      exportBody: '把当前方案导出成适合分享、保存和复盘的长图，发给同行人也更方便。',
      exportNote: '生成后会自动下载图片',
      exportAction: '生成手账长图'
    }
  : {
      eyebrow: 'Signature Action',
      title: 'Travel Scrapbook',
      exportTitle: 'Export Travel Scrapbook',
      exportBody: 'Turn this itinerary into a shareable long-form visual card that is easier to save and send around.',
      exportNote: 'The image downloads immediately after export.',
      exportAction: 'Export Scrapbook'
    }))

async function exportScrapbook() {
  if (!props.travelPlan) {
    return
  }
  await downloadTravelScrapbook(props.travelPlan, props.preferChinese)
}
</script>

<template>
  <section v-if="travelPlan" class="panel plan-actions-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
        <h2>{{ copy.title }}</h2>
      </div>
    </div>

    <article class="plan-actions-panel__card">
      <div>
        <strong>{{ copy.exportTitle }}</strong>
        <p>{{ copy.exportBody }}</p>
        <p class="plan-actions-panel__note">{{ copy.exportNote }}</p>
      </div>
      <button class="plan-actions-panel__primary" @click="exportScrapbook">{{ copy.exportAction }}</button>
    </article>
  </section>
</template>

<style scoped>
.plan-actions-panel {
  display: grid;
  gap: 1rem;
}

.plan-actions-panel__card {
  display: grid;
  gap: 0.9rem;
  padding: 1rem;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 180, 84, 0.16), rgba(47, 134, 255, 0.1));
  border: 1px solid rgba(24, 50, 74, 0.08);
}

.plan-actions-panel__card strong {
  font-size: 1rem;
}

.plan-actions-panel__card p {
  margin: 0;
  color: #61798b;
  line-height: 1.6;
}

.plan-actions-panel__note {
  font-size: 0.86rem;
}

.plan-actions-panel__primary {
  border: none;
  border-radius: 999px;
  padding: 0.82rem 1rem;
  background: linear-gradient(135deg, #2f86ff, #165b97);
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
</style>
