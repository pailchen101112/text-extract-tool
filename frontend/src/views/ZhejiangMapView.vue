<template>
  <section class="map-page">
    <div class="map-title"><div><p class="eyebrow">GEO VISUALIZATION</p><h2>浙江省三维态势</h2><p>基于 Three.js 的地级市边界展示，可旋转、缩放并悬停查看区域信息。</p></div><div class="map-badge"><span></span> 3D ENGINE ONLINE</div></div>
    <div class="map-stage">
      <div ref="canvasHost" class="map-canvas" @pointermove="onPointerMove" @pointerleave="clearHover"></div>
      <div class="map-grid"></div>
      <div class="map-summary"><p>区域总览</p><strong>{{ cityStats.length }}</strong><span>地级行政区</span><div><b>{{ totalUnits }}</b><small>区县单元（地图数据）</small></div></div>
      <div class="map-instructions">拖拽旋转 · 滚轮缩放 · 悬停查看</div>
      <div v-if="hovered" class="map-tooltip" :style="tooltipStyle"><strong>{{ hovered.name }}</strong><span>下辖 {{ hovered.childNum }} 个区县单元</span></div>
      <div class="city-list"><button v-for="city in cityStats" :key="city.name" :class="{active:selected?.name===city.name}" @click="focusCity(city.name)"><span>{{city.name}}</span><b>{{String(city.index).padStart(2,'0')}}</b></button></div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import ZhejiangGeoJSON from 'china-map-geojson/lib/province/zhe_jiang_geo.js'

const canvasHost = ref()
const hovered = ref(null)
const selected = ref(null)
const pointer = ref({ x: 0, y: 0 })
const tooltipStyle = computed(() => ({ left: `${pointer.value.x + 18}px`, top: `${pointer.value.y + 18}px` }))
const cityStats = ZhejiangGeoJSON.features.map((feature, index) => ({ name: feature.properties.name, index: index + 1 }))
const totalUnits = ZhejiangGeoJSON.features.reduce((sum, feature) => sum + (feature.properties.childNum || 0), 0)
let scene, camera, renderer, controls, raycaster, mouse, animationId, resizeObserver, hoveredMesh
const meshes = []
const center = [120.15, 29.15]
const scale = 14

const project = ([lng, lat]) => new THREE.Vector2((lng - center[0]) * scale, (lat - center[1]) * scale)

const ringToPath = (ring, PathType) => {
  const path = new PathType()
  ring.forEach((point, index) => {
    const p = project(point)
    index === 0 ? path.moveTo(p.x, p.y) : path.lineTo(p.x, p.y)
  })
  return path
}

const polygonToShape = (polygon) => {
  const shape = ringToPath(polygon[0], THREE.Shape)
  polygon.slice(1).forEach((hole) => shape.holes.push(ringToPath(hole, THREE.Path)))
  return shape
}

const addFeature = (feature, index) => {
  const polygons = feature.geometry.type === 'MultiPolygon' ? feature.geometry.coordinates : [feature.geometry.coordinates]
  polygons.forEach((polygon) => {
    const geometry = new THREE.ExtrudeGeometry(polygonToShape(polygon), {
      depth: 1.5 + (index % 4) * 0.22,
      bevelEnabled: true,
      bevelThickness: 0.16,
      bevelSize: 0.1,
      bevelSegments: 2
    })
    const material = new THREE.MeshStandardMaterial({
      color: new THREE.Color().setHSL(0.54 + index * 0.006, 0.68, 0.38 + (index % 3) * 0.035),
      roughness: 0.48,
      metalness: 0.22,
      emissive: 0x021b2d,
      emissiveIntensity: 0.25
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.userData = { ...feature.properties, baseEmissive: material.emissive.clone() }
    scene.add(mesh)
    meshes.push(mesh)
    const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), new THREE.LineBasicMaterial({ color: 0x70d7ff, transparent: true, opacity: 0.56 }))
    mesh.add(edges)
  })
}

const init = () => {
  const host = canvasHost.value
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x06131f)
  scene.fog = new THREE.FogExp2(0x06131f, 0.008)
  camera = new THREE.PerspectiveCamera(42, host.clientWidth / host.clientHeight, 0.1, 1000)
  camera.position.set(0, -54, 78)
  camera.lookAt(0, 0, 0)
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(host.clientWidth, host.clientHeight)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  host.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.065
  controls.minDistance = 46
  controls.maxDistance = 125
  controls.maxPolarAngle = Math.PI * 0.76
  scene.add(new THREE.AmbientLight(0x79bfe5, 1.3))
  const keyLight = new THREE.DirectionalLight(0xbdeaff, 3.2)
  keyLight.position.set(-20, -25, 55)
  scene.add(keyLight)
  const rimLight = new THREE.PointLight(0x00a8ff, 90, 120)
  rimLight.position.set(28, 25, 28)
  scene.add(rimLight)
  const floor = new THREE.GridHelper(120, 24, 0x174968, 0x102f45)
  floor.rotation.x = Math.PI / 2
  floor.position.z = -1.2
  scene.add(floor)
  ZhejiangGeoJSON.features.forEach(addFeature)
  raycaster = new THREE.Raycaster()
  mouse = new THREE.Vector2(2, 2)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host)
  animate()
}

const animate = () => {
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
}
const onPointerMove = (event) => {
  const rect = canvasHost.value.getBoundingClientRect()
  pointer.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  mouse.set((pointer.value.x / rect.width) * 2 - 1, -(pointer.value.y / rect.height) * 2 + 1)
  raycaster.setFromCamera(mouse, camera)
  const hit = raycaster.intersectObjects(meshes, false)[0]?.object
  if (hit === hoveredMesh) return
  if (hoveredMesh) { hoveredMesh.material.emissive.copy(hoveredMesh.userData.baseEmissive); hoveredMesh.position.z = 0 }
  hoveredMesh = hit
  if (hit) { hit.material.emissive.set(0x16a8d8); hit.material.emissiveIntensity = 0.8; hit.position.z = 0.45; hovered.value = hit.userData; selected.value = hit.userData; canvasHost.value.style.cursor = 'pointer' }
  else clearHover()
}
const clearHover = () => {
  if (hoveredMesh) { hoveredMesh.material.emissive.copy(hoveredMesh.userData.baseEmissive); hoveredMesh.material.emissiveIntensity = 0.25; hoveredMesh.position.z = 0 }
  hoveredMesh = null; hovered.value = null
  if (canvasHost.value) canvasHost.value.style.cursor = 'grab'
}
const focusCity = (name) => {
  const mesh = meshes.find((item) => item.userData.name === name)
  if (!mesh) return
  selected.value = mesh.userData
  const box = new THREE.Box3().setFromObject(mesh)
  const target = box.getCenter(new THREE.Vector3())
  controls.target.copy(target)
  camera.position.set(target.x, target.y - 35, 55)
}
onMounted(init)
onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
  resizeObserver?.disconnect()
  controls?.dispose()
  meshes.forEach((mesh) => { mesh.geometry.dispose(); mesh.material.dispose() })
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>
