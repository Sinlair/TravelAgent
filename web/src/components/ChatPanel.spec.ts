import { mount } from '@vue/test-utils'
import ChatPanel from './ChatPanel.vue'
import type { ConversationDetailResponse } from '../types/api'

const detail: ConversationDetailResponse = {
  conversation: {
    conversationId: 'conversation-1',
    title: 'Hangzhou trip',
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z'
  },
  messages: [
    {
      id: 'message-1',
      conversationId: 'conversation-1',
      role: 'USER',
      content: 'Plan a three-day Hangzhou trip',
      createdAt: '2026-04-01T00:00:00Z'
    }
  ],
  timeline: [],
  taskMemory: {
    conversationId: 'conversation-1',
    origin: 'Shanghai',
    destination: 'Hangzhou',
    days: 3,
    budget: '2500',
    preferences: ['West Lake'],
    updatedAt: '2026-04-01T00:00:00Z'
  },
  travelPlan: null
}

describe('ChatPanel', () => {
  it('emits send and renders memory tags', async () => {
    const wrapper = mount(ChatPanel, {
      props: {
        detail,
        sending: false,
        errorMessage: ''
      }
    })

    const tags = wrapper.findAll('.memory-tags span')
    expect(tags).toHaveLength(5)

    await wrapper.get('textarea').setValue('Need a rainy-day backup plan')
    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('send')).toEqual([['Need a rainy-day backup plan']])
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('renders the current error message', () => {
    const wrapper = mount(ChatPanel, {
      props: {
        detail,
        sending: false,
        errorMessage: 'Request failed'
      }
    })

    expect(wrapper.get('.composer__error').text()).toContain('Request failed')
  })
})
