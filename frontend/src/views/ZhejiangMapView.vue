<template>
  <section class="map-page">
    <div class="map-title">
      <div>
        <p class="eyebrow">ZHEJIANG ASSET INTELLIGENCE</p>
        <h2>浙江省不动产数字驾驶舱</h2>
        <p>热力感知、城市联动与区县级资产穿透分析</p>
      </div>
      <div class="map-title-actions">
        <div class="map-badge"><span></span> 数据引擎实时运行</div>
        <div class="map-clock">{{ currentTime }}</div>
      </div>
    </div>

    <div class="map-stage">
      <div
        ref="canvasHost"
        class="map-canvas"
        @pointermove="onPointerMove"
        @pointerleave="clearHover"
        @click="onMapClick"
      ></div>
      <div class="map-grid"></div>
      <div class="map-scanline"></div>

      <div class="map-breadcrumb glass-panel" @pointerdown.stop>
        <button :class="{ active: level === 'province' }" @click="backToProvince">浙江省</button>
        <template v-if="level === 'city'">
          <span>/</span>
          <button class="active">{{ currentCity }}</button>
          <small>区县视图</small>
        </template>
      </div>

      <div class="map-layer-tools glass-panel" @pointerdown.stop>
        <button :class="{ active: heatEnabled }" @click="heatEnabled = !heatEnabled">
          <i class="layer-dot heat"></i>热力图
        </button>
        <button :class="{ active: flowEnabled }" @click="flowEnabled = !flowEnabled">
          <i class="layer-dot flow"></i>轨道流光
        </button>
        <button :class="{ active: railEnabled }" @click="railEnabled = !railEnabled">
          <i class="layer-dot rail"></i>高铁网络
        </button>
      </div>

      <div class="map-kpis" @pointerdown.stop>
        <article v-for="item in kpis" :key="item.label">
          <div class="kpi-icon">{{ item.icon }}</div>
          <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.unit }}</small></div>
          <b :class="{ down: item.change < 0 }">{{ item.change > 0 ? '+' : '' }}{{ item.change }}%</b>
        </article>
      </div>

      <aside class="map-left-panel glass-panel" @pointerdown.stop>
        <header><div><small>REGION RANKING</small><strong>{{ level === 'province' ? '城市资产热度' : '区县资产热度' }}</strong></div><span>TOP {{ rankItems.length }}</span></header>
        <div class="rank-list">
          <button v-for="(item, index) in rankItems" :key="item.name" @click="selectFromList(item)">
            <i :class="{ top: index < 3 }">{{ String(index + 1).padStart(2, '0') }}</i>
            <span><b>{{ item.name }}</b><em><u :style="{ width: `${item.ratio}%` }"></u></em></span>
            <strong>{{ item.asset }}<small>亿</small></strong>
          </button>
        </div>
        <div class="heat-legend">
          <div><span>资产热力</span><small>低</small><i></i><small>高</small></div>
          <p><span class="legend-line"></span> 城市资产联动轨迹</p>
        </div>
      </aside>

      <aside class="map-stats-panel glass-panel" @pointerdown.stop>
        <header>
          <div><small>REGIONAL PROFILE</small><strong>{{ selectedStats.name }}</strong></div>
          <span>{{ level === 'province' ? 'CITY' : 'DISTRICT' }}</span>
        </header>
        <div class="stats-hero">
          <span>资产总额</span>
          <div><strong>{{ selectedStats.asset }}</strong><small>亿元</small></div>
          <p><b>↑ {{ selectedStats.growth }}%</b> 较上季度</p>
        </div>
        <div class="stats-grid">
          <div><span>在管项目</span><strong>{{ selectedStats.projects }}</strong><small>个</small></div>
          <div><span>管理面积</span><strong>{{ selectedStats.area }}</strong><small>万㎡</small></div>
          <div><span>平均出租率</span><strong>{{ selectedStats.occupancy }}</strong><small>%</small></div>
          <div><span>今日告警</span><strong>{{ selectedStats.alerts }}</strong><small>项</small></div>
        </div>
        <div class="trend-block">
          <div class="panel-subtitle"><span>近六月运营收益</span><b>单位：万元</b></div>
          <svg viewBox="0 0 260 88" preserveAspectRatio="none" aria-label="近六月运营收益趋势">
            <defs>
              <linearGradient id="trendArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#25d8ff" stop-opacity=".42" />
                <stop offset="1" stop-color="#25d8ff" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path class="trend-area" :d="trendAreaPath" />
            <polyline class="trend-line" :points="trendPoints" />
            <circle v-for="point in trendDots" :key="point.x" :cx="point.x" :cy="point.y" r="2.8" />
          </svg>
          <div class="trend-months"><span v-for="month in ['3月','4月','5月','6月','7月','8月']" :key="month">{{ month }}</span></div>
        </div>
        <div class="category-block">
          <div class="panel-subtitle"><span>资产类型分布</span><b>{{ selectedStats.projects }} 个项目</b></div>
          <div v-for="item in categoryBars" :key="item.name" class="category-row">
            <span>{{ item.name }}</span><i><u :style="{ width: `${item.value}%` }"></u></i><strong>{{ item.value }}%</strong>
          </div>
        </div>
      </aside>

      <div class="map-label-layer">
        <button
          v-for="(item, index) in mapLabels"
          :key="item.name"
          :ref="(element) => setLabelRef(element, index)"
          class="map-city-label"
          :class="{ selected: selectedStats.name === item.name, district: level === 'city' }"
          @click.stop="selectLabel(item.name)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.asset }} 亿</span>
          <small v-if="level === 'province'">{{ item.projects }} 项目</small>
        </button>
      </div>

      <div class="map-center-caption">
        <small>{{ level === 'province' ? 'ZHEJIANG PROVINCE' : 'DISTRICT ASSET MAP' }}</small>
        <strong>{{ level === 'province' ? '浙江省' : currentCity }}</strong>
        <span>{{ level === 'province' ? '11 市 · 90 区县 · 一图统览' : `${currentFeatures.length} 个区县 · 点击区县查看资产画像` }}</span>
      </div>

      <div class="map-instructions">
        <span v-if="level === 'province'">单击城市下钻区县</span><span v-else>单击区县联动统计</span> · 拖拽旋转 · 滚轮缩放
      </div>
      <div v-if="hovered" class="map-tooltip" :style="tooltipStyle">
        <small>{{ level === 'province' ? 'CITY OVERVIEW' : 'DISTRICT OVERVIEW' }}</small>
        <strong>{{ hovered.name }}</strong>
        <span>资产 {{ getStats(hovered.name).asset }} 亿元 · {{ getStats(hovered.name).projects }} 个项目</span>
        <em>{{ level === 'province' ? '点击进入区县视图 →' : '点击查看区域详情' }}</em>
      </div>
      <div v-if="mapLoading" class="map-loading"><i></i><strong>正在加载 {{ currentCity }} 区县数据</strong></div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import ZhejiangGeoJSON from 'china-map-geojson/lib/province/zhe_jiang_geo.js'
