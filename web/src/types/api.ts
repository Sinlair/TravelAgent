export type AgentType = 'WEATHER' | 'GEO' | 'TRAVEL_PLANNER' | 'GENERAL'
export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type ExecutionStage =
  | 'ANALYZE_QUERY'
  | 'RECALL_MEMORY'
  | 'SELECT_AGENT'
  | 'SPECIALIST'
  | 'CALL_TOOL'
  | 'VALIDATE_PLAN'
  | 'REPAIR_PLAN'
  | 'FINALIZE_MEMORY'
  | 'COMPLETED'
  | 'ERROR'

export type ConstraintCheckStatus = 'PASS' | 'WARN' | 'FAIL'
export type TravelPlanSlot = 'MORNING' | 'AFTERNOON' | 'EVENING'

export interface ApiResponse<T> {
  code: string
  info: string
  data: T
}

export interface ConversationSession {
  conversationId: string
  title: string
  lastAgent?: AgentType
  summary?: string
  createdAt: string
  updatedAt: string
}

export interface ConversationMessage {
  id: string
  conversationId: string
  role: MessageRole
  content: string
  agentType?: AgentType
  createdAt: string
  metadata?: ConversationMessageMetadata
}

export interface ConversationMessageImageAttachment {
  id: string
  name: string
  mediaType: string
  sizeBytes: number
}

export interface ConversationImageContext {
  conversationId: string
  summary: string
  facts: ConversationImageFacts
  attachments: ConversationMessageImageAttachment[]
  createdAt: string
  updatedAt: string
}

export interface ConversationImageFacts {
  origin?: string
  destination?: string
  startDate?: string
  endDate?: string
  days?: number
  budget?: string
  hotelName?: string
  hotelArea?: string
  activities: string[]
  missingFields: string[]
}

export interface ConversationMessageMetadata {
  imageAttachments?: ConversationMessageImageAttachment[]
  imageAttachmentCount?: number
  imageContextSummary?: string
  imageFacts?: ConversationImageFacts
}

export interface TaskMemory {
  conversationId: string
  origin?: string
  destination?: string
  days?: number
  budget?: string
  preferences: string[]
  pendingQuestion?: string
  summary?: string
  updatedAt: string
}

export interface TravelBudgetItem {
  category: string
  minAmount: number
  maxAmount: number
  rationale: string
}

export interface TravelConstraintCheck {
  code: string
  status: ConstraintCheckStatus
  message: string
}

export interface TravelCostBreakdown {
  ticketCost: number
  foodCost: number
  localTransitCost: number
  otherCost: number
  note: string
}

export interface TravelPoiMatch {
  query: string
  matchedName: string
  district: string
  address: string
  adCode: string
  longitude?: string
  latitude?: string
  confidence: number
  candidateNames: string[]
  source?: string
}

export interface TravelTransitStep {
  mode: string
  title: string
  instruction: string
  lineName: string
  fromName: string
  toName: string
  durationMinutes: number
  distanceMeters: number
  stopCount: number
  polyline: string[]
}

export interface TravelTransitLeg {
  fromName: string
  toName: string
  mode: string
  summary: string
  durationMinutes: number
  distanceMeters: number
  walkingMinutes: number
  estimatedCost: number
  lineNames: string[]
  steps: TravelTransitStep[]
  polyline: string[]
  source?: string
}

export interface TravelHotelRecommendation {
  name: string
  area: string
  address: string
  nightlyMin: number
  nightlyMax: number
  rationale: string
  longitude?: string
  latitude?: string
  source?: string
}

export interface TravelPlanStop {
  slot: TravelPlanSlot
  name: string
  area: string
  address?: string
  longitude?: string
  latitude?: string
  startTime: string
  endTime: string
  durationMinutes: number
  transitMinutesFromPrevious: number
  estimatedCost: number
  openTime: string
  closeTime: string
  rationale: string
  costBreakdown?: TravelCostBreakdown | null
  poiMatch?: TravelPoiMatch | null
  routeFromPrevious?: TravelTransitLeg | null
}

