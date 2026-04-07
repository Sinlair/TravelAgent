<script setup lang="ts">
import { computed, ref } from 'vue'
import PlanMap from './PlanMap.vue'
import type { TravelConstraintCheck, TravelPlan, TravelPlanStop, TravelTransitLeg } from '../types/api'
import { hotelPointId, stopPointId } from '../utils/travelPlan'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const activePointId = ref('')

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '方案',
      title: '这次出行建议',
      emptyTitle: '还没有生成结构化行程',
      emptyBody: '告诉我目的地、天数、预算和偏好后，我会把路线、住宿、花费和地图整理成一份可执行的安排。',
      overview: '行程总览',
      atGlance: '快速预览',
      hotels: '住宿建议',
      checks: '重点提醒',
      budget: '预算拆分',
      itinerary: '每日安排',
      totalCost: '预计总花费',
      hotelArea: '建议住宿区域',
      tripLength: '行程天数',
      currentWeather: '当前天气',
      locationCheck: '定位校验',
      matched: '匹配地点',
      district: '所在区域',
      coordinates: '坐标',
      amapAddress: '高德地址',
      otherCandidates: '其他候选',
      locationDetails: '查看定位细节',
      routeDetails: '查看详细路线',
      locationVerified: '位置已确认',
      fallbackHint: '当前为估算结果，建议出发前再次确认',
      arrivalRoute: '上一站前往',
      returnLabel: '返程',
      timeWindow: (start: string, end: string) => `${start} - ${end}`,
      day: (value: number) => `第 ${value} 天`,
      dayCount: (value: number) => `${value} 天`,
      stopCount: (value: number) => `${value} 个安排`,
      activityMinutes: (value: number) => `游玩 ${value} 分钟`,
      transitMinutes: (value: number) => `通勤 ${value} 分钟`,
      stopDuration: (value: number) => `停留 ${value} 分钟`,
      transferMinutes: (value: number) => `上段通勤 ${value} 分钟`,
      costValue: (value: number) => `约 ${value} 元`,
      nightly: (min: number, max: number) => `${min}-${max} 元/晚`,
      amount: (min: number, max: number) => `${min}-${max} 元`,
      ticket: (value: number) => `门票 ${value} 元`,
      food: (value: number) => `餐饮 ${value} 元`,
      localTransit: (value: number) => `市内交通 ${value} 元`,
      other: (value: number) => `其他 ${value} 元`,
      openWindow: (open: string, close: string) => `开放时间 ${open} - ${close}`,
      routeMeta: (route: TravelTransitLeg) => `全程约 ${route.durationMinutes} 分钟，步行 ${route.walkingMinutes} 分钟，预计 ${route.estimatedCost} 元`,
      budgetCap: (value: number) => `预算上限 ${value} 元`
    }
  : {
      eyebrow: 'Plan',
      title: 'Suggested Plan',
      emptyTitle: 'No structured itinerary yet',
      emptyBody: 'Share the destination, trip length, budget, and preferences and I will turn them into routes, stays, costs, and a map.',
      overview: 'Overview',
      atGlance: 'At A Glance',
      hotels: 'Stays',
      checks: 'Key Checks',
      budget: 'Budget',
      itinerary: 'Daily Itinerary',
      totalCost: 'Estimated Total',
      hotelArea: 'Recommended Stay Area',
      tripLength: 'Trip Length',
      currentWeather: 'Current Weather',
      locationCheck: 'Location Check',
      matched: 'Matched',
      district: 'District',
      coordinates: 'Coordinates',
      amapAddress: 'Amap Address',
      otherCandidates: 'Other candidates',
      locationDetails: 'View location details',
      routeDetails: 'View route details',
      locationVerified: 'Location verified',
      fallbackHint: 'Estimated result, please verify before departure',
      arrivalRoute: 'Route from previous stop',
      returnLabel: 'Return',
      timeWindow: (start: string, end: string) => `${start} - ${end}`,
      day: (value: number) => `Day ${value}`,
      dayCount: (value: number) => `${value} days`,
      stopCount: (value: number) => `${value} stops`,
      activityMinutes: (value: number) => `Activity ${value} min`,
      transitMinutes: (value: number) => `Transit ${value} min`,
      stopDuration: (value: number) => `Stay ${value} min`,
      transferMinutes: (value: number) => `Transfer ${value} min`,
      costValue: (value: number) => `About ${value} CNY`,
      nightly: (min: number, max: number) => `${min}-${max} CNY/night`,
      amount: (min: number, max: number) => `${min}-${max} CNY`,
      ticket: (value: number) => `Ticket ${value} CNY`,
      food: (value: number) => `Food ${value} CNY`,
      localTransit: (value: number) => `Transit ${value} CNY`,
      other: (value: number) => `Other ${value} CNY`,
      openWindow: (open: string, close: string) => `Open ${open} - ${close}`,
      routeMeta: (route: TravelTransitLeg) => `Around ${route.durationMinutes} min total, ${route.walkingMinutes} min walking, about ${route.estimatedCost} CNY`,
      budgetCap: (value: number) => `Budget cap ${value} CNY`
    }))

