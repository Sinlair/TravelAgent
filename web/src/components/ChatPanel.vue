<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ChatImageAttachmentRequest, ChatRequest, ConversationDetailResponse, ConversationMessage } from '../types/api'

const props = defineProps<{
  detail: ConversationDetailResponse | null
  sending: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  send: [payload: ChatRequest]
}>()

const input = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const attachments = ref<ChatImageAttachmentRequest[]>([])
const attachmentError = ref('')

const MAX_ATTACHMENTS = 4
const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif'])
const pendingImageContext = computed(() => props.detail?.imageContextCandidate ?? null)
const factLabels: Record<string, string> = {
  origin: 'Origin',
  destination: 'Destination',
  startDate: 'Start Date',
  endDate: 'End Date',
  days: 'Days',
  budget: 'Budget',
  hotelName: 'Hotel',
  hotelArea: 'Hotel Area',
  activities: 'Activities'
}

const memoryTags = computed(() => {
  if (!props.detail) {
    return []
  }
  const memory = props.detail.taskMemory
  return [
    memory.origin ? `Origin: ${memory.origin}` : '',
    memory.destination ? `Destination: ${memory.destination}` : '',
    memory.days ? `Days: ${memory.days}` : '',
    memory.budget ? `Budget: ${memory.budget}` : '',
    ...memory.preferences.map(item => `Preference: ${item}`)
  ].filter(Boolean)
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

async function onFilesSelected(event: Event) {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  target.value = ''
  if (!files.length) {
    return
  }
  if (attachments.value.length + files.length > MAX_ATTACHMENTS) {
    attachmentError.value = `You can attach up to ${MAX_ATTACHMENTS} images per turn.`
    return
  }
  const invalidFile = files.find(file => !ALLOWED_IMAGE_TYPES.has(file.type) || file.size > MAX_ATTACHMENT_BYTES)
  if (invalidFile) {
    attachmentError.value = 'Only PNG, JPEG, WEBP, and GIF images up to 5 MB are supported.'
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

const recognizedImageFacts = computed(() => {
  const facts = pendingImageContext.value?.facts
  if (!facts) {
    return []
  }
  const entries = [
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
  return entries
})

const missingImageFacts = computed(() => {
  const missingFields = pendingImageContext.value?.facts?.missingFields ?? []
  return missingFields.map(field => factLabels[field] ?? field)
})

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
        <p class="panel__eyebrow">Conversation</p>
        <h2>{{ detail?.conversation.title || 'New Trip Planning Session' }}</h2>
      </div>
      <div class="memory-tags">
        <span v-for="tag in memoryTags" :key="tag">{{ tag }}</span>
      </div>
    </div>

    <div class="chat-list">
      <div v-if="!detail" class="chat-empty">
        <h3>Start with the trip basics.</h3>
        <p>Type the destination, trip length, budget, and preferences, or upload travel screenshots for context.</p>
      </div>

      <article
        v-for="message in detail?.messages || []"
        :key="message.id"
        class="message"
        :class="message.role === 'USER' ? 'message--user' : 'message--assistant'"
      >
        <span class="message__role">{{ message.role === 'USER' ? 'User' : 'Assistant' }}</span>
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
          <strong>Image Facts Awaiting Confirmation</strong>
          <span>{{ pendingImageContext.attachments.length }} image{{ pendingImageContext.attachments.length > 1 ? 's' : '' }}</span>
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
          <strong>Needs More Input</strong>
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
          <button class="composer__attach" type="button" :disabled="sending" @click="confirmImageContext">Use These Facts</button>
          <button class="composer__attachment-remove" type="button" :disabled="sending" @click="dismissImageContext">Ignore</button>
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
      <div class="composer__toolbar">
        <button class="composer__attach" type="button" :disabled="sending" @click="openFilePicker">Add Images</button>
      </div>
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
          <button class="composer__attachment-remove" type="button" @click="removeAttachment(index)">Remove</button>
        </article>
      </div>
      <textarea
        v-model="input"
        rows="4"
        placeholder="Type the destination, trip length, budget, preferences, or explain how the uploaded images should be used..."
        @keydown.ctrl.enter.prevent="submit"
      />
      <button class="composer__submit" :disabled="sending" @click="submit">
        <span class="composer__submit-main">{{ sending ? 'Planning...' : 'Plan Trip' }}</span>
        <span class="composer__submit-sub">{{ sending ? 'Please wait' : 'Ctrl + Enter' }}</span>
      </button>
    </div>
  </section>
</template>
