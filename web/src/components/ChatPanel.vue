<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ChatImageAttachmentRequest, ChatRequest, ConversationDetailResponse, ConversationMessage } from '../types/api'

const props = withDefaults(defineProps<{
  detail: ConversationDetailResponse | null
  sending: boolean
  errorMessage: string
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const emit = defineEmits<{
  send: [payload: ChatRequest]
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

const factLabels = computed<Record<string, string>>(() => props.preferChinese
  ? {
      origin: '出发地',
      destination: '目的地',
      startDate: '开始日期',
      endDate: '结束日期',
      days: '天数',
      budget: '预算',
      hotelName: '酒店',
      hotelArea: '酒店区域',
      activities: '活动偏好'
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
      eyebrow: '对话面板',
      title: props.detail?.conversation.title || '新的旅行规划会话',
      emptyTitle: '先把目的地、天数和预算说清楚',
      emptyBody: '你可以直接描述旅行需求，也可以粘贴或拖拽截图，让系统从图片里补充旅行信息。',
      user: '用户',
      assistant: '助手',
      memoryLabels: {
        origin: '出发地',
        destination: '目的地',
        days: '天数',
        budget: '预算',
        preference: '偏好'
      },
      imageContextTitle: '图片信息待确认',
      imageCount: (count: number) => `${count} 张图片`,
      needsInput: '还需要补充',
      useFacts: '使用这些信息',
      ignoreFacts: '忽略',
      remove: '移除',
      uploadHint: '支持粘贴截图、拖拽图片，或',
      uploadAction: '点击这里上传',
      placeholder: '输入目的地、天数、预算、偏好，或继续补充这次行程想法...',
      submit: props.sending ? '生成中...' : '生成方案',
      submitHint: props.sending ? '请稍候' : 'Ctrl + Enter',
      limitError: `每轮最多上传 ${MAX_ATTACHMENTS} 张图片。`,
      fileError: '仅支持 PNG、JPEG、WEBP、GIF，且单张图片不能超过 5 MB。'
    }
  : {
      eyebrow: 'Conversation',
      title: props.detail?.conversation.title || 'New Trip Planning Session',
      emptyTitle: 'Start with the trip basics.',
      emptyBody: 'Describe the trip directly, or paste and drag travel screenshots so the system can pull useful facts from them.',
      user: 'User',
      assistant: 'Assistant',
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
    memory.destination ? `${copy.value.memoryLabels.destination}: ${memory.destination}` : '',
    memory.days ? `${copy.value.memoryLabels.days}: ${memory.days}` : '',
    memory.budget ? `${copy.value.memoryLabels.budget}: ${memory.budget}` : '',
    ...memory.preferences.map(item => `${copy.value.memoryLabels.preference}: ${item}`)
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
      html += `<h3>${renderInline(line.slice(4))}</h3>`
      continue
    }
    if (line.startsWith('## ')) {
      closeList()
      html += `<h2>${renderInline(line.slice(3))}</h2>`
      continue
    }
    if (line.startsWith('# ')) {
      closeList()
      html += `<h1>${renderInline(line.slice(2))}</h1>`
      continue
    }
    if (line.startsWith('- ')) {
      if (!inList) {
        html += '<ul>'
        inList = true
      }
      html += `<li>${renderInline(line.slice(2))}</li>`
      continue
    }
    closeList()
    html += `<p>${renderInline(line)}</p>`
  }

  closeList()
  return html
}
</script>

<template>
  <section class="panel chat-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">{{ copy.eyebrow }}</p>
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
        <p v-else>{{ message.content }}</p>
        <div v-if="imageAttachments(message).length" class="message__attachments">
          <span
            v-for="attachment in imageAttachments(message)"
            :key="attachment.id"
            class="message__attachment-pill"
          >
            {{ attachment.name }}
          </span>
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
        <p>{{ pendingImageContext.summary }}</p>
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
