<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import {
  Map as MapIcon,
  Navigation,
  Hotel,
  Wallet,
  CheckCircle2,
  Calendar,
  Layout,
  ChevronRight,
  Info,
  Thermometer,
  CloudSun,
  Clock,
  ArrowRight,
  AlertCircle,
  AlertTriangle,
  Zap,
  Star,
  MapPin
} from 'lucide-vue-next'
import PlanMap from './PlanMap.vue'
import type { TravelConstraintCheck, TravelPlan, TravelPlanStop, TravelTransitLeg } from '../types/api'
import type { ConversationResultViewModel } from '../utils/conversationResult'
import { hotelPointId, stopPointId } from '../utils/travelPlan'
import { normalizeDisplayText } from '../utils/text'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  resultView?: ConversationResultViewModel
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const activePointId = ref('')
type SectionId = 'overview' | 'decisions' | 'glance' | 'map' | 'stays' | 'budget' | 'checks' | 'itinerary'
const sectionRefs = ref<Record<SectionId, HTMLElement | null>>({
  overview: null,
  decisions: null,
  glance: null,
  map: null,
  stays: null,
  budget: null,
  checks: null,
  itinerary: null
})
const panelState = computed(() => props.resultView?.planState ?? (props.travelPlan ? 'success' : 'empty'))
const mapState = computed(() => props.resultView?.mapState ?? (props.travelPlan ? 'success' : 'empty'))

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '\u65b9\u6848',
      title: '\u8fd9\u6b21\u51fa\u884c\u5efa\u8bae',
      emptyTitle: '\u8fd8\u6ca1\u6709\u751f\u6210\u7ed3\u6784\u5316\u884c\u7a0b',
      emptyBody: '\u544a\u8bc9\u6211\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u548c\u504f\u597d\u540e\uff0c\u6211\u4f1a\u628a\u8def\u7ebf\u3001\u4f4f\u5bbf\u3001\u82b1\u8d39\u548c\u5730\u56fe\u6574\u7406\u6210\u4e00\u4efd\u53ef\u6267\u884c\u7684\u5b89\u6392\u3002',
      quickNav: '\u4f18\u5148\u67e5\u770b',
      recommended: '\u4f18\u5148',
      overview: '\u884c\u7a0b\u603b\u89c8',
      decisionSummary: '\u51b3\u7b56\u6458\u8981',
      nextSteps: '\u5efa\u8bae\u4e0b\u4e00\u6b65',
      atGlance: '\u5feb\u901f\u9884\u89c8',
      hotels: '\u4f4f\u5bbf\u5efa\u8bae',
      checks: '\u51fa\u53d1\u524d\u5148\u770b',
      budget: '\u9884\u7b97\u62c6\u5206',
      itinerary: '\u6bcf\u65e5\u5b89\u6392',
      totalCost: '\u9884\u8ba1\u603b\u82b1\u8d39',
      hotelArea: '\u5efa\u8bae\u4f4f\u5bbf\u533a\u57df',
      tripLength: '\u884c\u7a0b\u5929\u6570',
      currentWeather: '\u5f53\u524d\u5929\u6c14',
      estimateMode: '\u65e5\u671f\u8fd8\u672a\u9501\u5b9a\uff0c\u5f53\u524d\u6309\u4f30\u7b97\u6a21\u5f0f\u5c55\u793a',
      matched: '\u5339\u914d\u5730\u70b9',
      district: '\u6240\u5728\u533a\u57df',
      coordinates: '\u5750\u6807',
      amapAddress: '\u9ad8\u5fb7\u5730\u5740',
      otherCandidates: '\u5176\u4ed6\u5019\u9009',
      locationDetails: '\u67e5\u770b\u5b9a\u4f4d\u7ec6\u8282',
      routeDetails: '\u67e5\u770b\u8be6\u7ec6\u8def\u7ebf',
      locationVerified: '\u4f4d\u7f6e\u5df2\u786e\u8ba4',
      bookNow: '\u7acb\u5373\u9884\u8ba2',
      fallbackHint: '\u5f53\u524d\u4e3a\u4f30\u7b97\u7ed3\u679c\uff0c\u5efa\u8bae\u51fa\u53d1\u524d\u56de\u5230\u9ad8\u5fb7\u5730\u56fe\u786e\u8ba4',
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
      budgetCap: (value: number) => `\u9884\u7b97\u4e0a\u9650 ${value} \u5143`,
      readiness: '\u6267\u884c\u628a\u63e1',
      budgetFit: '\u9884\u7b97\u5339\u914d',
      paceFit: '\u8282\u594f\u5f3a\u5ea6',
      groundingFit: '\u5730\u70b9\u53ef\u9760\u5ea6',
      readyValue: '\u53ef\u4ee5\u76f4\u63a5\u7ee7\u7eed',
      watchValue: '\u6574\u4f53\u53ef\u7528\uff0c\u4f46\u9700\u8981\u7559\u610f',
      adjustValue: '\u5efa\u8bae\u5148\u8c03\u6574\u518d\u51fa\u53d1',
      readyHint: '\u6ca1\u6709\u660e\u663e\u51b2\u7a81\uff0c\u53ef\u4ee5\u8fdb\u5165\u9152\u5e97\u6216\u95e8\u7968\u786e\u8ba4',
      watchHint: '\u65b9\u6848\u5df2\u6210\u578b\uff0c\u4f46\u8fd8\u6709\u4e00\u4e9b\u63d0\u9192\u503c\u5f97\u5904\u7406',
      adjustHint: '\u5b58\u5728\u8d85\u9884\u7b97\u6216\u65f6\u95f4\u51b2\u7a81\uff0c\u5efa\u8bae\u5148\u4fee\u6b63',
      budgetGood: '\u9884\u7b97\u53ef\u63a7',
      budgetOver: '\u9884\u7b97\u504f\u9ad8',
      budgetOpen: '\u7f3a\u5c11\u9884\u7b97\u4e0a\u9650',
      budgetGoodHint: (value: number) => `\u8ddd\u79bb\u4e0a\u9650\u8fd8\u6709 ${value} \u5143\u7a7a\u95f4`,
      budgetOverHint: (value: number) => `\u9884\u8ba1\u8d85\u51fa ${value} \u5143`,
      budgetOpenHint: '\u8865\u4e00\u4e2a\u9884\u7b97\u4e0a\u9650\uff0c\u66f4\u5bb9\u6613\u53d6\u820d',
      paceRelaxed: '\u8282\u594f\u8f7b\u677e',
      paceBalanced: '\u8282\u594f\u5747\u8861',
      paceTight: '\u884c\u7a0b\u504f\u6ee1',
      paceRelaxedHint: '\u9002\u5408\u8fb9\u901b\u8fb9\u5403\uff0c\u4e34\u573a\u8c03\u6574\u4f59\u5730\u66f4\u5927',
      paceBalancedHint: '\u9002\u5408\u5927\u591a\u6570\u57ce\u5e02\u77ed\u9014\u51fa\u884c',
      paceTightHint: '\u5efa\u8bae\u51cf\u5c11\u8de8\u533a\u79fb\u52a8\u6216\u5220\u6389\u4e00\u7ad9',
      groundingHigh: '\u5730\u70b9\u8f83\u7a33',
      groundingMedium: '\u5730\u70b9\u90e8\u5206\u5f85\u786e\u8ba4',
      groundingLow: '\u5730\u70b9\u4ecd\u504f\u4f30\u7b97',
      groundingHighHint: '\u591a\u6570\u5730\u70b9\u5df2\u6709\u5750\u6807\u6216\u9ad8\u5fb7\u6821\u9a8c',
      groundingMediumHint: '\u51fa\u53d1\u524d\u8fd8\u9700\u518d\u6838\u4e00\u6b21\u5173\u952e\u843d\u70b9',
      groundingLowHint: '\u5efa\u8bae\u4f18\u5148\u786e\u8ba4\u9152\u5e97\u548c\u666f\u70b9\u843d\u70b9',
      actionFromPlanner: '\u89c4\u5212\u5efa\u8bae',
      actionFromChecks: '\u98ce\u9669\u63d0\u9192',
      actionFromBudget: '\u9884\u7b97\u4f18\u5316',
      actionFromPace: '\u8282\u594f\u4f18\u5316',
      actionFromGrounding: '\u5730\u70b9\u786e\u8ba4',
      actionFallback: '\u7ee7\u7eed\u63a8\u8fdb',
      actionFallbackBody: '\u53ef\u4ee5\u5f00\u59cb\u786e\u8ba4\u9152\u5e97\u3001\u95e8\u7968\u548c\u51fa\u53d1\u65f6\u95f4',
      reduceBudgetBody: '\u4f18\u5148\u538b\u7f29\u9152\u5e97\u6216\u9009\u51e0\u4e2a\u95e8\u7968\u70b9\uff0c\u4f1a\u6bd4\u7f29\u77ed\u5929\u6570\u66f4\u81ea\u7136',
      relaxPaceBody: '\u6bcf\u5929\u51cf\u5c11 1 \u4e2a\u8de8\u533a\u70b9\u4f4d\uff0c\u4f1a\u6bd4\u5355\u7eaf\u538b\u7f29\u505c\u7559\u65f6\u95f4\u66f4\u8212\u670d',
      confirmLocationBody: '\u4f18\u5148\u68c0\u67e5\u9152\u5e97\u3001\u5173\u952e\u666f\u70b9\u548c\u8fd4\u7a0b\u7ad9\u70b9\u662f\u5426\u5b9a\u5728\u5408\u9002\u533a\u57df'
    }
  : {
      eyebrow: 'Plan',
      title: 'Suggested Plan',
      emptyTitle: 'No structured itinerary yet',
      emptyBody: 'Share the destination, trip length, budget, and preferences and I will turn them into routes, stays, costs, and a map.',
      quickNav: 'Priority View',
      recommended: 'Recommended',
      overview: 'Overview',
      decisionSummary: 'Decision Summary',
      nextSteps: 'Next Best Moves',
      atGlance: 'At A Glance',
      hotels: 'Stays',
      checks: 'Before You Book',
      budget: 'Budget',
      itinerary: 'Daily Itinerary',
      totalCost: 'Estimated Total',
      hotelArea: 'Recommended Stay Area',
      tripLength: 'Trip Length',
      currentWeather: 'Current Weather',
      estimateMode: 'Dates are still in estimate mode until the trip window is locked.',
      matched: 'Matched',
      district: 'District',
      coordinates: 'Coordinates',
      amapAddress: 'Amap Address',
      otherCandidates: 'Other candidates',
      locationDetails: 'View location details',
      routeDetails: 'View route details',
      locationVerified: 'Location verified',
      bookNow: 'Book Now',
      fallbackHint: 'This is an estimate. Confirm via map before booking.',
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
      budgetCap: (value: number) => `Budget cap ${value} CNY`,
      readiness: 'Execution Readiness',
      budgetFit: 'Budget Fit',
      paceFit: 'Pace',
      groundingFit: 'Location Confidence',
      readyValue: 'Ready to keep moving',
      watchValue: 'Usable with caveats',
      adjustValue: 'Adjust before committing',
      readyHint: 'No major conflicts detected. You can move on to hotel or ticket confirmation.',
      watchHint: 'The plan is usable, but there are still a few details worth cleaning up.',
      adjustHint: 'There is a budget or timing conflict. It is better to revise first.',
      budgetGood: 'Within budget',
      budgetOver: 'Budget is tight',
      budgetOpen: 'No budget cap yet',
      budgetGoodHint: (value: number) => `${value} CNY headroom remains`,
      budgetOverHint: (value: number) => `Estimated ${value} CNY over the cap`,
      budgetOpenHint: 'Add a budget cap to make tradeoffs easier',
      paceRelaxed: 'Relaxed',
      paceBalanced: 'Balanced',
      paceTight: 'Packed',
      paceRelaxedHint: 'Leaves room for wandering, food stops, and spontaneous changes.',
      paceBalancedHint: 'A good default for most short city trips.',
      paceTightHint: 'Consider removing one cross-town stop per day.',
      groundingHigh: 'Locations look solid',
      groundingMedium: 'Some locations need review',
      groundingLow: 'Locations still feel estimated',
      groundingHighHint: 'Most key stops already have coordinates or Amap grounding.',
      groundingMediumHint: 'Recheck a few critical stops before departure.',
      groundingLowHint: 'Confirm the hotel area and core POIs before booking.',
      actionFromPlanner: 'Planner suggestion',
      actionFromChecks: 'Risk to address',
      actionFromBudget: 'Budget adjustment',
      actionFromPace: 'Pacing adjustment',
      actionFromGrounding: 'Location confirmation',
      actionFallback: 'Keep moving',
      actionFallbackBody: 'You can start confirming hotels, tickets, and departure timing.',
      reduceBudgetBody: 'Trim stay cost or remove a paid stop first. That usually feels more natural than cutting trip length.',
      relaxPaceBody: 'Dropping one cross-town stop per day often improves comfort more than shrinking every stop.',
      confirmLocationBody: 'Verify the hotel, anchor POIs, and return stop area before locking the plan.'
    }))

