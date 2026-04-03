import type { TravelHotelRecommendation, TravelPlan, TravelPlanDay, TravelPlanStop, TravelTransitLeg } from '../types/api'

export interface MapPoint {
  id: string
  label: string
  name: string
  longitude: number
  latitude: number
  kind: 'hotel' | 'stop'
  dayNumber?: number
  hotelIndex?: number
}

export function hotelPointId(index: number) {
  return `hotel-${index}`
}

export function stopPointId(dayNumber: number, slot: string, name: string) {
  return `${dayNumber}-${slot}-${name}`
}

export function parseCoordinate(longitude?: string, latitude?: string) {
  const lng = Number.parseFloat(longitude ?? '')
  const lat = Number.parseFloat(latitude ?? '')
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    return null
  }
  return { longitude: lng, latitude: lat }
}

export function buildMapPoints(plan: TravelPlan | null) {
  if (!plan) {
    return []
  }

  const points: MapPoint[] = []

  for (const [index, hotel] of (plan.hotels ?? []).entries()) {
    const coordinate = parseCoordinate(hotel.longitude, hotel.latitude)
    if (!coordinate) {
      continue
    }
    points.push({
      id: hotelPointId(index + 1),
      label: `H${index + 1}`,
      name: hotel.name,
      kind: 'hotel',
      hotelIndex: index + 1,
      ...coordinate
    })
  }

  for (const day of plan.days) {
    for (const stop of day.stops) {
      const coordinate = parseCoordinate(stop.longitude, stop.latitude)
      if (!coordinate) {
        continue
      }
      points.push({
        id: stopPointId(day.dayNumber, stop.slot, stop.name),
        label: `${day.dayNumber}`,
        name: stop.name,
        kind: 'stop',
        dayNumber: day.dayNumber,
        ...coordinate
      })
    }
  }

  return points
}

export function buildRoutePolylines(plan: TravelPlan | null) {
  if (!plan) {
    return []
  }
  const lines: number[][][] = []
  for (const day of plan.days) {
    for (const stop of day.stops) {
      pushLegPolyline(lines, stop.routeFromPrevious)
    }
    pushLegPolyline(lines, day.returnToHotel)
  }
  return lines
}

export function flattenStops(plan: TravelPlan | null) {
  return plan?.days.flatMap(day => day.stops) ?? []
}

export function dayLabel(day: TravelPlanDay, preferChinese: boolean) {
  return preferChinese ? `第 ${day.dayNumber} 天` : `Day ${day.dayNumber}`
}

export function stopCostTotal(stop: TravelPlanStop) {
  return stop.estimatedCost + (stop.costBreakdown?.localTransitCost ?? 0)
}

export function hotelLabel(hotel: TravelHotelRecommendation, preferChinese: boolean) {
  return preferChinese ? `${hotel.name} · ${hotel.area}` : `${hotel.name} · ${hotel.area}`
}

function pushLegPolyline(lines: number[][][], leg?: TravelTransitLeg | null) {
  if (!leg?.polyline?.length) {
    return
  }
  const points = leg.polyline
    .map(point => {
      const [longitude, latitude] = point.split(',').map(value => Number.parseFloat(value))
      if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
        return null
      }
      return [longitude, latitude]
    })
    .filter((point): point is number[] => point !== null)
  if (points.length >= 2) {
    lines.push(points)
  }
}
