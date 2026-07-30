<template>
  <div class="draft-detail-container">
    <el-card class="mb-4">
      <template #header>
        <div class="card-header">
          <el-button class="ui-btn ghost-btn" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span class="text-xl font-bold ml-4">草稿详情</span>
          <el-button v-if="draft?.status === 'APPROVED' && draft?.existingPointId" class="ui-btn primary-btn" style="margin-left: auto;" @click="jumpToKnowledgePoint">
            <el-icon><Position /></el-icon>
            跳转到知识点
          </el-button>
        </div>
      </template>
      <div v-if="draft" class="draft-content">
        <div class="flex justify-between items-start mb-4">
          <div class="editable-field" v-if="draft.status === 'PENDING'">
            <el-input
              v-if="editingTitle"
              v-model="editTitle"
              size="large"
              class="title-input"
              @blur="confirmEditTitle"
              @keyup.enter="confirmEditTitle"
            />
            <div v-else class="editable-display" @click="startEditTitle">
              <h2 class="text-2xl font-bold">{{ editTitle }}</h2>
              <el-icon class="edit-icon"><Edit /></el-icon>
            </div>
          </div>
          <h2 v-else class="text-2xl font-bold">{{ draft.title }}</h2>
          <div class="top-badges">
            <span class="status-badge" :class="'badge-' + draft.status.toLowerCase()">
              {{ getStatusText(draft.status) }}
            </span>
            <span v-if="draft.action" class="action-badge" :class="'action-' + draft.action.toLowerCase()">
              {{ getActionText(draft.action) }}
            </span>
          </div>
        </div>

        <div class="info-grid mb-6">
          <div class="info-item">
            <span class="label">创建时间：</span>
            <span class="value">{{ formatDate(draft.createTime) }}</span>
          </div>
          <div class="info-item">
            <span class="label">视频ID：</span>
            <span class="value">{{ draft.videoId }}</span>
          </div>
          <div class="info-item">
            <span class="label">知识框架：</span>
            <span class="value">{{ frameworkName }}</span>
          </div>
          <div class="info-item editable-field" v-if="draft.status === 'PENDING'">
            <span class="label">分类：</span>
            <el-input
              v-if="editingCategory"
              v-model="editCategory"
              size="small"
              style="width: 160px"
              @blur="confirmEditCategory"
              @keyup.enter="confirmEditCategory"
            />
            <span v-else class="value editable-display" @click="startEditCategory">
              {{ editCategory || '未设置' }}
              <el-icon class="edit-icon-sm"><Edit /></el-icon>
            </span>
          </div>
          <div class="info-item" v-else-if="draft.category">
            <span class="label">分类：</span>
            <span class="value">{{ draft.category }}</span>
          </div>
          <div class="info-item" v-if="draft.action">
            <span class="label">操作类型：</span>
            <span class="action-badge" :class="'action-' + draft.action.toLowerCase()">
              {{ getActionText(draft.action) }}
            </span>
          </div>
        </div>

        <div class="mb-6">
          <div class="section-header">
            <h3 class="text-lg font-semibold">内容</h3>
            <el-button
              v-if="draft.status === 'PENDING'"
              class="ui-btn ghost-btn"
              :class="{ 'active-ghost': editingContent }"
              size="small"
              @click="toggleContentEdit"
            >
              <el-icon><Edit /></el-icon>
              {{ editingContent ? '完成修改' : '修改内容' }}
            </el-button>
          </div>
          <el-divider />
          <div class="content-box">
            <MdEditor
              v-if="editingContent"
              v-model="editContentValue"
              :theme="'light'"
              height="400px"
              :preview="true"
            />
            <MdPreview v-else :editorId="'draft-content'" :modelValue="editContentValue" />
          </div>
        </div>

        <div v-if="draft.timestamps && parsedTimestamps.length > 0" class="mb-6">
          <h3 class="text-lg font-semibold mb-2">视频时间戳</h3>
          <el-divider />
          <div class="timestamps-box">
            <el-tag
              v-for="(segment, index) in parsedTimestamps"
              :key="index"
              class="mr-2 mb-2 timestamp-tag"
              type="primary"
              @click="jumpToVideo(segment.start)"
            >
              {{ formatTime(segment.start) }} - {{ formatTime(segment.end) }}
            </el-tag>
          </div>
        </div>

        <div v-if="draft.aiSuggestion" class="mb-6">
          <h3 class="text-lg font-semibold mb-2">AI建议</h3>
          <el-divider />
          <div class="ai-suggestion-box">
            <MdPreview :editorId="'draft-ai-suggestion'" :modelValue="draft.aiSuggestion" />
          </div>
        </div>

        <div v-if="draft.reviewComment" class="mb-6">
          <h3 class="text-lg font-semibold mb-2">审核意见</h3>
          <el-divider />
          <div class="comment-box">
            <MdPreview :editorId="'draft-review-comment'" :modelValue="draft.reviewComment" />
          </div>
        </div>

        <div v-if="draft.status === 'PENDING'" class="review-section mt-8">
          <h3 class="text-lg font-semibold mb-4">审核操作</h3>
          <el-form :model="reviewForm" label-width="80px">
            <el-form-item label="审核意见">
              <el-input
                v-model="reviewForm.comment"
                type="textarea"
                :rows="3"
                placeholder="请输入审核意见"
              />
            </el-form-item>
            <el-form-item>
              <div class="flex gap-4">
                <el-button class="ui-btn approve-btn" @click="approveDraft" :loading="submittingReview" :disabled="submittingReview">
                  <el-icon v-if="!submittingReview"><Check /></el-icon>
                  {{ submittingReview ? '正在生成专属 AI 概述，请稍候...' : '审核通过' }}
                </el-button>
                <el-button class="ui-btn reject-btn" @click="rejectDraft" :loading="submittingReview" :disabled="submittingReview">
                  <el-icon v-if="!submittingReview"><Close /></el-icon>
                  审核拒绝
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <el-skeleton v-else :rows="10" animated />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { MdEditor, MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import 'md-editor-v3/lib/preview.css';
import {
  getDraftDetailAPI,
  reviewDraftAPI,
  getMyFrameworksAPI
} from '@/api/knowledge';
import type { KnowledgePointDraftVo, KnowledgeFrameworkVo, ReviewDraftRequest } from '@/types';
import { DraftStatus, KnowledgeExtractionAction } from '@/types';
import { ArrowLeft, Check, Close, Edit, Position } from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();
const draftId = computed(() => route.params.id as string);
const draft = ref<KnowledgePointDraftVo | null>(null);
const frameworks = ref<KnowledgeFrameworkVo[]>([]);
const isEditing = ref(false);

const fromVideo = computed(() => route.query.from === 'video');
const fromVideoId = computed(() => route.query.videoId as string);

const editingTitle = ref(false);
const editTitle = ref('');
const editingCategory = ref(false);
const editCategory = ref('');
const editingContent = ref(false);
const editContentValue = ref('');

const hasContentChanged = computed(() => {
  if (!draft.value) return false;
  return editTitle.value !== draft.value.title
    || editContentValue.value !== draft.value.content
    || editCategory.value !== (draft.value.category || '');
});

const startEditTitle = () => {
  editingTitle.value = true;
};

const confirmEditTitle = () => {
  editingTitle.value = false;
};

const startEditCategory = () => {
  editingCategory.value = true;
};

const confirmEditCategory = () => {
  editingCategory.value = false;
};

const toggleContentEdit = () => {
  editingContent.value = !editingContent.value;
};

const reviewForm = ref<ReviewDraftRequest>({
  approved: true,
  comment: ''
});

const loadFrameworks = async () => {
  try {
    const response = await getMyFrameworksAPI({ page: 0, size: 100 });
    if (response.success) {
      frameworks.value = response.data.content;
    }
  } catch (error) {
    console.error('加载框架失败', error);
  }
};

const loadDraftDetail = async () => {
  try {
    const response = await getDraftDetailAPI(draftId.value);
    if (response.success) {
      draft.value = response.data;
      editTitle.value = draft.value.title;
      editContentValue.value = draft.value.content;
      editCategory.value = draft.value.category || '';
    }
  } catch (error) {
    ElMessage.error('加载草稿失败');
  }
};

const submittingReview = ref(false);

const approveDraft = async () => {
  if (!draft.value) return;
  submittingReview.value = true;
  try {
    const payload: ReviewDraftRequest = {
      approved: true,
      comment: reviewForm.value.comment
    };

    if (hasContentChanged.value) {
      payload.title = editTitle.value;
      payload.content = editContentValue.value;
      payload.category = editCategory.value;
    }

    const response = await reviewDraftAPI(draftId.value, payload);
    if (response.success) {
      ElMessage.success('审核通过');
      loadDraftDetail(); // Reload to show new status
    }
  } catch (error) {
    console.error('审核失败', error);
    ElMessage.error('审核失败，请重试');
  } finally {
    submittingReview.value = false;
  }
};

const rejectDraft = async () => {
  if (!draft.value) return;
  submittingReview.value = true;
  try {
    const payload: ReviewDraftRequest = {
      approved: false,
      comment: reviewForm.value.comment
    };
    
    // Only send updated fields if they changed
    if (hasContentChanged.value) {
      payload.title = editTitle.value;
      payload.content = editContentValue.value;
      payload.category = editCategory.value;
    }

    const response = await reviewDraftAPI(draftId.value, payload);
    if (response.success) {
      ElMessage.success('审核已拒绝');
      loadDraftDetail(); // Reload to show new status
    }
  } catch (error) {
    console.error('审核失败', error);
    ElMessage.error('审核失败，请重试');
  } finally {
    submittingReview.value = false;
  }
};

const goBack = () => {
  if (fromVideo.value && fromVideoId.value) {
    router.push({ name: 'videoDetail', params: { id: fromVideoId.value }, query: { tab: 'drafts' } });
  } else {
    router.push('/drafts');
  }
};

const jumpToVideo = (startTimeMs: number) => {
  const videoId = draft.value?.videoId;
  if (!videoId) return;
  router.push({
    name: 'videoDetail',
    params: { id: videoId.toString() },
    query: { t: Math.floor(startTimeMs / 1000) }
  });
};

const jumpToKnowledgePoint = () => {
  if (!draft.value?.existingPointId || !draft.value?.frameworkId) return;
  router.push({
    path: `/knowledge/${draft.value.frameworkId}`,
    query: { pointId: draft.value.existingPointId }
  });
};

const frameworkName = computed(() => {
  if (!draft.value) return '';
  const framework = frameworks.value.find(f => f.id === draft.value!.frameworkId);
  return framework ? framework.name : '';
});

const parsedTimestamps = computed(() => {
  if (!draft.value?.timestamps) return [];
  try {
    const raw = JSON.parse(draft.value.timestamps);
    if (Array.isArray(raw)) {
      return raw.map((item: any) => ({
        start: item.start !== undefined ? item.start : (item[0] || 0),
        end: item.end !== undefined ? item.end : (item[1] || 0)
      }));
    }
    return [];
  } catch (error) {
    return [];
  }
});

const getStatusTagType = (status: DraftStatus) => {
  switch (status) {
    case DraftStatus.PENDING:
      return 'warning';
    case DraftStatus.APPROVED:
      return 'success';
    case DraftStatus.REJECTED:
      return 'danger';
    default:
      return 'info';
  }
};

const getStatusText = (status: DraftStatus) => {
  switch (status) {
    case DraftStatus.PENDING:
      return '待审核';
    case DraftStatus.APPROVED:
      return '已通过';
    case DraftStatus.REJECTED:
      return '已拒绝';
    default:
      return status;
  }
};

const getActionTagType = (action: KnowledgeExtractionAction) => {
  switch (action) {
    case KnowledgeExtractionAction.NEW:
      return 'primary';
    case KnowledgeExtractionAction.UPDATE:
      return 'info';
    case KnowledgeExtractionAction.REDUNDANT:
      return 'warning';
    default:
      return 'info';
  }
};

const getActionText = (action: KnowledgeExtractionAction) => {
  switch (action) {
    case KnowledgeExtractionAction.NEW:
      return '新建';
    case KnowledgeExtractionAction.UPDATE:
      return '更新';
    case KnowledgeExtractionAction.REDUNDANT:
      return '冗余';
    default:
      return action;
  }
};

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString();
};

