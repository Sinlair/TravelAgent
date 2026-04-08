<script setup lang="ts">
import { computed } from 'vue'
import type { ConversationSession } from '../types/api'
import { normalizeDisplayText } from '../utils/text'

const props = withDefaults(defineProps<{
  conversations: ConversationSession[]
  currentConversationId: string
  loading: boolean
  preferChinese?: boolean
}>(), {
  preferChinese: true
})

const emit = defineEmits<{
  select: [conversationId: string]
  create: []
  remove: [conversationId: string]
}>()

const copy = computed(() => (props.preferChinese
  ? {
      title: '\u6211\u7684\u884c\u7a0b',
      create: '\u65b0\u5efa\u884c\u7a0b',
      loading: '\u6b63\u5728\u52a0\u8f7d\u4f1a\u8bdd...',
      empty: '\u8fd8\u6ca1\u6709\u5386\u53f2\u4f1a\u8bdd\uff0c\u5148\u53d1\u4e00\u53e5\u65c5\u884c\u9700\u6c42\u3002',
      fallback: '\u751f\u6210\u65b9\u6848\u540e\uff0c\u8fd9\u91cc\u4f1a\u663e\u793a\u4e00\u6bb5\u6458\u8981\u3002',
      remove: '\u5220\u9664'
    }
  : {
      title: 'My Trips',
      create: 'New Plan',
      loading: 'Loading conversations...',
      empty: 'No saved conversations yet. Start with a travel request.',
      fallback: 'A short summary will appear here after the itinerary is generated.',
      remove: 'Delete'
    }))

function timeLabel(value: string) {
  return new Date(value).toLocaleString(props.preferChinese ? 'zh-CN' : undefined)
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__header">
      <h1>{{ copy.title }}</h1>
      <button class="sidebar__new" @click="emit('create')">{{ copy.create }}</button>
    </div>

    <div class="sidebar__list">
      <div v-if="loading" class="sidebar__empty">{{ copy.loading }}</div>
      <div v-else-if="conversations.length === 0" class="sidebar__empty">{{ copy.empty }}</div>

      <article
        v-for="conversation in conversations"
        :key="conversation.conversationId"
        class="sidebar__item"
        :class="{ 'sidebar__item--active': conversation.conversationId === currentConversationId }"
        @click="emit('select', conversation.conversationId)"
      >
        <div>
          <strong>{{ normalizeDisplayText(conversation.title) }}</strong>
          <p>{{ normalizeDisplayText(conversation.summary) || copy.fallback }}</p>
          <p>{{ timeLabel(conversation.updatedAt) }}</p>
        </div>
        <button class="sidebar__delete" @click.stop="emit('remove', conversation.conversationId)">{{ copy.remove }}</button>
      </article>
    </div>
  </aside>
</template>
