<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from './stores/chat'
import { buildConversationResultViewModel } from './utils/conversationResult'
import {
  Globe,
  Map,
  MessageSquare,
  History,
  Clock,
  Layout,
  Sparkles,
  Info,
  Calendar,
  Wallet,
  Activity,
  CheckCircle2,
  Navigation
} from 'lucide-vue-next'
import ConversationSidebar from './components/ConversationSidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import TimelinePanel from './components/TimelinePanel.vue'
import PlanPanel from './components/PlanPanel.vue'
import PlanActionsPanel from './components/PlanActionsPanel.vue'
import FeedbackLoopPanel from './components/FeedbackLoopPanel.vue'
import PlanExecutionPanel from './components/PlanExecutionPanel.vue'
import { normalizeDisplayText } from './utils/text'

type UiLanguage = 'zh' | 'en'

const LANGUAGE_STORAGE_KEY = 'travel-agent-ui-language'

const store = useChatStore()
const {
  conversations,
  currentConversationId,
  detail,
  sending,
  loading,
  feedbackSaving,
  feedbackLoopSummary,
  feedbackLoopLoading,
  feedbackLoopStale,
  feedbackLoopLimit,
  feedbackLoopFilters,
  feedbackLoopError,
  errorMessage,
  streamError
} = storeToRefs(store)

const language = ref<UiLanguage>('zh')

if (typeof window !== 'undefined') {
  const stored = window.localStorage.getItem(LANGUAGE_STORAGE_KEY)
  if (stored === 'zh' || stored === 'en') {
    language.value = stored
  }
}

watch(language, value => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, value)
  }
})

const preferChinese = computed(() => language.value === 'zh')
const resultView = computed(() => buildConversationResultViewModel(detail.value, {
  sending: sending.value,
  errorMessage: errorMessage.value,
  streamError: streamError.value
}))
const languageLabels = {
  zh: '\u4e2d\u6587',
  en: 'EN'
} as const

const heroCopy = computed(() => (preferChinese.value
  ? {
      eyebrow: '\u65c5\u884c\u89c4\u5212\u5de5\u4f5c\u53f0',
      title: '\u628a\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u548c\u504f\u597d\u8f93\u5165\u6210\u4e00\u53e5\u8bdd\uff0c\u8fd9\u91cc\u4f1a\u628a\u5b83\u62c6\u6210\u4e00\u4efd\u66f4\u9002\u5408\u6267\u884c\u7684\u51fa\u884c\u65b9\u6848\u3002',
      subtitle: '\u652f\u6301\u6587\u672c + \u622a\u56fe\uff0c\u4e00\u6b21\u6574\u7406\u8def\u7ebf\u3001\u4f4f\u5bbf\u3001\u9884\u7b97\u548c\u5730\u56fe\u3002'
    }
  : {
      eyebrow: 'Travel Planning Studio',
      title: 'Describe the destination, timing, budget, and preferences once, and this workspace turns it into a trip plan you can actually execute.',
      subtitle: 'Text and screenshots flow into route, stay, budget, and map output.'
    }))

