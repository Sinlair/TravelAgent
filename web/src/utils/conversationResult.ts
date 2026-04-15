import type {
  ChatResponseFeedbackTarget,
  ChatResponseIssue,
  ConversationConstraintSummary,
  ConversationDetailResponse,
  ConversationFeedback,
  ConversationMissingInformationItem,
  TimelineEvent,
  TravelPlan
} from '../types/api'

export type ResultPanelState = 'loading' | 'partial' | 'success' | 'empty' | 'error'

export interface ConversationResultViewModel {
  answer: string
  chatState: ResultPanelState
  planState: ResultPanelState
  mapState: ResultPanelState
  timelineState: ResultPanelState
  feedbackState: ResultPanelState
  feedback: ConversationFeedback | null
  feedbackTarget: ChatResponseFeedbackTarget | null
  hasPendingImageContext: boolean
  issues: ChatResponseIssue[]
  latestAssistantMessageId: string
  missingInformation: ConversationMissingInformationItem[]
  streamError: string
  timeline: TimelineEvent[]
  travelPlan: TravelPlan | null
  constraintSummary: ConversationConstraintSummary
}

const EMPTY_CONSTRAINT_SUMMARY: ConversationConstraintSummary = {
  status: 'NONE',
  repaired: false,
  hasRisk: false,
  issues: []
}

export function buildConversationResultViewModel(
  detail: ConversationDetailResponse | null,
  options: {
    sending?: boolean
    errorMessage?: string
    streamError?: string
  } = {}
): ConversationResultViewModel {
  const sending = Boolean(options.sending)
  const errorMessage = options.errorMessage?.trim() ?? ''
  const streamError = options.streamError?.trim() ?? ''
  const messages = detail?.messages ?? []
  const latestAssistantMessage = [...messages].reverse().find(message => message.role === 'ASSISTANT')
  const latestAnswer = latestAssistantMessage?.content ?? ''
  const travelPlan = detail?.travelPlan ?? null
  const timeline = detail?.timeline ?? []
  const issues = detail?.issues ?? []
  const missingInformation = detail?.missingInformation ?? []
  const constraintSummary = detail?.constraintSummary ?? EMPTY_CONSTRAINT_SUMMARY
  const hasPendingImageContext = Boolean(detail?.imageContextCandidate)
  const hasAnswer = latestAnswer.trim().length > 0
  const hasCoordinates = Boolean(
    travelPlan?.hotels.some(hotel => hotel.longitude && hotel.latitude) ||
      travelPlan?.days.some(day => day.stops.some(stop => stop.longitude && stop.latitude))
  )

  const chatState: ResultPanelState = sending
    ? 'loading'
    : errorMessage && !detail
      ? 'error'
      : !detail
        ? 'empty'
        : hasAnswer
          ? issues.length > 0 || missingInformation.length > 0 ? 'partial' : 'success'
          : 'partial'

  const planState: ResultPanelState = sending && !travelPlan
    ? 'loading'
    : errorMessage && !travelPlan
      ? 'error'
      : !detail
        ? 'empty'
        : travelPlan
          ? constraintSummary.status === 'PASS' ? 'success' : 'partial'
          : hasAnswer || hasPendingImageContext ? 'partial' : 'empty'

  const mapState: ResultPanelState = sending && !travelPlan
    ? 'loading'
    : !detail
      ? 'empty'
      : errorMessage && !travelPlan
        ? 'error'
        : !travelPlan
          ? hasAnswer || hasPendingImageContext ? 'partial' : 'empty'
          : hasCoordinates ? 'success' : 'partial'

  const timelineState: ResultPanelState = streamError
    ? 'error'
    : sending
      ? 'loading'
      : !detail
        ? 'empty'
        : timeline.length > 0
          ? 'success'
          : hasAnswer || travelPlan ? 'partial' : 'empty'

  const feedbackState: ResultPanelState = sending
    ? 'loading'
    : !detail
      ? 'empty'
      : errorMessage && !detail.feedbackTarget
        ? 'error'
        : detail.feedbackTarget
          ? 'success'
          : hasAnswer ? 'partial' : 'empty'

  return {
    answer: latestAnswer,
    chatState,
    planState,
    mapState,
    timelineState,
    feedbackState,
    feedback: detail?.feedback ?? null,
    feedbackTarget: detail?.feedbackTarget ?? null,
    hasPendingImageContext,
    issues,
    latestAssistantMessageId: latestAssistantMessage?.id ?? '',
    missingInformation,
    streamError,
    timeline,
    travelPlan,
    constraintSummary
  }
}