import trainImageUrl from '../assets/map/high-speed-train.png'

const CITY_CODES = {
  杭州市: 330100, 宁波市: 330200, 温州市: 330300, 嘉兴市: 330400,
  湖州市: 330500, 绍兴市: 330600, 金华市: 330700, 衢州市: 330800,
  舟山市: 330900, 台州市: 331000, 丽水市: 331100
}

const RAIL_LINKS = [
  ['杭州市', '嘉兴市'], ['杭州市', '湖州市'], ['杭州市', '绍兴市'], ['杭州市', '金华市'],
  ['嘉兴市', '宁波市'], ['绍兴市', '宁波市'], ['宁波市', '舟山市'], ['宁波市', '台州市'],
  ['台州市', '温州市'], ['温州市', '丽水市'], ['丽水市', '金华市'], ['金华市', '衢州市'],
  ['金华市', '绍兴市'], ['台州市', '金华市']
]

const CITY_STATS = {
  杭州市: { asset: 4826, projects: 318, area: 1624, occupancy: 94.8, growth: 8.6, alerts: 3 },
  宁波市: { asset: 3869, projects: 265, area: 1382, occupancy: 93.5, growth: 7.2, alerts: 2 },
  温州市: { asset: 2916, projects: 228, area: 1176, occupancy: 91.8, growth: 6.4, alerts: 5 },
  嘉兴市: { asset: 2378, projects: 186, area: 968, occupancy: 94.1, growth: 7.8, alerts: 1 },
  绍兴市: { asset: 2185, projects: 172, area: 885, occupancy: 92.9, growth: 5.9, alerts: 2 },
  金华市: { asset: 1968, projects: 156, area: 816, occupancy: 91.6, growth: 8.1, alerts: 4 },
  台州市: { asset: 1832, projects: 148, area: 752, occupancy: 92.3, growth: 6.7, alerts: 2 },
  湖州市: { asset: 1645, projects: 126, area: 665, occupancy: 93.7, growth: 7.5, alerts: 1 },
  衢州市: { asset: 1186, projects: 92, area: 478, occupancy: 90.8, growth: 5.6, alerts: 3 },
  丽水市: { asset: 1088, projects: 86, area: 442, occupancy: 91.2, growth: 6.1, alerts: 2 },
  舟山市: { asset: 986, projects: 74, area: 356, occupancy: 95.2, growth: 4.8, alerts: 1 }
}

const PROVINCE_STATS = { name: '浙江省', asset: 24879, projects: 1851, area: 9544, occupancy: 93.2, growth: 7.3, alerts: 26 }
const DEFAULT_TREND = [72, 78, 76, 85, 91, 96]
const canvasHost = ref()
const hovered = ref(null)
const selected = ref({ name: '浙江省' })
const pointer = ref({ x: 0, y: 0 })
const level = ref('province')
const currentCity = ref('')
const currentFeatures = ref(ZhejiangGeoJSON.features)
const mapLoading = ref(false)
const heatEnabled = ref(true)
const flowEnabled = ref(true)
const railEnabled = ref(true)
const currentTime = ref('')
const tooltipStyle = computed(() => ({ left: `${Math.min(pointer.value.x + 18, (canvasHost.value?.clientWidth || 900) - 240)}px`, top: `${Math.min(pointer.value.y + 18, (canvasHost.value?.clientHeight || 700) - 110)}px` }))

