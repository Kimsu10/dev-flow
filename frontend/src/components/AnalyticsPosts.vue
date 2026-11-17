<script setup lang="ts">
import type { PostStats } from '../api/analytics'
defineProps<{ rows: PostStats[]; periodLabel?: string }>()
defineEmits<{ select: [post: PostStats] }>()
const date = (s: string) => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium' }).format(new Date(s))
</script>
<template><p v-if="!rows.length">해당 기간의 게시글이 없습니다.</p><div v-else class="analytics-table-wrap"><table><thead><tr><th>게시글</th><th>작성일</th><th>마지막 수정일</th><th>누적 조회</th><th>{{ periodLabel || '선택 기간' }} 조회</th><th>추이</th></tr></thead><tbody><tr v-for="p in rows" :key="p.id"><td><RouterLink :to="`/posts/${p.id}`">{{ p.title }}</RouterLink></td><td>{{ date(p.createdAt) }}</td><td>{{ date(p.updatedAt) }}</td><td>{{ p.totalViews.toLocaleString() }}</td><td>{{ p.periodViews.toLocaleString() }}</td><td><button @click="$emit('select', p)">일별 보기</button></td></tr></tbody></table></div></template>