const panelStateCopy = computed(() => (props.preferChinese
  ? {
      loading: '正在整理结构化行程...',
      partial: '当前只有部分方案数据，右侧内容会按已知信息尽量展开。',
      empty: '还没有可展示的结构化行程。',
      error: '结构化行程暂时不可用，请先修复请求错误后重试。'
    }
  : {
      loading: 'Preparing the structured itinerary...',
      partial: 'Only part of the plan is available right now, so this panel is showing the grounded pieces only.',
      empty: 'There is no structured itinerary to show yet.',
      error: 'The structured itinerary is temporarily unavailable. Fix the request error and retry.'
    }))

const visibleHotels = computed(() => props.travelPlan?.hotels.slice(0, 3) ?? [])
const visibleChecks = computed(() => props.travelPlan?.checks ?? [])
const visibleBudget = computed(() => props.travelPlan?.budget ?? [])
const visibleDays = computed(() => props.travelPlan?.days ?? [])
const estimateMode = computed(() => visibleDays.value.length > 0 && visibleDays.value.every(day => !day.date))

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

const decisionCards = computed(() => {
  if (!props.travelPlan) {
    return []
  }

  const trip = props.travelPlan
  const failCount = trip.checks.filter(check => check.status === 'FAIL').length
  const warnCount = trip.checks.filter(check => check.status === 'WARN').length
  const totalLoadMinutes = trip.days.reduce((sum, day) => sum + day.totalActivityMinutes + day.totalTransitMinutes, 0)
  const averageLoadMinutes = trip.days.length ? Math.round(totalLoadMinutes / trip.days.length) : 0
  const allPoints = [
    ...trip.hotels.map(hotel => ({ verified: Boolean(hotel.longitude && hotel.latitude) && hotel.source !== 'RULE.fallback' })),
    ...trip.days.flatMap(day =>
      day.stops.map(stop => ({
        verified: Boolean(stop.poiMatch?.source && stop.poiMatch.source !== 'RULE.fallback') || Boolean(stop.longitude && stop.latitude)
      }))
    )
  ]
  const verifiedCount = allPoints.filter(point => point.verified).length
  const groundingRatio = allPoints.length ? verifiedCount / allPoints.length : 0

  const readiness = failCount > 0
    ? {
        value: copy.value.adjustValue,
        hint: copy.value.adjustHint,
        tone: 'alert'
      }
    : warnCount > 0 || trip.constraintRelaxed
      ? {
          value: copy.value.watchValue,
          hint: copy.value.watchHint,
          tone: 'warn'
        }
      : {
          value: copy.value.readyValue,
          hint: copy.value.readyHint,
          tone: 'good'
        }

  const budgetCard = trip.totalBudget == null
    ? {
        value: copy.value.budgetOpen,
        hint: copy.value.budgetOpenHint,
        tone: 'neutral'
      }
    : trip.estimatedTotalMax <= trip.totalBudget
      ? {
          value: copy.value.budgetGood,
          hint: copy.value.budgetGoodHint(trip.totalBudget - trip.estimatedTotalMax),
          tone: 'good'
        }
      : {
          value: copy.value.budgetOver,
          hint: copy.value.budgetOverHint(trip.estimatedTotalMax - trip.totalBudget),
          tone: 'alert'
        }

  const paceCard = averageLoadMinutes > 500
    ? {
        value: copy.value.paceTight,
        hint: copy.value.paceTightHint,
        tone: 'warn'
      }
    : averageLoadMinutes >= 360
      ? {
          value: copy.value.paceBalanced,
          hint: copy.value.paceBalancedHint,
          tone: 'good'
        }
      : {
          value: copy.value.paceRelaxed,
          hint: copy.value.paceRelaxedHint,
          tone: 'good'
        }

  const groundingCard = groundingRatio >= 0.75
    ? {
        value: copy.value.groundingHigh,
        hint: copy.value.groundingHighHint,
        tone: 'good'
      }
    : groundingRatio >= 0.45
      ? {
          value: copy.value.groundingMedium,
          hint: copy.value.groundingMediumHint,
          tone: 'warn'
        }
      : {
          value: copy.value.groundingLow,
          hint: copy.value.groundingLowHint,
          tone: 'neutral'
        }

  return [
    {
      label: copy.value.readiness,
      ...readiness
    },
    {
      label: copy.value.budgetFit,
      ...budgetCard
    },
    {
      label: copy.value.paceFit,
      ...paceCard
    },
    {
      label: copy.value.groundingFit,
      ...groundingCard
    }
  ]
})