let scene, camera, renderer, controls, raycaster, mouse, animationId, resizeObserver, hoveredMesh
let mapGroup, effectGroup, heatGroup, flowGroup, railwayGroup, heatTexture, trainTexture, clockTimer, fetchController
let projection = { center: [120.15, 29.15], scale: 14 }
let cameraTween = null
const meshes = []
const pulses = []
const flowParticles = []
const railParticles = []
let labelWorldPoints = []
let labelElements = []

const hashName = (name) => [...name].reduce((sum, char) => sum + char.charCodeAt(0), 0)
const getStats = (name) => {
  if (name === '浙江省') return PROVINCE_STATS
  if (CITY_STATS[name]) return { name, ...CITY_STATS[name] }
  const parent = CITY_STATS[currentCity.value] || PROVINCE_STATS
  const seed = hashName(name)
  const divisor = Math.max(currentFeatures.value.length, 5)
  const factor = 0.62 + (seed % 55) / 100
  return {
    name,
    asset: Math.round(parent.asset / divisor * factor),
    projects: Math.max(8, Math.round(parent.projects / divisor * (.7 + (seed % 40) / 100))),
    area: Math.round(parent.area / divisor * (.68 + (seed % 47) / 100)),
    occupancy: (89 + (seed % 67) / 10).toFixed(1),
    growth: (4.2 + (seed % 58) / 10).toFixed(1),
    alerts: seed % 4
  }
}

const selectedStats = computed(() => getStats(selected.value?.name || (level.value === 'province' ? '浙江省' : currentCity.value)))
const mapLabels = computed(() => currentFeatures.value.map((feature) => getStats(feature.properties.name)))
const rankItems = computed(() => {
  const items = currentFeatures.value.map((feature) => getStats(feature.properties.name)).sort((a, b) => b.asset - a.asset).slice(0, 6)
  const max = items[0]?.asset || 1
  return items.map((item) => ({ ...item, ratio: Math.max(15, Math.round(item.asset / max * 100)) }))
})
const kpis = computed(() => {
  const data = level.value === 'province' ? PROVINCE_STATS : { name: currentCity.value, ...CITY_STATS[currentCity.value] }
  return [
    { label: '资产总额', value: data.asset.toLocaleString(), unit: '亿元', change: data.growth, icon: '资' },
    { label: '在管项目', value: data.projects.toLocaleString(), unit: '个', change: 5.8, icon: '项' },
    { label: '管理面积', value: data.area.toLocaleString(), unit: '万㎡', change: 4.6, icon: '面' },
    { label: '平均出租率', value: data.occupancy, unit: '%', change: 1.9, icon: '率' }
  ]
})
const trendValues = computed(() => {
  const seed = hashName(selectedStats.value.name)
  return DEFAULT_TREND.map((value, index) => value + ((seed + index * 7) % 12) - 5)
})
const trendDots = computed(() => trendValues.value.map((value, index) => ({ x: 5 + index * 50, y: 76 - (value - 65) * 1.6 })))
const trendPoints = computed(() => trendDots.value.map((point) => `${point.x},${point.y}`).join(' '))
const trendAreaPath = computed(() => `M 5 82 L ${trendDots.value.map((point) => `${point.x} ${point.y}`).join(' L ')} L 255 82 Z`)
const categoryBars = computed(() => {
  const seed = hashName(selectedStats.value.name)
  const office = 30 + seed % 14
  const commercial = 22 + seed % 9
  const park = 16 + seed % 8
  return [
    { name: '办公资产', value: office },
    { name: '商业资产', value: commercial },
    { name: '园区资产', value: park },
    { name: '其他资产', value: 100 - office - commercial - park }
  ]
})

