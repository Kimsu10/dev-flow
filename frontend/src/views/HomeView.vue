<script setup lang="ts">
import AnalyticsConsent from '../components/blog/AnalyticsConsent.vue'
import ServiceStatus from '../components/blog/ServiceStatus.vue'
</script>

<template>
  <main>
    <p class="eyebrow">개발의 흐름을 기록하다</p>
    <h1>DevFlow</h1>
    <p>개인 개발 블로그의 첫 시작.</p>
    <ServiceStatus class="service-status" />
    <AnalyticsConsent />
  </main>
</template>