const visibleHotels = computed(() => props.travelPlan?.hotels.slice(0, 3) ?? [])
const visibleChecks = computed(() => props.travelPlan?.checks ?? [])
const visibleBudget = computed(() => props.travelPlan?.budget ?? [])
const visibleDays = computed(() => props.travelPlan?.days ?? [])

const overviewStats = computed(() => {
  if (!props.travelPlan) {
    return []
  }

  const trip = props.travelPlan
  const stats = [
    {
      label: copy.value.totalCost,
      value: copy.value.amount(trip.estimatedTotalMin, trip.estimatedTotalMax),
      hint: trip.totalBudget ? copy.value.budgetCap(trip.totalBudget) : ''
    },
    {
      label: copy.value.hotelArea,
      value: trip.hotelArea,
      hint: trip.hotels[0]?.name || trip.hotelAreaReason
    },
    {
      label: copy.value.tripLength,
      value: copy.value.dayCount(trip.days.length),
      hint: trip.days.map(day => day.theme).join(' / ')
    }
  ]

  const weatherParts = [
    trip.weatherSnapshot?.description,
    trip.weatherSnapshot?.temperature ? `${trip.weatherSnapshot.temperature} C` : ''
  ].filter(Boolean)

  if (weatherParts.length) {
    stats.push({
      label: copy.value.currentWeather,
      value: weatherParts.join(' · '),
      hint: trip.weatherSnapshot?.city || trip.weatherSnapshot?.reportTime || ''
    })
  }

  return stats
})

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
    'Attractions and buffer': '景点与缓冲'
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

function stepMeta(step: TravelTransitLeg['steps'][number]) {
  if (props.preferChinese) {
    return `${modeLabel(step.mode)}，约 ${step.durationMinutes} 分钟${step.stopCount ? `，共 ${step.stopCount} 站` : ''}`
  }
  return `${modeLabel(step.mode)}, about ${step.durationMinutes} min${step.stopCount ? `, ${step.stopCount} stops` : ''}`
}

function statusLabel(check: TravelConstraintCheck) {
  if (!props.preferChinese) {
    return {
      PASS: 'Good',
      WARN: 'Heads up',
      FAIL: 'Conflict'
    }[check.status] ?? check.status
  }
  return {
    PASS: '通过',
    WARN: '提醒',
    FAIL: '冲突'
  }[check.status] ?? check.status
}

function locationStatus(source?: string) {
  if (!source) {
    return copy.value.locationVerified
  }
  if (!props.preferChinese) {
    return {
      'MCP.amap_input_tips': 'Verified with Amap suggestions',
      'MCP.amap_geocode': 'Confirmed with Amap geocode',
      'MCP.amap_transit_route': 'Route verified with Amap',
      'RULE.fallback': copy.value.fallbackHint
    }[source] ?? source
  }
  return {
    'MCP.amap_input_tips': '已用高德候选结果确认地点',
    'MCP.amap_geocode': '已用高德确认位置',
    'MCP.amap_transit_route': '路线来自高德公交或地铁规划',
    'RULE.fallback': copy.value.fallbackHint
  }[source] ?? `已校验：${source}`
}