const heroUi = computed(() => {
  const labels = preferChinese.value
    ? {
        focusLabel: '\u5f53\u524d\u7126\u70b9',
        focusFallback: '\u7b49\u5f85\u4e00\u4e2a\u65b0\u7684\u76ee\u7684\u5730',
        focusBody: '\u4ece\u4e00\u53e5\u9700\u6c42\u5f00\u59cb\uff0c\u6211\u4f1a\u628a\u8def\u7ebf\u3001\u4f4f\u5bbf\u3001\u9884\u7b97\u548c\u8282\u594f\u6574\u7406\u6210\u540c\u4e00\u4efd\u65b9\u6848\u3002',
        focusImagePending: '\u5df2\u89c6\u89c9\u63d0\u53d6\u56fe\u7247\u4e2d\u7684\u51fa\u884c\u4fe1\u606f\uff0c\u7b49\u4f60\u786e\u8ba4\u540e\u5c31\u53ef\u4ee5\u63a5\u7740\u751f\u6210\u884c\u7a0b\u3002',
        savedSessions: '\u5df2\u4fdd\u5b58\u4f1a\u8bdd',
        currentMessages: '\u5f53\u524d\u6d88\u606f',
        tripWindow: '\u884c\u7a0b\u957f\u5ea6',
        currentStatus: '\u5f53\u524d\u72b6\u6001',
        activeSession: '\u6b63\u5728\u67e5\u770b\u5f53\u524d\u4f1a\u8bdd',
        noSession: '\u8fd8\u6ca1\u6709\u9009\u4e2d\u4f1a\u8bdd',
        waitingHistory: '\u5386\u53f2\u8bb0\u5f55\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc',
        awaitingTripWindow: '\u5f85\u8865\u5145',
        addBudget: '\u8865\u5145\u5929\u6570\u6216\u9884\u7b97\u540e\u4f1a\u66f4\u7a33\u5b9a',
        budgetTag: '\u9884\u7b97 ',
        statusReady: '\u65b9\u6848\u5c31\u7eea',
        statusDrafting: '\u6574\u7406\u9700\u6c42',
        statusIdle: '\u7b49\u5f85\u8f93\u5165',
        statusPending: '\u56fe\u7247\u5f85\u786e\u8ba4',
        statusReadyHint: '\u53ef\u4ee5\u7ee7\u7eed\u8c03\u6574\u8282\u594f\u3001\u666f\u70b9\u6216\u9884\u7b97',
        statusIdleHint: '\u53d1\u4e00\u53e5\u9700\u6c42\u5c31\u80fd\u5f00\u59cb',
        defaultHighlights: [
          '\u8def\u7ebf\u5206\u89e3',
          '\u9884\u7b97\u62c6\u5206',
          '\u9152\u5e97\u5efa\u8bae',
          '\u622a\u56fe\u8865\u5145\u4fe1\u606f'
        ],
        dayCount: (value: number) => `${value} \u5929`
      }
    : {
        focusLabel: 'Current Focus',
        focusFallback: 'Waiting for a destination',
        focusBody: 'Start with one request and this workspace will shape route, stay, budget, and pacing into one plan.',
        focusImagePending: 'Image facts have been extracted and are waiting for confirmation before the plan continues.',
        savedSessions: 'Saved Sessions',
        currentMessages: 'Current Messages',
        tripWindow: 'Trip Window',
        currentStatus: 'Current Status',
        activeSession: 'Viewing the active session',
        noSession: 'No session selected yet',
        waitingHistory: 'Conversation history will appear here',
        awaitingTripWindow: 'Pending',
        addBudget: 'Add days or budget for a tighter plan',
        budgetTag: 'Budget ',
        statusReady: 'Plan Ready',
        statusDrafting: 'Shaping Request',
        statusIdle: 'Awaiting Input',
        statusPending: 'Image Pending',
        statusReadyHint: 'Refine the pacing, stops, or budget from here',
        statusIdleHint: 'One sentence is enough to start',
        defaultHighlights: [
          'Route breakdown',
          'Budget framing',
          'Stay suggestions',
          'Screenshot-assisted intake'
        ],
        dayCount: (value: number) => `${value} days`
      }

  const currentDetail = detail.value
  const taskMemory = currentDetail?.taskMemory
  const travelPlan = currentDetail?.travelPlan
  const hasPendingImageContext = Boolean(currentDetail?.imageContextCandidate)
  const focusTitle =
    normalizeDisplayText(taskMemory?.destination) ||
    normalizeDisplayText(travelPlan?.title) ||
    labels.focusFallback
  const focusBody = travelPlan
    ? normalizeDisplayText(travelPlan.summary)
    : normalizeDisplayText(taskMemory?.summary) || (hasPendingImageContext ? labels.focusImagePending : labels.focusBody)
  const tripLength = travelPlan?.days.length || taskMemory?.days
  const statusValue = hasPendingImageContext
    ? labels.statusPending
    : travelPlan
      ? labels.statusReady
      : currentDetail
        ? labels.statusDrafting
        : labels.statusIdle
  const statusHint = hasPendingImageContext
    ? labels.focusImagePending
    : travelPlan
      ? labels.statusReadyHint
      : normalizeDisplayText(taskMemory?.pendingQuestion) || labels.statusIdleHint
  const locale = preferChinese.value ? 'zh-CN' : undefined
  const highlights = (travelPlan?.highlights.length
    ? travelPlan.highlights
    : taskMemory?.preferences.length
      ? taskMemory.preferences
      : labels.defaultHighlights)
    .slice(0, 4)
    .map(item => normalizeDisplayText(item))

  return {
    labels,
    focusTitle,
    focusBody,
    highlights,
    stats: [
      {
        label: labels.savedSessions,
        value: String(conversations.value.length).padStart(2, '0'),
        hint: currentConversationId.value ? labels.activeSession : labels.noSession
      },
      {
        label: labels.currentMessages,
        value: String(currentDetail?.messages.length ?? 0).padStart(2, '0'),
        hint: currentDetail?.conversation.updatedAt
          ? new Date(currentDetail.conversation.updatedAt).toLocaleString(locale)
          : labels.waitingHistory
      },
      {
        label: labels.tripWindow,
        value: tripLength ? labels.dayCount(tripLength) : labels.awaitingTripWindow,
        hint: taskMemory?.budget ? `${labels.budgetTag}${normalizeDisplayText(taskMemory.budget)}` : labels.addBudget
      },
      {
        label: labels.currentStatus,
        value: statusValue,
        hint: statusHint
      }
    ]
  }
})

