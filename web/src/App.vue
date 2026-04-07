<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from './stores/chat'
import ConversationSidebar from './components/ConversationSidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import TimelinePanel from './components/TimelinePanel.vue'
import PlanPanel from './components/PlanPanel.vue'
import FeedbackLoopPanel from './components/FeedbackLoopPanel.vue'

const store = useChatStore()
const {
  conversations,
  currentConversationId,
  detail,
  sending,
  feedbackSaving,
  feedbackLoopSummary,
  feedbackLoopLoading,
  feedbackLoopStale,
  feedbackLoopLimit,
  loading,
  errorMessage,
  feedbackLoopError
} = storeToRefs(store)

const preferChinese = computed(() => {
  const latestUser = [...(detail.value?.messages ?? [])].reverse().find(message => message.role === 'USER')
  return latestUser ? /[\u4e00-\u9fff]/.test(latestUser.content) : true
})

onMounted(() => {
  void store.loadConversations()
})
</script>

<template>
  <main class="shell">
    <ConversationSidebar
      :conversations="conversations"
      :current-conversation-id="currentConversationId"
      :loading="loading"
      @select="store.openConversation"
      @create="store.newConversation"
      @remove="store.deleteConversation"
    />

    <section class="workspace">
      <header class="hero">
        <p>旅行小助手</p>
        <h1>把目的地、天数、预算和偏好告诉我，我会整理成一份更容易执行的出行方案。</h1>
        <span>你可以先从一句简单的话开始，比如“帮我规划三天杭州行程，预算 3000，想轻松一点”。</span>
      </header>

      <FeedbackLoopPanel
        :summary="feedbackLoopSummary"
        :loading="feedbackLoopLoading"
        :stale="feedbackLoopStale"
        :error-message="feedbackLoopError"
        :prefer-chinese="preferChinese"
        :initial-limit="feedbackLoopLimit"
        @refresh="store.loadFeedbackLoopSummary"
      />

      <div class="workspace__grid">
        <ChatPanel :detail="detail" :sending="sending" :error-message="errorMessage" @send="store.sendMessage" />
        <div class="workspace__side">
          <PlanPanel
            :travel-plan="detail?.travelPlan || null"
            :feedback="detail?.feedback || null"
            :feedback-saving="feedbackSaving"
            :prefer-chinese="preferChinese"
            @feedback="store.submitFeedback"
          />
          <TimelinePanel :timeline="detail?.timeline || []" :prefer-chinese="preferChinese" />
        </div>
      </div>
    </section>
  </main>
</template>