function coordinateText(longitude?: string, latitude?: string) {
  if (!longitude || !latitude) {
    return ''
  }
  return `${longitude}, ${latitude}`
}

function stopTotalCost(stop: TravelPlanStop) {
  const breakdown = stop.costBreakdown
  if (!breakdown) {
    return stop.estimatedCost
  }
  return breakdown.ticketCost + breakdown.foodCost + breakdown.localTransitCost + breakdown.otherCost
}

function activateHotel(index: number) {
  activePointId.value = hotelPointId(index)
}

function activateStop(dayNumber: number, stop: TravelPlanStop) {
  activePointId.value = stopPointId(dayNumber, stop.slot, stop.name)
}
</script>

<template>
  <section class="panel plan-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
        <h2>{{ copy.title }}</h2>
      </div>
    </div>

    <div v-if="!travelPlan" class="plan-empty">
      <h3>{{ copy.emptyTitle }}</h3>
      <p>{{ copy.emptyBody }}</p>
    </div>

    <template v-else>
      <article class="overview-card">
        <div class="overview-card__content">
          <p class="overview-card__eyebrow">{{ copy.overview }}</p>
          <h3>{{ travelPlan.summary }}</h3>
          <p class="overview-card__body">{{ travelPlan.hotelAreaReason }}</p>
        </div>

        <div class="overview-card__stats">
          <article v-for="stat in overviewStats" :key="stat.label" class="overview-stat">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
            <p v-if="stat.hint">{{ stat.hint }}</p>
          </article>
        </div>

        <div v-if="travelPlan.highlights.length" class="plan-highlights">
          <span v-for="item in travelPlan.highlights" :key="item">{{ item }}</span>
        </div>
      </article>

      <div v-if="visibleDays.length" class="plan-section">
        <div class="plan-section__title">{{ copy.atGlance }}</div>
        <div class="glance-grid">
          <article v-for="day in visibleDays" :key="day.dayNumber" class="glance-card">
            <span>{{ copy.day(day.dayNumber) }}</span>
            <strong>{{ day.theme }}</strong>
            <p>{{ copy.timeWindow(day.startTime, day.endTime) }}</p>
            <div class="glance-card__meta">
              <span>{{ copy.stopCount(day.stops.length) }}</span>
              <span>{{ copy.activityMinutes(day.totalActivityMinutes) }}</span>
              <span>{{ copy.transitMinutes(day.totalTransitMinutes) }}</span>
              <span>{{ copy.costValue(day.estimatedCost) }}</span>
            </div>
          </article>
        </div>
      </div>

      <PlanMap
        :travel-plan="travelPlan"
        :prefer-chinese="preferChinese"
        :active-point-id="activePointId"
        @select-point="activePointId = $event"
      />

      <div class="dashboard-grid">
        <section v-if="visibleHotels.length" class="plan-section">
          <div class="plan-section__title">{{ copy.hotels }}</div>
          <div class="stay-grid">
            <article
              v-for="(hotel, index) in visibleHotels"
              :key="hotel.name"
              class="stay-card"
              @mouseenter="activateHotel(index + 1)"
            >
              <div class="stay-card__head">
                <div>
                  <strong>{{ hotel.name }}</strong>
                  <p>{{ hotel.area }}</p>
                </div>
                <span class="stay-card__price">{{ copy.nightly(hotel.nightlyMin, hotel.nightlyMax) }}</span>
              </div>
              <p>{{ hotel.rationale }}</p>
              <p class="stay-card__status">{{ locationStatus(hotel.source) }}</p>
              <details v-if="hotel.longitude && hotel.latitude" class="stay-card__details">
                <summary>{{ copy.locationDetails }}</summary>
                <div class="details-pills">
                  <span>{{ coordinateText(hotel.longitude, hotel.latitude) }}</span>
                  <span>{{ hotel.address }}</span>
                </div>
              </details>
            </article>
          </div>
        </section>

        <section v-if="visibleBudget.length" class="plan-section">
          <div class="plan-section__title">{{ copy.budget }}</div>
          <div class="budget-grid">
            <article v-for="item in visibleBudget" :key="item.category" class="budget-card">
              <div>
                <strong>{{ categoryLabel(item.category) }}</strong>
                <p>{{ item.rationale }}</p>
              </div>
              <span>{{ copy.amount(item.minAmount, item.maxAmount) }}</span>
            </article>
          </div>
        </section>
      </div>

      <div v-if="visibleChecks.length" class="plan-section">
        <div class="plan-section__title">{{ copy.checks }}</div>
        <div class="checks-grid">
          <article
            v-for="check in visibleChecks"
            :key="check.code"
            class="check-card"
            :class="`check-card--${check.status.toLowerCase()}`"
          >
            <span>{{ statusLabel(check) }}</span>
            <strong>{{ check.message }}</strong>
          </article>
        </div>
      </div>

      <div v-if="visibleDays.length" class="plan-section">
        <div class="plan-section__title">{{ copy.itinerary }}</div>
        <div class="itinerary-list">
          <article v-for="day in visibleDays" :key="day.dayNumber" class="itinerary-day">
            <div class="itinerary-day__header">
              <div>
                <p class="itinerary-day__eyebrow">{{ copy.day(day.dayNumber) }}</p>
                <h4>{{ day.theme }}</h4>
                <p class="itinerary-day__window">{{ copy.timeWindow(day.startTime, day.endTime) }}</p>
              </div>
              <div class="itinerary-day__meta">
                <span>{{ copy.activityMinutes(day.totalActivityMinutes) }}</span>
                <span>{{ copy.transitMinutes(day.totalTransitMinutes) }}</span>
                <span>{{ copy.costValue(day.estimatedCost) }}</span>
              </div>
            </div>

            <div class="itinerary-stops">
              <article
                v-for="stop in day.stops"
                :key="`${day.dayNumber}-${stop.slot}-${stop.name}`"
                class="itinerary-stop"
                @mouseenter="activateStop(day.dayNumber, stop)"
              >
                <div class="itinerary-stop__time">
                  <strong>{{ stop.startTime }}</strong>
                  <span>{{ stop.endTime }}</span>
                  <em>{{ slotLabel(stop.slot) }}</em>
                </div>

                <div class="itinerary-stop__content">
                  <div class="itinerary-stop__head">
                    <div>
                      <strong>{{ stop.name }}</strong>
                      <p>{{ stop.area }}</p>
                    </div>
                    <span class="itinerary-stop__price">{{ copy.costValue(stopTotalCost(stop)) }}</span>
                  </div>

                  <p>{{ stop.rationale }}</p>

                  <div class="itinerary-stop__chips">
                    <span>{{ copy.stopDuration(stop.durationMinutes) }}</span>
                    <span v-if="stop.transitMinutesFromPrevious">{{ copy.transferMinutes(stop.transitMinutesFromPrevious) }}</span>
                    <span>{{ copy.openWindow(stop.openTime, stop.closeTime) }}</span>
                  </div>

                  <div v-if="stop.costBreakdown" class="cost-pills">
                    <span>{{ copy.ticket(stop.costBreakdown.ticketCost) }}</span>
                    <span>{{ copy.food(stop.costBreakdown.foodCost) }}</span>
                    <span>{{ copy.localTransit(stop.costBreakdown.localTransitCost) }}</span>
                    <span>{{ copy.other(stop.costBreakdown.otherCost) }}</span>
                  </div>

                  <div v-if="stop.routeFromPrevious" class="route-card">
                    <span>{{ copy.arrivalRoute }}</span>
                    <strong>{{ routeLine(stop.routeFromPrevious) || stop.routeFromPrevious.summary }}</strong>
                    <p>{{ copy.routeMeta(stop.routeFromPrevious) }}</p>
                    <details class="route-card__details">
                      <summary>{{ copy.routeDetails }}</summary>
                      <div class="route-steps">
                        <article
                          v-for="step in stop.routeFromPrevious.steps"
                          :key="`${step.mode}-${step.lineName}-${step.fromName}-${step.toName}`"
                          class="route-step"
                        >
                          <strong>{{ step.title || step.instruction }}</strong>
                          <p>{{ step.instruction }}</p>
                          <span>{{ stepMeta(step) }}</span>
                        </article>
                      </div>
                    </details>
                  </div>

                  <details v-if="stop.poiMatch || (stop.longitude && stop.latitude)" class="location-card">
                    <summary>{{ copy.locationDetails }}</summary>
                    <p class="location-card__status">{{ locationStatus(stop.poiMatch?.source) }}</p>
                    <div class="details-pills">
                      <span v-if="stop.poiMatch?.matchedName">{{ copy.matched }}: {{ stop.poiMatch.matchedName }}</span>
                      <span v-if="stop.poiMatch?.district">{{ copy.district }}: {{ stop.poiMatch.district }}</span>
                      <span v-if="stop.longitude && stop.latitude">{{ copy.coordinates }}: {{ coordinateText(stop.longitude, stop.latitude) }}</span>
                    </div>
                    <p v-if="stop.poiMatch?.address" class="location-card__note">{{ copy.amapAddress }}: {{ stop.poiMatch.address }}</p>
                    <p v-if="(stop.poiMatch?.candidateNames?.length ?? 0) > 1" class="location-card__note">
                      {{ copy.otherCandidates }}: {{ stop.poiMatch?.candidateNames?.join(' / ') }}
                    </p>
                  </details>
                </div>
              </article>

              <article v-if="day.returnToHotel" class="itinerary-stop itinerary-stop--return">
                <div class="itinerary-stop__time">
                  <strong>{{ copy.returnLabel }}</strong>
                  <span>{{ day.endTime }}</span>
                </div>
                <div class="itinerary-stop__content">
                  <div class="itinerary-stop__head">
                    <div>
                      <strong>{{ day.returnToHotel.toName }}</strong>
                      <p>{{ routeLine(day.returnToHotel) || day.returnToHotel.summary }}</p>
                    </div>
                  </div>
                  <div class="itinerary-stop__chips">
                    <span>{{ copy.routeMeta(day.returnToHotel) }}</span>
                  </div>
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
.plan-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: auto;
}

