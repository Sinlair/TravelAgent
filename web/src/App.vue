<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from './stores/chat'
import { buildConversationResultViewModel } from './utils/conversationResult'
import {
  Globe,
  Sparkles
} from 'lucide-vue-next'
import ConversationSidebar from './components/ConversationSidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import PlanPanel from './components/PlanPanel.vue'

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

const shellCopy = computed(() => (preferChinese.value
  ? {
      eyebrow: 'AI \u65c5\u884c\u52a9\u624b',
      title: '\u4eca\u5929\u60f3\u53bb\u54ea\u91cc\uff1f',
      subtitle: '\u8bf4\u4e00\u53e5\u60f3\u6cd5\uff0c\u6211\u6765\u6574\u7406\u8def\u7ebf\u3001\u4f4f\u5bbf\u3001\u9884\u7b97\u548c\u6bcf\u5929\u8282\u594f\u3002',
      planTitle: '\u4f60\u7684\u884c\u7a0b',
      chips: ['\u8def\u7ebf', '\u4f4f\u5bbf', '\u9884\u7b97', '\u6bcf\u65e5\u5b89\u6392']
    }
  : {
      eyebrow: 'AI Travel Assistant',
      title: 'Where are we going?',
      subtitle: 'Share one travel idea and I will shape route, stay, budget, and daily pacing.',
      planTitle: 'Your Itinerary',
      chips: ['Route', 'Stay', 'Budget', 'Daily Plan']
    }))

function setLanguage(nextLanguage: UiLanguage) {
  language.value = nextLanguage
}

onMounted(async () => {
  await store.loadConversations()
  if (currentConversationId.value) {
    await store.openConversation(currentConversationId.value)
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

    <section class="workspace workspace--chat">
      <header class="chat-hero">
        <div>
          <p class="chat-hero__eyebrow">
            <Sparkles :size="14" />
            {{ shellCopy.eyebrow }}
          </p>
          <h1>{{ shellCopy.title }}</h1>
          <p>{{ shellCopy.subtitle }}</p>
          <div class="chat-hero__chips">
            <span v-for="chip in shellCopy.chips" :key="chip">{{ chip }}</span>
          </div>
        </div>

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
      </header>

      <div class="chat-layout">
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

        <section v-if="resultView.travelPlan" class="plan-result">
          <div class="plan-result__header">
            <span>{{ shellCopy.planTitle }}</span>
          </div>
          <PlanPanel
            :travel-plan="resultView.travelPlan"
            :result-view="resultView"
            :prefer-chinese="preferChinese"
          />
        </section>
      </div>
    </section>
  </main>
</template>
