<template>
  <div class="drafts-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">草稿审核中心</h1>
        <p class="page-subtitle">审核 AI 生成的知识点草稿，确保知识库质量</p>
      </div>
      <div class="header-right">
        <el-select
          v-model="selectedFrameworkId"
          placeholder="选择知识框架"
          class="framework-select"
          @change="loadDrafts"
        >
          <el-option label="全部分类" value="all" />
          <el-option
            v-for="framework in frameworks"
            :key="framework.id"
            :label="framework.name"
            :value="framework.id"
          />
        </el-select>
        <el-button class="refresh-btn" @click="loadDrafts">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-tabs">
        <button
          v-for="ft in filterOptions"
          :key="ft.value"
          class="filter-tab"
          :class="{ active: activeFilter === ft.value }"
          @click="activeFilter = ft.value"
        >
          <span class="filter-label">{{ ft.label }}</span>
          <span class="filter-count" :class="'count-' + ft.value">{{ getFilterCount(ft.value) }}</span>
        </button>
      </div>
    </div>

    <div class="drafts-grid" v-if="filteredDrafts.length > 0">
      <div
        v-for="draft in filteredDrafts"
        :key="draft.id"
        class="draft-card"
        :class="'status-' + draft.status.toLowerCase()"
      >
        <div class="card-accent" :class="'accent-' + draft.status.toLowerCase()"></div>

        <div class="card-body">
          <div class="card-top">
            <div class="card-meta">
              <span class="status-badge" :class="'badge-' + draft.status.toLowerCase()">
                {{ getStatusText(draft.status) }}
              </span>
              <span v-if="draft.action" class="action-badge" :class="'action-' + draft.action.toLowerCase()">
                {{ getActionText(draft.action) }}
              </span>
            </div>
            <div class="card-time">
              <el-icon><Timer /></el-icon>
              {{ formatDate(draft.createTime) }}
            </div>
          </div>

          <h3 class="card-title">{{ draft.title }}</h3>

          <div class="card-content-preview">
            {{ stripMarkdown(draft.content) }}
          </div>

          <div class="card-footer">
            <div class="card-tags">
              <span v-if="draft.category" class="tag-item">
                <el-icon><Cpu /></el-icon>
                {{ draft.category }}
              </span>
              <span class="tag-item">
                <el-icon><VideoCamera /></el-icon>
                视频 #{{ draft.videoId }}
              </span>
            </div>
            <div class="card-actions">
              <button v-if="draft.status === 'APPROVED' && draft.existingPointId" class="action-btn jump-btn" @click="jumpToKnowledgePoint(draft)">
                <el-icon><Position /></el-icon>
                知识点
              </button>
              <button class="action-btn view-btn" @click="viewDraft(draft.id)">
                <el-icon><View /></el-icon>
                {{ draft.status === 'PENDING' ? '审核' : '查看' }}
              </button>
              <button class="action-btn delete-btn" @click="confirmDelete(draft.id)">
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <div class="empty-icon">
        <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
          <rect x="12" y="8" width="40" height="48" rx="4" stroke="#cbd5e1" stroke-width="2" fill="none"/>
          <line x1="20" y1="20" x2="44" y2="20" stroke="#cbd5e1" stroke-width="2" stroke-linecap="round"/>
          <line x1="20" y1="28" x2="38" y2="28" stroke="#cbd5e1" stroke-width="2" stroke-linecap="round"/>
          <line x1="20" y1="36" x2="42" y2="36" stroke="#cbd5e1" stroke-width="2" stroke-linecap="round"/>
          <circle cx="46" cy="46" r="12" fill="#f1f5f9" stroke="#e2e8f0" stroke-width="2"/>
          <path d="M42 46h8M46 42v8" stroke="#94a3b8" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <p class="empty-text">暂无{{ activeFilter === 'all' ? '' : getStatusText(activeFilter as DraftStatus) }}草稿</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getFrameworkDraftsAPI,
  getAllDraftsAPI,
  getMyFrameworksAPI,
  deleteDraftAPI
} from '@/api/knowledge';
import type { KnowledgePointDraftVo, KnowledgeFrameworkVo } from '@/types';
import { DraftStatus, KnowledgeExtractionAction } from '@/types';
import { Refresh, Timer, Cpu, VideoCamera, View, Delete, Position } from '@element-plus/icons-vue';

const router = useRouter();
const selectedFrameworkId = ref<number | string | null>('all');
const drafts = ref<KnowledgePointDraftVo[]>([]);
const frameworks = ref<KnowledgeFrameworkVo[]>([]);
const activeFilter = ref<string>('all');

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' }
];

const filteredDrafts = computed(() => {
  if (activeFilter.value === 'all') return drafts.value;
  return drafts.value.filter(d => d.status === activeFilter.value);
});

const getFilterCount = (filter: string) => {
  if (filter === 'all') return drafts.value.length;
  return drafts.value.filter(d => d.status === filter).length;
};

const stripMarkdown = (md: string) => {
  if (!md) return '';
  return md
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/\*(.*?)\*/g, '$1')
    .replace(/`(.*?)`/g, '$1')
    .replace(/\[(.*?)\]\(.*?\)/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/\n{2,}/g, ' ')
    .slice(0, 150);
};

