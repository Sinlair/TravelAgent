import { mount } from '@vue/test-utils'
import TimelinePanel from './TimelinePanel.vue'
import type { TimelineEvent } from '../types/api'

const timeline: TimelineEvent[] = [
  {
    id: 'event-1',
    conversationId: 'conversation-1',
    stage: 'VALIDATE_PLAN',
    message: 'Validate generated plan against budget, opening hours, and load',
    details: {
      attempt: 2,
      accepted: false,
      failCount: 1,
      repairCodes: ['budget', 'pace']
    },
    createdAt: '2026-04-03T08:00:00Z'
  }
]

describe('TimelinePanel', () => {
  it('renders structured detail pills for timeline events', () => {
    const wrapper = mount(TimelinePanel, {
      props: {
        timeline,
        preferChinese: false
      }
    })

    expect(wrapper.text()).toContain('How This Plan Was Built')
    expect(wrapper.text()).toContain('Attempt: 2')
    expect(wrapper.text()).toContain('Accepted: No')
    expect(wrapper.text()).toContain('Repair codes: budget / pace')
  })
})
