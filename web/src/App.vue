<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from './stores/chat'
import ConversationSidebar from './components/ConversationSidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import TimelinePanel from './components/TimelinePanel.vue'
import PlanPanel from './components/PlanPanel.vue'
import PlanActionsPanel from './components/PlanActionsPanel.vue'

const store = useChatStore()
const {
  conversations,
  currentConversationId,
  detail,
  sending,
  loading,
  errorMessage
} = storeToRefs(store)

const preferChinese = computed(() => {
  const latestUser = [...(detail.value?.messages ?? [])].reverse().find(message => message.role === 'USER')
  return latestUser ? /[\u4e00-\u9fff]/.test(latestUser.content) : true
})

const heroCopy = computed(() => (preferChinese.value
  ? {
      eyebrow: '旅行助理',
      title: '一句话说清目的地、天数、预算和偏好，我会把它整理成更容易执行的行程。',
      subtitle: '例如：三天杭州，预算 3000，节奏轻松'
    }
  : {
      eyebrow: 'Travel Agent',
      title: 'Tell me the destination, timing, budget, and preferences, and I will turn them into a more executable trip plan.',
      subtitle: 'A single sentence is enough to start.'
    }))

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
        <p>{{ heroCopy.eyebrow }}</p>
        <h1>{{ heroCopy.title }}</h1>
        <span>{{ heroCopy.subtitle }}</span>
      </header>

      <div class="workspace__grid">
        <ChatPanel
          :detail="detail"
          :sending="sending"
          :error-message="errorMessage"
          :prefer-chinese="preferChinese"
          @send="store.sendMessage"
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
          <TimelinePanel :timeline="detail?.timeline || []" :prefer-chinese="preferChinese" />
        </div>
      </div>
    </section>
  </main>
</template>
