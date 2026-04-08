<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { TravelPlan } from '../types/api'
import { buildMapPoints, buildRoutePolylines, type MapPoint } from '../utils/travelPlan'

const props = withDefaults(defineProps<{
  travelPlan: TravelPlan | null
  preferChinese?: boolean
  activePointId?: string
}>(), {
  preferChinese: true
})

const emit = defineEmits<{
  selectPoint: [pointId: string]
}>()

const mapContainer = ref<HTMLElement | null>(null)
const mapPoints = computed(() => buildMapPoints(props.travelPlan))
const routePolylines = computed(() => buildRoutePolylines(props.travelPlan))
const plottedPoints = computed(() =>
  [...mapPoints.value].sort((left, right) => {
    if (left.kind !== right.kind) {
      return left.kind === 'hotel' ? -1 : 1
    }
    if (left.kind === 'hotel') {
      return (left.hotelIndex ?? 0) - (right.hotelIndex ?? 0)
    }
    return (left.dayNumber ?? 0) - (right.dayNumber ?? 0)
  })
)

const amapKey = import.meta.env.VITE_AMAP_WEB_KEY
const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_JS_CODE

let mapInstance: any = null
let loaderPromise: Promise<any> | null = null
let markerOverlays: Array<{ pointId: string; marker: any }> = []

const copy = computed(() => (props.preferChinese
  ? {
      title: '路线与位置',
      nodes: (count: number) => `已标注 ${count} 个点位`,
      emptyPlan: '生成行程后，这里会显示路线和酒店位置。',
      emptyCoordinates: '当前行程还没有足够的坐标信息，暂时无法绘制地图。',
      noKey: '路线数据已经准备好，但还需要配置高德 Web Key 才能渲染底图。',
      plottedNodes: '关键地点',
      hotel: (index?: number) => `住宿 ${index ?? ''}`.trim(),
      day: (dayNumber?: number) => `第 ${dayNumber ?? ''} 天`.trim()
    }
  : {
      title: 'Route and locations',
      nodes: (count: number) => `${count} nodes plotted`,
      emptyPlan: 'The route map will appear here after a plan is generated.',
      emptyCoordinates: 'There are not enough coordinates in the current plan to draw the map yet.',
      noKey: 'The route data is ready, but an Amap Web key is still required to render the basemap.',
      plottedNodes: 'Key stops',
      hotel: (index?: number) => `Hotel ${index ?? ''}`.trim(),
      day: (dayNumber?: number) => `Day ${dayNumber ?? ''}`.trim()
    }))

watch([mapContainer, mapPoints, routePolylines], async () => {
  if (!mapContainer.value || !mapPoints.value.length || !amapKey) {
    destroyMap()
    return
  }

  let AMap: any
  try {
    AMap = await loadAmap()
  } catch {
    destroyMap()
    return
  }

  if (!mapContainer.value) {
    return
  }

  if (!mapInstance) {
    mapInstance = new AMap.Map(mapContainer.value, {
      viewMode: '2D',
      zoom: 12,
      mapStyle: 'amap://styles/whitesmoke'
    })
    mapInstance.addControl(new AMap.Scale())
    mapInstance.addControl(new AMap.ToolBar({ position: { right: '16px', top: '16px' } }))
  }

  mapInstance.clearMap()
  markerOverlays = []

  const lineOverlays = routePolylines.value.map((polyline, index) => new AMap.Polyline({
    path: polyline,
    strokeColor: index % 2 === 0 ? '#2f86ff' : '#0b8c87',
    strokeOpacity: 0.84,
    strokeWeight: 5
  }))

  const markers = mapPoints.value.map(point => {
    const marker = new AMap.Marker({
      position: [point.longitude, point.latitude],
      offset: new AMap.Pixel(-18, -18),
      content: markerHtml(point.label, point.kind, point.id === props.activePointId)
    })
    marker.on?.('click', () => emit('selectPoint', point.id))
    markerOverlays.push({ pointId: point.id, marker })
    return marker
  })

  const overlays = [...lineOverlays, ...markers]
  mapInstance.add(overlays)
  mapInstance.setFitView(overlays, false, [48, 48, 48, 48])
}, { immediate: true, deep: true })

watch(() => props.activePointId, activePointId => {
  markerOverlays.forEach(({ pointId, marker }) => {
    const point = mapPoints.value.find(item => item.id === pointId)
    if (!point || !marker?.setContent) {
      return
    }
    marker.setContent(markerHtml(point.label, point.kind, point.id === activePointId))
  })
})

onBeforeUnmount(() => {
  destroyMap()
})

function destroyMap() {
  mapInstance?.destroy?.()
  mapInstance = null
  markerOverlays = []
}

function markerHtml(label: string, kind: 'hotel' | 'stop', active = false) {
  const background = kind === 'hotel' ? '#0b8c87' : '#ffb454'
  const ring = active
    ? '0 0 0 4px rgba(47,134,255,.18), 0 14px 30px rgba(0,0,0,.16)'
    : '0 10px 24px rgba(0,0,0,.16)'
  const border = active ? '2px solid #2f86ff' : 'none'
  return `<div style="width:36px;height:36px;border-radius:18px;background:${background};color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;box-shadow:${ring};border:${border}">${label}</div>`
}

function pointTitle(point: MapPoint) {
  if (point.kind === 'hotel') {
    return copy.value.hotel(point.hotelIndex)
  }
  return copy.value.day(point.dayNumber)
}

function coordinateText(point: MapPoint) {
  return `${point.longitude.toFixed(6)}, ${point.latitude.toFixed(6)}`
}

async function loadAmap() {
  const globalWindow = window as Window & typeof globalThis & {
    AMap?: any
    _AMapSecurityConfig?: { securityJsCode?: string }
  }
  if (globalWindow.AMap) {
    return globalWindow.AMap
  }
  if (loaderPromise) {
    return loaderPromise
  }
  loaderPromise = new Promise((resolve, reject) => {
    if (securityJsCode) {
      globalWindow._AMapSecurityConfig = { securityJsCode }
    }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${amapKey}&plugin=AMap.Scale,AMap.ToolBar`
    script.async = true
    script.onload = () => resolve(globalWindow.AMap)
    script.onerror = () => reject(new Error('Failed to load AMap JS API'))
    document.head.appendChild(script)
  })
  return loaderPromise
}
</script>

<template>
  <section class="plan-map">
    <div class="plan-map__header">
      <div>
        <h3>{{ copy.title }}</h3>
      </div>
      <span v-if="travelPlan && mapPoints.length" class="plan-map__badge">
        {{ copy.nodes(mapPoints.length) }}
      </span>
    </div>

    <div v-if="!travelPlan" class="plan-map__empty">
      {{ copy.emptyPlan }}
    </div>
    <div v-else-if="!mapPoints.length" class="plan-map__empty">
      {{ copy.emptyCoordinates }}
    </div>
    <div v-else-if="!amapKey" class="plan-map__empty">
      {{ copy.noKey }}
    </div>
    <div v-else ref="mapContainer" class="plan-map__canvas" />

    <div v-if="plottedPoints.length" class="plan-map__nodes">
      <div class="plan-section__title">{{ copy.plottedNodes }}</div>
      <div class="plan-map__node-list">
        <article
          v-for="point in plottedPoints"
          :key="point.id"
          class="plan-map__node"
          :class="{ 'plan-map__node--active': point.id === activePointId }"
          @click="emit('selectPoint', point.id)"
        >
          <strong>{{ pointTitle(point) }} · {{ point.name }}</strong>
          <p>{{ coordinateText(point) }}</p>
        </article>
      </div>
    </div>
  </section>
</template>
