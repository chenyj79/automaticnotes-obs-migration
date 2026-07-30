<template>
  <div class="search-container">
    <div class="glass-panel search-header">
      <div class="search-title">
        <el-icon><Search /></el-icon>
        <h2>全局全文检索</h2>
      </div>
      <p class="search-subtitle">在所有已转写的视频内容中搜索特定词汇（如：股票代码、人名等）</p>

      <el-form :model="searchForm" class="search-form" @submit.prevent="handleSearch">
        <el-row :gutter="20" align="middle">
          <el-col :span="24">
            <el-form-item label="检索关键词">
              <div class="keywords-input-wrapper">
                <el-tag
                  v-for="(kw, idx) in searchForm.keywords"
                  :key="idx"
                  closable
                  size="large"
                  class="keyword-tag"
                  @close="removeKeyword(idx)"
                >
                  {{ kw }}
                </el-tag>
                <el-input
                  v-if="keywordInputVisible"
                  ref="keywordInputRef"
                  v-model="keywordInputValue"
                  size="large"
                  placeholder="输入关键词后回车"
                  class="keyword-input-inline"
                  @keyup.enter="addKeyword"
                  @blur="addKeyword"
                />
                <el-button v-else size="large" class="add-keyword-btn" @click="showKeywordInput">
                  + 添加关键词
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="开始日期">
              <el-date-picker
                v-model="searchForm.startDate"
                type="date"
                placeholder="选择开始日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束日期">
              <el-date-picker
                v-model="searchForm.endDate"
                type="date"
                placeholder="选择结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-button type="primary" native-type="submit" :loading="loading" class="search-btn">
              搜索
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <div class="glass-panel results-container" v-loading="loading">
      <div v-if="hasSearched && results.length === 0" class="empty-state">
        <el-empty description="未找到相关视频片段" />
      </div>

      <div v-else-if="results.length > 0" class="results-list">
        <div class="results-meta">
          共找到 <strong>{{ results.length }}</strong> 个相关片段
        </div>

        <el-card v-for="(item, index) in results" :key="index" class="result-card" shadow="hover" @click="goToVideo(item)">
          <div class="result-header">
            <div class="video-info">
              <el-icon><VideoCamera /></el-icon>
              <span class="video-name">{{ item.video.originalFileName }}</span>
              <el-tag size="small" type="info" class="upload-time">{{ formatDate(item.video.uploadTime) }}</el-tag>
            </div>
            <el-tag size="small" type="primary" class="timestamp-tag">
              {{ formatTime(item.segment.startTime) }} - {{ formatTime(item.segment.endTime) }}
            </el-tag>
          </div>
          
          <div class="result-body">
            <p class="transcript-text" v-html="highlightKeyword(item.segment.polishedText || item.segment.rawText)"></p>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Search, VideoCamera, Clock } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/api/request';

const router = useRouter();
const CACHE_KEY = 'autonotes_search_cache';

const searchForm = reactive<{
  keywords: string[];
  startDate: string;
  endDate: string;
}>({
  keywords: [],
  startDate: '',
  endDate: ''
});

const loading = ref(false);
const hasSearched = ref(false);
const results = ref<any[]>([]);
const keywordInputVisible = ref(false);
const keywordInputValue = ref('');
const keywordInputRef = ref<any>(null);

// 从缓存恢复
onMounted(() => {
  try {
    const cached = localStorage.getItem(CACHE_KEY);
    if (cached) {
      const parsed = JSON.parse(cached);
      searchForm.keywords = parsed.keywords || [];
      searchForm.startDate = parsed.startDate || '';
      searchForm.endDate = parsed.endDate || '';
    }
  } catch (e) {
    console.error('Failed to parse search cache', e);
  }
});

// 关键词变化时自动缓存
watch(() => searchForm.keywords, (val) => {
  localStorage.setItem(CACHE_KEY, JSON.stringify({
    keywords: val,
    startDate: searchForm.startDate,
    endDate: searchForm.endDate
  }));
}, { deep: true });

const showKeywordInput = () => {
  keywordInputVisible.value = true;
  nextTick(() => keywordInputRef.value?.focus());
};

const addKeyword = () => {
  const kw = keywordInputValue.value.trim();
  if (kw && !searchForm.keywords.includes(kw)) {
    searchForm.keywords.push(kw);
  }
  keywordInputValue.value = '';
  keywordInputVisible.value = false;
};

const removeKeyword = (idx: number) => {
  searchForm.keywords.splice(idx, 1);
};

const handleSearch = async () => {
  if (searchForm.keywords.length === 0) {
    ElMessage.warning('请至少添加一个检索关键词');
    return;
  }
  if (!searchForm.startDate || !searchForm.endDate) {
    ElMessage.warning('请选择完整的开始和结束日期');
    return;
  }
  if (new Date(searchForm.startDate) > new Date(searchForm.endDate)) {
    ElMessage.warning('开始日期不能晚于结束日期');
    return;
  }

  loading.value = true;
  hasSearched.value = true;
  try {
    const res = await request.get<any, any>('/video/search-segments', {
      params: {
        keywords: searchForm.keywords.join(' '),
        startDate: searchForm.startDate,
        endDate: searchForm.endDate
      }
    });
    if (res.code === 200 || res.success) {
      results.value = res.data;
    } else {
      ElMessage.error(res.message || '搜索失败');
    }
  } catch (error) {
    console.error(error);
    ElMessage.error('搜索出错');
  } finally {
    loading.value = false;
  }
};

const goToVideo = (item: any) => {
  const t = Math.floor(item.segment.startTime / 1000);
  router.push({ path: `/video/${item.video.id}`, query: { t, from: 'search' } });
};

const formatTime = (ms: number) => {
  if (!ms) return '00:00:00';
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
};

const formatDate = (dateStr: string) => {
  if (!dateStr) return '';
  return dateStr.substring(0, 10); // Simple format
};

const highlightKeyword = (text: string) => {
  if (!text || searchForm.keywords.length === 0) return text;
  // 多关键词高亮
  const escaped = searchForm.keywords.map(k => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const regex = new RegExp(`(${escaped.join('|')})`, 'gi');
  return text.replace(regex, '<span class="highlight-text">$1</span>');
};
</script>

<style scoped>
.keywords-input-wrapper {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 40px;
}

.keyword-tag {
  font-size: 14px;
  padding: 4px 12px;
}

.keyword-input-inline {
  width: 200px;
  flex: none;
}

.add-keyword-btn {
  border-style: dashed;
}

.search-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
}

.search-header {
  padding: 24px;
}

.search-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.search-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.search-title .el-icon {
  font-size: 24px;
  color: var(--primary-color);
}

.search-subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 24px;
}

.search-btn {
  width: 100%;
}

.results-container {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.results-meta {
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.results-meta strong {
  color: var(--primary-color);
  font-size: 16px;
}

.result-card {
  margin-bottom: 16px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.3s;
}

.result-card:hover {
  transform: translateY(-2px);
  border-color: var(--primary-color);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.video-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.video-name {
  font-weight: 600;
  color: var(--text-primary);
}

.result-body {
  background: var(--bg-color);
  padding: 12px;
  border-radius: 6px;
  border-left: 4px solid var(--primary-color);
}

.transcript-text {
  margin: 0;
  color: var(--text-regular);
  line-height: 1.6;
  font-size: 14px;
}

:deep(.highlight-text) {
  color: #f56c6c;
  font-weight: bold;
  background: rgba(245, 108, 108, 0.1);
  padding: 0 4px;
  border-radius: 2px;
}
</style>