const heroFlow = computed(() => {
  const labels = preferChinese.value
    ? {
        title: '\u89c4\u5212\u6d41\u7a0b',
        statusDone: '\u5df2\u5c31\u7eea',
        statusActive: '\u8fdb\u884c\u4e2d',
        statusWaiting: '\u5f85\u5f00\u59cb',
        steps: {
          intake: '\u91c7\u96c6\u9700\u6c42',
          grounding: '\u6838\u5bf9\u5730\u70b9',
          plan: '\u8f93\u51fa\u65b9\u6848'
        },
        hints: {
          intakeDone: '\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u548c\u504f\u597d\u5df2\u8fdb\u5165\u5de5\u4f5c\u533a',
          intakeActive: '\u5148\u8865\u9f50\u8fd9\u6b21\u51fa\u884c\u7684\u57fa\u672c\u6761\u4ef6',
          groundingDone: '\u5730\u56fe\u3001POI \u548c\u622a\u56fe\u4fe1\u606f\u5df2\u5f00\u59cb\u53c2\u4e0e\u7ea0\u504f',
          groundingActive: '\u7528\u5730\u56fe\u6216\u56fe\u7247\u628a\u5730\u70b9\u4fe1\u606f\u8865\u51c6',
          planDone: '\u6bcf\u65e5\u8282\u594f\u3001\u8def\u7ebf\u3001\u4f4f\u5bbf\u548c\u9884\u7b97\u5df2\u8f93\u51fa',
          planWaiting: '\u751f\u6210\u540e\u4f1a\u5728\u53f3\u4fa7\u81ea\u52a8\u6574\u7406'
        }
      }
    : {
        title: 'Planning Flow',
        statusDone: 'Ready',
        statusActive: 'In Progress',
        statusWaiting: 'Waiting',
        steps: {
          intake: 'Capture Brief',
          grounding: 'Verify Places',
          plan: 'Build Plan'
        },
        hints: {
          intakeDone: 'Destination, timing, budget, and preferences are already in the workspace',
          intakeActive: 'Start by tightening the core trip brief',
          groundingDone: 'Map, POI, and screenshot signals are already helping with grounding',
          groundingActive: 'Use map and image context to make locations more precise',
          planDone: 'Daily pacing, route, stay, and budget are already generated',
          planWaiting: 'The structured plan will appear on the right after generation'
        }
      }

  const currentDetail = detail.value
  const taskMemory = currentDetail?.taskMemory
  const travelPlan = currentDetail?.travelPlan
  const hasPendingImageContext = Boolean(currentDetail?.imageContextCandidate)
  const intakeDone = Boolean(taskMemory?.destination && taskMemory?.days && (taskMemory?.budget || taskMemory?.preferences.length))
  const groundingDone = Boolean(travelPlan || hasPendingImageContext || (currentDetail?.timeline.length ?? 0) > 0)
  const planDone = Boolean(travelPlan)

  return {
    title: labels.title,
    steps: [
      {
        title: labels.steps.intake,
        status: intakeDone ? 'done' : currentDetail ? 'active' : 'waiting',
        label: intakeDone ? labels.statusDone : currentDetail ? labels.statusActive : labels.statusWaiting,
        hint: intakeDone ? labels.hints.intakeDone : labels.hints.intakeActive
      },
      {
        title: labels.steps.grounding,
        status: groundingDone ? (planDone ? 'done' : 'active') : 'waiting',
        label: groundingDone ? (planDone ? labels.statusDone : labels.statusActive) : labels.statusWaiting,
        hint: groundingDone ? labels.hints.groundingDone : labels.hints.groundingActive
      },
      {
        title: labels.steps.plan,
        status: planDone ? 'done' : currentDetail ? 'active' : 'waiting',
        label: planDone ? labels.statusDone : currentDetail ? labels.statusActive : labels.statusWaiting,
        hint: planDone ? labels.hints.planDone : labels.hints.planWaiting
      }
    ]
  }
})

function setLanguage(nextLanguage: UiLanguage) {
  language.value = nextLanguage
}

function getStatIcon(label: string) {
  if (label.includes('\u4f1a\u8bdd') || label.includes('Sessions')) return History
  if (label.includes('\u6d88\u606f') || label.includes('Messages')) return MessageSquare
  if (label.includes('\u884c\u7a0b') || label.includes('Window')) return Calendar
  if (label.includes('\u72b6\u6001') || label.includes('Status')) return Activity
  return Info
}

function handleReplanHotel() {
  void store.sendMessage({
    replanScope: {
      scope: 'HOTEL_AREA'
    }
  })
}

function handleReplanDay(dayNumber: number) {
  void store.sendMessage({
    replanScope: {
      scope: 'DAY',
      dayNumber
    }
  })
}