const coordinatesOf = (feature) => feature.properties.centroid || feature.properties.center || feature.properties.cp || geometryCenter(feature.geometry)
const geometryCenter = (geometry) => {
  const points = []
  const collect = (value) => typeof value[0] === 'number' ? points.push(value) : value.forEach(collect)
  collect(geometry.coordinates)
  const bounds = points.reduce((acc, point) => [Math.min(acc[0], point[0]), Math.min(acc[1], point[1]), Math.max(acc[2], point[0]), Math.max(acc[3], point[1])], [Infinity, Infinity, -Infinity, -Infinity])
  return [(bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2]
}
const updateProjection = (features) => {
  const points = []
  features.forEach((feature) => {
    const collect = (value) => typeof value[0] === 'number' ? points.push(value) : value.forEach(collect)
    collect(feature.geometry.coordinates)
  })
  const bounds = points.reduce((acc, point) => [Math.min(acc[0], point[0]), Math.min(acc[1], point[1]), Math.max(acc[2], point[0]), Math.max(acc[3], point[1])], [Infinity, Infinity, -Infinity, -Infinity])
  projection.center = [(bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2]
  projection.scale = Math.min(48 / (bounds[2] - bounds[0]), 39 / (bounds[3] - bounds[1]))
}
const project = ([lng, lat]) => new THREE.Vector2((lng - projection.center[0]) * projection.scale, (lat - projection.center[1]) * projection.scale)
const ringToPath = (ring, PathType) => {
  const path = new PathType()
  ring.forEach((point, index) => {
    const p = project(point)
    index === 0 ? path.moveTo(p.x, p.y) : path.lineTo(p.x, p.y)
  })
  path.closePath()
  return path
}
const polygonToShape = (polygon) => {
  const shape = ringToPath(polygon[0], THREE.Shape)
  polygon.slice(1).forEach((hole) => shape.holes.push(ringToPath(hole, THREE.Path)))
  return shape
}

const createHeatTexture = () => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 128
  const context = canvas.getContext('2d')
  const gradient = context.createRadialGradient(64, 64, 0, 64, 64, 64)
  gradient.addColorStop(0, 'rgba(255,215,112,.58)')
  gradient.addColorStop(.12, 'rgba(80,231,207,.48)')
  gradient.addColorStop(.34, 'rgba(15,166,226,.3)')
  gradient.addColorStop(.68, 'rgba(0,105,220,.1)')
  gradient.addColorStop(1, 'rgba(0,100,255,0)')
  context.fillStyle = gradient
  context.fillRect(0, 0, 128, 128)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

const addFeature = (feature, index) => {
  const polygons = feature.geometry.type === 'MultiPolygon' ? feature.geometry.coordinates : [feature.geometry.coordinates]
  const depth = 1.25 + (index % 4) * .1
  polygons.forEach((polygon) => {
    const geometry = new THREE.ExtrudeGeometry(polygonToShape(polygon), {
      depth, bevelEnabled: true, bevelThickness: .12, bevelSize: .08, bevelSegments: 2
    })
    const heat = Math.min(1, getStats(feature.properties.name).asset / (level.value === 'province' ? 4800 : Math.max(...currentFeatures.value.map((item) => getStats(item.properties.name).asset))))
    const cold = new THREE.Color(0x07518a)
    const warm = new THREE.Color(heat > .72 ? 0x13a996 : 0x087ec2)
    const material = new THREE.MeshStandardMaterial({
      color: cold.lerp(warm, Math.min(1, heat * 1.2)),
      roughness: .4, metalness: .28, emissive: 0x03253a, emissiveIntensity: .34
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.userData = { ...feature.properties, depth, baseEmissive: material.emissive.clone(), feature }
    mapGroup.add(mesh)
    meshes.push(mesh)
    const edges = new THREE.LineSegments(
      new THREE.EdgesGeometry(geometry, 18),
      new THREE.LineBasicMaterial({ color: 0x72e6ff, transparent: true, opacity: .72, blending: THREE.AdditiveBlending })
    )
    mesh.add(edges)
  })
}

const addHeatPoint = (feature, index) => {
  const position = project(coordinatesOf(feature))
  const stats = getStats(feature.properties.name)
  const scaleValue = level.value === 'province' ? Math.min(9.6, 5.8 + stats.asset / 900) : 6.2 + (index % 4) * .55
  const field = new THREE.Mesh(
    new THREE.PlaneGeometry(scaleValue * 2.25, scaleValue * 2.25),
    new THREE.MeshBasicMaterial({ map: heatTexture, color: 0xb9eaff, transparent: true, opacity: .38, depthWrite: false, depthTest: true, blending: THREE.NormalBlending })
  )
  field.position.set(position.x, position.y, 2.02)
  field.renderOrder = 4
  heatGroup.add(field)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: heatTexture, color: 0xc9f6ff, transparent: true, opacity: .28, depthWrite: false, blending: THREE.AdditiveBlending }))
  sprite.position.set(position.x, position.y, 2.45)
  sprite.scale.set(scaleValue * .9, scaleValue * .9, 1)
  sprite.renderOrder = 5
  heatGroup.add(sprite)
  const ring = new THREE.Mesh(
    new THREE.RingGeometry(.42, .56, 48),
    new THREE.MeshBasicMaterial({ color: index % 3 === 0 ? 0xe5cb71 : 0x55cfe5, transparent: true, opacity: .48, side: THREE.DoubleSide, blending: THREE.AdditiveBlending })
  )
  ring.position.set(position.x, position.y, 2.35)
  heatGroup.add(ring)
  const beam = new THREE.Mesh(
    new THREE.CylinderGeometry(.045, .18, 3.2 + index % 3, 12, 1, true),
    new THREE.MeshBasicMaterial({ color: 0x5eeaff, transparent: true, opacity: .2, side: THREE.DoubleSide, blending: THREE.AdditiveBlending })
  )
  beam.rotation.x = Math.PI / 2
  beam.position.set(position.x, position.y, 3.7 + index % 3 * .5)
  heatGroup.add(beam)
  pulses.push({ field, ring, sprite, offset: index * .47 })
}

const addFlow = (fromFeature, toFeature, index) => {
  const from = project(coordinatesOf(fromFeature))
  const to = project(coordinatesOf(toFeature))
  const distance = from.distanceTo(to)
  if (distance < 1) return
  const curve = new THREE.QuadraticBezierCurve3(
    new THREE.Vector3(from.x, from.y, 2.1),
    new THREE.Vector3((from.x + to.x) / 2, (from.y + to.y) / 2, 5 + distance * .18),
    new THREE.Vector3(to.x, to.y, 2.1)
  )
  const line = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(curve.getPoints(80)),
    new THREE.LineBasicMaterial({ color: index % 3 === 0 ? 0xffd45b : 0x25cfff, transparent: true, opacity: .42, blending: THREE.AdditiveBlending })
  )
  flowGroup.add(line)
  const particle = new THREE.Mesh(
    new THREE.SphereGeometry(.18, 12, 12),
    new THREE.MeshBasicMaterial({ color: 0xd9ffff, blending: THREE.AdditiveBlending })
  )
  particle.add(new THREE.PointLight(index % 3 === 0 ? 0xffcc52 : 0x31dcff, 2.2, 5))
  flowGroup.add(particle)
  flowParticles.push({ mesh: particle, curve, progress: (index * .137) % 1, speed: .0015 + (index % 4) * .00018 })
}

