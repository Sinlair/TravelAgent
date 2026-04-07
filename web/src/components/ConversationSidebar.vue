<script setup lang="ts">
import { computed } from 'vue'
import type { ConversationSession } from '../types/api'

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
      eyebrow: '历史会话',
      title: '我的行程',
      create: '开始新的规划',
      loading: '正在加载历史会话...',
      empty: '还没有保存的会话，先发一条旅行需求试试看。',
      fallback: '这次规划还没有摘要，生成结果后会显示在这里。',
      remove: '删除'
    }
  : {
      eyebrow: 'History',
      title: 'My Trips',
      create: 'Start A New Plan',
      loading: 'Loading saved conversations...',
      empty: 'No saved conversations yet. Send a travel request to create one.',
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
      <p class="sidebar__eyebrow">{{ copy.eyebrow }}</p>
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
          <strong>{{ conversation.title }}</strong>
          <p>{{ conversation.summary || copy.fallback }}</p>
          <p>{{ timeLabel(conversation.updatedAt) }}</p>
        </div>
        <button class="sidebar__delete" @click.stop="emit('remove', conversation.conversationId)">{{ copy.remove }}</button>
      </article>
    </div>
  </aside>
</template>
