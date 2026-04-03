import type { TravelPlan } from '../types/api'
import { buildMapPoints, dayLabel, stopCostTotal } from './travelPlan'

export async function downloadTravelScrapbook(plan: TravelPlan, preferChinese: boolean) {
  const canvas = document.createElement('canvas')
  canvas.width = 1600
  canvas.height = 1080
  const context = canvas.getContext('2d')
  if (!context) {
    return
  }

  drawBackground(context, canvas.width, canvas.height)
  drawHeader(context, plan, preferChinese)
  drawHighlights(context, plan, preferChinese)
  drawHotelCard(context, plan, preferChinese)
  drawDays(context, plan, preferChinese)
  drawRouteSketch(context, plan)

  const link = document.createElement('a')
  link.download = `${plan.title || 'travel-plan'}.png`
  link.href = canvas.toDataURL('image/png')
  link.click()
}

function drawBackground(context: CanvasRenderingContext2D, width: number, height: number) {
  const gradient = context.createLinearGradient(0, 0, width, height)
  gradient.addColorStop(0, '#f5efe4')
  gradient.addColorStop(1, '#f1e2d1')
  context.fillStyle = gradient
  context.fillRect(0, 0, width, height)
  context.fillStyle = 'rgba(214,107,52,0.08)'
  context.fillRect(72, 72, width - 144, height - 144)
}

function drawHeader(context: CanvasRenderingContext2D, plan: TravelPlan, preferChinese: boolean) {
  context.fillStyle = '#1e2723'
  context.font = '700 58px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(plan.title, 108, 148)
  context.fillStyle = '#8a3e17'
  context.font = '28px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(plan.summary, 108, 198)
  context.fillStyle = '#1f6f6a'
  context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(preferChinese ? `预算 ${plan.estimatedTotalMin}-${plan.estimatedTotalMax} 元` : `Budget ${plan.estimatedTotalMin}-${plan.estimatedTotalMax} CNY`, 108, 246)
}

function drawHighlights(context: CanvasRenderingContext2D, plan: TravelPlan, preferChinese: boolean) {
  context.fillStyle = '#fffaf2'
  roundRect(context, 96, 286, 520, 160, 30)
  context.fillStyle = '#d66b34'
  context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(preferChinese ? '本次重点' : 'Highlights', 126, 330)
  context.fillStyle = '#1e2723'
  context.font = '26px "Noto Sans SC", "Segoe UI", sans-serif'
  plan.highlights.slice(0, 4).forEach((item, index) => {
    context.fillText(`0${index + 1} ${item}`, 126, 372 + index * 30)
  })
}

function drawHotelCard(context: CanvasRenderingContext2D, plan: TravelPlan, preferChinese: boolean) {
  const hotel = plan.hotels?.[0]
  context.fillStyle = '#fffaf2'
  roundRect(context, 650, 286, 420, 160, 30)
  context.fillStyle = '#1f6f6a'
  context.font = '700 24px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(preferChinese ? '住宿建议' : 'Hotel', 680, 330)
  context.fillStyle = '#1e2723'
  context.font = '700 30px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(hotel?.name ?? plan.hotelArea, 680, 374)
  context.font = '24px "Noto Sans SC", "Segoe UI", sans-serif'
  context.fillText(hotel?.address ?? plan.hotelAreaReason, 680, 410)
}

function drawDays(context: CanvasRenderingContext2D, plan: TravelPlan, preferChinese: boolean) {
  let x = 96
  let y = 490
  for (const day of plan.days.slice(0, 3)) {
    context.fillStyle = '#fffaf2'
    roundRect(context, x, y, 460, 230, 28)
    context.fillStyle = '#1e2723'
    context.font = '700 28px "Noto Sans SC", "Segoe UI", sans-serif'
    context.fillText(`${dayLabel(day, preferChinese)} · ${day.theme}`, x + 28, y + 42)
    context.fillStyle = '#58635d'
    context.font = '22px "Noto Sans SC", "Segoe UI", sans-serif'
    context.fillText(preferChinese ? `通勤 ${day.totalTransitMinutes} 分钟 · 花费 ${day.estimatedCost} 元` : `Transit ${day.totalTransitMinutes} min · Cost ${day.estimatedCost} CNY`, x + 28, y + 78)
    day.stops.slice(0, 3).forEach((stop, index) => {
      context.fillStyle = '#d66b34'
      context.fillText(`${stop.startTime} ${stop.name}`, x + 28, y + 122 + index * 34)
      context.fillStyle = '#58635d'
      context.fillText(preferChinese ? `${stop.area} · ${stopCostTotal(stop)} 元` : `${stop.area} · ${stopCostTotal(stop)} CNY`, x + 218, y + 122 + index * 34)
    })
    x += 490
    if (x > 1080) {
      x = 96
      y += 260
    }
  }
}

function drawRouteSketch(context: CanvasRenderingContext2D, plan: TravelPlan) {
  const points = buildMapPoints(plan)
  if (points.length < 2) {
    return
  }
  const sketchX = 1098
  const sketchY = 286
  const sketchWidth = 400
  const sketchHeight = 700
  context.fillStyle = '#fffaf2'
  roundRect(context, sketchX, sketchY, sketchWidth, sketchHeight, 34)

  const longitudes = points.map(point => point.longitude)
  const latitudes = points.map(point => point.latitude)
  const minLng = Math.min(...longitudes)
  const maxLng = Math.max(...longitudes)
  const minLat = Math.min(...latitudes)
  const maxLat = Math.max(...latitudes)
  const pad = 48

  const project = (longitude: number, latitude: number) => ({
    x: sketchX + pad + ((longitude - minLng) / Math.max(maxLng - minLng, 0.001)) * (sketchWidth - pad * 2),
    y: sketchY + sketchHeight - pad - ((latitude - minLat) / Math.max(maxLat - minLat, 0.001)) * (sketchHeight - pad * 2)
  })

  context.strokeStyle = '#1f6f6a'
  context.lineWidth = 5
  context.beginPath()
  points.forEach((point, index) => {
    const projected = project(point.longitude, point.latitude)
    if (index === 0) {
      context.moveTo(projected.x, projected.y)
    } else {
      context.lineTo(projected.x, projected.y)
    }
  })
  context.stroke()

  context.font = '700 20px "Noto Sans SC", "Segoe UI", sans-serif'
  points.forEach(point => {
    const projected = project(point.longitude, point.latitude)
    context.fillStyle = point.kind === 'hotel' ? '#1f6f6a' : '#d66b34'
    context.beginPath()
    context.arc(projected.x, projected.y, point.kind === 'hotel' ? 11 : 9, 0, Math.PI * 2)
    context.fill()
    context.fillStyle = '#1e2723'
    context.fillText(point.name, projected.x + 14, projected.y + 6)
  })
}

function roundRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  context.beginPath()
  context.moveTo(x + radius, y)
  context.arcTo(x + width, y, x + width, y + height, radius)
  context.arcTo(x + width, y + height, x, y + height, radius)
  context.arcTo(x, y + height, x, y, radius)
  context.arcTo(x, y, x + width, y, radius)
  context.closePath()
  context.fill()
}
