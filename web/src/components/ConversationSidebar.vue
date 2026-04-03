<script setup lang="ts">
import type { ConversationSession } from '../types/api'

defineProps<{
  conversations: ConversationSession[]
  currentConversationId: string
  loading: boolean
}>()

const emit = defineEmits<{
  select: [conversationId: string]
  create: []
  remove: [conversationId: string]
}>()

function timeLabel(value: string) {
  return new Date(value).toLocaleString()
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__header">
      <p class="sidebar__eyebrow">历史记录</p>
      <h1>我的行程</h1>
      <button class="sidebar__new" @click="emit('create')">开始新的规划</button>
    </div>

    <div class="sidebar__list">
      <div v-if="loading" class="sidebar__empty">正在加载历史会话...</div>
      <div v-else-if="conversations.length === 0" class="sidebar__empty">
        还没有保存的会话，先发送一条旅行需求试试看。
      </div>

      <article
        v-for="conversation in conversations"
        :key="conversation.conversationId"
        class="sidebar__item"
        :class="{ 'sidebar__item--active': conversation.conversationId === currentConversationId }"
        @click="emit('select', conversation.conversationId)"
      >
        <div>
          <strong>{{ conversation.title }}</strong>
          <p>{{ conversation.summary || '这次规划还没有摘要，生成结果后会显示在这里。' }}</p>
          <p>{{ timeLabel(conversation.updatedAt) }}</p>
        </div>
        <button class="sidebar__delete" @click.stop="emit('remove', conversation.conversationId)">删除</button>
      </article>
    </div>
  </aside>
</template>
