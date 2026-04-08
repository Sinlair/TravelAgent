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
      title: '\u65c5\u884c\u624b\u8d26',
      exportTitle: '\u751f\u6210\u65c5\u884c\u624b\u8d26',
      exportBody: '\u628a\u5f53\u524d\u65b9\u6848\u5bfc\u51fa\u6210\u9002\u5408\u5206\u4eab\u3001\u4fdd\u5b58\u548c\u56de\u770b\u7684\u957f\u56fe\u3002',
      exportNote: '\u751f\u6210\u540e\u4f1a\u81ea\u52a8\u4e0b\u8f7d\u56fe\u7247',
      exportAction: '\u751f\u6210\u624b\u8d26\u957f\u56fe'
    }
  : {
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
