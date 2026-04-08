<script setup lang="ts">
import { computed, ref } from 'vue'
import PlanMap from './PlanMap.vue'
import type { TravelConstraintCheck, TravelPlan, TravelPlanStop, TravelTransitLeg } from '../types/api'
import { hotelPointId, stopPointId } from '../utils/travelPlan'
import { normalizeDisplayText } from '../utils/text'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const activePointId = ref('')

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '\u65b9\u6848',
      title: '\u8fd9\u6b21\u51fa\u884c\u5efa\u8bae',
      emptyTitle: '\u8fd8\u6ca1\u6709\u751f\u6210\u7ed3\u6784\u5316\u884c\u7a0b',
      emptyBody: '\u544a\u8bc9\u6211\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u548c\u504f\u597d\u540e\uff0c\u6211\u4f1a\u628a\u8def\u7ebf\u3001\u4f4f\u5bbf\u3001\u82b1\u8d39\u548c\u5730\u56fe\u6574\u7406\u6210\u4e00\u4efd\u53ef\u6267\u884c\u7684\u5b89\u6392\u3002',
      overview: '\u884c\u7a0b\u603b\u89c8',
      atGlance: '\u5feb\u901f\u9884\u89c8',
      hotels: '\u4f4f\u5bbf\u5efa\u8bae',
      checks: '\u91cd\u70b9\u63d0\u9192',
      budget: '\u9884\u7b97\u62c6\u5206',
      itinerary: '\u6bcf\u65e5\u5b89\u6392',
      totalCost: '\u9884\u8ba1\u603b\u82b1\u8d39',
      hotelArea: '\u5efa\u8bae\u4f4f\u5bbf\u533a\u57df',
      tripLength: '\u884c\u7a0b\u5929\u6570',
      currentWeather: '\u5f53\u524d\u5929\u6c14',
      matched: '\u5339\u914d\u5730\u70b9',
      district: '\u6240\u5728\u533a\u57df',
      coordinates: '\u5750\u6807',
      amapAddress: '\u9ad8\u5fb7\u5730\u5740',
      otherCandidates: '\u5176\u4ed6\u5019\u9009',
      locationDetails: '\u67e5\u770b\u5b9a\u4f4d\u7ec6\u8282',
      routeDetails: '\u67e5\u770b\u8be6\u7ec6\u8def\u7ebf',
      locationVerified: '\u4f4d\u7f6e\u5df2\u786e\u8ba4',
      fallbackHint: '\u5f53\u524d\u4e3a\u4f30\u7b97\u7ed3\u679c\uff0c\u5efa\u8bae\u51fa\u53d1\u524d\u518d\u6b21\u786e\u8ba4',
      arrivalRoute: '\u4e0a\u4e00\u7ad9\u524d\u5f80',
      returnLabel: '\u8fd4\u7a0b',
      timeWindow: (start: string, end: string) => `${start} - ${end}`,
      day: (value: number) => `\u7b2c ${value} \u5929`,
      dayCount: (value: number) => `${value} \u5929`,
      stopCount: (value: number) => `${value} \u4e2a\u5b89\u6392`,
      activityMinutes: (value: number) => `\u6e38\u73a9 ${value} \u5206\u949f`,
      transitMinutes: (value: number) => `\u901a\u52e4 ${value} \u5206\u949f`,
      stopDuration: (value: number) => `\u505c\u7559 ${value} \u5206\u949f`,
      transferMinutes: (value: number) => `\u4e0a\u6bb5\u901a\u52e4 ${value} \u5206\u949f`,
      costValue: (value: number) => `\u7ea6 ${value} \u5143`,
      nightly: (min: number, max: number) => `${min}-${max} \u5143/\u665a`,
      amount: (min: number, max: number) => `${min}-${max} \u5143`,
      ticket: (value: number) => `\u95e8\u7968 ${value} \u5143`,
      food: (value: number) => `\u9910\u996e ${value} \u5143`,
      localTransit: (value: number) => `\u5e02\u5185\u4ea4\u901a ${value} \u5143`,
      other: (value: number) => `\u5176\u4ed6 ${value} \u5143`,
      openWindow: (open: string, close: string) => `\u5f00\u653e\u65f6\u95f4 ${open} - ${close}`,
      routeMeta: (route: TravelTransitLeg) => `\u5168\u7a0b\u7ea6 ${route.durationMinutes} \u5206\u949f\uff0c\u6b65\u884c ${route.walkingMinutes} \u5206\u949f\uff0c\u9884\u8ba1 ${route.estimatedCost} \u5143`,
      budgetCap: (value: number) => `\u9884\u7b97\u4e0a\u9650 ${value} \u5143`
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
      value: weatherParts.join(' / '),
      hint: trip.weatherSnapshot?.city || trip.weatherSnapshot?.reportTime || ''
    })
  }

  return stats
})

