<template>
  <div class="video-detail-container">
    <el-skeleton v-if="loading" :rows="8" animated />

    <div v-else class="content-wrapper">
      <!-- Left Panel: Video & Metrics -->
      <div class="left-panel">
        <div class="video-section glass-panel">
          <div class="video-header" v-if="videoData">
            <h2 class="video-title">{{ videoData.title || videoData.originalFileName }}</h2>
            <div class="video-badges">
              <el-tag :type="getStatusType(taskStatus)" effect="dark" round v-if="taskStatus">
                {{ getStatusText(taskStatus) }}
              </el-tag>
            </div>
          </div>

          <div class="video-summary-box" v-if="videoData && videoData.summary">
            <div class="summary-header" @click="isSummaryExpanded = !isSummaryExpanded">
              <div class="summary-label">
                <el-icon><MagicStick /></el-icon> AI 智能简介
              </div>
              <div class="summary-toggle">
                <span class="toggle-text">{{ isSummaryExpanded ? '收起' : '展开' }}</span>
                <el-icon><ArrowUp v-if="isSummaryExpanded"/><ArrowDown v-else/></el-icon>
              </div>
            </div>
            <el-collapse-transition>
              <div v-show="isSummaryExpanded" class="summary-content-wrapper">
                <MdPreview editorId="video-summary-preview" :modelValue="videoData.summary" class="summary-md-preview" />
              </div>
            </el-collapse-transition>
          </div>

          <div class="video-player-wrapper" v-if="videoData">
            <video
              ref="videoPlayer"
              class="video-player"
              controls
              :src="videoData.ossUrl || videoData.fileUrl">
              您的浏览器不支持该视频格式。
            </video>
          </div>
        </div>

        <div class="metrics-section glass-panel">
          <h3 class="section-title">知识元数据评估</h3>
          <div class="metrics-grid" v-if="taskStatus === 'STATUS_SUCCESS'">
            <div class="metric-card">
              <div class="metric-label">知识密度</div>
              <el-progress
                type="dashboard"
                :percentage="videoScore ? (videoScore.densityScore * 10).toFixed(1) : 0"
                :color="densityColors"
              />
              <div class="metric-desc">高信息量语境覆盖率</div>
            </div>

            <div class="metric-card">
              <div class="metric-label">内容有效性打分</div>
              <div class="validity-score">
                <span class="score-value">{{ videoScore ? (videoScore.effectivenessScore*10).toFixed(1) : 0 }}</span>
                <span class="score-max">/100</span>
              </div>
              <div class="metric-desc">学习资料的综合价值</div>
            </div>

            <div class="metric-card">
              <div class="metric-label">知识实体总数</div>
              <div class="entity-count">
                <el-icon><Connection /></el-icon>
                <span>{{ notes.length }}</span>
              </div>
              <div class="metric-desc">当前视频的知识点总数</div>
            </div>
          </div>
          <div v-else style="padding: 20px 0;">
            <el-empty :image-size="80" description="AI 正在深度分析中，视频评分即将呈现..." />
          </div>
        </div>
      </div>

      <!-- Right Panel: Tabs for Transcription & Notes -->
      <div class="right-panel glass-panel">
        <el-tabs v-model="activeTab" class="custom-tabs">
          <!-- Transcription Tab -->
          <el-tab-pane label="AI 智能转写" name="transcription">
            <div class="tab-content transcript-list" ref="transcriptListRef">
              <el-alert
                v-if="taskStatus && taskStatus !== 'STATUS_SUCCESS' && taskStatus !== 'STATUS_FAILED'"
                :title="'AI 分析进行中 (' + getStatusText(taskStatus) + ')，请稍后...'"
                type="info" show-icon :closable="false" style="margin-bottom: 16px"
              />
              <el-empty v-else-if="!transcripts.length" description="暂无语音转写结果" />

              <div
                v-for="(seg, index) in transcripts"
                :key="index"
                class="transcript-item"
                :class="{ active: currentTime >= seg.startTime / 1000 && currentTime < seg.endTime / 1000 }"
                @click="seekVideo(seg.startTime)"
              >
                <div class="time-stamp">{{ formatTime(seg.startTime) }}</div>
                <div class="text-content">
                  {{ seg.polishedText || seg.rawText }}
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Structured Notes Tab -->
          <el-tab-pane label="结构化知识笔记" name="notes">
            <div class="tab-content notes-container">
              <div class="notes-header">
                <h3>增量式知识图谱补齐</h3>
                <div class="header-actions">
                  <el-button class="notes-btn add-note-btn" size="small" @click="openAddPointDialog">
                    <el-icon><Plus /></el-icon> 新增知识点
                  </el-button>
                  <el-dropdown @command="handleExport">
                    <el-button class="notes-btn export-note-btn" size="small" :loading="exporting">
                      <el-icon><Download /></el-icon> 导出笔记
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="md">Markdown (.md)</el-dropdown-item>
                        <el-dropdown-item command="pdf">PDF (.pdf)</el-dropdown-item>
                        <el-dropdown-item command="word">Word (.doc)</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>

              <el-empty v-if="!notes.length" description="框架笔记仍在生成中，请耐心等待。" />

              <div class="timeline-notes">
                <el-timeline>
                  <el-timeline-item
                    v-for="(note, index) in notes"
                    :key="note.id || index"
                    placement="top"
                    type="primary"
                    :hollow="true"
                  >
                    <el-card shadow="hover" class="note-card">
                      <template #header>
                        <div class="note-card-header">
                          <div class="note-title-wrapper">
                            <MdPreview :editorId="'preview-note-title-' + (note.id || index)" :modelValue="note.title" class="note-title-preview" />
                            <el-button 
                              class="jump-point-btn" 
                              type="primary" 
                              link
                              size="small" 
                              @click.stop="jumpToKnowledgePoint(note)"
                            >
                              <el-icon><Position /></el-icon>
                              查看知识点
                            </el-button>
                          </div>
                          <div class="note-tags" v-if="getMergedTimestamps(note).length > 0">
                            <el-tag
                              v-for="(ts, tIdx) in getMergedTimestamps(note)"
                              :key="tIdx"
                              size="small"
                              type="primary"
                              class="timestamp-tag"
                              @click.stop="seekVideo(ts.start)"
                            >
                              {{ formatTime(ts.start) }} - {{ formatTime(ts.end) }}
                            </el-tag>
                          </div>
                        </div>
                      </template>
                      
                      <MdPreview :editorId="'preview-note-' + (note.id || index)" :modelValue="note.content" class="note-md-preview" />

                      <!-- Linked Videos for this note -->
                      <div class="note-linked-videos" v-if="note.videoIndexings?.length">
                        <div class="linked-title"><el-icon><VideoCamera /></el-icon> 关联视频片段</div>
                        <div class="video-jump-list">
                          <div v-for="v in note.videoIndexings" :key="v.videoId" class="video-jump-item">
                            <div class="v-name">{{ v.videoTitle}}</div>
                            <div class="v-times">
                              <el-tag
                                v-for="(seg, si) in renderTimestamps(v.timestamps)"
                                :key="si"
                                size="small"
                                class="jump-tag"
                                @click.stop="handleVideoJump(v.videoId, seg.start)"
                              >
                                {{ formatTime(seg.start) }} - {{ formatTime(seg.end) }}
                              </el-tag>
                            </div>
                          </div>
                        </div>
                      </div>
                    </el-card>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>
          </el-tab-pane>

          <!-- Drafts Tab -->
          <el-tab-pane label="草稿审核" name="drafts">
            <div class="tab-content drafts-container">
              <div class="drafts-toolbar">
                <div class="drafts-filter-tabs">
                  <button
                    v-for="ft in draftFilterOptions"
                    :key="ft.value"
                    class="draft-filter-tab"
                    :class="{ active: draftFilter === ft.value }"
                    @click="draftFilter = ft.value"
                  >
                    {{ ft.label }}
                    <span class="draft-filter-count" v-if="ft.value === 'pending'">{{ pendingDrafts.length }}</span>
                    <span class="draft-filter-count reviewed" v-else-if="ft.value === 'reviewed'">{{ reviewedDrafts.length }}</span>
                  </button>
                </div>
                <el-button size="small" class="draft-refresh-btn" @click="loadDrafts(true)">
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>

              <div v-if="draftFilter === 'pending'">
                <el-empty v-if="!pendingDrafts.length" description="暂无待审核草稿" :image-size="80" />

                <div class="draft-cards">
                  <div
                    v-for="draft in pendingDrafts"
                    :key="draft.id"
                    class="draft-item pending-item"
                  >
                    <div class="draft-item-accent accent-pending"></div>
                    <div class="draft-item-body">
                      <div class="draft-item-header">
                        <MdPreview :editorId="'draft-title-' + draft.id" :modelValue="draft.title" class="draft-title-preview" />
                        <span class="draft-status-dot dot-pending"></span>
                      </div>
                      <div class="draft-item-meta">
                        <span v-if="draft.category" class="meta-chip"><el-icon><Cpu /></el-icon> {{ draft.category }}</span>
                        <span class="meta-chip"><el-icon><Timer /></el-icon> {{ formatDate(draft.createTime) }}</span>
                        <span v-if="draft.action" class="meta-chip action-chip">{{ getActionText(draft.action) }}</span>
                      </div>
                      <div class="draft-content-preview">
                        <MdPreview :editorId="'draft-content-' + draft.id" :modelValue="draft.content" />
                      </div>
                      <div v-if="draft.timestamps" class="draft-item-ts">
                        <el-tag
                          v-for="(seg, si) in renderTimestamps(draft.timestamps)"
                          :key="si"
                          size="small"
                          class="draft-ts-tag"
                          @click.stop="seekVideo(seg.start)"
                        >
                          {{ formatTime(seg.start) }} - {{ formatTime(seg.end) }}
                        </el-tag>
                      </div>
                      <div class="draft-item-actions">
                        <button class="draft-action-btn primary-btn" @click="viewDraft(draft.id)">
                          <el-icon><View /></el-icon> 审核
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-else>
                <el-empty v-if="!reviewedDrafts.length" description="暂无已审核草稿" :image-size="80" />

                <div class="draft-cards">
                  <div
                    v-for="draft in reviewedDrafts"
                    :key="draft.id"
                    class="draft-item"
                    :class="draft.status === DraftStatus.APPROVED ? 'approved-item' : 'rejected-item'"
                  >
                    <div class="draft-item-accent" :class="draft.status === DraftStatus.APPROVED ? 'accent-approved' : 'accent-rejected'"></div>
                    <div class="draft-item-body">
                      <div class="draft-item-header">
                        <MdPreview :editorId="'reviewed-draft-title-' + draft.id" :modelValue="draft.title" class="draft-title-preview" />
                        <span class="draft-status-dot" :class="draft.status === DraftStatus.APPROVED ? 'dot-approved' : 'dot-rejected'"></span>
                      </div>
                      <div class="draft-item-meta">
                        <span v-if="draft.category" class="meta-chip"><el-icon><Cpu /></el-icon> {{ draft.category }}</span>
                        <span class="meta-chip"><el-icon><Timer /></el-icon> {{ formatDate(draft.createTime) }}</span>
                      </div>
                      <div class="draft-content-preview">
                        <MdPreview :editorId="'reviewed-draft-content-' + draft.id" :modelValue="draft.content" />
                      </div>
                      <div v-if="draft.reviewComment" class="draft-review-comment">
                        <span class="comment-label">审核意见</span>
                        {{ draft.reviewComment }}
                      </div>
                      <div class="draft-item-actions">
                        <button class="draft-action-btn ghost-btn" @click="viewDraft(draft.id)">
                          <el-icon><View /></el-icon> 查看
                        </button>
                        <button class="draft-action-btn danger-btn" @click="confirmDeleteDraft(draft.id)">
                          <el-icon><Delete /></el-icon> 删除
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- Add Knowledge Point Dialog -->
    <el-dialog
      v-model="addPointDialogVisible"
      title="新增知识点"
      width="700px"
      :close-on-click-modal="false"
      destroy-on-close
      class="add-point-dialog"
    >
      <div class="add-point-dialog-body">
        <el-form :model="addPointForm" label-position="top">
          <el-form-item label="名称" required>
            <el-input v-model="addPointForm.title" placeholder="请输入知识点名称" />
          </el-form-item>
          <el-form-item label="详细内容">
            <MdEditor v-model="addPointForm.content" :theme="'light'" height="300px" :preview="false" />
          </el-form-item>

          <el-form-item label="上级知识点（可选）">
            <el-select v-model="addPointForm.parentId" placeholder="选择父级知识点" style="width: 100%" clearable filterable>
                <el-option
                  v-for="p in frameworkPoints"
                  :key="p.id"
                  :label="p.title"
                  :value="p.id"
                  :disabled="isParentDisabled(p.id)"
                />
            </el-select>
          </el-form-item>

          <!-- Association Relations Section -->
          <div class="relation-extend-section">
            <div class="section-divider">
              <span>建立关联（可选）</span>
            </div>
            <RelationManager
              v-model:relations="addPointForm.relations"
              :all-points="frameworkPoints"
              :all-relations="frameworkRelations"
              :context-parent-id="addPointForm.parentId"
            />
          </div>

          <!-- Video Indexing Section -->
          <div class="video-index-section">
            <div class="section-divider">
              <span>建立视频索引</span>
            </div>
            <VideoIndexingManager
              v-model:video-refs="addPointForm.videoRefs"
              :all-videos="frameworkVideos"
            />
          </div>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="addPointDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleAddPoint" :loading="addingPoint">确认提交</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, reactive, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ElMessage, ElMessageBox} from 'element-plus';
