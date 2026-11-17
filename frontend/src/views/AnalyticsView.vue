<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import BlogShell from '../components/blog/BlogShell.vue'
import AnalyticsChart from '../components/AnalyticsChart.vue'
import AnalyticsPosts from '../components/AnalyticsPosts.vue'
import { analyticsGet, type Overview, type Traffic, type Referrer, type CategoryStats, type PostStats } from '../api/analytics'
const preset = ref('7'), start = ref(''), end = ref(''), loading = ref(true), error = ref('')
const overview = ref<Overview>(), traffic = ref<Traffic[]>([]), referrers = ref<Referrer[]>([]), categories = ref<CategoryStats[]>([]), recent = ref<PostStats[]>([]), popular = ref<PostStats[]>([])
const appliedQuery = ref('days=7')
const mode = ref('period'), selected = ref<PostStats>(), postTraffic = ref<Traffic[]>([]), postError = ref(''), postLoading = ref(false)
let version = 0, detailVersion = 0
onBeforeUnmount(() => { version++; detailVersion++ })
const query = computed(() => preset.value === 'custom' ? new URLSearchParams({ start: start.value, end: end.value }).toString() : `days=${preset.value}`)
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
const percent = (v: number | null) => v === null ? '이전 데이터 없음' : `${v > 0 ? '+' : ''}${v.toLocaleString()}%`
const sourceNames: Record<string, string> = { GOOGLE: 'Google 검색', NAVER: 'Naver 검색', DAUM: 'Daum 검색', GITHUB: 'GitHub', OTHER: '기타 외부', DIRECT: '직접 / 출처 불명', INTERNAL: '내부 이동으로 시작' }
const colors = ['#235c4b', '#469c82', '#8bc6b2', '#4b78b5', '#8999b8', '#d1b777', '#b1b1b1']
const lineSeries = computed(() => [{ label: '순 방문자', data: traffic.value.map(x => x.visitors), color: '#235c4b' }, { label: '페이지 조회', data: traffic.value.map(x => x.pageViews), color: '#4b78b5' }])
async function load() {
  const n = ++version; detailVersion++; loading.value = true; error.value = ''; selected.value = undefined
  if (preset.value === 'custom' && (!start.value || !end.value || start.value > end.value || end.value > today)) { error.value = '오늘까지의 올바른 시작일과 종료일을 입력하세요.'; loading.value = false; return }
  try {
    const q = query.value
    const result = await Promise.all([
      analyticsGet<Overview>('overview', q), analyticsGet<Traffic[]>('traffic', q), analyticsGet<Referrer[]>('referrers', q),
      analyticsGet<CategoryStats[]>('categories', q), analyticsGet<PostStats[]>('posts/recent', q),
      analyticsGet<PostStats[]>('posts/popular', mode.value === 'week' ? 'days=7' : `${q}&allTime=${mode.value === 'all'}`),
    ])
    if (n !== version) return
    appliedQuery.value = q
    ;[overview.value, traffic.value, referrers.value, categories.value, recent.value, popular.value] = result
  } catch { if (n === version) error.value = '통계를 불러오지 못했습니다. 다시 시도해 주세요.' }
  finally { if (n === version) loading.value = false }
}
async function selectPost(post: PostStats) {
  selected.value = post; postError.value = ''; postLoading.value = true; const n = ++detailVersion
  try { const rows = await analyticsGet<Traffic[]>(`posts/${post.id}/traffic`, appliedQuery.value); if (n === detailVersion) postTraffic.value = rows }
  catch { if (n === detailVersion) postError.value = '게시글 추이를 불러오지 못했습니다.' }
  finally { if (n === detailVersion) postLoading.value = false }
}
onMounted(load)
</script>
<template><BlogShell><div class="analytics-page">
<header class="analytics-heading"><p class="blog-eyebrow">DEVFLOW / ADMIN</p><h1>Analytics<span>.</span></h1><p>블로그 방문과 콘텐츠의 흐름 · Asia/Seoul</p></header>
<form class="analytics-filters" @submit.prevent="load"><label>조회 기간<select v-model="preset"><option value="7">최근 7일</option><option value="30">최근 30일</option><option value="90">최근 90일</option><option value="custom">사용자 지정</option></select></label><template v-if="preset === 'custom'"><label>시작일<input v-model="start" type="date" :max="today" required></label><label>종료일<input v-model="end" type="date" :min="start" :max="today" required></label></template><button :disabled="loading">조회</button></form>
<p v-if="loading" role="status">통계를 불러오는 중입니다…</p><div v-else-if="error" role="alert">{{ error }} <button @click="load">다시 시도</button></div>
<template v-else-if="overview"><p class="analytics-period">{{ overview.start }} — {{ overview.end }} · 이전 동일 일수와 비교 (오늘은 진행 중)</p>
<div class="analytics-cards"><article><span>순 방문자</span><strong>{{ overview.visitors.toLocaleString() }}</strong><small>{{ percent(overview.visitorChangePercent) }} · 방문 {{ overview.visits }}회</small></article><article><span>페이지 조회</span><strong>{{ overview.pageViews.toLocaleString() }}</strong><small>{{ percent(overview.pageViewChangePercent) }}</small></article><article><span>공개 게시글</span><strong>{{ overview.postCount.toLocaleString() }}</strong><small>현재 발행된 글</small></article><article><span>방문자당 조회</span><strong>{{ overview.viewsPerVisitor }}</strong><small>조회수 / 순 방문자</small></article></div>
<p class="analytics-totals">오늘 순 방문자 <b>{{ overview.todayVisitors }}</b> · 어제 {{ overview.yesterdayVisitors }} ({{ percent(overview.todayChangePercent) }})<br>보존 기간 내 전체 조회 <b>{{ overview.totalPageViews }}</b> · 전체 순 방문자 {{ overview.totalUniqueVisitors }}</p>
<p v-if="!overview.pageViews" class="analytics-empty">수집된 방문 데이터가 없습니다. 동의한 일반 방문자가 공개 페이지를 열면 통계가 표시됩니다.</p>
<section class="analytics-panel"><h2>일별 방문자 및 페이지 조회</h2><AnalyticsChart type="line" :labels="traffic.map(x => x.date)" :series="lineSeries" label="일별 순 방문자와 페이지 조회수"/><details><summary>일별 데이터 표</summary><div class="analytics-table-wrap"><table><thead><tr><th>날짜</th><th>순 방문자</th><th>페이지 조회</th></tr></thead><tbody><tr v-for="d in traffic" :key="d.date"><td>{{ d.date }}</td><td>{{ d.visitors }}</td><td>{{ d.pageViews }}</td></tr></tbody></table></div></details></section>
<section class="analytics-panel"><h2>유입 경로별 방문 비율</h2><p>선택 기간에 시작한 세션의 최초 유입 · 내부 이동은 기존 세션에 합산</p><p v-if="!referrers.length">유입 데이터가 없습니다.</p><AnalyticsChart v-else type="doughnut" :labels="referrers.map(x => sourceNames[x.source] || x.source)" :series="[{ label: '방문', data: referrers.map(x => x.visits), color: colors }]" label="유입 경로별 방문 비율"/><ul class="analytics-legend"><li v-for="r in referrers" :key="r.source">{{ sourceNames[r.source] || r.source }} · {{ r.visits }}회 · {{ r.percentage.toFixed(1) }}%</li></ul></section>
<section class="analytics-panel"><h2>카테고리별 조회수</h2><AnalyticsChart type="bar" :labels="categories.map(x => x.name)" :series="[{ label: '선택 기간 조회', data: categories.map(x => x.periodViews), color: '#235c4b' }]" label="선택 기간 인기 카테고리 순위"/><div class="analytics-table-wrap"><table><thead><tr><th>카테고리</th><th>현재 공개 글</th><th>누적 조회</th><th>기간 조회</th><th>기간 비율</th></tr></thead><tbody><tr v-for="c in categories" :key="c.id ?? 'none'"><td>{{ c.name }}</td><td>{{ c.postCount }}</td><td>{{ c.totalViews }}</td><td>{{ c.periodViews }}</td><td>{{ c.percentage.toFixed(1) }}%</td></tr></tbody></table></div></section>
<section class="analytics-panel"><h2>최근 작성한 게시글</h2><p>선택 기간에 작성된 공개 글 · 작성일 기준 최근 10개</p><AnalyticsPosts :rows="recent" @select="selectPost"/></section>
<section class="analytics-panel"><h2>인기 게시글 TOP 10</h2><label>순위 기준<select v-model="mode" @change="load"><option value="period">선택 기간</option><option value="all">전체 누적</option><option value="week">최근 7일</option></select></label><AnalyticsPosts :rows="popular" :period-label="mode === 'week' ? '최근 7일' : '선택 기간'" @select="selectPost"/></section>
<section v-if="selected" class="analytics-panel"><h2>{{ selected.title }} · 선택 기간 일별 조회</h2><p v-if="postLoading" role="status">불러오는 중…</p><p v-else-if="postError" role="alert">{{ postError }} <button @click="selectPost(selected)">다시 시도</button></p><template v-else><AnalyticsChart type="line" :labels="postTraffic.map(x => x.date)" :series="[{ label: '게시글 조회', data: postTraffic.map(x => x.pageViews), color: '#235c4b' }]" label="게시글별 일별 조회수"/><details><summary>게시글 일별 데이터</summary><ul><li v-for="d in postTraffic" :key="d.date">{{ d.date }} · {{ d.pageViews }}회</li></ul></details></template></section>
<p class="analytics-note">동의한 브라우저의 공개 페이지 방문만 수집합니다. 관리자·알려진 봇 제외, 같은 세션·페이지는 5분 구간당 1회 집계합니다. 쿠키 삭제·추적 차단·동의 여부에 따라 실제 방문과 차이가 있습니다. 원시 데이터는 {{ overview.retentionDays }}일 보존하며 누적 수치도 이 범위입니다.</p>
</template></div></BlogShell></template>
<style src="../styles/analytics.css"></style>
