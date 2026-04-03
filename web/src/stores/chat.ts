import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiDelete, apiGet, apiPost } from '../api/client'
import type { ChatResponse, ConversationDetailResponse, ConversationSession, TimelineEvent } from '../types/api'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationSession[]>([])
  const detail = ref<ConversationDetailResponse | null>(null)
  const currentConversationId = ref('')
  const sending = ref(false)
  const loading = ref(false)
  const errorMessage = ref('')
  let eventSource: EventSource | null = null

  const hasConversation = computed(() => currentConversationId.value.length > 0)

  async function loadConversations() {
    loading.value = true
    try {
      conversations.value = await apiGet<ConversationSession[]>('/api/conversations')
      errorMessage.value = ''
    } catch (error) {
      errorMessage.value = formatError(error)
    } finally {
      loading.value = false
    }
  }

  async function openConversation(conversationId: string) {
    if (!conversationId) {
      return
    }
    currentConversationId.value = conversationId
    try {
      detail.value = await apiGet<ConversationDetailResponse>(`/api/conversations/${conversationId}`)
      errorMessage.value = ''
      connectStream(conversationId)
    } catch (error) {
      errorMessage.value = formatError(error)
    }
  }

  async function sendMessage(message: string) {
    if (!message.trim() || sending.value) {
      return
    }
    sending.value = true
    try {
      errorMessage.value = ''
      const response = await apiPost<ChatResponse>('/api/conversations/chat', {
        conversationId: currentConversationId.value || undefined,
        message
      })
      await loadConversations()
      await openConversation(response.conversationId)
    } catch (error) {
      errorMessage.value = formatError(error)
    } finally {
      sending.value = false
    }
  }

  async function deleteConversation(conversationId: string) {
    try {
      await apiDelete<null>(`/api/conversations/${conversationId}`)
      if (currentConversationId.value === conversationId) {
        currentConversationId.value = ''
        detail.value = null
        closeStream()
      }
      errorMessage.value = ''
      await loadConversations()
    } catch (error) {
      errorMessage.value = formatError(error)
    }
  }

  function newConversation() {
    currentConversationId.value = ''
    detail.value = null
    errorMessage.value = ''
    closeStream()
  }

  function connectStream(conversationId: string) {
    closeStream()
    eventSource = new EventSource(`/api/conversations/${conversationId}/stream`)
    eventSource.onmessage = (event) => {
      const timelineEvent = JSON.parse(event.data) as TimelineEvent
      if (!detail.value || detail.value.conversation.conversationId !== conversationId) {
        return
      }
      if (detail.value.timeline.some(item => item.id === timelineEvent.id)) {
        return
      }
      detail.value.timeline = [...detail.value.timeline, timelineEvent]
    }
  }

  function closeStream() {
    eventSource?.close()
    eventSource = null
  }

  function formatError(error: unknown) {
    if (error instanceof Error && error.message) {
      return error.message
    }
    return 'Request failed. Please try again.'
  }

  return {
    conversations,
    detail,
    currentConversationId,
    sending,
    loading,
    errorMessage,
    hasConversation,
    loadConversations,
    openConversation,
    sendMessage,
    deleteConversation,
    newConversation
  }
})