const nextActions = computed(() => {
  if (!props.travelPlan) {
    return []
  }

  const trip = props.travelPlan
  const actions: Array<{ title: string; body: string; tone: 'planner' | 'risk' | 'budget' | 'pace' | 'grounding' | 'default' }> = []
  const plannerSuggestions = trip.adjustmentSuggestions ?? []

  actions.push(
    ...plannerSuggestions.slice(0, 2).map(item => ({
      title: copy.value.actionFromPlanner,
      body: normalizeDisplayText(item),
      tone: 'planner' as const
    }))
  )

  const firstFail = trip.checks.find(check => check.status === 'FAIL')
  if (firstFail) {
    actions.push({
      title: copy.value.actionFromChecks,
      body: normalizeDisplayText(firstFail.message),
      tone: 'risk'
    })
  }

  if (trip.totalBudget != null && trip.estimatedTotalMax > trip.totalBudget) {
    actions.push({
      title: copy.value.actionFromBudget,
      body: copy.value.reduceBudgetBody,
      tone: 'budget'
    })
  }

  const totalLoadMinutes = trip.days.reduce((sum, day) => sum + day.totalActivityMinutes + day.totalTransitMinutes, 0)
  const averageLoadMinutes = trip.days.length ? Math.round(totalLoadMinutes / trip.days.length) : 0
  if (averageLoadMinutes > 500) {
    actions.push({
      title: copy.value.actionFromPace,
      body: copy.value.relaxPaceBody,
      tone: 'pace'
    })
  }

  const hasFallbackLocation = trip.hotels.some(hotel => hotel.source === 'RULE.fallback') ||
    trip.days.some(day => day.stops.some(stop => stop.poiMatch?.source === 'RULE.fallback'))
  if (hasFallbackLocation) {
    actions.push({
      title: copy.value.actionFromGrounding,
      body: copy.value.confirmLocationBody,
      tone: 'grounding'
    })
  }

  if (!actions.length) {
    actions.push({
      title: copy.value.actionFallback,
      body: copy.value.actionFallbackBody,
      tone: 'default'
    })
  }

  return actions.slice(0, 4)
})

