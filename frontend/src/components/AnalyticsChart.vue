<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { Chart, LineController, LineElement, PointElement, CategoryScale, LinearScale, BarController, BarElement, DoughnutController, ArcElement, Tooltip, Legend, type ChartDataset } from 'chart.js'
Chart.register(LineController, LineElement, PointElement, CategoryScale, LinearScale, BarController, BarElement, DoughnutController, ArcElement, Tooltip, Legend)
const props = defineProps<{ type: 'line' | 'bar' | 'doughnut'; labels: string[]; series: { label: string; data: number[]; color: string | string[] }[]; label: string }>()
const canvas = ref<HTMLCanvasElement>(); let chart: Chart | undefined
function render() {
  chart?.destroy()
  if (!canvas.value) return
  const datasets = props.series.map(s => ({ label: s.label, data: s.data, borderColor: s.color, backgroundColor: s.color, borderWidth: props.type === 'line' ? 2 : 0, pointRadius: 2, tension: .2 })) as ChartDataset[]
  chart = new Chart(canvas.value, { type: props.type, data: { labels: props.labels, datasets }, options: {
    responsive: true, maintainAspectRatio: false, animation: false,
    plugins: { legend: { display: props.type !== 'bar', position: 'bottom' } },
    ...(props.type !== 'doughnut' ? { scales: { y: { beginAtZero: true, ticks: { precision: 0 } }, x: { ticks: { maxTicksLimit: 10 } } } } : {}),
  } })
}
onMounted(render); watch(() => [props.labels, props.series, props.type], render, { deep: true }); onBeforeUnmount(() => chart?.destroy())
</script>
<template><div class="analytics-chart"><canvas ref="canvas" role="img" :aria-label="label" /></div></template>
<style scoped>.analytics-chart { height:290px; position:relative; min-width:0; }</style>