const offsetCurvePoints = (curve, offset) => curve.getPoints(72).map((point, index, points) => {
  const previous = points[Math.max(0, index - 1)]
  const next = points[Math.min(points.length - 1, index + 1)]
  const dx = next.x - previous.x
  const dy = next.y - previous.y
  const length = Math.hypot(dx, dy) || 1
  return new THREE.Vector3(point.x - dy / length * offset, point.y + dx / length * offset, point.z)
})

const addRailway = (fromFeature, toFeature, index) => {
  const from = project(coordinatesOf(fromFeature))
  const to = project(coordinatesOf(toFeature))
  const dx = to.x - from.x
  const dy = to.y - from.y
  const length = Math.hypot(dx, dy)
  if (length < 1) return
  const bend = (index % 2 ? 1 : -1) * Math.min(1.2, length * .035)
  const curve = new THREE.QuadraticBezierCurve3(
    new THREE.Vector3(from.x, from.y, 2.08),
    new THREE.Vector3((from.x + to.x) / 2 - dy / length * bend, (from.y + to.y) / 2 + dx / length * bend, 2.08),
    new THREE.Vector3(to.x, to.y, 2.08)
  )
  const glow = new THREE.Mesh(
    new THREE.TubeGeometry(curve, 72, .16, 6, false),
    new THREE.MeshBasicMaterial({ color: 0x17bfe4, transparent: true, opacity: .16, depthWrite: false, blending: THREE.AdditiveBlending })
  )
  railwayGroup.add(glow)
  ;[-.12, .12].forEach((offset) => {
    const points = offsetCurvePoints(curve, offset)
    const rail = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(points),
      new THREE.LineBasicMaterial({ color: 0xbcefff, transparent: true, opacity: .82, blending: THREE.AdditiveBlending })
    )
    railwayGroup.add(rail)
  })
  const sleepers = []
  for (let step = .08; step < .96; step += .09) {
    const point = curve.getPoint(step)
    const tangent = curve.getTangent(step)
    const normal = new THREE.Vector3(-tangent.y, tangent.x, 0).normalize().multiplyScalar(.19)
    sleepers.push(point.clone().add(normal), point.clone().sub(normal))
  }
  const sleeperLines = new THREE.LineSegments(
    new THREE.BufferGeometry().setFromPoints(sleepers),
    new THREE.LineBasicMaterial({ color: 0x5bb3c8, transparent: true, opacity: .55 })
  )
  railwayGroup.add(sleeperLines)
  //1、整体改造尝试
  /*const trainGroup = new THREE.Group()
  const trainGlow = new THREE.Mesh(
    new THREE.PlaneGeometry(4.6, 1.15),
    new THREE.MeshBasicMaterial({ map: heatTexture, color: index % 4 === 0 ? 0xffd96a : 0x3edfff, transparent: true, opacity: .34, depthWrite: false, depthTest: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide })
  )
  trainGlow.position.z = -.04
  trainGlow.renderOrder = 7
  trainGroup.add(trainGlow)
  const train = new THREE.Mesh(
    new THREE.PlaneGeometry(3.8, .48),
    new THREE.MeshBasicMaterial({ map: trainTexture, color: 0xffffff, transparent: true, opacity: 1, depthWrite: false, depthTest: false, side: THREE.DoubleSide })
  )
  train.position.z = .04
  train.renderOrder = 8
  trainGroup.add(train)
  railwayGroup.add(trainGroup)
  railParticles.push({ mesh: trainGroup, glow: trainGlow, curve, progress: (index * .091) % 1, speed: .00105 + index % 3 * .00012, offset: index * .6 })*/


  // 2、===== 火车逐节消失改造：拆成 4 节独立车厢 =====
  const TRAIN_SEGMENTS = 4          // 贴图自然分为 车尾+2车厢+车头
  const TRAIN_TOTAL_LENGTH = 3.8    // 与原火车宽度一致
  const curveLength = curve.getLength()
  const unitToProgress = 1 / curveLength
  const segmentSpacing = TRAIN_TOTAL_LENGTH / TRAIN_SEGMENTS
  const trainSegments = []

  // 光晕（改为独立对象，跟随车头）
  const trainGlow = new THREE.Mesh(
      new THREE.PlaneGeometry(2.2, 1.15),
      new THREE.MeshBasicMaterial({ map: heatTexture, color: index % 4 === 0 ? 0xffd96a : 0x3edfff, transparent: true, opacity: .34, depthWrite: false, depthTest: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide })
  )
  trainGlow.position.z = -.04
  trainGlow.renderOrder = 7
  railwayGroup.add(trainGlow)

  // 创建 4 节车厢，每节通过 UV 裁剪显示贴图的对应等分
  for (let i = 0; i < TRAIN_SEGMENTS; i++) {
    const segment = new THREE.Mesh(
        new THREE.PlaneGeometry(segmentSpacing * 0.92, .48), // 0.92 留缝掩盖接缝
        new THREE.MeshBasicMaterial({ map: trainTexture, color: 0xffffff, transparent: true, opacity: 1, depthWrite: false, depthTest: false, side: THREE.DoubleSide })
    )
    // UV 裁剪：i=0 车尾，i=3 车头
    const uvStart = i / TRAIN_SEGMENTS
    const uvEnd = (i + 1) / TRAIN_SEGMENTS
    const uv = segment.geometry.attributes.uv
    uv.setXY(0, uvStart, 0)
    uv.setXY(1, uvEnd, 0)
    uv.setXY(2, uvStart, 1)
    uv.setXY(3, uvEnd, 1)
    uv.needsUpdate = true
    segment.position.z = .04
    segment.renderOrder = 8
    railwayGroup.add(segment)
    trainSegments.push({
      mesh: segment,
      // 车头(i=3) offset=0；越靠后 offset 越小（progress 越小）
      progressOffset: -(TRAIN_SEGMENTS - 1 - i) * segmentSpacing * unitToProgress
    })
    /*结束*/
  }

  railParticles.push({
    segments: trainSegments,
    glow: trainGlow,
    curve,
    progress: (index * .091) % 1,  // progress 现在表示【车头】位置
    speed: .00105 + index % 3 * .00012,
    offset: index * .6
  })


}

