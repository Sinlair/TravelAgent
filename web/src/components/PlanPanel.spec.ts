import { mount } from '@vue/test-utils'
import PlanPanel from './PlanPanel.vue'
import type { ConversationFeedback, TravelPlan } from '../types/api'

const travelPlan: TravelPlan = {
  conversationId: 'conversation-1',
  title: 'Hangzhou weekend',
  summary: 'A balanced Hangzhou weekend with lake views and food stops.',
  hotelArea: 'West Lake',
  hotelAreaReason: 'Central enough for scenic areas and evening food clusters.',
  hotels: [],
  totalBudget: 1600,
  estimatedTotalMin: 1400,
  estimatedTotalMax: 1800,
  highlights: ['West Lake', 'Tea village'],
  budget: [],
  checks: [],
  days: [],
  weatherSnapshot: {
    city: 'Hangzhou',
    reportTime: '2026-04-03 08:00:00',
    description: 'Cloudy',
    temperature: '22',
    windDirection: 'NE',
    windPower: '3'
  },
  knowledgeRetrieval: {
    destination: 'Hangzhou',
    inferredTopics: ['food', 'scenic'],
    inferredTripStyles: ['family', 'museum'],
    retrievalSource: 'vector-store',
    selections: [
      {
        city: 'Hangzhou',
        topic: 'food',
        title: 'Hubin works well for a first-night meal',
        content: 'Keep the first evening around Hubin to avoid extra cross-town transfers.',
        tags: ['hangzhou', 'hubin'],
        source: 'local-curated',
        schemaSubtype: 'hotel_area',
        qualityScore: 38,
        matchedTripStyles: ['family'],
        matchedCity: 'Hangzhou',
        matchedTopic: 'food'
      },
      {
        city: 'Hangzhou',
        topic: 'scenic',
        title: '.listi',
        content: 'noise',
        tags: ['hangzhou'],
        source: 'local-curated',
        qualityScore: 12,
        matchedCity: 'Hangzhou',
        matchedTopic: 'scenic'
      }
    ]
  },
  constraintRelaxed: true,
  adjustmentSuggestions: ['Increase the total budget to at least 1800 CNY.'],
  updatedAt: '2026-04-03T08:00:00Z'
}

const feedback: ConversationFeedback = {
  conversationId: 'conversation-1',
  label: 'PARTIAL',
  reasonCode: 'edited_before_use',
  note: 'Adjusted the hotel pick.',
  agentType: 'TRAVEL_PLANNER',
  destination: 'Hangzhou',
  days: 2,
  budget: '1800 CNY',
  hasTravelPlan: true,
  metadata: {
    dayCount: 2
  },
  createdAt: '2026-04-03T08:00:00Z',
  updatedAt: '2026-04-03T08:00:00Z'
}

describe('PlanPanel', () => {
  it('renders weather, knowledge, and closest feasible alternative sections', () => {
    const wrapper = mount(PlanPanel, {
      props: {
        travelPlan,
        feedback,
        feedbackSaving: false,
        preferChinese: false
      },
      global: {
        stubs: {
          PlanMap: {
            template: '<div class="plan-map-stub">Map</div>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('Closest Feasible Alternative')
    expect(wrapper.text()).toContain('Recommendation Feedback')
    expect(wrapper.text()).toContain('Partially accepted')
    expect(wrapper.text()).toContain('Use with Edits')
    expect(wrapper.text()).toContain('Weather Snapshot')
    expect(wrapper.text()).toContain('Retrieved Knowledge')
    expect(wrapper.text()).toContain('Point-in-time snapshot')
    expect(wrapper.text()).toContain('Vector store')
    expect(wrapper.text()).toContain('Average quality')
    expect(wrapper.text()).toContain('Suppressed noisy hints')
    expect(wrapper.text()).toContain('Inferred styles')
    expect(wrapper.text()).toContain('Family')
    expect(wrapper.text()).toContain('Stay area')
    expect(wrapper.text()).toContain('Strong 38')
    expect(wrapper.text()).toContain('Hubin works well for a first-night meal')
  })

  it('emits structured feedback when the user accepts the plan', async () => {
    const wrapper = mount(PlanPanel, {
      props: {
        travelPlan,
        feedback: null,
        feedbackSaving: false,
        preferChinese: false
      },
      global: {
        stubs: {
          PlanMap: {
            template: '<div class="plan-map-stub">Map</div>'
          }
        }
      }
    })

    await wrapper.get('.plan-feedback-button').trigger('click')

    expect(wrapper.emitted('feedback')).toEqual([
      [{ label: 'ACCEPTED', reasonCode: 'used_as_is' }]
    ])
  })
})