import {MdEditor, MdPreview} from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import 'md-editor-v3/lib/preview.css';
import html2pdf from 'html2pdf.js';
import MarkdownIt from 'markdown-it';
// @ts-ignore
import mk from '@iktakahiro/markdown-it-katex';
import 'katex/dist/katex.min.css';
import {fromMarkdown} from 'mdast-util-from-markdown';
import {math} from 'micromark-extension-math';
import {mathFromMarkdown} from 'mdast-util-math';
import {toDocx} from '@m2d/core';
import {mathPlugin} from '@m2d/math';
import type {
  KnowledgePointVo,
  KnowledgeRelationVo,
  PointVideoIndexingVo,
  VideoScoreVo,
  VideoSegmentVo,
  VideoVo,
  AddKnowledgePointRequest,
  KnowledgePointDraftVo
} from '@/types';
import {RelationType, DraftStatus, KnowledgeExtractionAction} from '@/types';
import {
  getVideoDetailAPI,
  getVideoScoreAPI,
  getVideoSegmentsAPI,
  getVideoTaskStatusAPI
} from '@/api/video';
import {
  addKnowledgePointAPI,
  addRelationAPI,
  getFrameworkPointsAPI,
  getFrameworkRelationsAPI,
  getVideoPointsAPI,
  getVideoDraftsAPI,
  deleteDraftAPI
} from '@/api/knowledge';
import {
  Connection,
  Download,
  Plus,
  VideoCamera,
  Refresh,
  Timer,
  Cpu,
  View,
  Delete,
  MagicStick,
  ArrowDown,
  ArrowUp,
  Document,
  Position
} from '@element-plus/icons-vue';
import VideoIndexingManager from '@/components/Knowledge/VideoIndexingManager.vue';
import RelationManager from '@/components/Knowledge/RelationManager.vue';
import { getFrameworkVideosAPI } from '@/api/video';

