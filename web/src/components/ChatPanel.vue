<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Send, Image, MessageSquare, Info, Sparkles, AlertCircle, CheckCircle2, XCircle } from 'lucide-vue-next'
import type {
  ChatImageAttachmentRequest,
  ChatRequest,
  ConversationDetailResponse,
  ConversationFeedback,
  ConversationFeedbackRequest,
  ConversationMessage,
  FeedbackTargetScope
} from '../types/api'
import { buildConversationResultViewModel } from '../utils/conversationResult'
import type { ConversationResultViewModel } from '../utils/conversationResult'
import { normalizeDisplayText } from '../utils/text'

const props = withDefaults(defineProps<{
  detail: ConversationDetailResponse | null
  resultView?: ConversationResultViewModel
  sending: boolean
  feedback: ConversationFeedback | null
  feedbackSaving: boolean
  errorMessage: string
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const emit = defineEmits<{
  send: [payload: ChatRequest]
  feedback: [payload: ConversationFeedbackRequest]
}>()

const input = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const attachments = ref<ChatImageAttachmentRequest[]>([])
const attachmentError = ref('')
const isDragging = ref(false)
const feedbackEditorLabel = ref<'PARTIAL' | 'REJECTED' | ''>('')
const feedbackScope = ref<FeedbackTargetScope>('ANSWER')
const feedbackNote = ref('')
const feedbackReasonLabels = ref<string[]>([])
const briefOrigin = ref('')
const briefDestination = ref('')
const briefStartDate = ref('')
const briefEndDate = ref('')
const briefDays = ref('')
const briefTravelers = ref('')
const briefBudget = ref('')
const briefPreferences = ref('')

const MAX_ATTACHMENTS = 4
const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif'])

const pendingImageContext = computed(() => props.detail?.imageContextCandidate ?? null)
const resolvedResultView = computed(() => props.resultView ?? buildConversationResultViewModel(props.detail, {
  sending: props.sending,
  errorMessage: props.errorMessage
}))
const latestAssistantMessageId = computed(() => resolvedResultView.value.latestAssistantMessageId)
const availableFeedbackScopes = computed<FeedbackTargetScope[]>(() =>
  (resolvedResultView.value.feedbackTarget?.availableScopes ?? ['ANSWER']) as FeedbackTargetScope[]
)
const normalizedFeedbackScope = computed<FeedbackTargetScope>(() =>
  (resolvedResultView.value.feedbackTarget?.scope ?? 'ANSWER') as FeedbackTargetScope
)

const feedbackUi = computed(() => {
  const scopeLabels = props.preferChinese
    ? {
        ANSWER: '文本答案',
        PLAN: '结构化行程',
        OVERALL: '整体方案'
      }
    : {
        ANSWER: 'Answer',
        PLAN: 'Plan',
        OVERALL: 'Overall'
      }
  const scopeHint = props.preferChinese
    ? '评价范围'
    : 'Feedback scope'
  const notePlaceholder = props.preferChinese
    ? '可选：补充你改了什么、哪里不准，或为什么不打算采用。'
    : 'Optional: add what you changed, what felt wrong, or why you would not use it.'
  const saveLabel = props.preferChinese
    ? (props.feedbackSaving ? '提交中...' : '提交反馈')
    : (props.feedbackSaving ? 'Saving...' : 'Submit feedback')
  const cancelLabel = props.preferChinese ? '取消' : 'Cancel'
  const issueTitle = props.preferChinese ? '当前状态' : 'Current result state'
  const missingTitle = props.preferChinese ? '还缺这些关键信息' : 'Missing trip details'

  const reasonOptions = {
    PARTIAL: props.preferChinese
      ? [
          { code: 'budget_needs_adjustment', label: '预算要再调' },
          { code: 'pace_needs_adjustment', label: '节奏要再松' },
          { code: 'location_needs_confirmation', label: '地点还要确认' },
          { code: 'too_generic', label: '内容还偏泛' }
        ]
      : [
          { code: 'budget_needs_adjustment', label: 'Budget needs work' },
          { code: 'pace_needs_adjustment', label: 'Pace needs work' },
          { code: 'location_needs_confirmation', label: 'Location needs review' },
          { code: 'too_generic', label: 'Still too generic' }
        ],
    REJECTED: props.preferChinese
      ? [
          { code: 'not_useful', label: '整体没用上' },
          { code: 'incorrect_grounding', label: '地点不准' },
          { code: 'budget_mismatch', label: '预算不匹配' },
          { code: 'too_packed', label: '行程太满' },
          { code: 'missing_context', label: '缺关键上下文' }
        ]
      : [
          { code: 'not_useful', label: 'Not useful' },
          { code: 'incorrect_grounding', label: 'Incorrect grounding' },
          { code: 'budget_mismatch', label: 'Budget mismatch' },
          { code: 'too_packed', label: 'Too packed' },
          { code: 'missing_context', label: 'Missing context' }
        ]
  } as const

  return {
    scopeLabels,
    scopeHint,
    notePlaceholder,
    saveLabel,
    cancelLabel,
    issueTitle,
    missingTitle,
    reasonOptions
  }
})

const factLabels = computed<Record<string, string>>(() => props.preferChinese
  ? {
      origin: '\u51fa\u53d1\u5730',
      destination: '\u76ee\u7684\u5730',
      startDate: '\u5f00\u59cb\u65e5\u671f',
      endDate: '\u7ed3\u675f\u65e5\u671f',
      days: '\u5929\u6570',
      budget: '\u9884\u7b97',
      hotelName: '\u9152\u5e97',
      hotelArea: '\u9152\u5e97\u533a\u57df',
      activities: '\u504f\u597d'
    }
  : {
      origin: 'Origin',
      destination: 'Destination',
      startDate: 'Start Date',
      endDate: 'End Date',
      days: 'Days',
      budget: 'Budget',
      hotelName: 'Hotel',
      hotelArea: 'Hotel Area',
      activities: 'Activities'
    })

const copy = computed(() => (props.preferChinese
  ? {
      eyebrow: '\u5bf9\u8bdd',
      title: normalizeDisplayText(props.detail?.conversation.title) || '\u65b0\u7684\u65c5\u884c\u89c4\u5212\u4f1a\u8bdd',
      messageCount: (count: number) => `${count} \u6761\u6d88\u606f`,
      pendingQuestion: '\u5f85\u8865\u5145\u4fe1\u606f',
      briefTitle: '\u5f53\u524d\u9700\u6c42\u7b80\u62a5',
      briefReady: '\u53ef\u4ee5\u5f00\u59cb\u7ec4\u88c5\u65b9\u6848',
      briefNeedsWork: '\u8fd8\u53ef\u4ee5\u518d\u8865\u4e00\u70b9',
      briefGenerated: '\u6838\u5fc3\u8981\u7d20\u5df2\u8db3\u591f\uff0c\u53ef\u4ee5\u76f4\u63a5\u8fed\u4ee3\u53f3\u4fa7\u65b9\u6848\u3002',
      briefAlmost: '\u57fa\u672c\u6761\u4ef6\u5df2\u7ecf\u6210\u578b\uff0c\u518d\u8865 1 \u5230 2 \u4e2a\u5173\u952e\u4fe1\u606f\u4f1a\u66f4\u7a33\u3002',
      briefMissing: '\u5148\u8865\u9f50\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u548c\u504f\u597d\uff0c\u7ed3\u679c\u4f1a\u66f4\u50cf\u4e00\u4efd\u53ef\u6267\u884c\u7684\u884c\u7a0b\u3002',
      briefMissingLabel: '\u8fd8\u7f3a',
      briefScore: (done: number, total: number) => `${done}/${total}`,
      followUpLabel: '\u4e0b\u4e00\u53e5\u53ef\u4ee5\u8fd9\u6837\u8865\u5145',
      briefEditorTitle: '\u76f4\u63a5\u4fee\u6539\u65c5\u884c\u7ea6\u675f',
      briefApply: '\u5e94\u7528\u7ea6\u675f',
      composerTitle: '\u7ee7\u7eed\u8865\u5145\u6216\u6539\u65b9\u6848',
      composerBody: '\u76f4\u63a5\u7528\u53e5\u5b50\u63cf\u8ff0\u53d8\u66f4\uff0c\u4f8b\u5982\u8c03\u6574\u8282\u594f\u3001\u63a7\u5236\u9884\u7b97\u3001\u66f4\u6362\u4f4f\u5bbf\u533a\u57df\u3002',
      slotMissing: '\u5f85\u8865\u5145',
      emptyTitle: '\u5148\u628a\u76ee\u7684\u5730\u3001\u5929\u6570\u548c\u9884\u7b97\u8bf4\u6e05\u695a',
      emptyBody: '\u4f60\u53ef\u4ee5\u76f4\u63a5\u63cf\u8ff0\u65c5\u884c\u9700\u6c42\uff0c\u4e5f\u53ef\u4ee5\u7c98\u8d34\u6216\u62d6\u62fd\u622a\u56fe\uff0c\u8ba9\u7cfb\u7edf\u4ece\u56fe\u7247\u91cc\u8865\u5145\u65c5\u884c\u4fe1\u606f\u3002',
      quickStart: '\u53ef\u4ee5\u76f4\u63a5\u4ece\u8fd9\u4e9b\u793a\u4f8b\u5f00\u59cb',
      user: '\u7528\u6237',
      assistant: '\u52a9\u624b',
      thinking: '\u6b63\u5728\u601d\u8003...',
      feedbackSaving: '\u63d0\u4ea4\u4e2d...',
      feedbackAccepted: '\u63a5\u53d7',
      feedbackPartial: '\u90e8\u5206\u63a5\u53d7',
      feedbackRejected: '\u62d2\u7edd',
      feedbackAdjust: '\u8bf4\u8bf4\u54ea\u91cc\u8fd8\u8981\u6539',
      memoryLabels: {
        origin: '\u51fa\u53d1\u5730',
        destination: '\u76ee\u7684\u5730',
        startDate: '\u5f00\u59cb\u65e5\u671f',
        endDate: '\u7ed3\u675f\u65e5\u671f',
        days: '\u5929\u6570',
        travelers: '\u540c\u884c\u4eba',
        budget: '\u9884\u7b97',
        preference: '\u504f\u597d'
      },
      imageContextTitle: '\u56fe\u7247\u4fe1\u606f\u5f85\u786e\u8ba4',
      imageCount: (count: number) => `${count} \u5f20\u56fe\u7247`,
      needsInput: '\u8fd8\u9700\u8981\u8865\u5145',
      useFacts: '\u4f7f\u7528\u8fd9\u4e9b\u4fe1\u606f',
      ignoreFacts: '\u5ffd\u7565',
      remove: '\u79fb\u9664',
      uploadHint: '\u652f\u6301\u7c98\u8d34\u622a\u56fe\u3001\u62d6\u62fd\u56fe\u7247\uff0c\u6216',
      uploadAction: '\u70b9\u51fb\u4e0a\u4f20',
      placeholder: '\u8f93\u5165\u76ee\u7684\u5730\u3001\u5929\u6570\u3001\u9884\u7b97\u3001\u504f\u597d\uff0c\u6216\u8005\u7ee7\u7eed\u8865\u5145\u8fd9\u6b21\u884c\u7a0b\u60f3\u6cd5...',
      submit: props.sending ? '\u751f\u6210\u4e2d...' : '\u751f\u6210\u65b9\u6848',
      submitHint: props.sending ? '\u8bf7\u7a0d\u5019' : 'Ctrl + Enter',
      limitError: `\u6bcf\u8f6e\u6700\u591a\u4e0a\u4f20 ${MAX_ATTACHMENTS} \u5f20\u56fe\u7247\u3002`,
      fileError: '\u4ec5\u652f\u6301 PNG\u3001JPEG\u3001WEBP\u3001GIF\uff0c\u4e14\u5355\u5f20\u56fe\u7247\u4e0d\u80fd\u8d85\u8fc7 5 MB\u3002'
    }
  : {
      eyebrow: 'Conversation',
      title: normalizeDisplayText(props.detail?.conversation.title) || 'New Trip Planning Session',
      messageCount: (count: number) => `${count} messages`,
      pendingQuestion: 'Needs More Detail',
      briefTitle: 'Current Brief',
      briefReady: 'Ready to shape the plan',
      briefNeedsWork: 'A bit more detail will help',
      briefGenerated: 'The core brief is strong enough. You can now refine the plan on the right.',
      briefAlmost: 'The brief is mostly there. One or two more details will make the plan more reliable.',
      briefMissing: 'Add destination, timing, budget, and preferences so the result feels like an executable itinerary.',
      briefMissingLabel: 'Still missing',
      briefScore: (done: number, total: number) => `${done}/${total}`,
      followUpLabel: 'Useful next messages',
      briefEditorTitle: 'Edit trip constraints directly',
      briefApply: 'Apply brief',
      composerTitle: 'Add a refinement or correction',
      composerBody: 'Write the next change directly, for example adjust pacing, tighten budget, or switch the stay area.',
      slotMissing: 'Pending',
      emptyTitle: 'Start with the trip basics.',
      emptyBody: 'Describe the trip directly, or paste and drag travel screenshots so the system can pull useful facts from them.',
      quickStart: 'Start from one of these prompts',
      user: 'User',
      assistant: 'Assistant',
      thinking: 'Thinking...',
      feedbackSaving: 'Saving...',
      feedbackAccepted: 'Accept',
      feedbackPartial: 'Partially Accept',
      feedbackRejected: 'Reject',
      feedbackAdjust: 'Say what still needs work',
      memoryLabels: {
        origin: 'Origin',
        destination: 'Destination',
        startDate: 'Start Date',
        endDate: 'End Date',
        days: 'Days',
        travelers: 'Travelers',
        budget: 'Budget',
        preference: 'Preference'
      },
      imageContextTitle: 'Image Facts Awaiting Confirmation',
      imageCount: (count: number) => `${count} image${count > 1 ? 's' : ''}`,
      needsInput: 'Needs More Input',
      useFacts: 'Use These Facts',
      ignoreFacts: 'Ignore',
      remove: 'Remove',
      uploadHint: 'Paste screenshots, drag images here, or',
      uploadAction: 'click to upload',
      placeholder: 'Type the destination, trip length, budget, preferences, or continue refining the trip...',
      submit: props.sending ? 'Planning...' : 'Plan Trip',
      submitHint: props.sending ? 'Please wait' : 'Ctrl + Enter',
      limitError: `You can attach up to ${MAX_ATTACHMENTS} images per turn.`,
      fileError: 'Only PNG, JPEG, WEBP, and GIF images up to 5 MB are supported.'
    }))

const starterPrompts = computed(() => (props.preferChinese
  ? [
      '\u676d\u5dde\u5468\u672b 2 \u5929\uff0c\u9884\u7b97 2500\uff0c\u60f3\u770b\u666f\u5403\u996d\uff0c\u8282\u594f\u8f7b\u677e',
      '\u4e94\u4e00\u53bb\u4e0a\u6d77 3 \u5929\uff0c\u5e26\u7238\u5988\u51fa\u884c\uff0c\u4f4f\u5730\u94c1\u65c1\uff0c\u5c11\u8d70\u8def',
      '\u6210\u90fd 4 \u5929\uff0c\u60f3\u5403\u672c\u5730\u5c0f\u9986\u5b50\uff0c\u9884\u7b97 3500\uff0c\u987a\u4fbf\u62cd\u7167'
    ]
  : [
      'Hangzhou for 2 days, budget 2500, scenic and food-focused, relaxed pace',
      'Shanghai for 3 days with parents, stay near the metro, keep walking light',
      'Chengdu for 4 days, local food, photography, budget 3500'
    ]))

const briefSummary = computed(() => {
  if (!props.detail) {
    return null
  }

  const memory = props.detail.taskMemory
  const slots = [
    {
      key: 'destination',
      label: copy.value.memoryLabels.destination,
      value: normalizeDisplayText(memory.destination) || copy.value.slotMissing,
      complete: Boolean(memory.destination)
    },
    {
      key: 'startDate',
      label: copy.value.memoryLabels.startDate,
      value: normalizeDisplayText(memory.startDate) || copy.value.slotMissing,
      complete: Boolean(memory.startDate)
    },
    {
      key: 'days',
      label: copy.value.memoryLabels.days,
      value: memory.days ? String(memory.days) : copy.value.slotMissing,
      complete: Boolean(memory.days)
    },
    {
      key: 'travelers',
      label: copy.value.memoryLabels.travelers,
      value: normalizeDisplayText(memory.travelers) || copy.value.slotMissing,
      complete: Boolean(memory.travelers)
    },
    {
      key: 'budget',
      label: copy.value.memoryLabels.budget,
      value: normalizeDisplayText(memory.budget) || copy.value.slotMissing,
      complete: Boolean(memory.budget)
    },
    {
      key: 'preferences',
      label: copy.value.memoryLabels.preference,
      value: memory.preferences.length
        ? memory.preferences.map(item => localizePreference(item)).join(' / ')
        : copy.value.slotMissing,
      complete: Boolean(memory.preferences.length)
    }
  ]
  const completed = slots.filter(item => item.complete).length
  const missing = slots.filter(item => !item.complete).map(item => item.label)
  const summary = props.detail.travelPlan
    ? copy.value.briefGenerated
    : completed >= 3
      ? copy.value.briefAlmost
      : copy.value.briefMissing

  return {
    slots,
    missing,
    completed,
    total: slots.length,
    status: completed >= 3 ? copy.value.briefReady : copy.value.briefNeedsWork,
    summary
  }
})

const followUpPrompts = computed(() => {
  if (!props.detail) {
    return starterPrompts.value
  }

  const prompts: string[] = []
  const memory = props.detail.taskMemory

  if (memory.pendingQuestion) {
    prompts.push(normalizeDisplayText(memory.pendingQuestion))
  }

  if (props.detail.travelPlan) {
    prompts.push(...(props.preferChinese
      ? [
          '\u628a\u8282\u594f\u518d\u653e\u677e\u4e00\u70b9',
          '\u4f18\u5148\u5b89\u6392\u672c\u5730\u7f8e\u98df',
          '\u5c3d\u91cf\u628a\u9884\u7b97\u518d\u538b\u4e00\u4e0b'
        ]
      : [
          'Relax the pace a bit more',
          'Prioritize more local food',
          'Try to reduce the budget a little'
        ]))
  } else {
    if (!memory.destination) {
      prompts.push(props.preferChinese
        ? '\u76ee\u7684\u5730\u662f\u676d\u5dde\uff0c\u5468\u672b 2 \u5929'
        : 'Destination is Hangzhou for a 2-day weekend trip')
    }
    if (!memory.days) {
      prompts.push(props.preferChinese
        ? '\u5b89\u6392\u6210 3 \u5929 2 \u665a'
        : 'Make it a 3-day 2-night itinerary')
    }
    if (!memory.budget) {
      prompts.push(props.preferChinese
        ? '\u9884\u7b97\u63a7\u5236\u5728 3000 \u5143\u5de6\u53f3'
        : 'Keep the budget around 3000 CNY')
    }
    if (!memory.preferences.length) {
      prompts.push(props.preferChinese
        ? '\u60f3\u5403\u672c\u5730\u7f8e\u98df\uff0c\u8282\u594f\u8f7b\u677e'
        : 'I want local food and a relaxed pace')
    }
  }

  return [...new Set(prompts.filter(Boolean))].slice(0, 4)
})

const memoryTags = computed(() => {
  if (!props.detail) {
    return []
  }
  const memory = props.detail.taskMemory
  return [
    memory.origin ? `${copy.value.memoryLabels.origin}: ${memory.origin}` : '',
    memory.destination ? `${copy.value.memoryLabels.destination}: ${normalizeDisplayText(memory.destination)}` : '',
    memory.startDate ? `${copy.value.memoryLabels.startDate}: ${normalizeDisplayText(memory.startDate)}` : '',
    memory.endDate ? `${copy.value.memoryLabels.endDate}: ${normalizeDisplayText(memory.endDate)}` : '',
    memory.days ? `${copy.value.memoryLabels.days}: ${memory.days}` : '',
    memory.travelers ? `${copy.value.memoryLabels.travelers}: ${normalizeDisplayText(memory.travelers)}` : '',
    memory.budget ? `${copy.value.memoryLabels.budget}: ${normalizeDisplayText(memory.budget)}` : '',
    ...memory.preferences.map(item => `${copy.value.memoryLabels.preference}: ${localizePreference(item)}`)
  ].filter(Boolean)
})

watch(
  () => props.detail?.taskMemory,
  memory => {
    briefOrigin.value = memory?.origin ?? ''
    briefDestination.value = memory?.destination ?? ''
    briefStartDate.value = memory?.startDate ?? ''
    briefEndDate.value = memory?.endDate ?? ''
    briefDays.value = memory?.days == null ? '' : String(memory.days)
    briefTravelers.value = memory?.travelers ?? ''
    briefBudget.value = memory?.budget ?? ''
    briefPreferences.value = memory?.preferences.join(', ') ?? ''
  },
  { immediate: true }
)

const recognizedImageFacts = computed(() => {
  const facts = pendingImageContext.value?.facts
  if (!facts) {
    return []
  }
  return [
    facts.origin ? { key: 'origin', value: facts.origin } : null,
    facts.destination ? { key: 'destination', value: facts.destination } : null,
    facts.startDate ? { key: 'startDate', value: facts.startDate } : null,
    facts.endDate ? { key: 'endDate', value: facts.endDate } : null,
    facts.days ? { key: 'days', value: String(facts.days) } : null,
    facts.budget ? { key: 'budget', value: facts.budget } : null,
    facts.hotelName ? { key: 'hotelName', value: facts.hotelName } : null,
    facts.hotelArea ? { key: 'hotelArea', value: facts.hotelArea } : null,
    facts.activities.length ? { key: 'activities', value: facts.activities.join(', ') } : null
  ].filter(Boolean) as Array<{ key: string; value: string }>
})

const missingImageFacts = computed(() => {
  const missingFields = pendingImageContext.value?.facts?.missingFields ?? []
  return missingFields.map(field => factLabels.value[field] ?? field)
})

const resultStatusCards = computed(() => {
  const cards: Array<{ title: string; body: string; tone: 'info' | 'warn' }> = []
  for (const issue of resolvedResultView.value.issues) {
    cards.push({
      title: issueLabel(issue.code),
      body: issueMessage(issue.code, issue.message),
      tone: issue.severity === 'WARN' ? 'warn' : 'info'
    })
  }
  return cards
})

function submit() {
  if (!input.value.trim() && attachments.value.length === 0) {
    return
  }

  const shouldConfirmPendingImageContext = pendingImageContext.value && attachments.value.length === 0
  emit('send', {
    message: input.value,
    attachments: attachments.value,
    imageContextAction: shouldConfirmPendingImageContext ? 'CONFIRM' : undefined
  })
  input.value = ''
  attachments.value = []
  attachmentError.value = ''
}

function applyBrief() {
  const preferences = briefPreferences.value
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
  emit('send', {
    message: input.value || undefined,
    brief: {
      origin: briefOrigin.value || undefined,
      destination: briefDestination.value || undefined,
      startDate: briefStartDate.value || undefined,
      endDate: briefEndDate.value || undefined,
      days: briefDays.value ? Number(briefDays.value) : undefined,
      travelers: briefTravelers.value || undefined,
      budget: briefBudget.value || undefined,
      preferences
    }
  })
  input.value = ''
}

function confirmImageContext() {
  emit('send', {
    message: input.value,
    imageContextAction: 'CONFIRM'
  })
  input.value = ''
  attachmentError.value = ''
}

function dismissImageContext() {
  emit('send', {
    imageContextAction: 'DISMISS'
  })
  input.value = ''
  attachmentError.value = ''
}

function openFilePicker() {
  fileInput.value?.click()
}

async function ingestFiles(files: File[]) {
  if (!files.length) {
    return
  }
  if (attachments.value.length + files.length > MAX_ATTACHMENTS) {
    attachmentError.value = copy.value.limitError
    return
  }

  const invalidFile = files.find(file => !ALLOWED_IMAGE_TYPES.has(file.type) || file.size > MAX_ATTACHMENT_BYTES)
  if (invalidFile) {
    attachmentError.value = copy.value.fileError
    return
  }

  const newAttachments = await Promise.all(files.map(async file => ({
    name: file.name,
    mediaType: file.type,
    dataUrl: await readFileAsDataUrl(file)
  })))
  attachments.value = [...attachments.value, ...newAttachments]
  attachmentError.value = ''
}

async function onFilesSelected(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  target.value = ''
  await ingestFiles(files)
}

async function onPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (!files.length) {
    return
  }
  await ingestFiles(files)
}

function onDragEnter() {
  isDragging.value = true
}

function onDragLeave(event: DragEvent) {
  const currentTarget = event.currentTarget as HTMLElement | null
  const relatedTarget = event.relatedTarget as Node | null
  if (currentTarget && relatedTarget && currentTarget.contains(relatedTarget)) {
    return
  }
  isDragging.value = false
}

async function onDrop(event: DragEvent) {
  isDragging.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  await ingestFiles(files)
}

function removeAttachment(index: number) {
  attachments.value = attachments.value.filter((_, itemIndex) => itemIndex !== index)
}

function applyPrompt(prompt: string) {
  input.value = prompt
}

function canRenderFeedback(message: ConversationMessage) {
  return Boolean(resolvedResultView.value.feedbackTarget) && message.role === 'ASSISTANT' && message.id === latestAssistantMessageId.value
}

function feedbackChoiceClass(label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED') {
  return {
    'message__feedback-choice--active': props.feedback?.label === label
  }
}

function feedbackScopeClass(scope: FeedbackTargetScope) {
  return {
    'message__feedback-choice--active': feedbackScope.value === scope
  }
}

function localizePreference(value: string) {
  const trimmed = value.trim()
  if (!props.preferChinese || !trimmed || /[\u4e00-\u9fff]/.test(trimmed)) {
    return trimmed
  }

  const lower = trimmed.toLowerCase()
  const directMap: Record<string, string> = {
    'relaxed pace': '\u8f7b\u677e\u8282\u594f',
    'local food': '\u672c\u5730\u7f8e\u98df',
    'signature sights': '\u6838\u5fc3\u666f\u70b9',
    'family-friendly': '\u4eb2\u5b50\u53cb\u597d',
    family: '\u4eb2\u5b50\u51fa\u884c',
    nightlife: '\u591c\u751f\u6d3b',
    shopping: '\u901b\u8857\u8d2d\u7269',
    museum: '\u535a\u7269\u9986',
    photography: '\u62cd\u7167\u51fa\u7247',
    tea: '\u559d\u8336\u4f53\u9a8c',
    'tea culture': '\u8336\u6587\u5316'
  }

  if (directMap[lower]) {
    return directMap[lower]
  }

  if (lower.startsWith('hotel area:')) {
    return `\u9152\u5e97\u533a\u57df\uff1a${trimmed.slice(trimmed.indexOf(':') + 1).trim()}`
  }

  if (lower.startsWith('hotel:')) {
    return `\u9152\u5e97\uff1a${trimmed.slice(trimmed.indexOf(':') + 1).trim()}`
  }

  if (lower.includes('relaxed')) {
    return '\u8f7b\u677e\u8282\u594f'
  }

  if (lower.includes('food')) {
    return '\u672c\u5730\u7f8e\u98df'
  }

  if (lower.includes('night')) {
    return '\u591c\u751f\u6d3b'
  }

  if (lower.includes('shop')) {
    return '\u901b\u8857\u8d2d\u7269'
  }

  return trimmed
}

function openFeedbackEditor(label: 'PARTIAL' | 'REJECTED') {
  feedbackEditorLabel.value = label
  feedbackScope.value = normalizedFeedbackScope.value
  feedbackReasonLabels.value = []
  feedbackNote.value = ''
}

function closeFeedbackEditor() {
  feedbackEditorLabel.value = ''
  feedbackReasonLabels.value = []
  feedbackNote.value = ''
}

function toggleFeedbackReason(code: string) {
  if (feedbackReasonLabels.value.includes(code)) {
    feedbackReasonLabels.value = feedbackReasonLabels.value.filter(item => item !== code)
    return
  }
  feedbackReasonLabels.value = [...feedbackReasonLabels.value, code]
}

function submitFeedback(label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED', reasonLabels: string[] = [], note = '') {
  if (props.feedbackSaving || !resolvedResultView.value.feedbackTarget) {
    return
  }
  emit('feedback', {
    label,
    targetId: resolvedResultView.value.feedbackTarget.targetId,
    targetScope: label === 'ACCEPTED' ? normalizedFeedbackScope.value : feedbackScope.value,
    planVersion: resolvedResultView.value.feedbackTarget.planVersion ?? undefined,
    reasonLabels,
    reasonCode: reasonLabels[0],
    note: note.trim() || undefined
  })
  if (label !== 'ACCEPTED') {
    closeFeedbackEditor()
  }
}

function submitFeedbackEditor() {
  if (!feedbackEditorLabel.value) {
    return
  }
  submitFeedback(feedbackEditorLabel.value, feedbackReasonLabels.value, feedbackNote.value)
}

function feedbackReasonOptions() {
  if (!feedbackEditorLabel.value) {
    return []
  }
  return feedbackUi.value.reasonOptions[feedbackEditorLabel.value]
}

function issueLabel(code: string) {
  if (!props.preferChinese) {
    return {
      IMAGE_CONTEXT_CONFIRMATION_REQUIRED: 'Image facts pending',
      CLARIFICATION_REQUIRED: 'More detail needed',
      PLAN_REQUIRES_REVIEW: 'Plan still has risk',
      PLAN_REPAIRED: 'Plan passed after repair'
    }[code] ?? code
  }
  return {
    IMAGE_CONTEXT_CONFIRMATION_REQUIRED: '图片信息待确认',
    CLARIFICATION_REQUIRED: '还需要补充信息',
    PLAN_REQUIRES_REVIEW: '方案仍有风险',
    PLAN_REPAIRED: '方案已修复通过'
  }[code] ?? code
}

function issueMessage(code: string, fallback: string) {
  if (!props.preferChinese) {
    return fallback
  }
  return {
    IMAGE_CONTEXT_CONFIRMATION_REQUIRED: '先确认或忽略截图里提取到的旅行事实，再继续规划。',
    CLARIFICATION_REQUIRED: '补齐目的地、天数、预算或偏好后，方案会更稳定。',
    PLAN_REQUIRES_REVIEW: '当前结果还能参考，但建议先处理风险再直接采用。',
    PLAN_REPAIRED: '系统已经为你做过修复，这份方案带着一些取舍。'
  }[code] ?? fallback
}

function missingPromptLabel(code: string, prompt: string) {
  if (!props.preferChinese) {
    return prompt
  }
  return {
    destination: '补一个明确目的地，比如杭州、西湖周边或成都主城区。',
    days: '补一下旅行天数或晚数，比如 3 天 2 晚。',
    budget: '补一个预算上限或期望区间，系统更容易做取舍。',
    preferences: '补一点偏好，比如轻松节奏、本地美食、亲子或拍照。',
    origin: '如果跨城交通重要，补一下出发地。'
  }[code] ?? prompt
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error(`Failed to read ${file.name}`))
    reader.readAsDataURL(file)
  })
}

