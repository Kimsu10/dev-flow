<script setup lang="ts">
import { ref } from 'vue'
import { analyticsConsent, consentToAnalytics } from '../../analytics/tracking'
const editing = ref(false)
function choose(value: 'yes' | 'no') { consentToAnalytics(value); editing.value = false }
</script>
<template><aside class="analytics-consent" aria-label="방문 통계 설정">
<template v-if="analyticsConsent === null || editing"><p>블로그 개선을 위해 익명 방문 통계를 수집할까요? 통계용 쿠키를 사용하며 IP 주소는 저장하지 않습니다. 거부해도 모든 글을 읽을 수 있습니다.</p><button @click="choose('yes')">통계 수집 동의</button> <button @click="choose('no')">거부</button></template>
<button v-else class="analytics-settings" @click="editing = true">방문 통계 설정 · {{ analyticsConsent === 'yes' ? '동의' : '거부' }}</button>
</aside></template>
<style scoped>.analytics-consent { font-size:12px; color:#666; padding:20px 0; border-top:1px solid #eee; } .analytics-consent p { max-width:680px; } .analytics-consent button { font-size:12px; padding:6px 10px; } .analytics-settings { border:0; padding:0!important; text-decoration:underline; }</style>