const route = useRoute();
const router = useRouter();
const videoId = computed(() => route.params.id as string);
const loading = ref(true);
const activeTab = ref('transcription');
const videoPlayer = ref<HTMLVideoElement | null>(null);
const currentTime = ref(0);
const videoData = ref<VideoVo | null>(null);
const videoScore = ref<VideoScoreVo | null>(null);
const taskStatus = ref<string>('');
let pollingTimer: any = null;

const densityColors = [
  { color: '#f56c6c', percentage: 40 },
  { color: '#e6a23c', percentage: 60 },
  { color: '#5cb87a', percentage: 80 },
  { color: '#4f46e5', percentage: 100 },
];

const transcripts = ref<VideoSegmentVo[]>([]);
const notes = ref<KnowledgePointVo[]>([]);
const drafts = ref<KnowledgePointDraftVo[]>([]);

const pendingDrafts = computed(() => drafts.value.filter(d => d.status === DraftStatus.PENDING));
const reviewedDrafts = computed(() => drafts.value.filter(d => d.status !== DraftStatus.PENDING));

const draftFilter = ref('pending');
const draftFilterOptions = [
  { label: '待审核', value: 'pending' },
  { label: '已审核', value: 'reviewed' }
];

const transcriptsLoaded = ref(false);
const notesLoaded = ref(false);
const scoreLoaded = ref(false);
const draftsLoaded = ref(false);
const addingPoint = ref(false);
const isSummaryExpanded = ref(false);
const transcriptListRef = ref<HTMLElement | null>(null);

