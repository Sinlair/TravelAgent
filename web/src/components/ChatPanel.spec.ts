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
      createdAt: '2026-04-01T00:00:00Z',
      metadata: {
        imageAttachments: [
          {
            id: 'attachment-1',
            name: 'hotel-booking.png',
            mediaType: 'image/png',
            sizeBytes: 2048
          }
        ]
      }
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
  travelPlan: null,
  feedback: null,
  imageContextCandidate: {
    conversationId: 'conversation-1',
    summary: '- Hotel: West Lake\n- Check-in: Friday night',
    facts: {
      destination: 'Hangzhou',
      startDate: 'Friday night',
      hotelArea: 'West Lake',
      activities: [],
      missingFields: ['origin', 'budget', 'days', 'hotelName']
    },
    attachments: [
      {
        id: 'candidate-1',
        name: 'hotel-booking.png',
        mediaType: 'image/png',
        sizeBytes: 2048
      }
    ],
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z'
  }
}

describe('ChatPanel', () => {
  it('emits send and renders memory tags', async () => {
    const wrapper = mount(ChatPanel, {
      props: {
        detail,
        sending: false,
        feedback: null,
        feedbackSaving: false,
        errorMessage: ''
      }
    })

    const tags = wrapper.findAll('.memory-tags span')
    expect(tags).toHaveLength(5)
    expect(wrapper.text()).toContain('hotel-booking.png')
    expect(wrapper.text()).toContain('Image Facts Awaiting Confirmation')
    expect(wrapper.text()).toContain('Hotel Area')
    expect(wrapper.text()).toContain('West Lake')
    expect(wrapper.text()).toContain('Needs More Input')
    expect(wrapper.text()).toContain('Budget')

    await wrapper.get('textarea').setValue('Need a rainy-day backup plan')
    await wrapper.get('.composer__submit').trigger('click')

    expect(wrapper.emitted('send')).toEqual([
      [{ message: 'Need a rainy-day backup plan', attachments: [], imageContextAction: 'CONFIRM' }]
    ])
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('renders the current error message', () => {
    const wrapper = mount(ChatPanel, {
      props: {
        detail,
        sending: false,
        feedback: null,
        feedbackSaving: false,
        errorMessage: 'Request failed'
      }
    })

    expect(wrapper.get('.composer__error').text()).toContain('Request failed')
  })

  it('emits dismiss when the user ignores extracted image facts', async () => {
    const wrapper = mount(ChatPanel, {
      props: {
        detail,
        sending: false,
        feedback: null,
        feedbackSaving: false,
        errorMessage: ''
      }
    })

    await wrapper.get('.composer__attachment-remove').trigger('click')

    expect(wrapper.emitted('send')).toEqual([[{ imageContextAction: 'DISMISS' }]])
  })
})