const quickSections = computed(() => {
  if (!props.travelPlan) {
    return []
  }

  const trip = props.travelPlan
  const failCount = trip.checks.filter(check => check.status === 'FAIL').length
  const warnCount = trip.checks.filter(check => check.status === 'WARN').length
  const isBudgetTight = trip.totalBudget != null && trip.estimatedTotalMax > trip.totalBudget

  const sections: Array<{
    id: SectionId
    label: string
    meta?: string
    tone: 'default' | 'good' | 'warn' | 'alert'
    recommended?: boolean
  }> = [
    {
      id: 'decisions',
      label: copy.value.decisionSummary,
      meta: decisionCards.value[0]?.value,
      tone: failCount > 0 ? 'alert' : warnCount > 0 ? 'warn' : 'good',
      recommended: true
    }
  ]

  if (visibleChecks.value.length) {
    sections.push({
      id: 'checks',
      label: copy.value.checks,
      meta: `${failCount + warnCount}/${visibleChecks.value.length}`,
      tone: failCount > 0 ? 'alert' : warnCount > 0 ? 'warn' : 'good',
      recommended: failCount > 0 || warnCount > 0
    })
  }

  if (visibleBudget.value.length) {
    sections.push({
      id: 'budget',
      label: copy.value.budget,
      meta: `${visibleBudget.value.length}`,
      tone: isBudgetTight ? 'alert' : 'default',
      recommended: isBudgetTight
    })
  }

  if (visibleHotels.value.length) {
    sections.push({
      id: 'stays',
      label: copy.value.hotels,
      meta: `${visibleHotels.value.length}`,
      tone: 'default'
    })
  }

  if (visibleDays.value.length) {
    sections.push({
      id: 'itinerary',
      label: copy.value.itinerary,
      meta: `${visibleDays.value.length}`,
      tone: 'default'
    })
  }

  sections.push({
    id: 'map',
    label: props.preferChinese ? '\u5730\u56fe' : 'Map',
    tone: 'default'
  })

  return sections
})