function imageAttachments(message: ConversationMessage) {
  return message.metadata?.imageAttachments ?? []
}

function messageTimeLabel(value: string) {
  return new Date(value).toLocaleTimeString(props.preferChinese ? 'zh-CN' : undefined, {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInline(value: string) {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

function markdownToHtml(markdown: string) {
  const lines = markdown.replace(/\r/g, '').split('\n')
  let html = ''
  let inList = false

  const closeList = () => {
    if (inList) {
      html += '</ul>'
      inList = false
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      closeList()
      continue
    }
    if (line.startsWith('### ')) {
      closeList()
      html += `<h3>${renderInline(normalizeDisplayText(line.slice(4)))}</h3>`
      continue
    }
    if (line.startsWith('## ')) {
      closeList()
      html += `<h2>${renderInline(normalizeDisplayText(line.slice(3)))}</h2>`
      continue
    }
    if (line.startsWith('# ')) {
      closeList()
      html += `<h1>${renderInline(normalizeDisplayText(line.slice(2)))}</h1>`
      continue
    }
    if (line.startsWith('- ')) {
      if (!inList) {
        html += '<ul>'
        inList = true
      }
      html += `<li>${renderInline(normalizeDisplayText(line.slice(2)))}</li>`
      continue
    }
    closeList()
    html += `<p>${renderInline(normalizeDisplayText(line))}</p>`
  }

  closeList()
  return html
}
</script>

<template>
  <section class="panel chat-panel">
    <div class="panel__header">
      <div class="panel__header-info">
        <div class="panel__icon-badge">
          <MessageSquare :size="18" />
        </div>
        <div>
          <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
          <h2>{{ copy.title }}</h2>
          <p v-if="detail?.taskMemory.pendingQuestion" class="panel__subtle">
            <Info :size="12" />
            {{ copy.pendingQuestion }}: {{ normalizeDisplayText(detail.taskMemory.pendingQuestion) }}
          </p>
        </div>
      </div>
      <div class="memory-tags">
        <span class="memory-tag memory-tag--count">{{ copy.messageCount(detail?.messages.length ?? 0) }}</span>
        <span v-for="tag in memoryTags" :key="tag" class="memory-tag">{{ tag }}</span>
      </div>
    </div>

    <article v-if="briefSummary" class="brief-card">
      <div class="brief-card__header">
        <div class="brief-card__title-group">
          <Sparkles :size="16" class="brief-card__sparkle" />
          <div>
            <span class="brief-card__eyebrow">{{ copy.briefTitle }}</span>
            <strong>{{ briefSummary.status }}</strong>
            <p>{{ briefSummary.summary }}</p>
          </div>
        </div>
        <div class="brief-card__score">{{ copy.briefScore(briefSummary.completed, briefSummary.total) }}</div>
      </div>

      <div class="brief-card__grid">
        <article
          v-for="slot in briefSummary.slots"
          :key="slot.key"
          class="brief-card__slot"
          :class="{ 'brief-card__slot--done': slot.complete }"
        >
          <span>{{ slot.label }}</span>
          <strong>{{ slot.value }}</strong>
        </article>
      </div>

      <div v-if="briefSummary.missing.length" class="brief-card__missing">
        <span>{{ copy.briefMissingLabel }}</span>
        <p>{{ briefSummary.missing.join(' / ') }}</p>
      </div>

      <div class="brief-card__grid">
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.origin }}</span>
          <input v-model="briefOrigin" class="message__feedback-note" type="text" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.destination }}</span>
          <input v-model="briefDestination" class="message__feedback-note" type="text" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.startDate }}</span>
          <input v-model="briefStartDate" class="message__feedback-note" type="date" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.endDate }}</span>
          <input v-model="briefEndDate" class="message__feedback-note" type="date" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.days }}</span>
          <input v-model="briefDays" class="message__feedback-note" type="number" min="1" max="30" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.travelers }}</span>
          <input v-model="briefTravelers" class="message__feedback-note" type="text" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.budget }}</span>
          <input v-model="briefBudget" class="message__feedback-note" type="text" />
        </label>
        <label class="brief-card__slot">
          <span>{{ copy.memoryLabels.preference }}</span>
          <input v-model="briefPreferences" class="message__feedback-note" type="text" />
        </label>
      </div>

      <div class="message__actions">
        <button
          type="button"
          class="message__feedback-choice message__feedback-choice--active"
          :disabled="sending"
          @click="applyBrief"
        >
          {{ copy.briefApply }}
        </button>
      </div>
    </article>

    <article v-if="resultStatusCards.length || resolvedResultView.missingInformation.length" class="brief-card brief-card--status">
      <div class="brief-card__header">
        <div class="brief-card__title-group">
          <AlertCircle :size="16" class="brief-card__sparkle" />
          <div>
            <span class="brief-card__eyebrow">{{ feedbackUi.issueTitle }}</span>
            <strong>{{ resolvedResultView.chatState === 'success' ? copy.briefReady : copy.feedbackAdjust }}</strong>
          </div>
        </div>
      </div>

      <div v-if="resultStatusCards.length" class="brief-card__grid">
        <article
          v-for="card in resultStatusCards"
          :key="`${card.title}-${card.body}`"
          class="brief-card__slot"
          :class="{ 'brief-card__slot--done': card.tone === 'info' }"
        >
          <span>{{ card.title }}</span>
          <strong>{{ card.body }}</strong>
        </article>
      </div>

      <div v-if="resolvedResultView.missingInformation.length" class="brief-card__missing">
        <span>{{ feedbackUi.missingTitle }}</span>
        <p>
          {{ resolvedResultView.missingInformation.map(item => `${item.label}: ${missingPromptLabel(item.code, item.prompt)}`).join(' / ') }}
        </p>
      </div>
    </article>

    <div class="chat-list">
      <div v-if="!detail" class="chat-empty">
        <h3>{{ copy.emptyTitle }}</h3>
        <p>{{ copy.emptyBody }}</p>
        <div class="chat-empty__prompts">
          <button
            v-for="prompt in starterPrompts"
            :key="prompt"
            type="button"
            class="chat-empty__prompt"
            @click="applyPrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </div>
        <span class="chat-empty__hint">{{ copy.quickStart }}</span>
      </div>

      <article
        v-for="message in detail?.messages || []"
        :key="message.id"
        class="message"
        :class="message.role === 'USER' ? 'message--user' : 'message--assistant'"
      >
        <div class="message__meta">
          <span class="message__role">{{ message.role === 'USER' ? copy.user : copy.assistant }}</span>
          <time class="message__time">{{ messageTimeLabel(message.createdAt) }}</time>
        </div>
        <div v-if="message.role === 'ASSISTANT'" class="message__markdown" v-html="markdownToHtml(message.content)" />
        <p v-else>{{ normalizeDisplayText(message.content) }}</p>
        <div v-if="imageAttachments(message).length" class="message__attachments">
          <span
            v-for="attachment in imageAttachments(message)"
            :key="attachment.id"
            class="message__attachment-pill"
          >
            {{ attachment.name }}
          </span>
        </div>
        <div v-if="canRenderFeedback(message)" class="message__actions">
          <button
            type="button"
            class="message__feedback-choice"
            :class="feedbackChoiceClass('ACCEPTED')"
            :disabled="feedbackSaving"
            @click="submitFeedback('ACCEPTED')"
          >
            {{ feedbackSaving && feedback?.label === 'ACCEPTED' ? copy.feedbackSaving : copy.feedbackAccepted }}
          </button>
          <button
            type="button"
            class="message__feedback-choice"
            :class="feedbackChoiceClass('PARTIAL')"
            :disabled="feedbackSaving"
            @click="openFeedbackEditor('PARTIAL')"
          >
            {{ feedbackSaving && feedback?.label === 'PARTIAL' ? copy.feedbackSaving : copy.feedbackPartial }}
          </button>
          <button
            type="button"
            class="message__feedback-choice"
            :class="feedbackChoiceClass('REJECTED')"
            :disabled="feedbackSaving"
            @click="openFeedbackEditor('REJECTED')"
          >
            {{ feedbackSaving && feedback?.label === 'REJECTED' ? copy.feedbackSaving : copy.feedbackRejected }}
          </button>
        </div>
        <div v-if="canRenderFeedback(message) && feedbackEditorLabel" class="message__feedback-editor">
          <div class="message__feedback-scopes">
            <span>{{ feedbackUi.scopeHint }}</span>
            <button
              v-for="scope in availableFeedbackScopes"
              :key="scope"
              type="button"
              class="message__feedback-choice"
              :class="feedbackScopeClass(scope)"
              :disabled="feedbackSaving"
              @click="feedbackScope = scope"
            >
              {{ feedbackUi.scopeLabels[scope] }}
            </button>
          </div>
          <div class="message__feedback-reasons">
            <button
              v-for="option in feedbackReasonOptions()"
              :key="option.code"
              type="button"
              class="composer__suggestion"
              :class="{ 'message__feedback-choice--active': feedbackReasonLabels.includes(option.code) }"
              :disabled="feedbackSaving"
              @click="toggleFeedbackReason(option.code)"
            >
              {{ option.label }}
            </button>
          </div>
          <textarea
            v-model="feedbackNote"
            rows="3"
            class="message__feedback-note"
            :placeholder="feedbackUi.notePlaceholder"
          />
          <div class="message__actions">
            <button
              type="button"
              class="message__feedback-choice message__feedback-choice--active"
              :disabled="feedbackSaving || !feedbackReasonLabels.length"
              @click="submitFeedbackEditor"
            >
              {{ feedbackUi.saveLabel }}
            </button>
            <button
              type="button"
              class="message__feedback-choice"
              :disabled="feedbackSaving"
              @click="closeFeedbackEditor"
            >
              {{ feedbackUi.cancelLabel }}
            </button>
          </div>
        </div>
      </article>

      <div v-if="sending" class="message message--assistant message--thinking">
        <div class="message__meta">
          <span class="message__role">{{ copy.assistant }}</span>
          <span class="message__status">{{ copy.thinking }}</span>
        </div>
        <div class="message__loader">
          <div class="dot"></div>
          <div class="dot"></div>
          <div class="dot"></div>
        </div>
      </div>
    </div>

    <div class="composer">
      <div v-if="errorMessage" class="composer__error">
        <AlertCircle :size="14" />
        <span>{{ errorMessage }}</span>
      </div>
      <div v-if="attachmentError" class="composer__error">
        <AlertCircle :size="14" />
        <span>{{ attachmentError }}</span>
      </div>

      <article v-if="pendingImageContext" class="composer__image-context">
        <div class="composer__image-context-header">
          <div class="composer__image-context-title">
            <Sparkles :size="14" />
            <strong>{{ copy.imageContextTitle }}</strong>
          </div>
          <span class="composer__attachment-count">{{ copy.imageCount(pendingImageContext.attachments.length) }}</span>
        </div>
        <p>{{ normalizeDisplayText(pendingImageContext.summary) }}</p>
        <div v-if="recognizedImageFacts.length" class="composer__image-fact-list">
          <article
            v-for="fact in recognizedImageFacts"
            :key="fact.key"
            class="composer__image-fact"
          >
            <span>{{ factLabels[fact.key] }}</span>
            <strong>{{ fact.value }}</strong>
          </article>
        </div>
        <div v-if="missingImageFacts.length" class="composer__image-missing">
          <AlertCircle :size="12" />
          <strong>{{ copy.needsInput }}</strong>
          <p>{{ missingImageFacts.join(', ') }}</p>
        </div>
        <div class="message__attachments">
          <span
            v-for="attachment in pendingImageContext.attachments"
            :key="attachment.id"
            class="message__attachment-pill"
          >
            <Image :size="12" />
            {{ attachment.name }}
          </span>
        </div>
        <div class="composer__image-context-actions">
          <button class="composer__attach" type="button" :disabled="sending" @click="confirmImageContext">
            <CheckCircle2 :size="14" />
            {{ copy.useFacts }}
          </button>
          <button class="composer__attachment-remove" type="button" :disabled="sending" @click="dismissImageContext">
            <XCircle :size="14" />
            {{ copy.ignoreFacts }}
          </button>
        </div>
      </article>

      <input
        ref="fileInput"
        class="composer__file-input"
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        multiple
        @change="onFilesSelected"
      />

      <div v-if="attachments.length" class="composer__attachments">
        <article
          v-for="(attachment, index) in attachments"
          :key="`${attachment.name}-${index}`"
          class="composer__attachment"
        >
          <img :src="attachment.dataUrl" :alt="attachment.name" class="composer__attachment-preview" />
          <div class="composer__attachment-meta">
            <strong>{{ attachment.name }}</strong>
            <span>{{ attachment.mediaType }}</span>
          </div>
          <button class="composer__attachment-remove" type="button" @click="removeAttachment(index)">
            <XCircle :size="14" />
            {{ copy.remove }}
          </button>
        </article>
      </div>

      <div
        class="composer__entry"
        :class="{ 'composer__entry--dragging': isDragging }"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent="onDragEnter"
        @dragleave="onDragLeave"
        @drop.prevent="onDrop"
      >
        <p class="composer__hint">
          <Image :size="14" />
          {{ copy.uploadHint }}
          <button type="button" class="composer__upload-link" @click="openFilePicker">{{ copy.uploadAction }}</button>
        </p>
        <textarea
          v-model="input"
          rows="4"
          :placeholder="copy.placeholder"
          @keydown.ctrl.enter.prevent="submit"
          @paste="onPaste"
        />
        <button class="composer__submit" :disabled="sending" @click="submit">
          <Send :size="18" v-if="!sending" />
          <div class="dot-loader" v-else></div>
          <div class="composer__submit-text">
            <span class="composer__submit-main">{{ copy.submit }}</span>
            <span class="composer__submit-sub">{{ copy.submitHint }}</span>
          </div>
        </button>
      </div>

      <div class="composer__assistant">
        <div>
          <strong>{{ copy.composerTitle }}</strong>
          <p>{{ copy.composerBody }}</p>
        </div>
      </div>

      <div v-if="detail && followUpPrompts.length" class="composer__suggestions">
        <span class="composer__suggestions-label">{{ copy.followUpLabel }}</span>
        <button
          v-for="prompt in followUpPrompts"
          :key="prompt"
          type="button"
          class="composer__suggestion"
          @click="applyPrompt(prompt)"
        >
          {{ prompt }}
        </button>
      </div>
    </div>
  </section>
</template>
