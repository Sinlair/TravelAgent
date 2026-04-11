<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Share2,
  Download,
  FileText,
  ClipboardList,
  CheckCircle2,
  Info,
  Calendar,
  MapPin,
  Hotel
} from 'lucide-vue-next'
import type { TravelPlan } from '../types/api'
import { downloadTravelScrapbook } from '../utils/travelScrapbook'
import type { ScrapbookVariant } from '../utils/travelScrapbook'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const exporting = ref(false)
const selectedVariant = ref<ScrapbookVariant>('story')

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '\u5206\u4eab',
      title: '\u65c5\u884c\u624b\u8d26',
      exportTitle: '\u5bfc\u51fa\u53ef\u5206\u4eab\u7248\u884c\u7a0b',
      exportBody: '\u628a\u5f53\u524d\u65b9\u6848\u6574\u7406\u6210\u53ef\u4fdd\u5b58\u3001\u53ef\u8f6c\u53d1\u3001\u53ef\u590d\u76d8\u7684\u65c5\u884c\u624b\u8d26\u3002',
      exportNote: '\u751f\u6210\u540e\u4f1a\u81ea\u52a8\u4e0b\u8f7d\u56fe\u7247',
      variantTitle: '\u9009\u62e9\u5bfc\u51fa\u6a21\u677f',
      coverageTitle: '\u8fd9\u6b21\u5bfc\u51fa\u4f1a\u5e26\u4e0a',
      exportingAction: '\u6b63\u5728\u751f\u6210\u624b\u8d26...',
      exportLabel: (name: string) => `\u5bfc\u51fa${name}`,
      stats: {
        days: (value: number) => `${value} \u5929\u884c\u7a0b`,
        stops: (value: number) => `${value} \u4e2a\u8282\u70b9`,
        hotels: (value: number) => `${value} \u4e2a\u4f4f\u5bbf\u5efa\u8bae`
      }
    }
  : {
      eyebrow: 'Share',
      title: 'Travel Scrapbook',
      exportTitle: 'Export A Shareable Itinerary',
      exportBody: 'Turn the current plan into a saveable travel artifact that is easier to send, revisit, and act on.',
      exportNote: 'The image downloads immediately after export.',
      variantTitle: 'Choose A Template',
      coverageTitle: 'This export will include',
      exportingAction: 'Building Scrapbook...',
      exportLabel: (name: string) => `Export ${name}`,
      stats: {
        days: (value: number) => `${value} day itinerary`,
        stops: (value: number) => `${value} stops`,
        hotels: (value: number) => `${value} stay options`
      }
    }))

