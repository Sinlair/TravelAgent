import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiDelete, apiGet, apiPost } from '../api/client'
import type {
  ChatRequest,
  ChatResponse,
  ConversationDetailResponse,
  ConversationFeedback,
  ConversationFeedbackRequest,
  ConversationSession,
  FeedbackLoopSummaryResponse,
  TimelineEvent
} from '../types/api'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationSession[]>([])
  const detail = ref<ConversationDetailResponse | null>(null)
  const feedbackLoopSummary = ref<FeedbackLoopSummaryResponse | null>(null)
  const currentConversationId = ref('')
  const sending = ref(false)
  const feedbackSaving = ref(false)
  const feedbackLoopLoading = ref(false)
  const feedbackLoopStale = ref(true)
  const feedbackLoopLimit = ref(200)
  const loading = ref(false)
  const errorMessage = ref('')
  const streamError = ref('')
  const feedbackLoopError = ref('')
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
      streamError.value = ''
      connectStream(conversationId)
    } catch (error) {
      errorMessage.value = formatError(error)
    }
  }

  async function sendMessage(payload: ChatRequest) {
    const trimmedMessage = payload.message?.trim() ?? ''
    const attachments = payload.attachments ?? []
    if ((!trimmedMessage && attachments.length === 0) || sending.value) {
      return
    }
    sending.value = true
    try {
      errorMessage.value = ''
      const response = await apiPost<ChatResponse>('/api/conversations/chat', {
        conversationId: currentConversationId.value || undefined,
        message: trimmedMessage || undefined,
        attachments,
        imageContextAction: payload.imageContextAction
      })
      streamError.value = ''
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
      feedbackLoopStale.value = true
      errorMessage.value = ''
      await loadConversations()
    } catch (error) {
      errorMessage.value = formatError(error)
    }
  }

  async function submitFeedback(payload: ConversationFeedbackRequest) {
    if (!currentConversationId.value || feedbackSaving.value) {
      return
    }
    feedbackSaving.value = true
    try {
      const feedback = await apiPost<ConversationFeedback>(
        `/api/conversations/${currentConversationId.value}/feedback`,
        payload
      )
      if (detail.value?.conversation.conversationId === currentConversationId.value) {
        detail.value.feedback = feedback
      }
      feedbackLoopStale.value = true
      errorMessage.value = ''
    } catch (error) {
      errorMessage.value = formatError(error)
    } finally {
      feedbackSaving.value = false
    }
  }

  async function loadFeedbackLoopSummary(limit = feedbackLoopLimit.value) {
    if (feedbackLoopLoading.value) {
      return
    }
    feedbackLoopLoading.value = true
    feedbackLoopLimit.value = limit
    try {
      feedbackLoopSummary.value = await apiGet<FeedbackLoopSummaryResponse>(
        `/api/conversations/feedback/summary?limit=${limit}`
      )
      feedbackLoopStale.value = false
      feedbackLoopError.value = ''
    } catch (error) {
      feedbackLoopError.value = formatError(error)
    } finally {
      feedbackLoopLoading.value = false
    }
  }

  function newConversation() {
    currentConversationId.value = ''
    detail.value = null
    errorMessage.value = ''
    streamError.value = ''
    closeStream()
  }

  function connectStream(conversationId: string) {
    closeStream()
    streamError.value = ''
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
    eventSource.onerror = () => {
      streamError.value = 'Live execution updates disconnected. Retry the stream.'
      closeStream()
    }
  }

  function closeStream() {
    eventSource?.close()
    eventSource = null
  }

  function reconnectStream() {
    if (!currentConversationId.value) {
      return
    }
    connectStream(currentConversationId.value)
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
    feedbackLoopSummary,
    currentConversationId,
    sending,
    feedbackSaving,
    feedbackLoopLoading,
    feedbackLoopStale,
    feedbackLoopLimit,
    loading,
    errorMessage,
    streamError,
    feedbackLoopError,
    hasConversation,
    loadConversations,
    openConversation,
    sendMessage,
    submitFeedback,
    loadFeedbackLoopSummary,
    deleteConversation,
    newConversation,
    reconnectStream
  }
})