const disposeGroup = (group) => {
  if (!group) return
  group.traverse((object) => {
    object.geometry?.dispose()
    const materials = Array.isArray(object.material) ? object.material : [object.material]
    materials.filter(Boolean).forEach((material) => material.dispose())
  })
  scene.remove(group)
}

const buildMap = (features) => {
  clearHover()
  meshes.length = 0
  pulses.length = 0
  flowParticles.length = 0
  railParticles.length = 0
  labelElements = []
  disposeGroup(mapGroup)
  disposeGroup(effectGroup)
  updateProjection(features)
  mapGroup = new THREE.Group()
  effectGroup = new THREE.Group()
  heatGroup = new THREE.Group()
  flowGroup = new THREE.Group()
  railwayGroup = new THREE.Group()
  effectGroup.add(heatGroup, railwayGroup, flowGroup)
  scene.add(mapGroup, effectGroup)
  features.forEach(addFeature)
  features.forEach(addHeatPoint)
  const hubName = level.value === 'province' ? '杭州市' : currentCity.value
  const hub = features.find((feature) => feature.properties.name === hubName) || features[Math.floor(features.length / 2)]
  features.filter((feature) => feature !== hub).forEach((feature, index) => addFlow(hub, feature, index))
  if (level.value === 'province') {
    RAIL_LINKS.forEach(([fromName, toName], index) => {
      const fromFeature = features.find((feature) => feature.properties.name === fromName)
      const toFeature = features.find((feature) => feature.properties.name === toName)
      if (fromFeature && toFeature) addRailway(fromFeature, toFeature, index)
    })
  }
  labelWorldPoints = features.map((feature) => {
    const position = project(coordinatesOf(feature))
    return new THREE.Vector3(position.x, position.y, 3.5)
  })
  heatGroup.visible = heatEnabled.value
  flowGroup.visible = flowEnabled.value
  railwayGroup.visible = railEnabled.value
  nextTick(updateLabels)
  resetCamera(true)
}

const resetCamera = (animated = false) => {
  const position = new THREE.Vector3(0, -55, 72)
  const target = new THREE.Vector3(0, 0, 0)
  if (animated) cameraTween = { fromPosition: camera.position.clone(), toPosition: position, fromTarget: controls.target.clone(), toTarget: target, progress: 0 }
  else { camera.position.copy(position); controls.target.copy(target) }
}

