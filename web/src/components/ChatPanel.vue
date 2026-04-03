<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ConversationDetailResponse } from '../types/api'

const props = defineProps<{
  detail: ConversationDetailResponse | null
  sending: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  send: [message: string]
}>()

const input = ref('')

const memoryTags = computed(() => {
  if (!props.detail) {
    return []
  }
  const memory = props.detail.taskMemory
  return [
    memory.origin ? `出发地：${memory.origin}` : '',
    memory.destination ? `目的地：${memory.destination}` : '',
    memory.days ? `天数：${memory.days}` : '',
    memory.budget ? `预算：${memory.budget}` : '',
    ...memory.preferences.map(item => `偏好：${item}`)
  ].filter(Boolean)
})

function submit() {
  if (!input.value.trim()) {
    return
  }
  emit('send', input.value)
  input.value = ''
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
        <p class="panel__eyebrow">对话</p>
        <h2>{{ detail?.conversation.title || '新的旅行规划' }}</h2>
      </div>
      <div class="memory-tags">
        <span v-for="tag in memoryTags" :key="tag">{{ tag }}</span>
      </div>
    </div>

    <div class="chat-list">
      <div v-if="!detail" class="chat-empty">
        <h3>先告诉我这次出行的基本条件</h3>
        <p>例如：帮我规划 3 天杭州行程，从上海出发，预算 3000，想去西湖和灵隐寺，整体轻松一点。</p>
      </div>

      <article
        v-for="message in detail?.messages || []"
        :key="message.id"
        class="message"
        :class="message.role === 'USER' ? 'message--user' : 'message--assistant'"
      >
        <span class="message__role">{{ message.role === 'USER' ? '你' : '助手' }}</span>
        <div v-if="message.role === 'ASSISTANT'" class="message__markdown" v-html="markdownToHtml(message.content)" />
        <p v-else>{{ message.content }}</p>
      </article>
    </div>

    <div class="composer">
      <p v-if="errorMessage" class="composer__error">{{ errorMessage }}</p>
      <textarea
        v-model="input"
        rows="4"
        placeholder="输入出发地、目的地、天数、预算、想去的地方和偏好..."
        @keydown.ctrl.enter.prevent="submit"
      />
      <button class="composer__submit" :disabled="sending" @click="submit">
        <span class="composer__submit-main">{{ sending ? '规划中' : '开始规划' }}</span>
        <span class="composer__submit-sub">{{ sending ? '请稍候' : 'Ctrl + Enter' }}</span>
      </button>
    </div>
  </section>
</template>