// Knowledge Point Creation State
const addPointDialogVisible = ref(false);
const frameworkPoints = ref<KnowledgePointVo[]>([]);
const frameworkRelations = ref<KnowledgeRelationVo[]>([]);
const frameworkVideos = ref<VideoVo[]>([]);

// Tree validation logic removed as it's now handled by the RelationManager component
const refreshTreeValidationSets = () => {};

const addPointForm = reactive({
    title: '',
    content: '',
    parentId: null as number | null,
    relations: [] as { sourcePointId: number, targetPointId: number, relationType: RelationType }[],
    videoRefs: [] as { videoId: number, segments: { startStr: string, endStr: string }[] }[]
});

const isParentDisabled = (nodeId: number) => {
    // 1. 防止直接重复：如果已经在关联列表中（不论类型），则不能作为父节点
    if (addPointForm.relations.some(rel => rel.targetPointId === nodeId || rel.sourcePointId === nodeId)) {
        return true;
    }

    // 2. 防止长路径循环：如果 candidateParent (nodeId) 是任何已选子节点的“后代”，则会形成环
    // 这里的逻辑是：NewNode -> Child -> ... -> candidateParent -> NewNode
    const childrenIds = addPointForm.relations
        .filter(r => r.relationType === RelationType.CONTAINS)
        .map(r => r.targetPointId);

    // 预读框架现有的父级映射
    const parentMap = new Map<number, number>();
    frameworkRelations.value.forEach(r => {
        if (r.relationType === RelationType.CONTAINS) {
            parentMap.set(r.targetPointId, r.sourcePointId);
        }
    });

    const isAncestor = (ancestorId: number, childId: number): boolean => {
        let curr: number | undefined = childId;
        const visited = new Set<number>();
        while (curr !== undefined && curr !== null && !visited.has(curr)) {
            if (curr === ancestorId) return true;
            visited.add(curr);
            curr = parentMap.get(curr);
        }
        return false;
    };

    for (const childId of childrenIds) {
        if (isAncestor(childId, nodeId)) return true;
    }

    return false;
};

const formatSecondsToTime = (seconds: number): string => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
};

const parseTimeToMilliSeconds = (timeStr: string): number => {
    if (!timeStr || !timeStr.includes(':')) return 0;
    const parts = timeStr.split(':');
    let hrs = 0, mins = 0, secs = 0;
    if (parts.length === 3) {
        hrs = parseInt(parts[0] ?? '0') || 0;
        mins = parseInt(parts[1] ?? '0') || 0;
        secs = parseInt(parts[2] ?? '0') || 0;
    } else if (parts.length === 2) {
        mins = parseInt(parts[0] ?? '0') || 0;
        secs = parseInt(parts[1] ?? '0') || 0;
    }
    return (hrs * 3600 + mins * 60 + secs) * 1000;
};

const getStatusType = (status: string) => {
  if (status === 'STATUS_SUCCESS') return 'success';
  if (['STATUS_TRANSCRIPTING', 'STATUS_EXTRACTING', 'STATUS_SCORING'].includes(status)) return 'warning';
  if (status === 'STATUS_FAILED') return 'danger';
  return 'info';
};

const getStatusText = (status: string) => {
  switch (status) {
    case 'STATUS_SUCCESS': return '分析完成';
    case 'STATUS_TRANSCRIPTING': return '语音转写中...';
    case 'STATUS_EXTRACTING': return '知识提取中...';
    case 'STATUS_SCORING': return '智能评分中...';
    case 'STATUS_FAILED': return '分析失败';
    case 'STATUS_PENDING': return '等待分析';
    default: return 'AI 处理中...';
  }
};

const formatTime = (ms: number) => {
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600).toString().padStart(2, '0');
  const m = Math.floor((totalSeconds % 3600) / 60).toString().padStart(2, '0');
  const s = (totalSeconds % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
};

const seekVideo = (ms: number) => {
  if (videoPlayer.value) {
    videoPlayer.value.currentTime = ms / 1000;
    videoPlayer.value.play();
  }
};