const init = () => {
  const host = canvasHost.value
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x04101d)
  scene.fog = new THREE.FogExp2(0x04101d, .0085)
  camera = new THREE.PerspectiveCamera(40, host.clientWidth / host.clientHeight, .1, 1000)
  camera.position.set(0, -55, 72)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(host.clientWidth, host.clientHeight)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.18
  host.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = .065
  controls.minDistance = 38
  controls.maxDistance = 120
  controls.maxPolarAngle = Math.PI * .7
  controls.minPolarAngle = Math.PI * .12
  scene.add(new THREE.HemisphereLight(0x8eeaff, 0x06101d, 1.8))
  const keyLight = new THREE.DirectionalLight(0xc9f7ff, 3.6)
  keyLight.position.set(-25, -35, 55)
  scene.add(keyLight)
  const cyanLight = new THREE.PointLight(0x00bfff, 80, 100)
  cyanLight.position.set(24, 18, 22)
  scene.add(cyanLight)
  const floor = new THREE.GridHelper(130, 38, 0x147495, 0x0b344b)
  floor.rotation.x = Math.PI / 2
  floor.position.z = -1.35
  floor.material.transparent = true
  floor.material.opacity = .38
  scene.add(floor)
  ;[30, 41, 54].forEach((radius, index) => {
    const ring = new THREE.Mesh(new THREE.RingGeometry(radius, radius + .08, 128), new THREE.MeshBasicMaterial({ color: 0x1687aa, transparent: true, opacity: .24 - index * .04, side: THREE.DoubleSide }))
    ring.position.z = -1.28
    scene.add(ring)
  })
  heatTexture = createHeatTexture()
  trainTexture = new THREE.TextureLoader().load(trainImageUrl)
  trainTexture.colorSpace = THREE.SRGBColorSpace
  trainTexture.minFilter = THREE.LinearMipmapLinearFilter
  buildMap(ZhejiangGeoJSON.features)
  raycaster = new THREE.Raycaster()
  mouse = new THREE.Vector2(2, 2)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host)
  updateTime()
  clockTimer = window.setInterval(updateTime, 1000)
  animate()
}

const animate = (time = 0) => {
  if (cameraTween) {
    cameraTween.progress = Math.min(1, cameraTween.progress + .035)
    const ease = 1 - Math.pow(1 - cameraTween.progress, 3)
    camera.position.lerpVectors(cameraTween.fromPosition, cameraTween.toPosition, ease)
    controls.target.lerpVectors(cameraTween.fromTarget, cameraTween.toTarget, ease)
    if (cameraTween.progress >= 1) cameraTween = null
  }
  pulses.forEach((pulse, index) => {
    const wave = (Math.sin(time * .0023 + pulse.offset) + 1) / 2
    const size = .92 + wave * .72
    pulse.ring.scale.set(size, size, 1)
    pulse.ring.material.opacity = .42 - wave * .24
    pulse.sprite.material.opacity = .2 + Math.sin(time * .0014 + index) * .06
    pulse.field.material.opacity = .3 + wave * .1
  })
  flowParticles.forEach((item) => {
    item.progress = (item.progress + item.speed) % 1
    item.mesh.position.copy(item.curve.getPoint(item.progress))
  })
  //1、整体改造
  /*railParticles.forEach((item) => {
    item.progress = (item.progress + item.speed) % 1
    const position = item.curve.getPoint(item.progress)
    const tangent = item.curve.getTangent(item.progress)
    item.mesh.position.copy(position)
    item.mesh.rotation.z = Math.atan2(tangent.y, tangent.x)
    item.glow.material.opacity = .27 + (Math.sin(time * .002 + item.offset) + 1) * .08
  })*/
  railParticles.forEach((item) => {
    item.progress += item.speed
    // 车尾也完全通过终点后，重置到起点之前，留出空窗间隔
    const tailOffset = item.segments[0].progressOffset
    if (item.progress + tailOffset >= 1.02) {
      item.progress = -0.18  // 起点前 18% 曲线长度的间隔，再驶入
    }

    // 逐节更新：车头先到终点先淡出消失，后面车厢陆续消失
    item.segments.forEach((seg) => {
      const segProgress = item.progress + seg.progressOffset
      if (segProgress >= 0 && segProgress < 1) {
        seg.mesh.visible = true
        const t = Math.max(0, Math.min(1, segProgress))
        const position = item.curve.getPoint(t)
        const tangent = item.curve.getTangent(t)
        seg.mesh.position.copy(position)
        seg.mesh.rotation.z = Math.atan2(tangent.y, tangent.x)
        // 终点前 6% 淡出，起点前 5% 淡入
        const fadeOutZone = 0.06
        const fadeInZone = 0.05
        if (segProgress > 1 - fadeOutZone) {
          seg.mesh.material.opacity = Math.max(0, (1 - segProgress) / fadeOutZone)
        } else if (segProgress < fadeInZone) {
          seg.mesh.material.opacity = Math.min(1, segProgress / fadeInZone)
        } else {
          seg.mesh.material.opacity = 1
        }
      } else {
        seg.mesh.visible = false
      }
    })

    // 光晕跟随车头，车头消失时光晕也隐藏
    if (item.progress >= 0 && item.progress < 1) {
      item.glow.visible = true
      const t = Math.max(0, Math.min(1, item.progress))
      item.glow.position.copy(item.curve.getPoint(t))
      item.glow.rotation.z = Math.atan2(item.curve.getTangent(t).y, item.curve.getTangent(t).x)
      item.glow.material.opacity = .27 + (Math.sin(time * .002 + item.offset) + 1) * .08
    } else {
      item.glow.visible = false
    }
  })
/*结束*/


  updateLabels()
  if (effectGroup) effectGroup.rotation.z = Math.sin(time * .00018) * .002
  controls.update()
  renderer.render(scene, camera)
  animationId = requestAnimationFrame(animate)
}