const templateOptions = computed(() => (props.preferChinese
  ? [
      {
        id: 'story' as const,
        name: '\u5206\u4eab\u7248\u624b\u8d26',
        tag: '\u9002\u5408\u53d1\u7ed9\u540c\u884c',
        body: '\u66f4\u50cf\u4e00\u4efd\u53ef\u8f6c\u53d1\u7684\u65c5\u884c\u6545\u4e8b\uff0c\u4f1a\u7a81\u51fa\u91cd\u70b9\u3001\u8def\u7ebf\u3001\u9884\u7b97\u548c\u5b8c\u6574\u6bcf\u65e5\u884c\u7a0b\u3002',
        features: [
          '\u5b8c\u6574\u5c55\u5f00\u6bcf\u4e00\u5929\u884c\u7a0b',
          '\u5305\u542b\u8def\u7ebf\u8349\u56fe\u548c\u4f4f\u5bbf\u5efa\u8bae',
          '\u66f4\u9002\u5408\u8f6c\u53d1\u7ed9\u670b\u53cb\u6216\u7559\u6863'
        ]
      },
      {
        id: 'brief' as const,
        name: '\u6267\u884c\u7248\u624b\u8d26',
        tag: '\u9002\u5408\u51fa\u53d1\u524d\u81ea\u5df1\u8fc7\u4e00\u904d',
        body: '\u66f4\u504f\u51b3\u7b56\u548c\u6267\u884c\uff0c\u4f1a\u5148\u7ed9\u51fa\u51fa\u53d1\u524d\u8981\u9501\u5b9a\u7684\u4fe1\u606f\uff0c\u518d\u7ed9\u7d27\u51d1\u7248\u65e5\u7a0b\u3002',
        features: [
          '\u5148\u770b\u98ce\u9669\u3001\u9884\u7b97\u548c\u4f4f\u5bbf\u57fa\u70b9',
          '\u6bcf\u5929\u884c\u7a0b\u66f4\u7d27\u51d1\uff0c\u9002\u5408\u51fa\u53d1\u524d\u5feb\u901f\u626b\u4e00\u904d',
          '\u66f4\u9002\u5408\u81ea\u5df1\u7559\u5b58\u6216\u5b9e\u9645\u51fa\u884c'
        ]
      }
    ]
  : [
      {
        id: 'story' as const,
        name: 'Share Story',
        tag: 'Best for sending around',
        body: 'A narrative export that keeps the highlights, route, budget, and full itinerary in one shareable long-form card.',
        features: [
          'Full day-by-day coverage',
          'Route sketch and stay recommendations',
          'Better for forwarding to friends or saving as a record'
        ]
      },
      {
        id: 'brief' as const,
        name: 'Trip Brief',
        tag: 'Best for pre-trip review',
        body: 'A tighter execution-first export that surfaces the lock-ins, risks, and a compact run sheet before you leave.',
        features: [
          'Starts with lock-ins, risks, and budget signals',
          'More compact daily run sheet',
          'Better for your own departure checklist'
        ]
      }
    ]))

const activeTemplate = computed(() => templateOptions.value.find(option => option.id === selectedVariant.value) ?? templateOptions.value[0])

const coverageStats = computed(() => {
  if (!props.travelPlan) {
    return []
  }

  const totalStops = props.travelPlan.days.reduce((sum, day) => sum + day.stops.length, 0)
  return [
    copy.value.stats.days(props.travelPlan.days.length),
    copy.value.stats.stops(totalStops),
    copy.value.stats.hotels(Math.min(props.travelPlan.hotels.length, 3))
  ]
})

const primaryActionLabel = computed(() => (
  exporting.value
    ? copy.value.exportingAction
    : copy.value.exportLabel(activeTemplate.value.name)
))

async function exportScrapbook() {
  if (!props.travelPlan || exporting.value) {
    return
  }
  exporting.value = true
  try {
    await downloadTravelScrapbook(props.travelPlan, props.preferChinese, selectedVariant.value)
  } finally {
    exporting.value = false
  }
}

function getCoverageIcon(index: number) {
  return [Calendar, MapPin, Hotel][index] ?? Info
}
</script>

<template>
  <section v-if="travelPlan" class="panel plan-actions-panel">
    <div class="panel__header">
      <div class="panel__header-info">
        <div class="panel__icon-badge">
          <Share2 :size="18" />
        </div>
        <div>
          <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
          <h2>{{ copy.title }}</h2>
        </div>
      </div>
    </div>

    <article class="plan-actions-panel__card">
      <div class="plan-actions-panel__content">
        <div class="plan-actions-panel__export-header">
          <Download :size="20" class="plan-actions-panel__export-icon" />
          <div>
            <strong>{{ copy.exportTitle }}</strong>
            <p>{{ copy.exportBody }}</p>
          </div>
        </div>

        <div class="plan-actions-panel__section">
          <span class="plan-actions-panel__label">
            <ClipboardList :size="14" />
            {{ copy.variantTitle }}
          </span>
          <div class="plan-actions-panel__variants">
            <button
              v-for="option in templateOptions"
              :key="option.id"
              type="button"
              class="plan-actions-panel__variant"
              :class="{ 'plan-actions-panel__variant--active': option.id === selectedVariant }"
              @click="selectedVariant = option.id"
            >
              <div class="plan-actions-panel__variant-head">
                <component :is="option.id === 'story' ? FileText : ClipboardList" :size="14" />
                <strong>{{ option.name }}</strong>
                <em>{{ option.tag }}</em>
              </div>
              <p>{{ option.body }}</p>
            </button>
          </div>
        </div>

        <div class="plan-actions-panel__section">
          <span class="plan-actions-panel__label">
            <Info :size="14" />
            {{ copy.coverageTitle }}
          </span>
          <div class="plan-actions-panel__coverage">
            <span v-for="(stat, index) in coverageStats" :key="stat">
              <component :is="getCoverageIcon(index)" :size="12" />
              {{ stat }}
            </span>
          </div>
        </div>

        <div class="plan-actions-panel__features">
          <span v-for="feature in activeTemplate.features" :key="feature">
            <CheckCircle2 :size="12" />
            {{ feature }}
          </span>
        </div>
        <p class="plan-actions-panel__note">{{ copy.exportNote }}</p>
      </div>
      <button class="plan-actions-panel__primary" :disabled="exporting" @click="exportScrapbook">
        <Download :size="18" v-if="!exporting" />
        <div class="dot-loader" v-else></div>
        {{ primaryActionLabel }}
      </button>
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
  gap: 1rem;
  padding: 1.1rem;
  border-radius: 20px;
  border: 1px solid rgba(116, 70, 34, 0.12);
  background:
    radial-gradient(circle at top right, rgba(232, 93, 51, 0.18), transparent 34%),
    linear-gradient(145deg, rgba(255, 247, 236, 0.98), rgba(255, 241, 219, 0.94));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.plan-actions-panel__content {
  display: grid;
  gap: 0.55rem;
}