const scrollToTranscript = (ms: number) => {
  if (!transcripts.value.length) return;
  
  if (activeTab.value !== 'transcription') {
    activeTab.value = 'transcription';
  }

  setTimeout(() => {
    if (!transcriptListRef.value) return;
    const index = transcripts.value.findIndex(seg => ms >= seg.startTime && ms < seg.endTime);
    if (index !== -1) {
      const items = transcriptListRef.value.querySelectorAll('.transcript-item');
      const targetItem = items[index] as HTMLElement;
      if (targetItem) {
        targetItem.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }, 100);
};

const renderTimestamps = (timestampsJson: string) => {
  try {
    const raw = JSON.parse(timestampsJson);
    if (Array.isArray(raw)) {
      return raw.map((item: any) => ({
        start: item.start !== undefined ? item.start : (item[0] || 0),
        end: item.end !== undefined ? item.end : (item[1] || 0)
      }));
    }
  } catch (e) {
    console.error('Failed to parse timestamps', e);
  }
  return [];
};

const mergeVideoIndexings = (indexings: PointVideoIndexingVo[]): PointVideoIndexingVo[] => {
    if (!indexings || indexings.length === 0) return [];
    const map = new Map<number, PointVideoIndexingVo>();
    indexings.forEach(v => {
        if (map.has(v.videoId)) {
            const existing = map.get(v.videoId)!;
            const existingTags = renderTimestamps(existing.timestamps);
            const newTags = renderTimestamps(v.timestamps);
            const mergedTags = [...existingTags, ...newTags].sort((a, b) => a.start - b.start);
            existing.timestamps = JSON.stringify(mergedTags);
        } else {
            // Ensure first entry is also sorted
            const tags = renderTimestamps(v.timestamps).sort((a, b) => a.start - b.start);
            map.set(v.videoId, { ...v, timestamps: JSON.stringify(tags) });
        }
    });
    return Array.from(map.values());
};

const openAddPointDialog = async () => {
    if (!videoData.value) return;

    // 1. Pause video and capture time
    if (videoPlayer.value) {
        videoPlayer.value.pause();
    }

    // 2. Reset form
    addPointForm.title = '';
    addPointForm.content = '';
    addPointForm.parentId = null;
    addPointForm.relations = [];

    // 3. Load framework metadata
    if (videoData.value.frameworkId) {
        try {
            const [pointsRes, relationsRes, videosRes] = await Promise.all([
                getFrameworkPointsAPI(videoData.value.frameworkId),
                getFrameworkRelationsAPI(videoData.value.frameworkId.toString()),
                getFrameworkVideosAPI(videoData.value.frameworkId)
            ]);

            if (pointsRes.data) {
                frameworkPoints.value = pointsRes.data;
            }
            if (relationsRes.data) {
                frameworkRelations.value = relationsRes.data;
                refreshTreeValidationSets();
            }
            if (videosRes.data) {
                frameworkVideos.value = videosRes.data;
            }
        } catch (e) {
            console.error('Failed to load framework metadata', e);
        }
    }

    // 4. Initial video ref for current video
    addPointForm.videoRefs = [{
        videoId: Number(videoId.value),
        segments: [{
            startStr: formatSecondsToTime(videoPlayer.value?.currentTime || 0),
            endStr: formatSecondsToTime(videoData.value?.duration || 0)
        }]
    }];

    addPointDialogVisible.value = true;
};

const getMergedTimestamps = (note: KnowledgePointVo) => {
  const match = note.videoIndexings?.find(v => v.videoId === Number(videoId.value));
  return match ? renderTimestamps(match.timestamps) : [];
};

const handleVideoJump = (targetVideoId: number, startTimeMs: number) => {
    if (targetVideoId === Number(videoId.value)) {
        // Same video, just seek
        seekVideo(startTimeMs);
    } else {
        // Different video, navigate
        router.push({
            name: 'videoDetail',
            params: { id: targetVideoId.toString() },
            query: { t: Math.floor(startTimeMs / 1000) }
        });
    }
};

const jumpToKnowledgePoint = (note: KnowledgePointVo) => {
  if (!videoData.value?.frameworkId || !note.id) return;
  router.push({
    path: `/knowledge/${videoData.value.frameworkId}`,
    query: { pointId: note.id }
  });
};

const handleAddPoint = async () => {
    if (!addPointForm.title.trim()) {
        ElMessage.warning('请输入标题');
        return;
    }
    if (!videoData.value) return;

    addingPoint.value = true;
    try {
        const payload: AddKnowledgePointRequest = {
            frameworkId: videoData.value.frameworkId,
            title: addPointForm.title,
            content: addPointForm.content,
            videoRefs: addPointForm.videoRefs.map(vr => ({
                videoId: vr.videoId,
                segments: vr.segments.map(s => ({
                    start: parseTimeToMilliSeconds(s.startStr),
                    end: parseTimeToMilliSeconds(s.endStr)
                }))
            }))
        };

        const res = await addKnowledgePointAPI(payload);

        if (res.success && res.data) {
            const newPointId = res.data.id;
            // 1. Create parent relation (CONTAINS)
            if (addPointForm.parentId) {
                await addRelationAPI({
                    sourcePointId: addPointForm.parentId,
                    targetPointId: newPointId,
                    relationType: RelationType.CONTAINS
                });
            }

            // 2. Create other relations
            for (const rel of addPointForm.relations) {
                await addRelationAPI({
                    sourcePointId: rel.sourcePointId || newPointId,
                    targetPointId: rel.targetPointId,
                    relationType: rel.relationType
                });
            }

            ElMessage.success('新增成功');
            addPointDialogVisible.value = false;
            loadNotes(true); // Force reload notes list
            loadScore(true); // Force reload score (in case manual labels affect it, though user says it's immutable, it's safer to refresh if backend ever changes)
        }
    } catch (e) {
        ElMessage.error('添加失败');
    } finally {
        addingPoint.value = false;
    }
};

let timeUpdateInterval: any;

const fetchVideoData = async () => {
  try {
    const videoRes = await getVideoDetailAPI(videoId.value as string);
    if (videoRes.data) {
      videoData.value = videoRes.data;

      // Fetch task status independently
      await fetchTaskStatus();
    }
  } catch (err: any) {
    ElMessage.error('获取视频详情失败: ' + (err.message || '网络错误'));
  } finally {
    loading.value = false;
  }
};

const fetchTaskStatus = async () => {
  try {
    const res = await getVideoTaskStatusAPI(videoId.value as string);
    if (res.data) {
      const currentStatus = res.data;
      taskStatus.value = currentStatus;

      // Granular data loading based on status
      const isFinished = currentStatus === 'STATUS_SUCCESS';
      const isFailed = currentStatus === 'STATUS_FAILED';

      // 1. Transcripts: Available from EXTRACTING onwards
      if (!transcriptsLoaded.value &&
          ['STATUS_EXTRACTING', 'STATUS_SCORING', 'STATUS_SUCCESS'].includes(currentStatus)) {
        loadTranscripts();
      }

      // 2. Notes: Available from SCORING onwards
      if (!notesLoaded.value &&
          ['STATUS_SCORING', 'STATUS_SUCCESS'].includes(currentStatus)) {
        loadNotes();
      }

      // 3. Score: Available only when SUCCESS
      if (!scoreLoaded.value && isFinished) {
        loadScore();
      }

      // 4. Drafts: Available from EXTRACTING onwards
      if (!draftsLoaded.value &&
          ['STATUS_EXTRACTING', 'STATUS_SCORING', 'STATUS_SUCCESS'].includes(currentStatus)) {
        loadDrafts();
      }

      // Manage polling
      if (isFinished || isFailed) {
        stopPolling();
      } else {
        startPolling();
      }
    }
  } catch (err) {
    console.error('Failed to fetch task status', err);
    stopPolling();
  }
};

const loadTranscripts = async (force = false) => {
  if (transcriptsLoaded.value && !force) return;
  try {
    const res = await getVideoSegmentsAPI(videoId.value as string);
    if (res.data && res.data.length > 0) {
      transcripts.value = res.data;
      transcriptsLoaded.value = true;

      // 如果是从全局搜索跳转过来的，需要同步滚动到对应的转写片段
      if (route.query.t && route.query.from === 'search') {
        setTimeout(() => {
          scrollToTranscript(Number(route.query.t) * 1000);
        }, 500);
      }
    }
  } catch (err) {
    console.error('Failed to load transcripts', err);
  }
};

const loadNotes = async (force = false) => {
  if (notesLoaded.value && !force) return;
  try {
    const res = await getVideoPointsAPI(videoId.value as string);
    console.log(res)
    if (res.data && res.data.length > 0) {
      const processedNotes = res.data.map(n => ({
        ...n,
        videoIndexings: mergeVideoIndexings(n.videoIndexings || [])
      }));
      // Sort notes by the start time of their first timestamp
      notes.value = processedNotes.sort((a, b) => {
        const aTs = getMergedTimestamps(a);
        const bTs = getMergedTimestamps(b);
        const aStart = aTs[0]?.start ?? 0;
        const bStart = bTs[0]?.start ?? 0;
        return aStart - bStart;
      });
      notesLoaded.value = true;
    }
  } catch (err) {
    console.error('Failed to load notes', err);
  }
};

const loadScore = async (force = false) => {
  if (scoreLoaded.value && !force) return;
  try {
    const res = await getVideoScoreAPI(videoId.value as string);
    if (res.data) {
      videoScore.value = res.data;
      scoreLoaded.value = true;
    }
  } catch (err) {
    console.error('Failed to load score', err);
  }
};

const startPolling = () => {
  if (pollingTimer) return;
  pollingTimer = setInterval(fetchTaskStatus, 3000);
};

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
};

const exporting = ref(false);

const preprocessMath = (md: string) => {
  if (!md) return md;
  // 1. 修复 Pandoc 严格模式下的空格问题 ($ formula $ -> $formula$)
  // 许多解析器要求 $ 内部的首尾不能有空格
  const processed = md.replace(/\$\s+([^$]+?)\s+\$/g, '$$$1$$');

  // 2. 统一处理公式块内不支持的 LaTeX 命令（主要针对 Word 导出兼容性）
  return processed.replace(/\$\$([\s\S]+?)\$\$|\$([^$]+?)\$/g, (match, block, inline) => {
    let content = block || inline;
    if (!content) return match;

    // 解决 \mathbb{R} 等黑板报字体在 Word 中无法渲染的问题
    // 将常见的数集符号直接替换为 Unicode 字符，这是最稳健的跨平台方案
    content = content
      .replace(/\\mathbb\{R\}/g, 'ℝ')
      .replace(/\\mathbb\{N\}/g, 'ℕ')
      .replace(/\\mathbb\{Z\}/g, 'ℤ')
      .replace(/\\mathbb\{Q\}/g, 'ℚ')
      .replace(/\\mathbb\{C\}/g, 'ℂ');

    // 解决 \operatorname{...} 在 Word 中解析失败的问题
    // 将其转换为 \text{...}，Word 的公式引擎对 \text 包装的算子支持度极高
    content = content.replace(/\\operatorname\{([^}]+?)\}/g, '\\text{$1}');

    // 解决 \quad, \qquad 等间距命令在 Word/PDF 中可能无法渲染或解析异常的问题
    // 将其映射为 \text{ } 块，这在大多数语义解析器中支持度更好
    content = content
      .replace(/\\quad/g, '\\text{  }')
      .replace(/\\qquad/g, '\\text{    }')
      .replace(/\\,/g, ' ')
      .replace(/\\:/g, ' ')
      .replace(/\\;/g, ' ')
      .replace(/\\!/g, ''); // 忽略负间距

    return block ? `$$\n${content}\n$$` : `$${content}$`;
  });
};