function setSectionRef(sectionId: SectionId, element: Element | ComponentPublicInstance | null) {
  sectionRefs.value[sectionId] = element instanceof HTMLElement ? element : null
}

function scrollToSection(sectionId: SectionId) {
  sectionRefs.value[sectionId]?.scrollIntoView({
    behavior: 'smooth',
    block: 'start'
  })
}

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

function getSectionIcon(id: SectionId) {
  return {
    overview: Layout,
    decisions: Zap,
    glance: Calendar,
    map: MapIcon,
    stays: Hotel,
    budget: Wallet,
    checks: AlertCircle,
    itinerary: Calendar
  }[id] ?? Info
}
</script>

<template>
  <section class="panel plan-panel">
    <div class="panel__header">
      <div class="panel__header-info">
        <div class="panel__icon-badge">
          <Layout :size="18" />
        </div>
        <div>
          <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
          <h2>{{ copy.title }}</h2>
        </div>
      </div>
    </div>

    <div v-if="panelState !== 'success'" class="panel__empty plan-panel__status">
      <AlertCircle v-if="panelState === 'error'" :size="24" />
      <Clock v-else-if="panelState === 'loading'" :size="24" />
      <Info v-else :size="24" />
      <p>{{ panelStateCopy[panelState] }}</p>
    </div>

    <div v-if="!travelPlan" class="panel__empty">
      <Calendar :size="32" />
      <h3>{{ copy.emptyTitle }}</h3>
      <p>{{ copy.emptyBody }}</p>
    </div>

    <template v-else>
      <article :ref="el => setSectionRef('overview', el)" class="overview-card plan-anchor">
        <div class="overview-card__content">
          <div class="overview-card__header-row">
            <p class="overview-card__eyebrow">{{ copy.overview }}</p>
            <div class="overview-card__date-badge" v-if="travelPlan.updatedAt">
              <Clock :size="12" />
              <span>{{ new Date(travelPlan.updatedAt).toLocaleDateString() }}</span>
            </div>
          </div>
          <h3>{{ normalizeDisplayText(travelPlan.summary) }}</h3>
          <p class="overview-card__body">{{ normalizeDisplayText(travelPlan.hotelAreaReason) }}</p>
        </div>

        <div class="overview-card__stats">
          <article v-for="stat in overviewStats" :key="stat.label" class="overview-stat">
            <div class="overview-stat__header">
              <component :is="stat.label.includes('\u5929\u6c14') ? CloudSun : Calendar" :size="14" class="overview-stat__icon" />
              <span>{{ stat.label }}</span>
            </div>
            <strong>{{ stat.value }}</strong>
            <p v-if="stat.hint">{{ stat.hint }}</p>
          </article>
        </div>

        <div v-if="travelPlan.highlights.length" class="plan-highlights">
          <span v-for="item in travelPlan.highlights" :key="item">
            <Star :size="12" />
            {{ item }}
          </span>
        </div>
      </article>

      <div class="section-nav">
        <div class="section-nav__title">
          <Navigation :size="14" />
          {{ copy.quickNav }}
        </div>
        <div class="section-nav__list">
          <button
            v-for="section in quickSections"
            :key="section.id"
            type="button"
            class="section-nav__item"
            :class="[
              `section-nav__item--${section.tone}`,
              { 'section-nav__item--recommended': section.recommended }
            ]"
            @click="scrollToSection(section.id)"
          >
            <component :is="getSectionIcon(section.id)" :size="14" />
            <span>{{ section.label }}</span>
            <strong v-if="section.meta">{{ section.meta }}</strong>
            <em v-if="section.recommended">{{ copy.recommended }}</em>
          </button>
        </div>
      </div>

      <div v-if="decisionCards.length" :ref="el => setSectionRef('decisions', el)" class="plan-section plan-anchor">
        <div class="plan-section__title">
          <Zap :size="16" />
          {{ copy.decisionSummary }}
        </div>
        <div class="decision-grid">
          <article
            v-for="card in decisionCards"
            :key="card.label"
            class="decision-card"
            :class="`decision-card--${card.tone}`"
          >
            <div class="decision-card__header">
              <span>{{ card.label }}</span>
              <component
                :is="card.tone === 'good' ? CheckCircle2 : card.tone === 'warn' ? AlertTriangle : AlertCircle"
                :size="14"
              />
            </div>
            <strong>{{ card.value }}</strong>
            <p>{{ card.hint }}</p>
          </article>
        </div>
      </div>

      <div v-if="nextActions.length" class="plan-section">
        <div class="plan-section__title">
          <ArrowRight :size="16" />
          {{ copy.nextSteps }}
        </div>
        <div class="next-actions">
          <article
            v-for="action in nextActions"
            :key="`${action.title}-${action.body}`"
            class="next-action"
            :class="`next-action--${action.tone}`"
          >
            <div class="next-action__title-row">
              <component :is="action.tone === 'default' ? Star : CheckCircle2" :size="14" />
              <span>{{ action.title }}</span>
            </div>
            <p>{{ action.body }}</p>
          </article>
        </div>
      </div>

      <div v-if="visibleDays.length" :ref="el => setSectionRef('glance', el)" class="plan-section plan-anchor">
        <div class="plan-section__title">
          <Calendar :size="16" />
          {{ copy.atGlance }}
        </div>
        <div class="glance-grid">
          <article v-for="day in visibleDays" :key="day.dayNumber" class="glance-card">
            <div class="glance-card__header">
              <span>{{ copy.day(day.dayNumber) }}</span>
              <Clock :size="12" />
            </div>
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

      <div :ref="el => setSectionRef('map', el)" class="plan-anchor">
        <PlanMap
          :travel-plan="travelPlan"
          :state="mapState"
          :prefer-chinese="preferChinese"
          :active-point-id="activePointId"
          @select-point="activePointId = $event"
        />
      </div>

      <div class="dashboard-grid">
        <section v-if="visibleHotels.length" :ref="el => setSectionRef('stays', el)" class="plan-section plan-anchor">
          <div class="plan-section__title">
            <Hotel :size="16" />
            {{ copy.hotels }}
          </div>
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
              <div class="stay-card__status-row">
                <div class="stay-card__status-info">
                  <CheckCircle2 :size="12" v-if="hotel.source !== 'RULE.fallback'" />
                  <AlertCircle :size="12" v-else />
                  <p class="stay-card__status">{{ locationStatus(hotel.source) }}</p>
                </div>
                <a
                  v-if="hotel.bookingUrl"
                  :href="hotel.bookingUrl"
                  target="_blank"
                  class="stay-card__book-btn"
                >
                  <Navigation :size="12" />
                  {{ copy.bookNow }}
                </a>
              </div>
              <details v-if="hotel.longitude && hotel.latitude" class="stay-card__details">
                <summary>{{ copy.locationDetails }}</summary>
                <div class="details-pills">
                  <span>
                    <MapPin :size="10" />
                    {{ coordinateText(hotel.longitude, hotel.latitude) }}
                  </span>
                  <span>{{ hotel.address }}</span>
                </div>
              </details>
            </article>
          </div>
        </section>

        <section v-if="visibleBudget.length" :ref="el => setSectionRef('budget', el)" class="plan-section plan-anchor">
          <div class="plan-section__title">
            <Wallet :size="16" />
            {{ copy.budget }}
          </div>
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

      <div v-if="visibleChecks.length" :ref="el => setSectionRef('checks', el)" class="plan-section plan-anchor">
        <div class="plan-section__title">
          <AlertCircle :size="16" />
          {{ copy.checks }}
        </div>
        <div class="checks-grid">
          <article
            v-for="check in visibleChecks"
            :key="check.code"
            class="check-card"
            :class="`check-card--${check.status.toLowerCase()}`"
          >
            <div class="check-card__header">
              <span>{{ statusLabel(check) }}</span>
              <component
                :is="check.status === 'PASS' ? CheckCircle2 : check.status === 'WARN' ? AlertTriangle : AlertCircle"
                :size="14"
              />
            </div>
            <strong>{{ normalizeDisplayText(check.message) }}</strong>
          </article>
        </div>
      </div>

      <div v-if="visibleDays.length" :ref="el => setSectionRef('itinerary', el)" class="plan-section plan-anchor">
        <div class="plan-section__title">
          <Calendar :size="16" />
          {{ copy.itinerary }}
        </div>
        <p v-if="estimateMode" class="plan-section__note">{{ copy.estimateMode }}</p>
        <div class="itinerary-list">
          <article v-for="day in visibleDays" :key="day.dayNumber" class="itinerary-day">
            <div class="itinerary-day__header">
              <div>
                <p class="itinerary-day__eyebrow">{{ copy.day(day.dayNumber) }}</p>
                <p v-if="day.date" class="itinerary-day__date">{{ day.date }}</p>
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
                  <Clock :size="12" />
                  <strong>{{ stop.startTime }}</strong>
                  <span>{{ stop.endTime }}</span>
                  <em>{{ slotLabel(stop.slot) }}</em>
                </div>

                <div class="itinerary-stop__content">
                  <div class="itinerary-stop__header">
                    <strong>{{ normalizeDisplayText(stop.name) }}</strong>
                    <div class="itinerary-stop__actions">
                      <span v-if="stop.poiMatch?.source" class="itinerary-stop__source">
                        <MapPin :size="10" />
                        {{ locationStatus(stop.poiMatch.source) }}
                      </span>
                    </div>
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