function slotLabel(slot: string) {
  if (!props.preferChinese) {
    return { MORNING: 'Morning', AFTERNOON: 'Afternoon', EVENING: 'Evening' }[slot] ?? slot
  }
  return {
    MORNING: '\u4e0a\u5348',
    AFTERNOON: '\u4e0b\u5348',
    EVENING: '\u665a\u4e0a'
  }[slot] ?? slot
}

function categoryLabel(category: string) {
  if (!props.preferChinese) {
    return category
  }
  return {
    Hotel: '\u4f4f\u5bbf',
    'Intercity transport': '\u8de8\u57ce\u4ea4\u901a',
    'Local transit': '\u672c\u5730\u901a\u52e4',
    Food: '\u9910\u996e',
    'Attractions and buffer': '\u666f\u70b9\u4e0e\u673a\u52a8\u9884\u7b97'
  }[category] ?? category
}

function modeLabel(mode: string) {
  if (!props.preferChinese) {
    return mode
  }
  return {
    SUBWAY: '\u5730\u94c1',
    BUS: '\u516c\u4ea4',
    WALK: '\u6b65\u884c',
    TAXI: '\u6253\u8f66',
    RAIL: '\u94c1\u8def'
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
    return `${modeLabel(step.mode)}\uff0c\u7ea6 ${step.durationMinutes} \u5206\u949f${step.stopCount ? `\uff0c\u5171 ${step.stopCount} \u7ad9` : ''}`
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
    PASS: '\u901a\u8fc7',
    WARN: '\u63d0\u9192',
    FAIL: '\u51b2\u7a81'
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
    'MCP.amap_input_tips': '\u5df2\u7528\u9ad8\u5fb7\u5019\u9009\u7ed3\u679c\u786e\u8ba4\u5730\u70b9',
    'MCP.amap_geocode': '\u5df2\u7528\u9ad8\u5fb7\u786e\u8ba4\u4f4d\u7f6e',
    'MCP.amap_transit_route': '\u8def\u7ebf\u6765\u81ea\u9ad8\u5fb7\u516c\u4ea4\u6216\u5730\u94c1\u89c4\u5212',
    'RULE.fallback': copy.value.fallbackHint
  }[source] ?? `\u5df2\u6821\u9a8c\uff1a${source}`
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
          <h3>{{ normalizeDisplayText(travelPlan.summary) }}</h3>
          <p class="overview-card__body">{{ normalizeDisplayText(travelPlan.hotelAreaReason) }}</p>
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
            <strong>{{ normalizeDisplayText(day.theme) }}</strong>
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
                  <strong>{{ normalizeDisplayText(hotel.name) }}</strong>
                  <p>{{ normalizeDisplayText(hotel.area) }}</p>
                </div>
                <span class="stay-card__price">{{ copy.nightly(hotel.nightlyMin, hotel.nightlyMax) }}</span>
              </div>
              <p>{{ normalizeDisplayText(hotel.rationale) }}</p>
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
                <p>{{ normalizeDisplayText(item.rationale) }}</p>
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
            <strong>{{ normalizeDisplayText(check.message) }}</strong>
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
                <h4>{{ normalizeDisplayText(day.theme) }}</h4>
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
                      <strong>{{ normalizeDisplayText(stop.name) }}</strong>
                      <p>{{ normalizeDisplayText(stop.area) }}</p>
                    </div>
                    <span class="itinerary-stop__price">{{ copy.costValue(stopTotalCost(stop)) }}</span>
                  </div>

                  <p>{{ normalizeDisplayText(stop.rationale) }}</p>

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