const loadFrameworks = async () => {
  try {
    const response = await getMyFrameworksAPI({ page: 0, size: 100 });
    if (response.success) {
      frameworks.value = response.data.content;
      // 'all' is default, so we just load drafts
      loadDrafts();
    }
  } catch (error) {
    ElMessage.error('加载框架失败');
  }
};

// 加载草稿
const loadDrafts = async () => {
  if (!selectedFrameworkId.value) return;

  try {
    let response;
    if (selectedFrameworkId.value === 'all') {
      response = await getAllDraftsAPI();
    } else {
      response = await getFrameworkDraftsAPI(selectedFrameworkId.value);
    }

    if (response.success) {
      drafts.value = response.data;
    }
  } catch (error) {
    ElMessage.error('加载草稿失败');
  }
};

// 查看草稿详情
const viewDraft = (draftId: number) => {
  router.push({ path: `/drafts/${draftId}`, query: { from: 'drafts' } });
};

// 跳转到对应的知识点
const jumpToKnowledgePoint = (draft: KnowledgePointDraftVo) => {
  if (!draft.existingPointId || !draft.frameworkId) return;
  router.push({
    path: `/knowledge/${draft.frameworkId}`,
    query: { pointId: draft.existingPointId }
  });
};

// 确认删除
const confirmDelete = (draftId: number) => {
  ElMessageBox.confirm('确定要删除这个草稿吗？删除后不可恢复。', '删除草稿', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const response = await deleteDraftAPI(draftId);
      if (response.success) {
        ElMessage.success('删除成功');
        loadDrafts();
      }
    } catch (error) {
      ElMessage.error('删除失败');
    }
  });
};

const getStatusText = (status: DraftStatus | string) => {
  switch (status) {
    case DraftStatus.PENDING:
      return '待审核';
    case DraftStatus.APPROVED:
      return '已通过';
    case DraftStatus.REJECTED:
      return '已拒绝';
    default:
      return String(status);
  }
};

// 获取操作文本
const getActionText = (action: KnowledgeExtractionAction) => {
  switch (action) {
    case KnowledgeExtractionAction.NEW:
      return '新建';
    case KnowledgeExtractionAction.UPDATE:
      return '更新';
    case KnowledgeExtractionAction.REDUNDANT:
      return '冗余';
    default:
      return String(action);
  }
};

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString();
};

onMounted(() => {
  loadFrameworks();
});
</script>

<style scoped>
.drafts-page {
  padding: 32px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 28px;
}

.page-title {
  font-size: 26px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.5px;
  margin: 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 6px;
}

.header-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.framework-select {
  width: 220px;
}

.refresh-btn {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.refresh-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(79, 70, 229, 0.04);
}

.filter-bar {
  margin-bottom: 24px;
}

.filter-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-color-light);
  padding: 4px;
  border-radius: 12px;
  width: fit-content;
}

.filter-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.filter-tab:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.6);
}

.filter-tab.active {
  background: white;
  color: var(--text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.filter-count {
  font-size: 12px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 10px;
  background: #e2e8f0;
  color: #64748b;
}

.filter-tab.active .filter-count {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary-color);
}

.count-PENDING {
  background: rgba(245, 158, 11, 0.15) !important;
  color: #b45309 !important;
}

.count-APPROVED {
  background: rgba(34, 197, 94, 0.15) !important;
  color: #15803d !important;
}

.count-REJECTED {
  background: rgba(239, 68, 68, 0.15) !important;
  color: #b91c1c !important;
}

.drafts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.draft-card {
  background: white;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  display: flex;
}

.draft-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.08), 0 4px 8px rgba(0, 0, 0, 0.04);
  border-color: transparent;
}

.card-accent {
  width: 4px;
  flex-shrink: 0;
}

.accent-pending {
  background: linear-gradient(180deg, #f59e0b, #fbbf24);
}

.accent-approved {
  background: linear-gradient(180deg, #22c55e, #4ade80);
}

.accent-rejected {
  background: linear-gradient(180deg, #ef4444, #f87171);
}

.card-body {
  flex: 1;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-meta {
  display: flex;
  gap: 8px;
}

.status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 6px;
}

.badge-pending {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.badge-approved {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.badge-rejected {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.action-badge {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 6px;
  background: rgba(79, 70, 229, 0.08);
  color: var(--primary-color);
}

.action-new {
  background: rgba(79, 70, 229, 0.1);
  color: #4f46e5;
}

.action-update {
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
}

.action-redundant {
  background: rgba(148, 163, 184, 0.16);
  color: #475569;
}

.card-time {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 10px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-content-preview {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.card-tags {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.tag-item {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-actions {
  display: flex;
  gap: 6px;
}

.action-btn {
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 14px;
  border-radius: 8px;
  transition: all 0.2s;
}

.view-btn {
  background: var(--primary-color);
  color: white;
}

.view-btn:hover {
  background: var(--primary-light);
  transform: translateY(-1px);
}

.jump-btn {
  background: transparent;
  color: #10b981;
}

.jump-btn:hover {
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
}

.delete-btn {
  background: transparent;
  color: #94a3b8;
  padding: 6px 8px;
}

.delete-btn:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-text {
  font-size: 15px;
  color: #94a3b8;
}
</style>