.plan-anchor {
  scroll-margin-top: 96px;
}

.section-nav {
  position: sticky;
  top: -0.1rem;
  z-index: 2;
  display: grid;
  gap: 10px;
  padding: 14px;
  margin: -2px 0 0;
  border-radius: 22px;
  background: rgba(246, 241, 233, 0.92);
  border: 1px solid rgba(23, 48, 66, 0.08);
  backdrop-filter: blur(18px);
}

.section-nav__title {
  font-size: 0.76rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--muted);
}

.section-nav__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.section-nav__item {
  display: inline-grid;
  grid-auto-flow: column;
  gap: 8px;
  align-items: center;
  padding: 9px 12px;
  border-radius: 999px;
  border: 1px solid rgba(23, 48, 66, 0.08);
  background: rgba(255, 255, 255, 0.84);
  color: var(--ink);
  cursor: pointer;
  transition: transform 140ms ease, border-color 140ms ease, background 140ms ease;
}

.section-nav__item:hover {
  transform: translateY(-1px);
}

.section-nav__item span {
  font-size: 0.84rem;
}

.section-nav__item strong,
.section-nav__item em {
  font-style: normal;
  font-size: 0.74rem;
}

.section-nav__item strong {
  color: var(--accent-deep);
}

.section-nav__item em {
  padding: 4px 7px;
  border-radius: 999px;
  background: rgba(214, 98, 58, 0.1);
  color: var(--accent-deep);
}