const formatTime = (ms: number) => {
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600).toString().padStart(2, '0');
  const m = Math.floor((totalSeconds % 3600) / 60).toString().padStart(2, '0');
  const s = (totalSeconds % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
};

onMounted(() => {
  loadFrameworks();
  loadDraftDetail();
});
</script>

<style scoped>
.draft-detail-container {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
}

.draft-content {
  line-height: 1.6;
}

.editable-field {
  display: flex;
  align-items: center;
}

.editable-display {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 4px;
  padding: 2px 6px;
  transition: background-color 0.2s;
}

.editable-display:hover {
  background-color: #f0f0f0;
}

.edit-icon {
  font-size: 14px;
  color: #94a3b8;
  opacity: 0;
  transition: opacity 0.2s;
}

.editable-display:hover .edit-icon {
  opacity: 1;
}

.edit-icon-sm {
  font-size: 12px;
  color: #94a3b8;
  opacity: 0;
  transition: opacity 0.2s;
}

.editable-display:hover .edit-icon-sm {
  opacity: 1;
}

.card-header {
  display: flex;
  align-items: center;
}

.title-input {
  font-size: 22px;
  font-weight: 700;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.top-badges {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 6px;
  line-height: 1.2;
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
  line-height: 1.2;
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

.ui-btn {
  border: none !important;
  border-radius: 8px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 6px 14px !important;
  transition: all 0.2s ease !important;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08) !important;
}

.ui-btn.is-loading,
.ui-btn.is-disabled {
  opacity: 0.72;
  transform: none !important;
  box-shadow: none !important;
}

.ghost-btn {
  background: white !important;
  color: var(--text-secondary) !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: none !important;
}

.ghost-btn:hover {
  border-color: var(--primary-color) !important;
  color: var(--primary-color) !important;
  background: rgba(79, 70, 229, 0.04) !important;
}

.active-ghost {
  border-color: rgba(245, 158, 11, 0.35) !important;
  color: #b45309 !important;
  background: rgba(245, 158, 11, 0.08) !important;
}

.approve-btn {
  background: linear-gradient(135deg, #22c55e, #16a34a) !important;
  color: #fff !important;
}

.approve-btn:hover {
  filter: brightness(1.03);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(34, 197, 94, 0.25) !important;
}

.reject-btn {
  background: linear-gradient(135deg, #ef4444, #dc2626) !important;
  color: #fff !important;
}

.reject-btn:hover {
  filter: brightness(1.03);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(239, 68, 68, 0.25) !important;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  align-items: center;
}

.label {
  font-weight: 500;
  margin-right: 8px;
  color: #666;
}

.value {
  color: #333;
}

.content-box {
  padding: 16px;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.content-box :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.timestamps-box {
  padding: 16px;
  background-color: #f9f9f9;
  border-radius: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.timestamp-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.timestamp-tag:hover {
  opacity: 0.8;
  transform: translateY(-1px);
}

.ai-suggestion-box {
  padding: 16px;
  background-color: #f0f9ff;
  border-radius: 8px;
  border-left: 4px solid #3b82f6;
}

.ai-suggestion-box :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.comment-box {
  padding: 16px;
  background-color: #f9fafb;
  border-radius: 8px;
  border-left: 4px solid #6b7280;
}

.comment-box :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.review-section {
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  margin-top: 32px;
}
</style>
