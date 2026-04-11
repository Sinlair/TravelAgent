import { mount } from '@vue/test-utils'
import PlanPanel from './PlanPanel.vue'
import type { TravelPlan } from '../types/api'

const travelPlan: TravelPlan = {
  conversationId: 'conversation-1',
  title: 'Hangzhou weekend',
  summary: 'A balanced Hangzhou weekend with lake views and food stops.',
  hotelArea: 'West Lake',
  hotelAreaReason: 'Central enough for scenic areas and evening food clusters.',
  hotels: [
    {
      name: 'West Lake Selected Hotel',
      area: 'West Lake',
      address: '1 Hubin Road, Hangzhou',
      nightlyMin: 480,
      nightlyMax: 680,
      rationale: 'Close to the lake and evening food clusters.',
      longitude: '120.1551',
      latitude: '30.2520',
      source: 'MCP.amap_input_tips'
    }
  ],
  totalBudget: 1600,
  estimatedTotalMin: 1400,
  estimatedTotalMax: 1800,
  highlights: ['West Lake', 'Tea village'],
  budget: [
    {
      category: 'Food',
      minAmount: 240,
      maxAmount: 360,
      rationale: 'Mix casual local meals with one signature dinner.'
    }
  ],
  checks: [
    {
      code: 'pace',
      status: 'WARN',
      message: 'Day 1 stays full, so keep transfers tight.'
    }
  ],
  days: [
    {
      dayNumber: 1,
      theme: 'West Lake and Hubin',
      startTime: '09:00',
      endTime: '20:30',
      totalTransitMinutes: 45,
      totalActivityMinutes: 360,
      estimatedCost: 680,
      stops: [
        {
          slot: 'MORNING',
          name: 'West Lake',
          area: 'West Lake',
          address: 'West Lake Scenic Area',
          longitude: '120.1551',
          latitude: '30.2520',
          startTime: '09:00',
          endTime: '11:30',
          durationMinutes: 150,
          transitMinutesFromPrevious: 0,
          estimatedCost: 80,
          openTime: '00:00',
          closeTime: '23:59',
          rationale: 'Start with the lakefront while it is quieter.',
          costBreakdown: {
            ticketCost: 0,
            foodCost: 20,
            localTransitCost: 10,
            otherCost: 0,
            note: ''
          },
          poiMatch: {
            query: 'West Lake',
            matchedName: 'West Lake',
            district: 'Xihu',
            address: 'Hangzhou West Lake Scenic Area',
            adCode: '330106',
            longitude: '120.1551',
            latitude: '30.2520',
            confidence: 92,
            candidateNames: ['West Lake'],
            source: 'MCP.amap_input_tips'
          },
          routeFromPrevious: null
        }
      ],
      returnToHotel: {
        fromName: 'West Lake',
        toName: 'West Lake Selected Hotel',
        mode: 'WALK',
        summary: 'Walk back to the hotel along the waterfront.',
        durationMinutes: 18,
        distanceMeters: 1200,
        walkingMinutes: 18,
        estimatedCost: 0,
        lineNames: [],
        steps: [],
        polyline: [],
        source: 'RULE.fallback'
      }
    }
  ],
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

describe('PlanPanel', () => {
  it('renders the current overview, stays, budget, checks, and itinerary sections', () => {
    const wrapper = mount(PlanPanel, {
      props: {
        travelPlan,
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

    expect(wrapper.text()).toContain('Suggested Plan')
    expect(wrapper.text()).toContain('At A Glance')
    expect(wrapper.text()).toContain('Stays')
    expect(wrapper.text()).toContain('Budget')
    expect(wrapper.text()).toContain('Before You Book')
    expect(wrapper.text()).toContain('Daily Itinerary')
    expect(wrapper.text()).toContain('Current Weather')
    expect(wrapper.text()).toContain('West Lake Selected Hotel')
    expect(wrapper.text()).toContain('Day 1')
    expect(wrapper.text()).toContain('View location details')
    expect(wrapper.text()).toContain('Map')
  })

  it('renders the empty state when no plan is available', () => {
    const wrapper = mount(PlanPanel, {
      props: {
        travelPlan: null,
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

    expect(wrapper.text()).toContain('No structured itinerary yet')
    expect(wrapper.text()).toContain('Share the destination, trip length, budget, and preferences')
  })
})
