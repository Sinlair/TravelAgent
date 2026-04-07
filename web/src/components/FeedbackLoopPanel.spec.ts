import { mount } from '@vue/test-utils'
import FeedbackLoopPanel from './FeedbackLoopPanel.vue'
import type { FeedbackLoopSummaryResponse } from '../types/api'

const summary: FeedbackLoopSummaryResponse = {
  generatedAt: '2026-04-07T11:00:00Z',
  limitApplied: 200,
  sampleCount: 18,
  acceptedCount: 7,
  partialCount: 6,
  rejectedCount: 5,
  acceptedRatePct: 38.89,
  usableRatePct: 72.22,
  structuredPlanCount: 16,
  structuredPlanCoveragePct: 88.89,
  topReasonCodes: [
    {
      key: 'edited_before_use',
      totalCount: 6,
      acceptedCount: 0,
      partialCount: 6,
      rejectedCount: 0,
      acceptedRatePct: 0,
      usableRatePct: 100
    }
  ],
  topDestinations: [
    {
      key: 'Hangzhou',
      totalCount: 8,
      acceptedCount: 4,
      partialCount: 2,
      rejectedCount: 2,
      acceptedRatePct: 50,
      usableRatePct: 75
    }
  ],
  topAgentTypes: [],
  keyFindings: [
    {
      type: 'VALIDATION_FAIL',
      key: 'validationFailCount>0',
      totalCount: 5,
      acceptedCount: 0,
      partialCount: 2,
      rejectedCount: 3,
      usableRatePct: 40,
      recommendation: 'Review failing constraint checks first.'
    }
  ]
}

describe('FeedbackLoopPanel', () => {
  it('renders summary metrics and findings', () => {
    const wrapper = mount(FeedbackLoopPanel, {
      props: {
        summary,
        loading: false,
        stale: true,
        errorMessage: '',
        preferChinese: false,
        initialLimit: 200
      }
    })

    expect(wrapper.text()).toContain('Analyze Recent Feedback On Demand')
    expect(wrapper.text()).toContain('New feedback arrived. This view may be stale.')
    expect(wrapper.text()).toContain('38.89%')
    expect(wrapper.text()).toContain('72.22%')
    expect(wrapper.text()).toContain('Structured plan coverage')
    expect(wrapper.text()).toContain('edited_before_use')
    expect(wrapper.text()).toContain('Hangzhou')
    expect(wrapper.text()).toContain('validationFailCount>0')
  })

  it('emits refresh with the selected limit', async () => {
    const wrapper = mount(FeedbackLoopPanel, {
      props: {
        summary: null,
        loading: false,
        stale: false,
        errorMessage: '',
        preferChinese: false,
        initialLimit: 100
      }
    })

    await wrapper.get('.feedback-loop-panel__select').setValue('500')
    await wrapper.get('.feedback-loop-panel__action').trigger('click')

    expect(wrapper.emitted('refresh')).toEqual([[500]])
  })
})