const handleExport = async (type: string) => {
  if (!notes.value.length) {
    ElMessage.warning('暂无笔记可导出');
    return;
  }
  exporting.value = true;
  try {
    const title = videoData.value?.title || videoData.value?.originalFileName || '知识笔记';

    // 生成 Markdown 文本
    let mdContent = `# ${title}\n\n`;
    notes.value.forEach(note => {
      mdContent += `## ${note.title}\n\n`;
      mdContent += `${note.content}\n\n`;
    });

    // 预处理数学公式（兼容性修复）
    mdContent = preprocessMath(mdContent);

    if (type === 'md') {
      const blob = new Blob([mdContent], { type: 'text/markdown;charset=utf-8' });
      downloadBlob(blob, `${title}.md`);
    } else if (type === 'pdf') {
      const md = new MarkdownIt({ html: true }).use(mk);
      const htmlContent = md.render(mdContent);
      const tempElement = document.createElement('div');
      tempElement.innerHTML = `
        <div style="padding: 20px; font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif; color: #333; line-height: 1.6;">
          ${htmlContent}
        </div>
      `;
      // Inject Katex styles into tempElement so html2pdf can capture them
      const styleSheet = document.createElement('style');
      styleSheet.innerHTML = Array.from(document.styleSheets)
        .filter(s => s.href && s.href.includes('katex'))
        .map(s => {
          try {
            return Array.from(s.cssRules).map(r => r.cssText).join('\n');
          } catch(e) { return ''; }
        }).join('\n');
      tempElement.appendChild(styleSheet);

      const opt = {
        margin:       10,
        filename:     `${title}.pdf`,
        image:        { type: 'jpeg' as const, quality: 0.98 },
        html2canvas:  { scale: 2, useCORS: true },
        jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' as const }
      };
      await html2pdf().from(tempElement).set(opt).save();
    } else if (type === 'word') {
      // Create AST from Markdown, supporting math extensions
      const ast = fromMarkdown(mdContent, 'utf-8', {
        extensions: [math()],
        mdastExtensions: [mathFromMarkdown()]
      });
      // Convert AST to docx blob with math plugin enabled
      // @ts-ignore
      const docxBlob = await toDocx(ast, {}, { plugins: [mathPlugin()] }, 'blob');

      downloadBlob(docxBlob as Blob, `${title}.docx`);
    }
    ElMessage.success('导出成功');
  } catch (err: any) {
    ElMessage.error('导出失败: ' + err.message);
  } finally {
    exporting.value = false;
  }
};

