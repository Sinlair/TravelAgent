import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiDelete, apiGet, apiPatch, apiPost } from '../api/client'
import type {
  ChatRequest,
  ChatResponse,
  ConversationChecklistUpdateRequest,
  ConversationDetailResponse,
  ConversationFeedback,
  ConversationFeedbackRequest,
  ConversationSession,
  FeedbackLoopSummaryResponse,
  TimelineEvent,
  TravelPlan
} from '../types/api'

type FeedbackLoopFilters = {
  destination?: string
  agentType?: string
  targetScope?: string
  reasonLabel?: string
}

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
  const feedbackLoopFilters = ref<FeedbackLoopFilters>({
    destination: '',
    agentType: '',
    targetScope: '',
    reasonLabel: ''
  })
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
    const brief = payload.brief
    const hasReplan = Boolean(payload.replanScope?.scope)
    const hasBrief = Boolean(
      brief && (
        brief.origin ||
        brief.destination ||
        brief.startDate ||
        brief.endDate ||
        brief.days ||
        brief.travelers ||
        brief.budget ||
        (brief.preferences?.length ?? 0) > 0
      )
    )
    if ((!trimmedMessage && attachments.length === 0 && !hasBrief && !hasReplan) || sending.value) {
      return
    }
    sending.value = true
    try {
      errorMessage.value = ''
      const response = await apiPost<ChatResponse>('/api/conversations/chat', {
        conversationId: currentConversationId.value || undefined,
        message: trimmedMessage || undefined,
        brief: hasBrief ? brief : undefined,
        attachments,
        imageContextAction: payload.imageContextAction,
        replanScope: payload.replanScope
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

  async function updateChecklist(payload: ConversationChecklistUpdateRequest) {
    if (!currentConversationId.value) {
      return
    }
    try {
      const updatedPlan = await apiPatch<TravelPlan>(
        `/api/conversations/${currentConversationId.value}/checklist`,
        payload
      )
      if (detail.value?.conversation.conversationId === currentConversationId.value) {
        detail.value.travelPlan = updatedPlan
      }
      errorMessage.value = ''
    } catch (error) {
      errorMessage.value = formatError(error)
    }
  }

  async function loadFeedbackLoopSummary(
    limit = feedbackLoopLimit.value,
    filters: FeedbackLoopFilters = feedbackLoopFilters.value
  ) {
    if (feedbackLoopLoading.value) {
      return
    }
    feedbackLoopLoading.value = true
    feedbackLoopLimit.value = limit
    feedbackLoopFilters.value = {
      destination: filters.destination ?? '',
      agentType: filters.agentType ?? '',
      targetScope: filters.targetScope ?? '',
      reasonLabel: filters.reasonLabel ?? ''
    }
    try {
      const params = new URLSearchParams({
        limit: String(limit)
      })
      if (feedbackLoopFilters.value.destination) params.set('destination', feedbackLoopFilters.value.destination)
      if (feedbackLoopFilters.value.agentType) params.set('agentType', feedbackLoopFilters.value.agentType)
      if (feedbackLoopFilters.value.targetScope) params.set('targetScope', feedbackLoopFilters.value.targetScope)
      if (feedbackLoopFilters.value.reasonLabel) params.set('reasonLabel', feedbackLoopFilters.value.reasonLabel)
      feedbackLoopSummary.value = await apiGet<FeedbackLoopSummaryResponse>(
        `/api/conversations/feedback/summary?${params.toString()}`
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
    feedbackLoopFilters,
    loading,
    errorMessage,
    streamError,
    feedbackLoopError,
    hasConversation,
    loadConversations,
    openConversation,
    sendMessage,
    submitFeedback,
    updateChecklist,
    loadFeedbackLoopSummary,
    deleteConversation,
    newConversation,
    reconnectStream
  }
})
