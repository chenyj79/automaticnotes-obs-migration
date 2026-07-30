<template>
  <div class="dashboard-container">
    <div class="stats-row">
      <div class="stat-card glass-panel" v-for="stat in stats" :key="stat.title">
        <div class="stat-icon" :style="{ background: stat.bgColor, color: stat.color }">
          <el-icon><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-title">{{ stat.title }}</div>
        </div>
      </div>
    </div>

    <div class="recent-videos glass-panel">
      <div class="section-header">
        <h3 class="section-title">我的视频库</h3>
        <el-select
          v-model="selectedFrameworkId"
          placeholder="全部分类"
          clearable
          class="framework-filter"
          style="width: 200px"
        >
          <el-option label="全部分类" :value="null" />
          <el-option
            v-for="[id, name] in Object.entries(frameworksMap)"
            :key="id"
            :label="name"
            :value="Number(id)"
          />
        </el-select>
      </div>

      <el-skeleton :rows="5" animated v-if="loading" />
      <el-empty description="暂无已上传的视频" v-else-if="filteredGroupedVideos.length === 0" />
      
      <div v-else class="framework-groups">
        <div 
          v-for="group in filteredGroupedVideos" 
          :key="group.frameworkId" 
          class="framework-group"
        >
          <h4 class="framework-title">
            <el-icon><Collection /></el-icon>
            {{ group.frameworkName }}
          </h4>
          <div class="video-grid">
            <div
              class="video-card"
              v-for="video in group.videos"
              :key="video.id"
              @click="goToDetail(video.id)"
            >
              <div class="video-cover">
                <template v-if="video.ossUrl">
                  <div class="cover-placeholder">
                    <el-icon :size="48"><VideoCamera /></el-icon>
                  </div>
                </template>
                <el-icon class="play-icon"><VideoPlay /></el-icon>
                <div class="status-badge" :class="video.processStatus?.toLowerCase() || video.status?.toLowerCase()">
                  {{ getStatusText(video.processStatus || video.status) }}
                </div>
              </div>
              <div class="video-info">
                <div class="video-title" :title="video.title || video.originalFileName">{{ video.title || video.originalFileName }}</div>
                <div class="video-summary" :title="video.summary" v-if="video.summary">{{ video.summary }}</div>
                <div class="video-meta">
                  <span class="date">{{ new Date(video.uploadTime).toLocaleDateString() }}</span>
                  <span class="size" v-if="video.fileSize">{{ (video.fileSize / 1024 / 1024).toFixed(1) }} MB</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { VideoPlay, Document, Tickets, TrendCharts, Collection } from '@element-plus/icons-vue';
import { getMyVideosAPI } from '@/api/video';
import { getMyFrameworksAPI } from '@/api/knowledge';
import { getDashboardStatisticsAPI } from '@/api/statistics';
import { ElMessage } from 'element-plus';
import type { VideoVo } from '@/types';

const router = useRouter();
const loading = ref(true);
const videos = ref<VideoVo[]>([]);
const frameworksMap = ref<Record<number, string>>({});
const selectedFrameworkId = ref<number | null>(null);

const stats = ref([
  { title: '总视频数', value: '0', icon: 'VideoPlay', color: '#4f46e5', bgColor: 'rgba(79, 70, 229, 0.1)' },
  { title: '生成的笔记', value: '0', icon: 'Document', color: '#0ea5e9', bgColor: 'rgba(14, 165, 233, 0.1)' },
  { title: '知识实体总数', value: '0', icon: 'Tickets', color: '#10b981', bgColor: 'rgba(16, 185, 129, 0.1)' },
  { title: '平均知识密度', value: '0%', icon: 'TrendCharts', color: '#f59e0b', bgColor: 'rgba(245, 158, 11, 0.1)' }
]);

const getStatusText = (status?: string) => {
  switch (status) {
    case 'STATUS_SUCCESS': return '分析完成';
    case 'STATUS_PROCESSING': return '分析中...';
    case 'STATUS_FAILED': return '处理失败';
    case 'STATUS_PENDING': return '等待处理';
    default: return '未知状态';
  }
};