const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

// ==================== Draft Methods ====================

const loadDrafts = async (force = false) => {
  if (draftsLoaded.value && !force) return;
  try {
    const response = await getVideoDraftsAPI(videoId.value);
    if (response.success) {
      drafts.value = response.data;
      draftsLoaded.value = true;
    }
  } catch (error) {
    ElMessage.error('加载草稿失败');
  }
};

// 查看草稿详情
const viewDraft = (draftId: number) => {
  router.push({ path: `/drafts/${draftId}`, query: { from: 'video', videoId: videoId.value } });
};

const confirmDeleteDraft = (draftId: number) => {
  ElMessageBox.confirm('确定要删除这个草稿吗？', '删除草稿', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const response = await deleteDraftAPI(draftId);
      if (response.success) {
        ElMessage.success('删除成功');
        loadDrafts(true);
      }
    } catch (error) {
      ElMessage.error('删除失败');
    }
  });
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
      return action;
  }
};

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString();
};

onMounted(() => {
  fetchVideoData();

  if (route.query.tab === 'drafts') {
    activeTab.value = 'drafts';
  }

  const autoSeekTime = route.query.t;
  if (autoSeekTime) {
    const checkPlayerAndSeek = setInterval(() => {
      if (videoPlayer.value && videoPlayer.value.duration > 0) {
        seekVideo(Number(autoSeekTime) * 1000);
        if (route.query.from === 'search') {
          scrollToTranscript(Number(autoSeekTime) * 1000);
        }
        clearInterval(checkPlayerAndSeek);
      }
    }, 500);
    setTimeout(() => clearInterval(checkPlayerAndSeek), 10000);
  }

  timeUpdateInterval = setInterval(() => {
    if (videoPlayer.value) {
      currentTime.value = videoPlayer.value.currentTime;
    }
  }, 500);
});

watch(videoId, (newId) => {
  if (newId) {
    videoData.value = null;
    videoScore.value = null;
    notes.value = [];
    transcripts.value = [];
    drafts.value = [];
    transcriptsLoaded.value = false;
    notesLoaded.value = false;
    scoreLoaded.value = false;
    draftsLoaded.value = false;

    fetchVideoData();

    const autoSeekTime = route.query.t;
    if (autoSeekTime) {
      setTimeout(() => seekVideo(Number(autoSeekTime) * 1000), 1000);
    }
  }
});

watch(activeTab, (tab) => {
  if (tab === 'drafts') {
    loadDrafts();
  }
});

onUnmounted(() => {
  clearInterval(timeUpdateInterval);
  stopPolling();
});
</script>

<style scoped>
.video-detail-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  display: flex;
  height: 100%;
  gap: 20px;
}

.left-panel {
  flex: 5;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.video-section {
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.video-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.video-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.video-summary-box {
  background: linear-gradient(145deg, rgba(243, 244, 246, 0.6), rgba(255, 255, 255, 0.8));
  border-radius: 12px;
  margin-bottom: 24px;
  border: 1px solid rgba(229, 231, 235, 0.8);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0,0,0,0.02);
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  background: rgba(255, 255, 255, 0.5);
  transition: background-color 0.2s;
}

.summary-header:hover {
  background: rgba(255, 255, 255, 0.8);
}

.summary-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--primary-color);
}

.summary-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.summary-content-wrapper {
  padding: 0 18px 18px;
  border-top: 1px solid rgba(229, 231, 235, 0.5);
  margin-top: 4px;
}

:deep(.summary-md-preview) {
  background: transparent !important;
}
:deep(.summary-md-preview .md-editor-preview-wrapper) {
  padding: 0;
}
:deep(.summary-md-preview p) {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-top: 8px;
  margin-bottom: 8px;
}
:deep(.summary-md-preview ul) {
  margin-top: 4px;
  margin-bottom: 8px;
  padding-left: 20px;
}
:deep(.summary-md-preview li) {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.video-player-wrapper {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  background-color: #000;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1);
  aspect-ratio: 16 / 9;
}

.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.metrics-section {
  padding: 24px;
  flex-grow: 1;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.metric-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  border: 1px solid var(--border-color);
}

.metric-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.metric-desc {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 12px;
}

.validity-score {
  color: var(--primary-color);
  font-weight: 700;
}

.score-value {
  font-size: 42px;
  line-height: 1;
}

.score-max {
  font-size: 16px;
  color: #94a3b8;
}

.entity-count {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 42px;
  font-weight: 700;
  color: #10b981;
  line-height: 1;
}

.right-panel {
  flex: 4;
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 500px;
}

.custom-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 600;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:deep(.el-tab-pane) {
  flex: 1;
  overflow-y: auto;
}

.tab-content {
  height: 100%;
  overflow-y: auto;
  padding-right: 8px;
}

.tab-content::-webkit-scrollbar {
  width: 6px;
}

.tab-content::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.transcript-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.transcript-item:hover {
  background: rgba(0, 0, 0, 0.02);
}

.transcript-item.active {
  background: rgba(79, 70, 229, 0.05);
  border-color: rgba(79, 70, 229, 0.2);
}

.time-stamp {
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-color);
  background: rgba(79, 70, 229, 0.1);
  padding: 4px 8px;
  border-radius: 6px;
  height: fit-content;
}