.plan-actions-panel__section {
  display: grid;
  gap: 0.55rem;
}

.plan-actions-panel__label {
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--muted);
}

.plan-actions-panel__variants {
  display: grid;
  gap: 0.75rem;
}

.plan-actions-panel__variant {
  display: grid;
  gap: 0.5rem;
  text-align: left;
  padding: 0.9rem;
  border-radius: 18px;
  border: 1px solid rgba(116, 70, 34, 0.12);
  background: rgba(255, 255, 255, 0.68);
  cursor: pointer;
  transition: transform 140ms ease, border-color 140ms ease, background 140ms ease;
}

.plan-actions-panel__variant:hover {
  transform: translateY(-1px);
}

.plan-actions-panel__variant--active {
  border-color: rgba(214, 98, 58, 0.28);
  background: rgba(255, 252, 247, 0.94);
  box-shadow: inset 0 0 0 1px rgba(214, 98, 58, 0.08);
}

.plan-actions-panel__variant-head {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
}

.plan-actions-panel__variant-head em {
  font-style: normal;
  font-size: 0.74rem;
  padding: 0.28rem 0.55rem;
  border-radius: 999px;
  background: rgba(214, 98, 58, 0.1);
  color: var(--accent-deep);
}

.plan-actions-panel__coverage {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
}

.plan-actions-panel__coverage span {
  display: inline-flex;
  padding: 0.4rem 0.7rem;
  border-radius: 999px;
  background: rgba(23, 48, 66, 0.06);
  color: var(--ink);
  font-size: 0.8rem;
}

.plan-actions-panel__features {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
}

.plan-actions-panel__features span {
  display: inline-flex;
  padding: 0.42rem 0.72rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(116, 70, 34, 0.12);
  color: var(--accent-deep);
  font-size: 0.8rem;
  line-height: 1.4;
}

.plan-actions-panel__card strong {
  font-size: 1rem;
}

.plan-actions-panel__card p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.plan-actions-panel__note {
  font-size: 0.86rem;
}

.plan-actions-panel__primary {
  border: none;
  border-radius: 16px;
  padding: 0.95rem 1rem;
  background: linear-gradient(135deg, var(--accent), #a93d1d);
  color: #fff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 18px 28px rgba(168, 74, 38, 0.22);
  transition: transform 140ms ease, box-shadow 140ms ease;
}

.plan-actions-panel__primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 20px 30px rgba(168, 74, 38, 0.28);
}

.plan-actions-panel__primary:disabled {
  cursor: wait;
  opacity: 0.78;
  transform: none;
  box-shadow: 0 10px 18px rgba(168, 74, 38, 0.16);
}
</style>
