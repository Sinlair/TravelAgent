<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from './stores/chat'
import ConversationSidebar from './components/ConversationSidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import TimelinePanel from './components/TimelinePanel.vue'
import PlanPanel from './components/PlanPanel.vue'
import PlanActionsPanel from './components/PlanActionsPanel.vue'

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
  errorMessage
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

const heroCopy = computed(() => (preferChinese.value
  ? {
      eyebrow: '\u65c5\u884c\u52a9\u624b',
      title: '\u4e00\u53e5\u8bdd\u8bf4\u6e05\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u548c\u504f\u597d\uff0c\u6211\u4f1a\u628a\u5b83\u6574\u7406\u6210\u66f4\u5bb9\u6613\u6267\u884c\u7684\u884c\u7a0b\u3002',
      subtitle: '\u4f8b\u5982\uff1a\u4e09\u5929\u676d\u5dde\uff0c\u9884\u7b97 3000\uff0c\u8282\u594f\u8f7b\u677e'
    }
  : {
      eyebrow: 'Travel Agent',
      title: 'Tell me the destination, timing, budget, and preferences, and I will turn them into a more executable trip plan.',
      subtitle: 'A single sentence is enough to start.'
    }))

function setLanguage(nextLanguage: UiLanguage) {
  language.value = nextLanguage
}

onMounted(async () => {
  await store.loadConversations()
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
        <div class="hero__top">
          <div>
            <p>{{ heroCopy.eyebrow }}</p>
            <h1>{{ heroCopy.title }}</h1>
            <span>{{ heroCopy.subtitle }}</span>
          </div>
          <div class="hero__language">
            <button
              type="button"
              class="hero__language-button"
              :class="{ 'hero__language-button--active': preferChinese }"
              @click="setLanguage('zh')"
            >
              中文
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
        </div>
      </header>

      <div class="workspace__grid">
        <ChatPanel
          :detail="detail"
          :sending="sending"
          :feedback="detail?.feedback || null"
          :feedback-saving="feedbackSaving"
          :error-message="errorMessage"
          :prefer-chinese="preferChinese"
          @send="store.sendMessage"
          @feedback="store.submitFeedback"
        />

        <div class="workspace__side">
          <PlanActionsPanel
            :travel-plan="detail?.travelPlan || null"
            :prefer-chinese="preferChinese"
          />
          <PlanPanel
            :travel-plan="detail?.travelPlan || null"
            :prefer-chinese="preferChinese"
          />
          <TimelinePanel
            :timeline="detail?.timeline || []"
            :prefer-chinese="preferChinese"
          />
        </div>
      </div>
    </section>
  </main>
</template>