function handleChecklistToggle(itemKey: string, confirmed: boolean) {
  void store.updateChecklist({
    itemKey,
    confirmed
  })
}

onMounted(async () => {
  await store.loadConversations()
  await store.loadFeedbackLoopSummary()
  const targetConversationId = currentConversationId.value || conversations.value[0]?.conversationId
  if (targetConversationId) {
    await store.openConversation(targetConversationId)
  }
})
</script>

<template>
  <main class="shell">
    <ConversationSidebar
      :conversations="conversations"
      :current-conversation-id="currentConversationId"
      :loading="loading"
      :prefer-chinese="preferChinese"
      @select="store.openConversation"
      @create="store.newConversation"
      @remove="store.deleteConversation"
    />

    <section class="workspace">
      <header class="hero">
        <div class="hero__content">
          <div class="hero__copy">
            <p class="hero__eyebrow">
              <Sparkles :size="14" />
              {{ heroCopy.eyebrow }}
            </p>
            <h1>{{ heroCopy.title }}</h1>
            <span class="hero__subtitle">{{ heroCopy.subtitle }}</span>
          </div>

          <div class="hero__rail">
            <div class="hero__language">
              <Globe :size="14" />
              <button
                type="button"
                class="hero__language-button"
                :class="{ 'hero__language-button--active': preferChinese }"
                @click="setLanguage('zh')"
              >
                {{ languageLabels.zh }}
              </button>
              <button
                type="button"
                class="hero__language-button"
                :class="{ 'hero__language-button--active': !preferChinese }"
                @click="setLanguage('en')"
              >
                EN
              </button>
            </div>

            <article class="hero__focus">
              <div class="hero__focus-header">
                <Activity :size="14" />
                <span>{{ heroUi.labels.focusLabel }}</span>
              </div>
              <strong>{{ heroUi.focusTitle }}</strong>
              <p>{{ heroUi.focusBody }}</p>
            </article>
          </div>
        </div>

        <div class="hero__stats">
          <article v-for="stat in heroUi.stats" :key="stat.label" class="hero__stat">
            <div class="hero__stat-header">
              <component :is="getStatIcon(stat.label)" :size="14" />
              <span>{{ stat.label }}</span>
            </div>
            <strong>{{ stat.value }}</strong>
            <p>{{ stat.hint }}</p>
          </article>
        </div>

        <div class="hero__highlights">
          <span v-for="item in heroUi.highlights" :key="item">
            <CheckCircle2 :size="12" />
            {{ item }}
          </span>
        </div>

        <div class="hero__flow">
          <div class="hero__flow-title">
            <Navigation :size="14" />
            {{ heroFlow.title }}
          </div>
          <article
            v-for="step in heroFlow.steps"
            :key="step.title"
            class="hero__flow-step"
            :class="`hero__flow-step--${step.status}`"
          >
            <div class="hero__flow-dot">
              <CheckCircle2 :size="12" v-if="step.status === 'done'" />
            </div>
            <strong>{{ step.title }}</strong>
            <span>{{ step.label }}</span>
            <p>{{ step.hint }}</p>
          </article>
        </div>
      </header>

      <div class="workspace__grid">
        <ChatPanel
          :detail="detail"
          :result-view="resultView"
          :sending="sending"
          :feedback="resultView.feedback"
          :feedback-saving="feedbackSaving"
          :error-message="errorMessage"
          :prefer-chinese="preferChinese"
          @send="store.sendMessage"
          @feedback="store.submitFeedback"
        />

        <div class="workspace__side">
          <PlanActionsPanel
            :travel-plan="resultView.travelPlan"
            :prefer-chinese="preferChinese"
          />
          <PlanExecutionPanel
            :travel-plan="resultView.travelPlan"
            :recent-version-diff="detail?.recentVersionDiff ?? null"
            :sending="sending"
            :prefer-chinese="preferChinese"
            @replan-hotel="handleReplanHotel"
            @replan-day="handleReplanDay"
            @toggle-checklist="handleChecklistToggle"
          />
          <PlanPanel
            :travel-plan="resultView.travelPlan"
            :result-view="resultView"
            :prefer-chinese="preferChinese"
          />
          <TimelinePanel
            :result-view="resultView"
            :prefer-chinese="preferChinese"
            @retry-stream="store.reconnectStream"
          />
          <FeedbackLoopPanel
            :summary="feedbackLoopSummary"
            :loading="feedbackLoopLoading"
            :stale="feedbackLoopStale"
            :error-message="feedbackLoopError"
            :initial-limit="feedbackLoopLimit"
            :initial-filters="feedbackLoopFilters"
            :prefer-chinese="preferChinese"
            @refresh="({ limit, ...filters }) => store.loadFeedbackLoopSummary(limit, filters)"
          />
        </div>
      </div>
    </section>
  </main>
</template>