const resize = () => {
  if (!canvasHost.value || !renderer) return
  const { clientWidth, clientHeight } = canvasHost.value
  camera.aspect = clientWidth / clientHeight
  camera.updateProjectionMatrix()
  renderer.setSize(clientWidth, clientHeight)
  updateLabels()
}
const setLabelRef = (element, index) => { if (element) labelElements[index] = element }
const updateLabels = () => {
  if (!camera || !canvasHost.value) return
  const width = canvasHost.value.clientWidth
  const height = canvasHost.value.clientHeight
  labelWorldPoints.forEach((worldPoint, index) => {
    const element = labelElements[index]
    if (!element) return
    const screen = worldPoint.clone().project(camera)
    const x = (screen.x * .5 + .5) * width
    const y = (-screen.y * .5 + .5) * height
    const visible = screen.z > -1 && screen.z < 1 && x > 8 && x < width - 8 && y > 115 && y < height - 42
    element.style.display = visible ? 'grid' : 'none'
    if (visible) element.style.transform = `translate3d(${x}px,${y}px,0) translate(-50%,-100%)`
  })
}
const pick = (event) => {
  const rect = canvasHost.value.getBoundingClientRect()
  pointer.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  mouse.set((pointer.value.x / rect.width) * 2 - 1, -(pointer.value.y / rect.height) * 2 + 1)
  raycaster.setFromCamera(mouse, camera)
  return raycaster.intersectObjects(meshes, false)[0]?.object || null
}
const setMeshHighlight = (mesh, active) => {
  if (!mesh) return
  mesh.material.emissive.copy(active ? new THREE.Color(0x16bce8) : mesh.userData.baseEmissive)
  mesh.material.emissiveIntensity = active ? .9 : .34
  mesh.position.z = active ? .4 : 0
}
const onPointerMove = (event) => {
  if (!raycaster) return
  const hit = pick(event)
  if (hit === hoveredMesh) return
  setMeshHighlight(hoveredMesh, false)
  hoveredMesh = hit
  if (hit) {
    setMeshHighlight(hit, true)
    hovered.value = hit.userData
    canvasHost.value.style.cursor = 'pointer'
  } else clearHover()
}
const clearHover = () => {
  setMeshHighlight(hoveredMesh, false)
  hoveredMesh = null
  hovered.value = null
  if (canvasHost.value) canvasHost.value.style.cursor = 'grab'
}
const onMapClick = () => {
  if (!hoveredMesh) return
  const name = hoveredMesh.userData.name
  if (level.value === 'province') drillDown(name)
  else {
    selected.value = { name }
    focusFeature(name)
  }
}
const focusFeature = (name) => {
  const targets = meshes.filter((item) => item.userData.name === name)
  if (!targets.length) return
  const box = new THREE.Box3()
  targets.forEach((mesh) => box.expandByObject(mesh))
  const target = box.getCenter(new THREE.Vector3())
  const size = box.getSize(new THREE.Vector3())
  const distance = Math.max(31, Math.max(size.x, size.y) * 2.2)
  cameraTween = {
    fromPosition: camera.position.clone(),
    toPosition: new THREE.Vector3(target.x, target.y - distance * .58, Math.max(44, distance)),
    fromTarget: controls.target.clone(), toTarget: target, progress: 0
  }
}
const selectFromList = (item) => {
  selected.value = { name: item.name }
  if (level.value === 'province') drillDown(item.name)
  else focusFeature(item.name)
}
const selectLabel = (name) => {
  selected.value = { name }
  if (level.value === 'province') drillDown(name)
  else focusFeature(name)
}
const drillDown = async (name) => {
  const adcode = CITY_CODES[name]
  if (!adcode || mapLoading.value) return
  mapLoading.value = true
  currentCity.value = name
  fetchController?.abort()
  fetchController = new AbortController()
  try {
    const response = await fetch(`https://geo.datav.aliyun.com/areas_v3/bound/${adcode}_full.json`, { signal: fetchController.signal })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const geojson = await response.json()
    if (!geojson.features?.length) throw new Error('区县边界为空')
    level.value = 'city'
    currentFeatures.value = geojson.features
    selected.value = { name }
    buildMap(geojson.features)
  } catch (error) {
    if (error.name !== 'AbortError') ElMessage.error('区县地图加载失败，请检查网络后重试')
  } finally { mapLoading.value = false }
}
const backToProvince = () => {
  if (level.value === 'province') { resetCamera(true); selected.value = { name: '浙江省' }; return }
  fetchController?.abort()
  level.value = 'province'
  currentCity.value = ''
  currentFeatures.value = ZhejiangGeoJSON.features
  selected.value = { name: '浙江省' }
  buildMap(ZhejiangGeoJSON.features)
}
const updateTime = () => {
  currentTime.value = new Intl.DateTimeFormat('zh-CN', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date()).replaceAll('/', '-')
}

watch(heatEnabled, (visible) => { if (heatGroup) heatGroup.visible = visible })
watch(flowEnabled, (visible) => { if (flowGroup) flowGroup.visible = visible })
watch(railEnabled, (visible) => { if (railwayGroup) railwayGroup.visible = visible })
onMounted(init)
onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
  window.clearInterval(clockTimer)
  fetchController?.abort()
  resizeObserver?.disconnect()
  controls?.dispose()
  disposeGroup(mapGroup)
  disposeGroup(effectGroup)
  heatTexture?.dispose()
  trainTexture?.dispose()
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>
