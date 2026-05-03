<script setup lang="ts">
import { computed } from 'vue'
import { Plus, Trash2, Map, History, Clock } from 'lucide-vue-next'
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
      eyebrow: 'TravelAgent',
      title: '\u6211\u7684\u884c\u7a0b',
      count: (value: number) => value ? `${value} \u4efd\u5df2\u4fdd\u5b58` : '\u8fd8\u6ca1\u6709\u4fdd\u5b58\u884c\u7a0b',
      create: '\u65b0\u5efa\u8ba1\u5212',
      recent: '\u6700\u8fd1\u66f4\u65b0',
      loading: '\u6b63\u5728\u52a0\u8f7d...',
      empty: '\u4ece\u53f3\u4fa7\u8f93\u5165\u4e00\u53e5\u65c5\u884c\u9700\u6c42\uff0c\u8fd9\u91cc\u4f1a\u4fdd\u7559\u4f60\u7684\u884c\u7a0b\u3002',
      fallback: '\u884c\u7a0b\u751f\u6210\u540e\u4f1a\u81ea\u52a8\u663e\u793a\u6458\u8981\u3002',
      remove: '\u5220\u9664'
    }
  : {
      eyebrow: 'TravelAgent',
      title: 'My Trips',
      count: (value: number) => value ? `${value} saved` : 'No saved trips yet',
      create: 'New Plan',
      recent: 'Recently Updated',
      loading: 'Loading...',
      empty: 'Start with one travel request on the right and your trips will stay here.',
      fallback: 'A short summary appears after the itinerary is generated.',
      remove: 'Delete'
    }))

function timeLabel(value: string) {
  return new Date(value).toLocaleString(props.preferChinese ? 'zh-CN' : undefined)
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__brand">
      <div class="sidebar__brand-mark">
        <Map :size="20" />
      </div>
      <div class="sidebar__header">
        <p class="sidebar__eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.title }}</h1>
        <span class="sidebar__caption">{{ copy.count(conversations.length) }}</span>
      </div>
    </div>

    <button class="sidebar__new" @click="emit('create')">
      <Plus :size="18" />
      {{ copy.create }}
    </button>

    <div class="sidebar__section-label">
      <History :size="14" />
      {{ copy.recent }}
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
        <div class="sidebar__item-content">
          <strong>{{ normalizeDisplayText(conversation.title) }}</strong>
          <p>{{ normalizeDisplayText(conversation.summary) || copy.fallback }}</p>
        </div>
        <div class="sidebar__item-footer">
          <div class="sidebar__time">
            <Clock :size="12" />
            <span>{{ timeLabel(conversation.updatedAt) }}</span>
          </div>
          <button class="sidebar__delete" @click.stop="emit('remove', conversation.conversationId)">
            <Trash2 :size="14" />
          </button>
        </div>
      </article>
    </div>
  </aside>
</template>