.overview-card,
.glance-card,
.stay-card,
.budget-card,
.check-card,
.itinerary-day,
.itinerary-stop,
.route-step {
  border-radius: 18px;
  border: 1px solid rgba(24, 50, 74, 0.08);
  background: rgba(255, 255, 255, 0.8);
}

.overview-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(244, 250, 255, 0.88));
}

.overview-card__eyebrow,
.itinerary-day__eyebrow {
  margin: 0 0 8px;
  font-size: 0.78rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #165b97;
}

.overview-card__content h3,
.itinerary-day h4 {
  margin: 0;
  line-height: 1.35;
}

.overview-card__body,
.glance-card p,
.stay-card p,
.budget-card p,
.route-card p,
.route-step p,
.location-card__note,
.itinerary-day__window,
.itinerary-stop__head p,
.itinerary-stop__content > p {
  margin: 0;
  color: #61798b;
  line-height: 1.55;
}

.overview-card__stats,
.glance-grid,
.dashboard-grid,
.checks-grid {
  display: grid;
  gap: 10px;
}

.overview-card__stats {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.overview-stat {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
}

.overview-stat span,
.stay-card__status,
.location-card__status {
  display: block;
  color: #61798b;
  font-size: 0.82rem;
}

.overview-stat strong {
  display: block;
  margin-top: 6px;
  font-size: 1rem;
}

.overview-stat p {
  margin: 6px 0 0;
  color: #61798b;
  font-size: 0.82rem;
  line-height: 1.5;
}

.glance-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.glance-card {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.glance-card > span {
  color: #165b97;
  font-size: 0.8rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.glance-card strong {
  display: block;
  font-size: 1rem;
}

.glance-card__meta,
.itinerary-day__meta,
.itinerary-stop__chips,
.cost-pills,
.details-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.glance-card__meta span,
.itinerary-day__meta span,
.itinerary-stop__chips span,
.cost-pills span,
.details-pills span {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(47, 134, 255, 0.08);
  color: #165b97;
  font-size: 0.78rem;
}

.dashboard-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}

.stay-grid,
.budget-grid,
.itinerary-list,
.itinerary-stops,
.route-steps {
  display: grid;
  gap: 10px;
}

.stay-card,
.budget-card {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.stay-card__head,
.budget-card,
.itinerary-day__header,
.itinerary-stop__head {
  display: grid;
  gap: 12px;
}

.stay-card__head,
.budget-card,
.itinerary-day__header {
  grid-template-columns: 1fr auto;
}

.stay-card__head strong,
.budget-card strong,
.check-card strong,
.itinerary-stop__head strong,
.route-card strong,
.route-step strong {
  display: block;
}

.stay-card__price,
.budget-card > span,
.itinerary-stop__price {
  color: #165b97;
  font-weight: 700;
}

.stay-card__details,
.route-card__details,
.location-card {
  display: grid;
  gap: 8px;
}

.stay-card__details summary,
.route-card__details summary,
.location-card summary {
  cursor: pointer;
  color: #0b8c87;
  font-size: 0.88rem;
}

.checks-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.check-card {
  display: grid;
  gap: 8px;
  padding: 12px;
}

.check-card span {
  color: #165b97;
  font-size: 0.8rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.check-card--pass {
  border-color: rgba(11, 140, 135, 0.24);
}

.check-card--warn {
  border-color: rgba(255, 180, 84, 0.34);
}

.check-card--fail {
  border-color: rgba(176, 52, 52, 0.3);
}

.itinerary-day {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.itinerary-day__window {
  margin-top: 6px;
}

.itinerary-day__meta {
  justify-content: flex-end;
  align-content: start;
}

.itinerary-stop {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 12px;
  padding: 12px;
}

.itinerary-stop__time {
  display: grid;
  gap: 4px;
  align-content: start;
  font-family: "IBM Plex Mono", "JetBrains Mono", monospace;
}

.itinerary-stop__time strong {
  font-size: 1rem;
  color: #18324a;
}

.itinerary-stop__time span,
.itinerary-stop__time em {
  color: #61798b;
  font-style: normal;
  font-size: 0.78rem;
}

.itinerary-stop__content,
.route-card {
  display: grid;
  gap: 8px;
}

.itinerary-stop__head {
  grid-template-columns: 1fr auto;
  align-items: start;
}

.route-card {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(11, 140, 135, 0.14);
  background: rgba(11, 140, 135, 0.06);
}

.route-card > span {
  color: #165b97;
  font-size: 0.82rem;
}

.route-step {
  padding: 10px 12px;
}

.route-step span {
  display: block;
  margin-top: 6px;
  color: #165b97;
  font-size: 0.82rem;
}

.location-card {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(47, 134, 255, 0.14);
  background: rgba(47, 134, 255, 0.05);
}

.itinerary-stop--return {
  border-style: dashed;
  background: rgba(247, 251, 255, 0.9);
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .stay-card__head,
  .budget-card,
  .itinerary-day__header,
  .itinerary-stop__head,
  .itinerary-stop {
    grid-template-columns: 1fr;
  }

  .itinerary-day__meta {
    justify-content: flex-start;
  }
}
</style>
