<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  ChatImageAttachmentRequest,
  ChatRequest,
  ConversationDetailResponse,
  ConversationFeedback,
  ConversationFeedbackRequest,
  ConversationMessage
} from '../types/api'
import { normalizeDisplayText } from '../utils/text'

const props = withDefaults(defineProps<{
  detail: ConversationDetailResponse | null
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

const MAX_ATTACHMENTS = 4
const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif'])

const pendingImageContext = computed(() => props.detail?.imageContextCandidate ?? null)
const latestAssistantMessageId = computed(() =>
  [...(props.detail?.messages ?? [])].reverse().find(message => message.role === 'ASSISTANT')?.id ?? ''
)

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
      title: normalizeDisplayText(props.detail?.conversation.title) || '\u65b0\u7684\u65c5\u884c\u89c4\u5212\u4f1a\u8bdd',
      emptyTitle: '\u5148\u628a\u76ee\u7684\u5730\u3001\u5929\u6570\u548c\u9884\u7b97\u8bf4\u6e05\u695a',
      emptyBody: '\u4f60\u53ef\u4ee5\u76f4\u63a5\u63cf\u8ff0\u65c5\u884c\u9700\u6c42\uff0c\u4e5f\u53ef\u4ee5\u7c98\u8d34\u6216\u62d6\u62fd\u622a\u56fe\uff0c\u8ba9\u7cfb\u7edf\u4ece\u56fe\u7247\u91cc\u8865\u5145\u65c5\u884c\u4fe1\u606f\u3002',
      user: '\u7528\u6237',
      assistant: '\u52a9\u624b',
      feedbackSaving: '\u63d0\u4ea4\u4e2d...',
      feedbackAccepted: '\u63a5\u53d7',
      feedbackPartial: '\u90e8\u5206\u63a5\u53d7',
      feedbackRejected: '\u62d2\u7edd',
      memoryLabels: {
        origin: '\u51fa\u53d1\u5730',
        destination: '\u76ee\u7684\u5730',
        days: '\u5929\u6570',
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
      title: normalizeDisplayText(props.detail?.conversation.title) || 'New Trip Planning Session',
      emptyTitle: 'Start with the trip basics.',
      emptyBody: 'Describe the trip directly, or paste and drag travel screenshots so the system can pull useful facts from them.',
      user: 'User',
      assistant: 'Assistant',
      feedbackSaving: 'Saving...',
      feedbackAccepted: 'Accept',
      feedbackPartial: 'Partially Accept',
      feedbackRejected: 'Reject',
      memoryLabels: {
        origin: 'Origin',
        destination: 'Destination',
        days: 'Days',
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

const memoryTags = computed(() => {
  if (!props.detail) {
    return []
  }
  const memory = props.detail.taskMemory
  return [
    memory.origin ? `${copy.value.memoryLabels.origin}: ${memory.origin}` : '',
    memory.destination ? `${copy.value.memoryLabels.destination}: ${normalizeDisplayText(memory.destination)}` : '',
    memory.days ? `${copy.value.memoryLabels.days}: ${memory.days}` : '',
    memory.budget ? `${copy.value.memoryLabels.budget}: ${normalizeDisplayText(memory.budget)}` : '',
    ...memory.preferences.map(item => `${copy.value.memoryLabels.preference}: ${localizePreference(item)}`)
  ].filter(Boolean)
})

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

function canRenderFeedback(message: ConversationMessage) {
  return Boolean(props.detail?.travelPlan) && message.role === 'ASSISTANT' && message.id === latestAssistantMessageId.value
}

function feedbackChoiceClass(label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED') {
  return {
    'message__feedback-choice--active': props.feedback?.label === label
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

function submitFeedback(label: 'ACCEPTED' | 'PARTIAL' | 'REJECTED', reasonCode?: string) {
  if (props.feedbackSaving) {
    return
  }
  emit('feedback', { label, reasonCode })
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
      <div>
        <h2>{{ copy.title }}</h2>
      </div>
      <div class="memory-tags">
        <span v-for="tag in memoryTags" :key="tag">{{ tag }}</span>
      </div>
    </div>

    <div class="chat-list">
      <div v-if="!detail" class="chat-empty">
        <h3>{{ copy.emptyTitle }}</h3>
        <p>{{ copy.emptyBody }}</p>
      </div>

      <article
        v-for="message in detail?.messages || []"
        :key="message.id"
        class="message"
        :class="message.role === 'USER' ? 'message--user' : 'message--assistant'"
      >
        <span class="message__role">{{ message.role === 'USER' ? copy.user : copy.assistant }}</span>
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
            @click="submitFeedback('ACCEPTED', 'used_as_is')"
          >
            {{ feedbackSaving && feedback?.label === 'ACCEPTED' ? copy.feedbackSaving : copy.feedbackAccepted }}
          </button>
          <button
            type="button"
            class="message__feedback-choice"
            :class="feedbackChoiceClass('PARTIAL')"
            :disabled="feedbackSaving"
            @click="submitFeedback('PARTIAL', 'edited_before_use')"
          >
            {{ feedbackSaving && feedback?.label === 'PARTIAL' ? copy.feedbackSaving : copy.feedbackPartial }}
          </button>
          <button
            type="button"
            class="message__feedback-choice"
            :class="feedbackChoiceClass('REJECTED')"
            :disabled="feedbackSaving"
            @click="submitFeedback('REJECTED', 'not_useful')"
          >
            {{ feedbackSaving && feedback?.label === 'REJECTED' ? copy.feedbackSaving : copy.feedbackRejected }}
          </button>
        </div>
      </article>
    </div>

    <div class="composer">
      <p v-if="errorMessage" class="composer__error">{{ errorMessage }}</p>
      <p v-if="attachmentError" class="composer__error">{{ attachmentError }}</p>

      <article v-if="pendingImageContext" class="composer__image-context">
        <div class="composer__image-context-header">
          <strong>{{ copy.imageContextTitle }}</strong>
          <span>{{ copy.imageCount(pendingImageContext.attachments.length) }}</span>
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
          <strong>{{ copy.needsInput }}</strong>
          <p>{{ missingImageFacts.join(', ') }}</p>
        </div>
        <div class="message__attachments">
          <span
            v-for="attachment in pendingImageContext.attachments"
            :key="attachment.id"
            class="message__attachment-pill"
          >
            {{ attachment.name }}
          </span>
        </div>
        <div class="composer__image-context-actions">
          <button class="composer__attach" type="button" :disabled="sending" @click="confirmImageContext">{{ copy.useFacts }}</button>
          <button class="composer__attachment-remove" type="button" :disabled="sending" @click="dismissImageContext">{{ copy.ignoreFacts }}</button>
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
          <button class="composer__attachment-remove" type="button" @click="removeAttachment(index)">{{ copy.remove }}</button>
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
          <span class="composer__submit-main">{{ copy.submit }}</span>
          <span class="composer__submit-sub">{{ copy.submitHint }}</span>
        </button>
      </div>
    </div>
  </section>
</template>
