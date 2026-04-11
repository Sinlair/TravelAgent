<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Map as MapIcon, MapPin, AlertCircle, Info } from 'lucide-vue-next'
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
      title: '\u8def\u7ebf\u4e0e\u4f4d\u7f6e',
      nodes: (count: number) => `\u5df2\u6807\u6ce8 ${count} \u4e2a\u70b9\u4f4d`,
      emptyPlan: '\u751f\u6210\u884c\u7a0b\u540e\uff0c\u8fd9\u91cc\u4f1a\u663e\u793a\u8def\u7ebf\u548c\u9152\u5e97\u4f4d\u7f6e\u3002',
      emptyCoordinates: '\u5f53\u524d\u884c\u7a0b\u8fd8\u6ca1\u6709\u8db3\u591f\u7684\u5750\u6807\u4fe1\u606f\uff0c\u6682\u65f6\u65e0\u6cd5\u7ed8\u5236\u5730\u56fe\u3002',
      noKey: '\u8def\u7ebf\u6570\u636e\u5df2\u7ecf\u51c6\u5907\u597d\uff0c\u4f46\u8fd8\u9700\u8981\u914d\u7f6e\u9ad8\u5fb7 Web Key \u624d\u80fd\u6e32\u67d3\u5e95\u56fe\u3002',
      plottedNodes: '\u5173\u952e\u70b9\u4f4d',
      hotel: (index?: number) => `\u4f4f\u5bbf ${index ?? ''}`.trim(),
      day: (dayNumber?: number) => `\u7b2c ${dayNumber ?? ''} \u5929`.trim()
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
    strokeColor: index % 2 === 0 ? '#d6623a' : '#0f7b73',
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
  const background = kind === 'hotel' ? '#0f7b73' : '#d6623a'
  const ring = active
    ? '0 0 0 4px rgba(214,98,58,.18), 0 14px 30px rgba(0,0,0,.16)'
    : '0 10px 24px rgba(0,0,0,.16)'
  const border = active ? '2px solid #173042' : 'none'
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
      <div class="panel__header-info">
        <div class="panel__icon-badge">
          <MapIcon :size="18" />
        </div>
        <div>
          <h3>{{ copy.title }}</h3>
          <p v-if="travelPlan && mapPoints.length" class="panel__subtle">
            <MapPin :size="12" />
            {{ copy.nodes(mapPoints.length) }}
          </p>
        </div>
      </div>
    </div>

    <div v-if="!travelPlan" class="panel__empty">
      <MapIcon :size="32" />
      <p>{{ copy.emptyPlan }}</p>
    </div>
    <div v-else-if="!mapPoints.length" class="panel__empty">
      <AlertCircle :size="32" />
      <p>{{ copy.emptyCoordinates }}</p>
    </div>
    <div v-else-if="!amapKey" class="panel__empty">
      <Info :size="32" />
      <p>{{ copy.noKey }}</p>
    </div>
    <div v-else ref="mapContainer" class="plan-map__canvas" />

    <div v-if="plottedPoints.length" class="plan-map__nodes">
      <div class="plan-section__title">
        <MapPin :size="14" />
        {{ copy.plottedNodes }}
      </div>
      <div class="plan-map__node-list">
        <article
          v-for="point in plottedPoints"
          :key="point.id"
          class="plan-map__node"
          :class="{ 'plan-map__node--active': point.id === activePointId }"
          @click="emit('selectPoint', point.id)"
        >
          <strong>{{ pointTitle(point) }} / {{ point.name }}</strong>
          <p>{{ coordinateText(point) }}</p>
        </article>
      </div>
    </div>
  </section>
</template>