const handleImgError = (e: Event) => {
  const target = e.target as HTMLImageElement;
  target.style.display = 'none'; // hide broken images, fallback to gradient
};

const fetchFrameworks = async () => {
  try {
    const res = await getMyFrameworksAPI({ page: 0, size: 100 });
    if (res.data && res.data.content) {
      res.data.content.forEach(fw => {
        frameworksMap.value[fw.id] = fw.name;
      });
    }
  } catch (error: any) {
    console.error('Failed to fetch frameworks', error);
  }
};

const fetchVideos = async () => {
  loading.value = true;
  try {
    const res = await getMyVideosAPI({ page: 0, size: 100 });
    if (res.data && res.data.content) {
      videos.value = res.data.content;
    }
  } catch (error: any) {
    if (error.message !== 'Unauthorized, please login again.') {
      ElMessage.error('加载视频列表失败');
    }
  } finally {
    loading.value = false;
  }
};

const groupedVideos = computed(() => {
  const groups: Record<number, { frameworkId: number, frameworkName: string, videos: VideoVo[] }> = {};
  
  videos.value.forEach(video => {
    const fwId = video.frameworkId || 0; // fallback to 0 if not assigned
    if (!groups[fwId]) {
      groups[fwId] = {
        frameworkId: fwId,
        frameworkName: frameworksMap.value[fwId] || (fwId === 0 ? '未分类视频' : `未知框架 (ID: ${fwId})`),
        videos: []
      };
    }
    groups[fwId].videos.push(video);
  });
  
  return Object.values(groups);
});

const filteredGroupedVideos = computed(() => {
  if (selectedFrameworkId.value === null || selectedFrameworkId.value === undefined) {
    return groupedVideos.value;
  }
  return groupedVideos.value.filter(group => group.frameworkId === selectedFrameworkId.value);
});

const fetchStats = async () => {
  try {
    const res = await getDashboardStatisticsAPI();
    if (res.data) {
      if (stats.value.length >= 4) {
        stats.value[0]!.value = res.data.videoCount.toString();
        stats.value[1]!.value = res.data.frameworkCount.toString();
        stats.value[2]!.value = res.data.knowledgePointCount.toString();
        stats.value[3]!.value = res.data.averageScore.toFixed(1);
      }
    }
  } catch (error: any) {
    if (error.message !== 'Unauthorized, please login again.') {
      ElMessage.error('加载仪表盘统计失败');
    }
  }
};

const goToDetail = (id: string | number) => {
  router.push(`/video/${id}`);
};

onMounted(() => {
  fetchFrameworks().then(() => {
    fetchVideos();
  });
  fetchStats();
});
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.stat-card {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.025);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.stat-title {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.recent-videos {
  padding: 24px;
  flex-grow: 1;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.framework-filter {
  min-width: 200px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.framework-groups {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.framework-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.video-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s ease;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
  border-color: var(--primary-light);
}

.video-cover {
  height: 160px;
  background: linear-gradient(135deg, #e0e7ff, #ede9fe);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.cover-img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 1;
  transition: transform 0.3s ease;
}

.video-card:hover .cover-img {
  transform: scale(1.05);
}

.play-icon {
  font-size: 48px;
  color: rgba(255, 255, 255, 0.9);
  transition: all 0.3s;
  z-index: 2;
  filter: drop-shadow(0 4px 6px rgba(0,0,0,0.3));
}

.video-card:hover .play-icon {
  transform: scale(1.1);
  color: rgba(79, 70, 229, 0.9);
}

.status-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  backdrop-filter: blur(4px);
  background: rgba(255, 255, 255, 0.8);
  color: var(--text-primary);
}

.status-badge.completed, .status-badge.success {
  background: rgba(16, 185, 129, 0.9);
  color: white;
}

.status-badge.processing {
  background: rgba(245, 158, 11, 0.9);
  color: white;
}

.video-info {
  padding: 16px;
}

.video-title {
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
}

.video-summary {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.5;
}

.video-meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-secondary);
}

.density-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.density-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

:deep(.el-progress) {
  flex-grow: 1;
}
</style>