export interface TravelPlanDay {
  dayNumber: number
  theme: string
  startTime: string
  endTime: string
  totalTransitMinutes: number
  totalActivityMinutes: number
  estimatedCost: number
  stops: TravelPlanStop[]
  returnToHotel?: TravelTransitLeg | null
}

export interface WeatherSnapshot {
  city?: string
  province?: string
  reportTime?: string
  description?: string
  temperature?: string
  windDirection?: string
  windPower?: string
}

export interface TravelKnowledgeSelection {
  city: string
  topic: string
  title: string
  content: string
  tags: string[]
  source?: string
  schemaSubtype?: string
  qualityScore?: number
  matchedTripStyles?: string[]
  matchedCity?: string
  matchedTopic?: string
}

export interface TravelKnowledgeRetrievalResult {
  destination?: string
  inferredTopics: string[]
  inferredTripStyles?: string[]
  retrievalSource?: string
  selections: TravelKnowledgeSelection[]
}

export interface TravelPlan {
  conversationId: string
  title: string
  summary: string
  hotelArea: string
  hotelAreaReason: string
  hotels: TravelHotelRecommendation[]
  totalBudget?: number
  estimatedTotalMin: number
  estimatedTotalMax: number
  highlights: string[]
  budget: TravelBudgetItem[]
  checks: TravelConstraintCheck[]
  days: TravelPlanDay[]
  weatherSnapshot?: WeatherSnapshot | null
  knowledgeRetrieval?: TravelKnowledgeRetrievalResult | null
  constraintRelaxed?: boolean
  adjustmentSuggestions?: string[]
  updatedAt: string
}

export interface ConversationFeedback {
  conversationId: string
  label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED'
  reasonCode?: string
  note?: string
  agentType?: AgentType
  destination?: string
  days?: number
  budget?: string
  hasTravelPlan: boolean
  metadata: Record<string, string | number | boolean | string[] | null>
  createdAt: string
  updatedAt: string
}

export interface ConversationFeedbackRequest {
  label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED'
  reasonCode?: string
  note?: string
}

export interface ChatImageAttachmentRequest {
  name: string
  mediaType: string
  dataUrl: string
}

export interface ChatRequest {
  conversationId?: string
  message?: string
  attachments?: ChatImageAttachmentRequest[]
  imageContextAction?: 'CONFIRM' | 'DISMISS'
}

export interface FeedbackBreakdownItem {
  key: string
  totalCount: number
  acceptedCount: number
  partialCount: number
  rejectedCount: number
  acceptedRatePct: number
  usableRatePct: number
}

export interface FeedbackLoopFinding {
  type: string
  key: string
  totalCount: number
  acceptedCount: number
  partialCount: number
  rejectedCount: number
  usableRatePct: number
  recommendation: string
}

export interface FeedbackLoopSummaryResponse {
  generatedAt: string
  limitApplied: number
  sampleCount: number
  acceptedCount: number
  partialCount: number
  rejectedCount: number
  acceptedRatePct: number
  usableRatePct: number
  structuredPlanCount: number
  structuredPlanCoveragePct: number
  topReasonCodes: FeedbackBreakdownItem[]
  topDestinations: FeedbackBreakdownItem[]
  topAgentTypes: FeedbackBreakdownItem[]
  keyFindings: FeedbackLoopFinding[]
}

export interface TimelineEvent {
  id: string
  conversationId: string
  stage: ExecutionStage
  message: string
  details: Record<string, string | number | boolean | string[] | null>
  createdAt: string
}

export interface ChatResponse {
  conversationId: string
  agentType: AgentType
  answer: string
  taskMemory: TaskMemory
  travelPlan: TravelPlan | null
  timeline: TimelineEvent[]
}

export interface ConversationDetailResponse {
  conversation: ConversationSession
  messages: ConversationMessage[]
  timeline: TimelineEvent[]
  taskMemory: TaskMemory
  travelPlan: TravelPlan | null
  feedback: ConversationFeedback | null
  imageContextCandidate: ConversationImageContext | null
}
