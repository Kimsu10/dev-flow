<script setup lang="ts">
import { usePageView } from '../analytics/tracking'
import { computed } from 'vue'
import BlogShell from '../components/blog/BlogShell.vue'
import CategorySection from '../components/blog/CategorySection.vue'
import RequestState from '../components/blog/RequestState.vue'
import { getBlogHome } from '../api/blog'
import { useBlogResource } from '../composables/useBlogResource'
import { auth } from '../auth'
import HomeAiPublisher from '../components/blog/HomeAiPublisher.vue'
import AiWordmark from '../components/blog/AiWordmark.vue'

const { data, loading, error, reload } = useBlogResource(getBlogHome)
const sections = computed(() => (data.value?.categories || []).filter(section => section.posts.length > 0))
usePageView(computed(() => !loading.value && !error.value && !!data.value))
</script>

<template>
  <BlogShell>
    <header class="blog-intro">
      <div class="blog-intro-heading">
        <h1>기술 노트 <AiWordmark class="intro-ai-wordmark" /></h1>
        <p class="blog-eyebrow blog-intro-description">회사를 다니면서 배운 기술을 매일 기록하기 어려운가요?<br>AI에게 오늘 배운 지식을 알려주세요.<br>AI가 당신의 요구를 토대로 초안을 작성하고 발행해줍니다.</p>
      </div>
      <HomeAiPublisher v-if="auth.user?.role === 'ADMIN' || auth.user?.role === 'USER'" :key="auth.user.id + auth.user.role" />
    </header>
    <div :aria-busy="loading" class="blog-content">
      <RequestState :loading="loading" :error="error" :empty="!sections.length" @retry="reload" />
      <template v-if="!loading && !error"><CategorySection v-for="(section, index) in sections" :key="section.category.id" :section="section" :eager="index === 0" /></template>
    </div>
  </BlogShell>
</template>