.section-nav__item--good {
  border-color: rgba(15, 123, 115, 0.18);
}

.section-nav__item--warn {
  border-color: rgba(214, 164, 70, 0.24);
  background: rgba(255, 250, 241, 0.94);
}

.section-nav__item--alert {
  border-color: rgba(214, 98, 58, 0.22);
  background: rgba(255, 244, 239, 0.94);
}

.section-nav__item--recommended {
  box-shadow: inset 0 0 0 1px rgba(214, 98, 58, 0.08);
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
  color: var(--accent-deep);
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
.plan-section__note,
.route-card p,
.route-step p,
.location-card__note,
.itinerary-day__window,
.itinerary-day__date,
.itinerary-stop__head p,
.itinerary-stop__content > p {
  margin: 0;
  color: var(--muted);
  line-height: 1.55;
}

.overview-card__stats,
.decision-grid,
.next-actions,
.glance-grid,
.dashboard-grid,
.checks-grid {
  display: grid;
  gap: 10px;
}

.overview-card__stats {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.decision-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.next-actions {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-stat {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
}

.decision-card,
.next-action {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(23, 48, 66, 0.08);
  background: rgba(255, 255, 255, 0.84);
}

.overview-stat span,
.decision-card span,
.next-action span,
.stay-card__status,
.location-card__status {
  display: block;
  color: var(--muted);
  font-size: 0.82rem;
}

.overview-stat strong,
.decision-card strong {
  display: block;
  margin-top: 6px;
  font-size: 1rem;
}

.overview-stat p,
.decision-card p,
.next-action p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 0.82rem;
  line-height: 1.5;
}

.decision-card--good {
  border-color: rgba(15, 123, 115, 0.18);
  background: rgba(245, 252, 251, 0.92);
}

.decision-card--warn {
  border-color: rgba(214, 164, 70, 0.24);
  background: rgba(255, 249, 238, 0.94);
}

.decision-card--alert {
  border-color: rgba(214, 98, 58, 0.22);
  background: rgba(255, 244, 239, 0.94);
}

.decision-card--neutral {
  background: rgba(248, 248, 248, 0.88);
}

.next-action {
  align-content: start;
}

.next-action span {
  color: var(--accent-deep);
  font-size: 0.76rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.next-action--planner {
  border-color: rgba(214, 98, 58, 0.18);
}

.next-action--risk {
  border-color: rgba(176, 52, 52, 0.2);
  background: rgba(255, 246, 246, 0.92);
}

.next-action--budget {
  border-color: rgba(214, 164, 70, 0.24);
  background: rgba(255, 250, 241, 0.94);
}

.next-action--pace,
.next-action--grounding {
  border-color: rgba(15, 123, 115, 0.18);
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
  color: var(--accent-deep);
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
  background: rgba(23, 48, 66, 0.06);
  color: var(--accent-deep);
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
  color: var(--accent-deep);
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
  color: var(--teal);
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
  color: var(--accent-deep);
  font-size: 0.8rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.check-card--pass {
  border-color: rgba(15, 123, 115, 0.24);
}

.check-card--warn {
  border-color: rgba(214, 164, 70, 0.34);
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

.itinerary-day__date {
  margin-top: 4px;
  color: var(--accent-deep);
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  color: var(--ink);
}

.itinerary-stop__time span,
.itinerary-stop__time em {
  color: var(--muted);
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
  border: 1px solid rgba(15, 123, 115, 0.14);
  background: rgba(15, 123, 115, 0.07);
}

.route-card > span {
  color: var(--accent-deep);
  font-size: 0.82rem;
}

.route-step {
  padding: 10px 12px;
}

.route-step span {
  display: block;
  margin-top: 6px;
  color: var(--accent-deep);
  font-size: 0.82rem;
}

.location-card {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(214, 98, 58, 0.14);
  background: rgba(255, 247, 241, 0.72);
}

.itinerary-stop--return {
  border-style: dashed;
  background: rgba(247, 251, 255, 0.9);
}

@media (max-width: 1180px) {
  .section-nav {
    top: 0;
  }

  .decision-grid,
  .next-actions,
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