.text-content {
  flex-grow: 1;
  font-size: 15px;
  line-height: 1.6;
  color: var(--text-primary);
}

.speaker {
  font-weight: 600;
  color: var(--text-secondary);
}

.notes-container {
  padding-top: 10px;
}

.notes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.notes-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.timeline-notes {
  padding: 0 8px;
}

.note-card {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.note-card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.05);
}

.note-card-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-title-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
}

.jump-point-btn {
  opacity: 0.8;
  transition: all 0.2s;
  flex-shrink: 0;
  margin-left: 12px;
}

.note-card-header:hover .jump-point-btn {
  opacity: 1;
}

.note-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.timestamp-tag {
  cursor: pointer;
  transition: opacity 0.2s;
}

.timestamp-tag:hover {
  opacity: 0.8;
}

:deep(.note-title-preview) {
  padding: 0 !important;
  background: transparent !important;
}

:deep(.note-title-preview .md-editor-preview-wrapper) {
  padding: 0;
}

:deep(.note-title-preview p) {
  margin: 0;
  font-weight: 700;
  font-size: 16px;
  color: var(--text-primary);
}

:deep(.note-md-preview) {
  padding: 0 !important;
  background: transparent;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.notes-btn {
  border: none !important;
  border-radius: 8px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 6px 14px !important;
  transition: all 0.2s ease !important;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08) !important;
}

.add-note-btn {
  background: linear-gradient(135deg, #22c55e, #16a34a) !important;
  color: #fff !important;
}

.add-note-btn:hover {
  filter: brightness(1.03);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(34, 197, 94, 0.25) !important;
}

.export-note-btn {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light)) !important;
  color: #fff !important;
}

.export-note-btn:hover {
  filter: brightness(1.03);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.28) !important;
}

.notes-btn.is-loading,
.notes-btn.is-disabled {
  opacity: 0.72;
  transform: none !important;
  box-shadow: none !important;
}

/* Align with FrameworkDetail Styles */
.section-divider {
  display: flex;
  align-items: center;
  margin: 24px 0 16px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.section-divider::after {
  content: "";
  flex-grow: 1;
  height: 1px;
  background: #edf2f7;
  margin-left: 12px;
}

.preview-header .label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.preview-header .tip {
  font-size: 11px;
  color: #94a3b8;
}

.indexing-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: white;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
}

.indexing-row .connector {
  font-size: 12px;
  color: #94a3b8;
}

.add-point-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
}
.note-linked-videos {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed var(--border-color-light);
}

.linked-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.video-jump-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.video-jump-item {
  background: var(--bg-color-light);
  padding: 10px;
  border-radius: 8px;
  border: 1px solid var(--border-color-light);
}

.video-jump-item .v-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.video-jump-item .v-times {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.jump-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.jump-tag:hover {
  background-color: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
  transform: translateY(-1px);
}

.draft-title-preview {
  flex: 1;
  min-width: 0;
}

:deep(.draft-title-preview .md-editor-preview-wrapper) {
  padding: 0;
}

:deep(.draft-title-preview p) {
  margin: 0;
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
}

.drafts-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.drafts-filter-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-color-light);
  padding: 3px;
  border-radius: 10px;
}

.draft-filter-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: none;
  background: transparent;
  border-radius: 7px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.draft-filter-tab:hover {
  color: var(--text-primary);
}

.draft-filter-tab.active {
  background: white;
  color: var(--text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.draft-filter-count {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.15);
  color: #b45309;
}

.draft-filter-count.reviewed {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary-color);
}

.draft-refresh-btn {
  border-radius: 8px;
}

.draft-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.draft-item {
  display: flex;
  border-radius: 12px;
  overflow: hidden;
  background: white;
  border: 1px solid var(--border-color);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.draft-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
  border-color: transparent;
}

.draft-item-accent {
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

.draft-item-body {
  flex: 1;
  padding: 16px 20px;
  min-width: 0;
}

.draft-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.draft-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-pending {
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.2);
}

.dot-approved {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2);
}

.dot-rejected {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.2);
}

.draft-item-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.meta-chip {
  font-size: 12px;
  color: #94a3b8;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-chip {
  background: rgba(79, 70, 229, 0.08);
  color: var(--primary-color);
  padding: 1px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.draft-content-preview {
  max-height: 120px;
  overflow: hidden;
  margin-bottom: 12px;
  position: relative;
}

.draft-content-preview :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.draft-content-preview :deep(.md-editor-preview) {
  padding: 0;
  background: transparent;
}

.draft-item-ts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.draft-ts-tag {
  cursor: pointer;
  transition: all 0.2s;
  border-radius: 6px;
}

.draft-ts-tag:hover {
  background-color: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
  transform: translateY(-1px);
}

.draft-item-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.draft-action-btn {
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 14px;
  border-radius: 7px;
  transition: all 0.2s;
}

.primary-btn {
  background: var(--primary-color);
  color: white;
}

.primary-btn:hover {
  background: var(--primary-light);
  transform: translateY(-1px);
}

.ghost-btn {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}

.ghost-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(79, 70, 229, 0.04);
}

.danger-btn {
  background: transparent;
  color: #94a3b8;
}

.danger-btn:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.draft-review-comment {
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 8px;
  border-left: 3px solid #94a3b8;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 12px;
  line-height: 1.5;
}

.draft-review-comment .comment-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
</style>
