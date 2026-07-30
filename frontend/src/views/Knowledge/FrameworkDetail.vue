<template>
  <div class="framework-detail-container" v-loading="loading">
    <div v-if="framework" class="header-section glass-panel">
      <div class="breadcrumb">
        <el-icon @click="$router.push('/knowledge')"><Back /></el-icon>
        <span class="divider">/</span>
        <span class="fw-name">{{ framework.name }}</span>
      </div>
      <div class="actions">
        <button class="top-btn upload-btn" @click="openUploadDialog">
          <el-icon><Upload /></el-icon> 上传视频至本框架
        </button>
        <button class="top-btn refresh-btn" @click="fetchGraphData">
          <el-icon><RefreshRight /></el-icon> 刷新知识图谱
        </button>
        <button class="top-btn video-btn" @click="openVideoManagementDialog">
          <el-icon><VideoCamera /></el-icon> 视频管理
        </button>
        <button class="top-btn draft-btn" @click="navigateToDrafts">
          <el-icon><Message /></el-icon> 草稿审核
          <el-badge v-if="pendingDraftCount > 0" :value="pendingDraftCount" class="draft-badge" />
        </button>
      </div>
    </div>

    <div class="content-wrapper">
      <!-- Left Panel: Knowledge Tree -->
      <div class="tree-panel glass-panel">
        <div class="tree-header">
          <h3 class="panel-title">
            <el-icon><Connection /></el-icon> 知识树谱大纲
          </h3>
          <el-input
            v-model="pointKeyword"
            clearable
            placeholder="按知识点名称搜索"
            class="point-search-input"
          />
          <div class="tree-header-actions">
            <el-select
              v-model="selectedCategoryFilter"
              placeholder="全部分类"
              clearable
              class="category-filter"
            >
              <el-option label="全部分类" value="" />
              <el-option v-for="item in categoryFilterOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <button class="tree-add-btn" @click="openAddPointDialog()">
              <el-icon><Plus /></el-icon> 新增根节点
            </button>
          </div>
        </div>

        <el-empty
          v-if="!treeData.length"
          :description="selectedCategoryFilter || pointKeyword ? '筛选条件下暂无知识点' : '该框架下暂无知识点'"
        />

        <el-tree
          v-else
          :data="treeData"
          :props="defaultProps"
          default-expand-all
          highlight-current
          node-key="id"
          :current-node-key="selectedNode?.id"
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
          class="knowledge-tree"
        >
          <template #default="{ node, data }">
            <div class="custom-tree-node">
              <div class="node-left">
                <MdPreview :editorId="'preview-title-' + data.id" :modelValue="node.label" class="node-title-preview" />
              </div>
              <div class="node-right">
                <el-button
                  type="primary"
                  link
                  class="node-action"
                  @click.stop="openAddPointDialog(data.id)"
                  title="新增子知识点"
                >
                  <el-icon><Plus /></el-icon>
                </el-button>
                <el-tag v-if="data.children?.length" size="small" type="info" round class="node-badge">
                  {{ data.children.length }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- Right Panel: Node Details -->
      <div class="detail-panel glass-panel cursor-anim" :class="{ 'has-selection': !!selectedNode }">
        <template v-if="selectedNode">
          <div class="detail-header">
            <div class="header-main">
              <div v-if="isEditing" class="title-edit-container">
                <div class="title-edit-hint">
                  <el-icon><Edit /></el-icon> 正在编辑
                </div>
                <el-input
                  v-model="selectedNode.title"
                  type="textarea"
                  autosize
                  class="title-input-edit"
                  placeholder="请输入知识点标题"
                />
                <el-select v-model="selectedNode.category" class="category-select" placeholder="请选择知识点类别">
                  <el-option v-for="item in knowledgeCategories" :key="item" :label="item" :value="item" />
                </el-select>
              </div>
              <MdPreview v-else :editorId="'preview-header-' + selectedNode.id" :modelValue="selectedNode.title" class="detail-title-preview" />
              <div class="header-actions">
                <button v-if="!isEditing" class="action-btn edit-btn" @click="startEdit">
                   <el-icon><Edit /></el-icon> 编辑
                </button>
                <template v-else>
                  <button class="action-btn save-btn" @click="handleSaveContent" :disabled="saving">
                    <el-icon v-if="!saving"><Check /></el-icon>
                    <el-icon v-else class="is-loading"><Loading /></el-icon>
                    保存修改
                  </button>
                  <button class="action-btn cancel-btn" @click="cancelEdit">
                    取消
                  </button>
                </template>
                <button class="action-btn delete-btn" @click="handleDeletePoint">
                  <el-icon><Delete /></el-icon> 删除
                </button>
              </div>
            </div>
            <div class="detail-meta">
              <span class="meta-item">
                <el-tag size="small" effect="light" type="info">{{ selectedNode.category || defaultCategoryName }}</el-tag>
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon> 最后编辑: {{ new Date(selectedNode.updateTime).toLocaleString() }}
              </span>
            </div>
          </div>

          <div class="relations-section">
            <div class="relations-header">
              <h3><el-icon><Link /></el-icon> 关联知识探索</h3>
              <el-tooltip content="该节点与其周边知识的网状延伸" placement="top">
                <el-icon class="info-icon"><InfoFilled /></el-icon>
              </el-tooltip>
            </div>

            <div class="relations-placeholder">
              <el-empty v-if="!hasRelations" description="该节点暂无关联扩展" :image-size="60" />

              <div class="relation-group prerequisite" v-if="prerequisitePoints.length > 0">
                <div class="rf-title"><el-icon><Back /></el-icon> 前置基础</div>
                <div class="rf-items">
                  <el-tag
                    v-for="rel in prerequisitePoints"
                    :key="rel.id"
                    effect="light" type="warning" class="rel-tag"
                    @click="navigateToPoint(rel)"
                  >
                    {{ rel.title }}
                  </el-tag>
                </div>
              </div>

              <div class="relation-group related" v-if="relatedPoints.length > 0">
                <div class="rf-title"><el-icon><Connection /></el-icon> 相关知识</div>
                <div class="rf-items">
                  <el-tag
                    v-for="rel in relatedPoints"
                    :key="rel.id"
                    effect="light" type="info" class="rel-tag"
                    @click="navigateToPoint(rel)"
                  >
                    {{ rel.title }}
                  </el-tag>
                </div>
              </div>

              <div class="relation-group extends" v-if="extendsPoints.length > 0">
                <div class="rf-title"><el-icon><Right /></el-icon> 拓展延伸</div>
                <div class="rf-items">
                  <el-tag
                    v-for="rel in extendsPoints"
                    :key="rel.id"
                    effect="light" type="success" class="rel-tag"
                    @click="navigateToPoint(rel)"
                  >
                    {{ rel.title }}
                  </el-tag>
                </div>
              </div>

              <!-- Linked Videos Section (Trigger Button) -->
              <div class="relation-group linked-videos-trigger" v-if="selectedNode.videoIndexings?.length && !isEditing">
                <div class="rf-title"><el-icon><VideoCamera /></el-icon> 关联视频片段</div>
                <div class="view-videos-btn-wrapper">
                  <button class="top-btn view-segments-btn" @click="videoSegmentsDialogVisible = true">
                    <el-icon><View /></el-icon> 查看 {{ selectedNode.videoIndexings.length }} 个关联视频及片段概述
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-content">
            <MdEditor
              v-model="selectedNode.content"
              :previewOnly="!isEditing"
              :theme="'light'"
              class="md-editor-main"
              :preview="false"
              v-if="isEditing"
            />
            <MdPreview
              v-else
              :editorId="'preview-content-' + selectedNode.id"
              :modelValue="selectedNode.content"
              class="md-previewer"
            />
          </div>

          <!-- Edit Mode Management -->
          <div class="relations-section" v-if="isEditing">
            <div class="relations-placeholder">
              <div class="edit-management-section">
                <div class="management-item">
                  <div class="m-label">知识关联管理</div>
                  <RelationManager
                    v-model:relations="editedRelations"
                    :all-points="allPoints"
                    :all-relations="relations"
                    :current-point-id="selectedNode.id"
                  />
                </div>
                <div class="management-item">
                  <div class="m-label">视频索引管理</div>
                  <VideoIndexingManager
                    v-model:video-refs="editedVideoRefs"
                    :all-videos="frameworkVideos"
                  />
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <el-empty description="请从左侧知识树中点击选择一个核心节点进行查阅" class="empty-selection">
            <template #image>
              <el-icon :size="80" color="#e2e8f0"><Reading /></el-icon>
            </template>
          </el-empty>
        </template>
      </div>
    </div>

    <!-- Upload Dialog -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传教学视频"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="upload-dialog-content">
        <p class="subtitle">上传视频将由 AI 自动分析转写，并在此框架内生成结构化知识短视频笔记。</p>

        <el-upload
          class="upload-area"
          drag
          action="#"
          :auto-upload="false"
          :show-file-list="true"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          accept="video/mp4,video/mkv,video/avi"
        >
          <div v-if="!selectedFile">
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到这里，或 <em>点击上传</em>
            </div>
          </div>
          <div v-else class="file-summary">
            <el-icon :size="48" color="var(--primary-color)"><VideoCamera /></el-icon>
            <div class="file-details">
              <div class="file-name">{{ selectedFile.name }}</div>
              <div class="file-size">{{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB</div>
            </div>
          </div>
          <template #tip>
            <div class="el-upload__tip text-center text-gray-500 mt-2">
              支持 MP4, MKV, AVI。最大支持 1GB。
            </div>
          </template>
        </el-upload>

        <div class="progress-area mt-6" v-if="uploading">
          <el-steps :active="activeStep" finish-status="success" align-center class="process-steps">
            <el-step title="上传并分析" :description="uploadProgress < 100 ? uploadProgress + '%' : '已入队'" />
            <el-step title="智能处理" />
            <el-step title="生成报告" />
          </el-steps>

          <div class="status-indicator mt-4">
            <el-icon class="is-loading" v-if="!processFinished">
              <Loading />
            </el-icon>
            <span class="status-text">{{ currentStatusText }}</span>
          </div>

          <div class="auto-close-tip mt-2" v-if="showAutoCloseTip">
            <el-icon><InfoFilled /></el-icon>
            请放心，分析将在后台持续进行。窗口将在 {{ countdown }}s 后自动最小化...
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="uploadDialogVisible = false" :disabled="uploading">关闭</el-button>
          <el-button type="primary" @click="submitUpload" :loading="uploading" :disabled="!selectedFile">
            确认上传并分析
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Analysis Report Dialog -->
    <el-dialog
      v-model="reportDialogVisible"
      title="✨ AI 增量分析简报"
      width="800px"
      :close-on-click-modal="false"
      destroy-on-close
      class="report-dialog"
    >
      <div v-if="processResult" class="report-container">
        <!-- 头部数据总览 -->
        <div class="report-header-refined">
          <div class="video-brief">
            <el-icon class="pulse-icon"><VideoCamera /></el-icon>
            <div class="video-name-wrapper">
              <span class="video-label">分析视频</span>
              <span class="video-name">{{ processResult.video.originalFileName }}</span>
            </div>
          </div>
          <div class="overall-metrics">
            <div class="metric-circle">
              <el-progress type="circle" :percentage="processResult.score.totalScore * 10" :stroke-width="8" :color="scoreColors">
                <template #default>
                  <div class="metric-content">
                    <span class="metric-val">{{ processResult.score.totalScore.toFixed(1) }}</span>
                    <span class="metric-lab">综合评分</span>
                  </div>
                </template>
              </el-progress>
            </div>
            <div class="metric-info-grid">
              <div class="m-item">
                <div class="m-top">
                  <span class="m-val">{{ (processResult.score.densityScore * 10).toFixed(0) }}%</span>
                  <el-icon color="#10b981"><TrendCharts /></el-icon>
                </div>
                <div class="m-lab">知识密度</div>
              </div>
              <div class="m-item">
                <div class="m-top">
                  <span class="m-val">{{ processResult.extractedPoints.length }}</span>
                  <el-icon color="#6366f1"><Collection /></el-icon>
                </div>
                <div class="m-lab">总提取点</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 增量贡献展示区 -->
        <div class="report-body">
          <div class="incremental-section">
            <div class="section-tag new">
              <el-icon><CirclePlusFilled /></el-icon> 新增知识储备 ({{ processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.NEW).length }})
            </div>
            <div class="incremental-grid">
              <div v-for="point in processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.NEW)" :key="point.title" class="inc-card new">
                <div class="inc-title">{{ point.title }}</div>
                <div class="inc-meta">
                  <el-tag size="small" effect="plain" type="success">NEW</el-tag>
                  <span class="inc-ts"><el-icon><Timer /></el-icon> {{ formatDuration(point.timestamps[0]?.start) }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="incremental-section mt-6">
            <div class="section-tag update">
              <el-icon><Checked /></el-icon> 补充细化已有知识 ({{ processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.UPDATE).length }})
            </div>
            <div class="incremental-grid">
              <div v-for="point in processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.UPDATE)" :key="point.title" class="inc-card update">
                <div class="inc-title">{{ point.title }}</div>
                <div class="inc-meta">
                  <el-tag size="small" effect="plain" type="primary">REFINED</el-tag>
                  <span class="inc-ts"><el-icon><Timer /></el-icon> {{ formatDuration(point.timestamps[0]?.start) }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="incremental-section mt-6" v-if="processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.REDUNDANT).length > 0">
            <div class="section-tag redundant">
              <el-icon><RefreshRight /></el-icon> 识别到重复知识点 ({{ processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.REDUNDANT).length }})
            </div>
            <div class="incremental-grid">
              <div v-for="point in processResult.extractedPoints.filter(p => p.action === KnowledgeExtractionAction.REDUNDANT)" :key="point.title" class="inc-card redundant">
                <div class="inc-title">{{ point.title }}</div>
                <div class="inc-meta">
                  <el-tag size="small" effect="plain" type="info">REPETITIVE</el-tag>
                  <span class="inc-ts"><el-icon><Timer /></el-icon> {{ formatDuration(point.timestamps[0]?.start) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- AI 诊断说明 -->
        <div class="ai-insight-box mt-6">
          <div class="insight-label"><el-icon><MagicStick /></el-icon> AI 专家点评</div>
          <div class="insight-text">{{ processResult.score.explanation }}</div>
        </div>
      </div>

      <template #footer>
        <div class="report-footer">
          <el-button @click="reportDialogVisible = false" round>留在本页</el-button>
          <el-button type="primary" @click="closeReportAndGoDetail" round class="glow-button">
            进入视频笔记详情 <el-icon class="el-icon--right"><Right /></el-icon>
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Video Management Dialog -->
    <el-dialog
      v-model="videoManagementDialogVisible"
      title="视频管理"
      width="900px"
      :close-on-click-modal="false"
      destroy-on-close
      class="video-management-dialog"
    >
      <div class="video-management-content">
        <el-table :data="frameworkVideos" v-loading="loadingVideos" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="originalFileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="duration" label="时长" width="100">
            <template #default="{ row }">
              {{ formatDuration(row.duration) }}
            </template>
          </el-table-column>
          <el-table-column prop="fileSize" label="文件大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="uploadTime" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.uploadTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                type="danger"
                size="small"
                @click="handleDeleteVideo(row)"
                :loading="deletingVideoId === row.id"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!frameworkVideos.length" description="暂无视频" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="videoManagementDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="fetchVideos">刷新</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Video Management Dialog -->
    <el-dialog
      v-model="videoManagementDialogVisible"
      title="视频管理"
      width="900px"
      :close-on-click-modal="false"
      destroy-on-close
      class="video-management-dialog"
    >
      <div class="video-management-content">
        <el-table :data="frameworkVideos" v-loading="loadingVideos" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="originalFileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="duration" label="时长" width="100">
            <template #default="{ row }">
              {{ formatDuration(row.duration) }}
            </template>
          </el-table-column>
          <el-table-column prop="fileSize" label="文件大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="uploadTime" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.uploadTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                type="danger"
                size="small"
                @click="handleDeleteVideo(row)"
                :loading="deletingVideoId === row.id"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!frameworkVideos.length" description="暂无视频" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="videoManagementDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="fetchVideos">刷新</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Video Segments Dialog -->
    <el-dialog
      v-model="videoSegmentsDialogVisible"
      title="关联视频片段与 AI 概述"
      width="800px"
      top="5vh"
      :close-on-click-modal="true"
      destroy-on-close
      class="video-segments-dialog"
    >
      <div class="segments-dialog-content">
        <div class="segments-list">
          <div v-for="v in selectedNode?.videoIndexings" :key="v.videoId" class="segment-card">
            <div class="seg-header">
              <div class="seg-title">
                <el-icon><VideoCameraFilled /></el-icon> {{ v.videoTitle }}
              </div>
            </div>
            
            <div class="seg-details-list">
              <div v-for="(seg, idx) in parseSegmentSummaries(v.videoSummary, v.timestamps)" :key="idx" class="seg-item-block">
                <div class="seg-timestamps">
                  <el-tag size="small" type="primary" class="jump-tag" @click="jumpToVideo(v.videoId, seg.start)" title="点击跳转至视频该片段">
                    {{ formatTime(seg.start) }} - {{ formatTime(seg.end) }}
                  </el-tag>
                </div>
                <div class="seg-summary-box" v-if="seg.summary">
                  <div class="seg-summary-header" @click="toggleSegment(v.videoId, idx)">
                    <div class="seg-summary-label"><el-icon><MagicStick /></el-icon> AI 片段概述</div>
                    <div class="seg-summary-toggle">
                      <span class="toggle-text">{{ expandedSegments[`${v.videoId}-${idx}`] ? '收起' : '展开' }}</span>
                      <el-icon class="toggle-icon" :class="{ 'is-expanded': expandedSegments[`${v.videoId}-${idx}`] }"><ArrowDown /></el-icon>
                    </div>
                  </div>
                  <el-collapse-transition>
                    <div v-show="expandedSegments[`${v.videoId}-${idx}`]" class="seg-summary-content-wrapper">
                      <MdPreview :editorId="'seg-summary-' + v.videoId + '-' + idx" :modelValue="seg.summary" class="seg-md-preview" />
                    </div>
                  </el-collapse-transition>
                </div>
                <div class="seg-summary-box empty" v-else>
                  <el-icon><InfoFilled /></el-icon> 暂无 AI 片段概述
                </div>
              </div>
            </div>
            
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- Add Knowledge Point Dialog -->
    <el-dialog
      v-model="addPointDialogVisible"
      :title="addPointForm.parentId ? '新增子知识点' : '新增根知识点'"
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
          <el-form-item label="类别" required>
            <el-select v-model="addPointForm.category" placeholder="请选择知识点类别" style="width: 100%">
              <el-option v-for="item in knowledgeCategories" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="详细内容">
            <MdEditor v-model="addPointForm.content" :theme="'light'" height="300px" :preview="false" />
          </el-form-item>

          <div class="relation-extend-section">
            <div class="section-divider">
              <span>建立关联（可选）</span>
            </div>
            <RelationManager
              v-model:relations="addPointForm.relations"
              :all-points="allPoints"
              :all-relations="relations"
              :context-parent-id="addPointForm.parentId"
            />
          </div>

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

    <!-- Background Task Tracker Widget -->
    <div class="task-tracker-widget" v-if="pendingTasks.length > 0">
      <el-popover placement="top-end" :width="320" trigger="click" popper-class="task-tracker-popper">
        <template #reference>
          <div class="tracker-floating-btn animate-pulse">
            <el-badge :value="pendingTasks.length" class="item">
              <el-icon :size="24"><Cpu /></el-icon>
            </el-badge>
            <span class="btn-label">AI 任务进行中</span>
          </div>
        </template>
        <div class="task-popover-content">
          <h4 class="popover-title">当前后台处理任务</h4>
          <div class="task-list-mini">
            <div v-for="task in pendingTasks" :key="task.id" class="task-mini-item">
              <div class="task-header-mini">
                <span class="task-filename" :title="task.filename">{{ task.filename }}</span>
                <div class="task-status-group">
                  <span class="task-perc" :class="task.status.toLowerCase()">{{ getTaskStatusLabel(task.status) }}</span>
                  <el-button
                    v-if="task.status !== 'STATUS_SUCCESS' && task.status !== 'STATUS_FAILED' && task.status !== 'STATUS_CANCELLED'"
                    type="danger"
                    size="small"
                    plain
                    @click="handleCancelTask(task.id, task.filename)"
                    :loading="cancellingTaskId === task.id"
                    class="cancel-task-btn"
                  >
                    <el-icon class="cancel-icon"><Close /></el-icon>
                    <span class="cancel-text">取消</span>
                  </el-button>
                </div>
              </div>
              <el-progress
                :percentage="getTaskPercentage(task.status)"
                :status="task.status === 'STATUS_FAILED' ? 'exception' : (task.status === 'STATUS_SUCCESS' ? 'success' : '')"
                :stroke-width="6"
                striped
                striped-flow
              />
            </div>
          </div>
        </div>
      </el-popover>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted, reactive, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Back, RefreshRight, Connection, Link, InfoFilled, Clock, Reading, Right, Upload, UploadFilled, VideoCamera, Histogram, ChatDotRound, Checked, CirclePlusFilled, Cpu, StarFilled, TrendCharts, Collection, MagicStick, Timer, Check, Plus, Delete, Edit, Minus, Close, View, VideoPlay, VideoCameraFilled, ArrowDown } from '@element-plus/icons-vue';
import { MdEditor, MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import 'md-editor-v3/lib/preview.css';
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus';
import type { UploadFile } from 'element-plus';
import {
  addKnowledgePointAPI,
  addRelationAPI,
  deleteKnowledgePointAPI,
  getFrameworkDetailAPI,
  getFrameworkPointsAPI,
  getFrameworkRelationsAPI,
  getFrameworkDraftsAPI,
  updateKnowledgePointAPI
} from '@/api/knowledge';
import { processVideoAPI, getTaskResultAPI, getPendingTasksAPI, markTaskReportViewedAPI, getFrameworkVideosAPI, getObsUploadSignatureAPI, deleteVideoAPI, cancelTaskAPI, getSseTicketAPI } from '@/api/video';
import type {
  KnowledgeFrameworkVo, KnowledgePointVo, KnowledgeRelationVo,
  PointVideoIndexingVo, VideoProcessResultVo, VideoVo, FrameworkCategoryVo
} from '@/types';
import { RelationType, KnowledgeExtractionAction } from '@/types';
import { Loading } from '@element-plus/icons-vue';
import VideoIndexingManager from '@/components/Knowledge/VideoIndexingManager.vue';
import RelationManager from '@/components/Knowledge/RelationManager.vue';
import type { AddKnowledgePointRequest, UpdateKnowledgePointRequest } from '@/types';

const route = useRoute();
const router = useRouter();
const frameworkId = route.params.id as string;

const loading = ref(true);
const framework = ref<KnowledgeFrameworkVo | null>(null);
const treeData = ref<KnowledgePointVo[]>([]);
const selectedNode = ref<KnowledgePointVo | null>(null);

const rawPoints = ref<KnowledgePointVo[]>([]);
const allPoints = ref<KnowledgePointVo[]>([]);
const relations = ref<KnowledgeRelationVo[]>([]);
const frameworkVideos = ref<VideoVo[]>([]);
const selectedCategoryFilter = ref('');
const pointKeyword = ref('');

const uploadDialogVisible = ref(false);
const selectedFile = ref<File | null>(null);
const uploading = ref(false);
const reportDialogVisible = ref(false);
const videoManagementDialogVisible = ref(false);
const videoSegmentsDialogVisible = ref(false);
const loadingVideos = ref(false);
const deletingVideoId = ref<number | null>(null);
const processResult = ref<VideoProcessResultVo | null>(null);
const processStatus = ref<string>('');
const activeStep = ref(0);
const sseConnections = new Map<number, EventSource>();
const uploadProgress = ref(0);
const isEditing = ref(false);
const saving = ref(false);
const addingPoint = ref(false);
let originalContent = '';
let originalTitle = '';
let originalCategory = '';

// Segment Expanded State
const expandedSegments = ref<Record<string, boolean>>({});
const toggleSegment = (videoId: number, idx: number) => {
  const key = `${videoId}-${idx}`;
  expandedSegments.value[key] = !expandedSegments.value[key];
};

const defaultCategoryName = '其他';
const knowledgeCategories = computed(() => {
  const categories = framework.value?.categories ?? [];
  if (!categories.length) {
    return [defaultCategoryName];
  }
  return categories.map((item: FrameworkCategoryVo) => item.name).filter(Boolean);
});
const lastInvalidCategoryHint = ref('');
const categoryFilterOptions = computed(() =>
  knowledgeCategories.value.map((category) => {
    const count = allPoints.value.filter((point) => (point.category || defaultCategoryName) === category).length;
    return {
      value: category,
      label: `${category} (${count})`
    };
  })
);

const normalizeCategoryFilter = (value: unknown): string => {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw !== 'string') return '';
  return knowledgeCategories.value.includes(raw) ? raw : '';
};

const readCategoryQuery = (value: unknown): string => {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === 'string' ? raw : '';
};

const syncCategoryFromQuery = (queryCategory: unknown) => {
  const rawCategory = readCategoryQuery(queryCategory);
  if (rawCategory && !knowledgeCategories.value.includes(rawCategory)) {
    if (lastInvalidCategoryHint.value !== rawCategory) {
      ElMessage.info('分类参数无效，已切换为全部分类');
      lastInvalidCategoryHint.value = rawCategory;
    }

    if (selectedCategoryFilter.value) {
      selectedCategoryFilter.value = '';
    }

    const nextQuery = { ...route.query };
    delete nextQuery.category;
    router.replace({ query: nextQuery });
    return;
  }

  lastInvalidCategoryHint.value = '';
  const normalized = normalizeCategoryFilter(rawCategory);
  if (normalized !== selectedCategoryFilter.value) {
    selectedCategoryFilter.value = normalized;
  }
};

const applyCategoryFilter = () => {
  const keyword = pointKeyword.value.trim().toLowerCase();
  rawPoints.value = allPoints.value.filter((point) => {
    const categoryMatched = !selectedCategoryFilter.value || (point.category || defaultCategoryName) === selectedCategoryFilter.value;
    const titleMatched = !keyword || (point.title || '').toLowerCase().includes(keyword);
    return categoryMatched && titleMatched;
  });
};

// Add Point Form
const addPointDialogVisible = ref(false);
const addPointForm = reactive({
    title: '',
    category: defaultCategoryName,
    content: '',
    parentId: null as number | null,
    relations: [] as { sourcePointId: number, targetPointId: number, relationType: RelationType }[],
    videoRefs: [] as { videoId: number, segments: { startStr: string, endStr: string }[] }[]
});

// Delta tracking for editing
const editedRelations = ref<{ sourcePointId: number, targetPointId: number, relationType: RelationType }[]>([]);
const editedVideoRefs = ref<{ videoId: number, segments: { startStr: string, endStr: string }[] }[]>([]);
let originalRelations: any[] = [];
let originalVideoRefs: any[] = [];

/**
 * 将秒数转换为 mm:ss 格式
 */
const formatSecondsToTime = (seconds: number): string => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
};

/**
 * 将 HH:mm:ss 或 mm:ss 格式转换为毫秒数
 */
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

const formatTime = (ms: number) => {
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600).toString().padStart(2, '0');
  const m = Math.floor((totalSeconds % 3600) / 60).toString().padStart(2, '0');
  const s = (totalSeconds % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
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

const parseSegmentSummaries = (videoSummaryStr: string | undefined, timestampsStr: string) => {
  const timestamps = renderTimestamps(timestampsStr).sort((a, b) => a.start - b.start);

  if (!videoSummaryStr || videoSummaryStr === '暂无概述') {
    return timestamps.map(ts => ({ ...ts, summary: '' }));
  }
  
  try {
    const parsed = JSON.parse(videoSummaryStr);
    if (Array.isArray(parsed)) {
      return parsed.sort((a, b) => a.start - b.start);
    } else {
      // Backward compatibility for old single string summaries
      return timestamps.map(ts => ({ ...ts, summary: videoSummaryStr }));
    }
  } catch (e) {
    // If it's not JSON, it's an old string summary
    return timestamps.map(ts => ({ ...ts, summary: videoSummaryStr }));
  }
};

const statusTextMap: Record<string, string> = {
  'STATUS_PENDING': '等待上传...',
  'STATUS_TRANSCRIPTING': '正在进行语音转写，提取文稿...',
  'STATUS_EXTRACTING': '文稿提取成功！正在通过 AI 提取核心知识点...',
  'STATUS_SCORING': '核心知识已提取，正在进行多维度智能评分...',
  'STATUS_SUCCESS': '处理完成！',
  'STATUS_FAILED': '处理失败，请重试'
};

const pendingTasks = ref<{ id: number; filename: string; status: string }[]>([]);
const cancellingTaskId = ref<number | null>(null);
const showAutoCloseTip = ref(false);
const countdown = ref(3);
let countdownTimer: any = null;
const pendingDraftCount = ref(0);

const processFinished = computed(() => processStatus.value === 'STATUS_SUCCESS' || processStatus.value === 'STATUS_FAILED');
const getTaskStatusLabel = (status: string) => {
  if (status === 'STATUS_TRANSCRIPTING') return '转写中';
  if (status === 'STATUS_EXTRACTING') return '提取中';
  if (status === 'STATUS_SCORING') return '评分中';
  if (status === 'STATUS_SUCCESS') return '已完成';
  if (status === 'STATUS_FAILED') return '失败';
  return '排队中';
};

const getTaskPercentage = (status: string) => {
  if (status === 'STATUS_TRANSCRIPTING') return 30;
  if (status === 'STATUS_EXTRACTING') return 60;
  if (status === 'STATUS_SCORING') return 90;
  if (status === 'STATUS_SUCCESS') return 100;
  return 10;
};

const scoreColors = [
  { color: '#f56c6c', percentage: 20 },
  { color: '#e6a23c', percentage: 40 },
  { color: '#5cb87a', percentage: 60 },
  { color: '#1989fa', percentage: 80 },
  { color: '#6f7ad3', percentage: 100 },
];

const formatDuration = (ms?: number) => {
  if (ms === undefined) return '--:--';
  const totalSeconds = Math.floor(ms / 1000);
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
};

const currentStatusText = computed(() => {
  if (uploadProgress.value < 100) return `正在上传视频至云端服务器 (${uploadProgress.value}%)`;
  return statusTextMap[processStatus.value] || '后台深度分析中...';
});

const defaultProps = {
  children: 'children',
  label: 'title'
};

const fetchFramework = async () => {
  try {
    const res = await getFrameworkDetailAPI(frameworkId);
    if (res.data) {
      framework.value = res.data;
    }
  } catch (error) {
    console.error(error);
  }
};

const fetchVideos = async () => {
    try {
        const res = await getFrameworkVideosAPI(frameworkId);
        if (res.data) {
            frameworkVideos.value = res.data;
        }
    } catch (error) {
        console.error("Failed to fetch framework videos:", error);
    }
};

const openVideoManagementDialog = () => {
    videoManagementDialogVisible.value = true;
    fetchVideos();
};

const handleDeleteVideo = async (video: VideoVo) => {
    try {
        await ElMessageBox.confirm(
            `确定要删除视频 "${video.originalFileName}" 吗？\n\n此操作将同时删除：\n1. OSS云存储中的视频文件\n2. 视频转写片段\n3. 知识点关联\n4. 视频评分\n5. 视频关联的草稿\n删除后无法恢复！`,
            '删除确认',
            {
                confirmButtonText: '确定删除',
                cancelButtonText: '取消',
                type: 'warning',
                confirmButtonClass: 'el-button--danger'
            }
        );

        deletingVideoId.value = video.id;

        await deleteVideoAPI(video.id);

        ElMessage.success('视频删除成功');

        await fetchVideos();
        await fetchGraphData();

    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('Delete video failed:', error);
            ElMessage.error(error.response?.data?.message || '删除视频失败');
        }
    } finally {
        deletingVideoId.value = null;
    }
};

const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
};

const formatDateTime = (dateTime: string) => {
    if (!dateTime) return '';
    const date = new Date(dateTime);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
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
            const tags = renderTimestamps(v.timestamps).sort((a, b) => a.start - b.start);
            map.set(v.videoId, { ...v, timestamps: JSON.stringify(tags) });
        }
    });
    return Array.from(map.values());
};

const fetchGraphData = async () => {
  try {
    const [pointsRes, relationsRes] = await Promise.all([
      getFrameworkPointsAPI(frameworkId),
      getFrameworkRelationsAPI(frameworkId)
    ]);

    if (pointsRes.data && relationsRes.data) {
      allPoints.value = pointsRes.data.map(p => ({
        ...p,
        videoIndexings: mergeVideoIndexings(p.videoIndexings || [])
      }));
      applyCategoryFilter();
      relations.value = relationsRes.data;
      buildTree();
    }
  } catch (error) {
    console.error("Failed to fetch graph data:", error);
  }
};

// --- Tree Validation Logic for Relations ---
const hasParentSet = ref(new Set<number>());
const parentMap = ref(new Map<number, number>());

const isAncestor = (ancestorId: number, childId: number): boolean => {
  let curr: number | undefined = childId;
  while (curr !== undefined) {
    if (curr === ancestorId) return true;
    curr = parentMap.value.get(curr);
  }
  return false;
};

const buildTree = () => {
  // 1. Initialize map and populate flat points
  const pointsMap = new Map<number, KnowledgePointVo>();
  rawPoints.value.forEach(p => {
    pointsMap.set(p.id, { ...p, children: [] });
  });

  // 2. Map CONTAINS relations safely (breaking cycles and enforcing strict tree)
  hasParentSet.value.clear();
  parentMap.value.clear();
  relations.value.forEach(r => {
    if (r.relationType === RelationType.CONTAINS) {
      const sourceId = r.sourcePointId;
      const targetId = r.targetPointId;

      // 为了让 el-tree 正常显示并防止无限递归/节点消失：
      // 1. 一个节点只能有一个父节点 (!hasParentSet.value.has(targetId))
      // 2. 不能形成死循环 (!isAncestor(targetId, sourceId))
      if (!hasParentSet.value.has(targetId) && !isAncestor(targetId, sourceId)) {
        const parent = pointsMap.get(sourceId);
        const child = pointsMap.get(targetId);
        if (parent && child) {
          parent.children = parent.children || [];
          parent.children.push(child);
          hasParentSet.value.add(targetId);
          parentMap.value.set(targetId, sourceId);
        }
      }
    }
  });

  const roots: KnowledgePointVo[] = [];
  // 3. Find roots (nodes that are not a child of any node)
  pointsMap.forEach((node, id) => {
    if (!hasParentSet.value.has(id)) {
      roots.push(node);
    }
  });

  treeData.value = roots;

  // Refresh selectedNode reference if it exists, otherwise auto-select first root
  if (selectedNode.value) {
    const freshNode = pointsMap.get(selectedNode.value.id);
    if (freshNode) {
        selectedNode.value = freshNode;
    } else {
        const firstRoot = roots[0];
        selectedNode.value = firstRoot ?? null;
    }
  } else if (route.query.pointId) {
    const targetId = Number(route.query.pointId);
    const targetNode = pointsMap.get(targetId);
    if (targetNode) {
      selectedNode.value = targetNode;
    } else if (roots.length > 0) {
      selectedNode.value = roots[0];
    }
  } else if (roots.length > 0) {
    const firstRoot = roots[0];
    if (firstRoot) {
      handleNodeClick(firstRoot);
    }
  }
};

// Use watcher or simple assignment to track original content
// Redundant watch import removed as it's now in the main vue import


watch(selectedNode, (newVal) => {
    if (newVal) {
        originalContent = newVal.content;
        originalTitle = newVal.title;
        originalCategory = newVal.category || defaultCategoryName;
    }
    isEditing.value = false;
});

watch(selectedCategoryFilter, (newCategory) => {
  const routeCategory = normalizeCategoryFilter(route.query.category);
  if (newCategory !== routeCategory) {
    const nextQuery = { ...route.query };
    if (newCategory) {
      nextQuery.category = newCategory;
    } else {
      delete nextQuery.category;
    }
    router.replace({ query: nextQuery });
  }

  applyCategoryFilter();
  buildTree();
});

watch(pointKeyword, () => {
  applyCategoryFilter();
  buildTree();
});

watch(
  () => route.query.category,
  (queryCategory) => {
    syncCategoryFromQuery(queryCategory);
  }
);

watch(knowledgeCategories, (categories) => {
  if (!categories.length) return;

  if (selectedCategoryFilter.value && !categories.includes(selectedCategoryFilter.value)) {
    selectedCategoryFilter.value = '';
  }

  if (!categories.includes(addPointForm.category)) {
    addPointForm.category = categories[0] || defaultCategoryName;
  }
});

const jumpToVideo = (videoId: number, startTime: number) => {
    // Navigate to videoDetail with timestamp t in seconds
    router.push({
        name: 'videoDetail',
        params: { id: videoId.toString() },
        query: { t: Math.floor(startTime / 1000) }
    });
};

const handleNodeClick = (data: KnowledgePointVo) => {
  if (isEditing.value) {
    ElMessage.warning('请先完成正在进行的编辑');
    return;
  }
  selectedNode.value = data;
  // Relations are already fetched locally, computed properties will auto-react
};

const cancelEdit = () => {
    isEditing.value = false;
    if (selectedNode.value) {
        selectedNode.value.content = originalContent;
        selectedNode.value.title = originalTitle;
        selectedNode.value.category = originalCategory;
    }
};

// Add / Delete Point Logic
const openAddPointDialog = (parentId: number | null = null) => {
    addPointForm.title = '';
    addPointForm.category = knowledgeCategories.value[0] || defaultCategoryName;
    addPointForm.content = '';
    addPointForm.parentId = parentId;
    addPointForm.relations = [];
    addPointForm.videoRefs = [];


    addPointDialogVisible.value = true;
    fetchVideos(); // 每次打开都刷新可选视频
};

const startEdit = () => {
    if (!selectedNode.value) return;
    originalContent = selectedNode.value.content;
    originalTitle = selectedNode.value.title;
    originalCategory = selectedNode.value.category || defaultCategoryName;

    // Initialize relations for editing
    const currentId = selectedNode.value.id;
    originalRelations = relations.value.filter(r => r.sourcePointId === currentId || r.targetPointId === currentId).map(r => ({
        sourcePointId: r.sourcePointId,
        targetPointId: r.targetPointId,
        relationType: r.relationType
    }));
    editedRelations.value = JSON.parse(JSON.stringify(originalRelations));

    // Initialize video refs for editing
    originalVideoRefs = (selectedNode.value.videoIndexings || []).map(v => ({
        videoId: v.videoId,
        segments: renderTimestamps(v.timestamps).map((s: any) => ({
            startStr: formatTime(s.start),
            endStr: formatTime(s.end)
        }))
    }));
    editedVideoRefs.value = JSON.parse(JSON.stringify(originalVideoRefs));

    fetchVideos()

    isEditing.value = true;
};
const handleAddPoint = async () => {
    if (!addPointForm.title.trim()) {
        ElMessage.warning('请输入标题');
        return;
    }

    addingPoint.value = true;

    try {
        const payload: AddKnowledgePointRequest = {
            frameworkId: parseInt(frameworkId as string),
            title: addPointForm.title,
            category: addPointForm.category,
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

            // 建立额外关系 (CONTAINS parent added automatically if specified)
            if (addPointForm.parentId) {
                await addRelationAPI({
                    sourcePointId: addPointForm.parentId,
                    targetPointId: newPointId,
                    relationType: 'CONTAINS'
                });
            }

            for (const rel of addPointForm.relations) {
                await addRelationAPI({
                    sourcePointId: rel.sourcePointId || newPointId,
                    targetPointId: rel.targetPointId,
                    relationType: rel.relationType
                });
            }

            ElMessage.success('新增成功');
            addPointDialogVisible.value = false;
            fetchGraphData(); // 刷新数据
        }
    } catch (e) {
        ElMessage.error('新增失败');
    } finally {
        addingPoint.value = false;
    }
};

const handleDeletePoint = () => {
    if (!selectedNode.value) return;

    ElMessageBox.confirm(
        '删除知识点会导致其下的知识关联关系也一并移除，确定要删除吗？',
        '警告',
        {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning'
        }
    ).then(async () => {
        try {
            const res = await deleteKnowledgePointAPI(selectedNode.value!.id);
            if (res.success) {
                ElMessage.success('删除成功');
                selectedNode.value = null;
                fetchGraphData();
            }
        } catch (e) {
            ElMessage.error('删除失败');
        }
    }).catch(() => {});
};

const handleSaveContent = async () => {
    if (!selectedNode.value) return;
    saving.value = true;
    try {
        const payload: UpdateKnowledgePointRequest = {};
        if (selectedNode.value.title !== originalTitle) payload.title = selectedNode.value.title;
        if ((selectedNode.value.category || defaultCategoryName) !== originalCategory) {
          payload.category = selectedNode.value.category || defaultCategoryName;
        }
        if (selectedNode.value.content !== originalContent) payload.content = selectedNode.value.content;

        // Relation deltas
        const relationUpdates: UpdateKnowledgePointRequest['relationUpdates'] = [];
        // Helper to stringify relation for comparison
        const relKey = (r: any) => `${r.sourcePointId}-${r.targetPointId}-${r.relationType}`;
        const originalRelSet = new Set(originalRelations.map(relKey));
        const editedRelSet = new Set(editedRelations.value.map(relKey));

        editedRelations.value.forEach(r => {
            if (!originalRelSet.has(relKey(r))) {
                relationUpdates.push({ action: 'ADD', ...r });
            }
        });
        originalRelations.forEach(r => {
            if (!editedRelSet.has(relKey(r))) {
                relationUpdates.push({ action: 'DELETE', ...r });
            }
        });
        if (relationUpdates.length > 0) payload.relationUpdates = relationUpdates;

        // Video Ref deltas
        const videoUpdates: UpdateKnowledgePointRequest['videoUpdates'] = [];
        const originalVideoMap = new Map(originalVideoRefs.map(v => [v.videoId, JSON.stringify(v.segments)]));
        const editedVideoMap = new Map(editedVideoRefs.value.map(v => [v.videoId, JSON.stringify(v.segments)]));

        editedVideoRefs.value.forEach(v => {
            if (editedVideoMap.get(v.videoId) !== originalVideoMap.get(v.videoId)) {
                videoUpdates.push({
                    action: 'SET',
                    videoId: v.videoId,
                    segments: v.segments.map(s => ({
                        start: parseTimeToMilliSeconds(s.startStr),
                        end: parseTimeToMilliSeconds(s.endStr)
                    }))
                });
            }
        });
        originalVideoRefs.forEach(v => {
            if (!editedVideoMap.has(v.videoId)) {
                videoUpdates.push({ action: 'DELETE', videoId: v.videoId });
            }
        });
        if (videoUpdates.length > 0) payload.videoUpdates = videoUpdates;

        const res = await updateKnowledgePointAPI(selectedNode.value.id, payload);
        if (res.success) {
            ElMessage.success('保存成功');
            isEditing.value = false;
            fetchGraphData(); // Refresh all data to show changes
            if (selectedNode.value) {
                selectedNode.value.updateTime = new Date().toISOString();
            }
        }
    } catch (error) {
        console.error('Failed to save content:', error);
    } finally {
        saving.value = false;
    }
};

// --- Computed Relations ---
interface DisplayRelation {
  id: number;
  title: string;
  targetId: number;
}

const mapRelation = (r: KnowledgeRelationVo, currentId: number): DisplayRelation => {
  const isSource = r.sourcePointId === currentId;
  return {
    id: r.id,
    title: isSource ? r.targetPointTitle : r.sourcePointTitle,
    targetId: isSource ? r.targetPointId : r.sourcePointId
  };
};

const prerequisitePoints = computed(() => {
  if (!selectedNode.value) return [];
  const currentId = selectedNode.value.id;
  return relations.value
    .filter(r => r.relationType === RelationType.PREREQUISITE && r.sourcePointId === currentId)
    .map(r => mapRelation(r, currentId));
});

const extendsPoints = computed(() => {
  if (!selectedNode.value) return [];
  const currentId = selectedNode.value.id;
  // If we are evaluating what this point extends TO, we are the source.
  // Example: Basic Math (source) EXTENDS -> Calculus (target)
  const list = relations.value
    .filter(r => r.relationType === RelationType.PREREQUISITE && r.targetPointId === currentId)
    .map(r => mapRelation(r, currentId));
  return Array.from(new Map(list.map(r => [r.targetId, r])).values())
});

const relatedPoints = computed(() => {
  if (!selectedNode.value) return [];
  const currentId = selectedNode.value.id;
  const list = relations.value
    .filter(r =>
      r.relationType === RelationType.RELATED &&
      (r.sourcePointId === currentId || r.targetPointId === currentId)
    )
    .map(r => mapRelation(r, currentId));
  return Array.from(new Map(list.map(r => [r.targetId, r])).values())
});

const hasRelations = computed(() =>
  prerequisitePoints.value.length > 0 ||
  extendsPoints.value.length > 0 ||
  relatedPoints.value.length > 0
);

// Navigation helper for graph traversal
const navigateToPoint = (rel: DisplayRelation) => {
  const findNodeInTree = (nodes: KnowledgePointVo[], targetId: number): KnowledgePointVo | null => {
    for (const node of nodes) {
      if (node.id === targetId) return node;
      if (node.children?.length) {
        const found = findNodeInTree(node.children, targetId);
        if (found) return found;
      }
    }
    return null;
  };

  const targetNode = findNodeInTree(treeData.value, rel.targetId);
  if (targetNode) {
    handleNodeClick(targetNode);
  } else {
    // Edge case if we can't find it in the tree directly, maybe it's floating. Check raw points.
    const flatNode = rawPoints.value.find(p => p.id === rel.targetId);
    if (flatNode) {
      handleNodeClick(flatNode);
    }
  }
};

const fetchPendingTasks = async () => {
  try {
    const res = await getPendingTasksAPI(frameworkId);
    if (res.success && res.data && res.data.length > 0) {
      res.data.forEach((task: any) => {
        // If it's already in the list, skip
        if (!pendingTasks.value.find(t => t.id === task.id)) {
          pendingTasks.value.push({ id: task.id, filename: task.filename, status: task.status });
          setupSse(task.id);
        }
      });
    }
  } catch (error) {
    console.error('Failed to fetch pending tasks', error);
  }
};

// 加载待审核草稿数量
const fetchPendingDraftCount = async () => {
  try {
    const res = await getFrameworkDraftsAPI(frameworkId);
    if (res.success) {
      pendingDraftCount.value = res.data.length;
    }
  } catch (error) {
    console.error('Failed to fetch pending drafts', error);
  }
};

// 导航到草稿页面
const navigateToDrafts = () => {
  router.push('/drafts');
};

const initData = async () => {
  loading.value = true;
  await Promise.all([fetchFramework(), fetchGraphData(), fetchPendingTasks(), fetchVideos(), fetchPendingDraftCount()]);
  loading.value = false;
};

// --- Upload Logic ---
const openUploadDialog = () => {
  selectedFile.value = null;
  uploadProgress.value = 0;
  uploading.value = false;
  showAutoCloseTip.value = false;
  if (countdownTimer) clearInterval(countdownTimer);
  uploadDialogVisible.value = true;
};

const checkVideoMagicNumber = (file: File): Promise<boolean> => {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      if (!e.target || !e.target.result) {
        resolve(false);
        return;
      }
      const arr = new Uint8Array(e.target.result as ArrayBuffer);
      const header = Array.from(arr).map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase();

      // MP4/MOV: usually starts with 00 00 00 ** 66 74 79 70 (....ftyp)
      const isMP4 = header.substring(8, 16) === '66747970';
      // MKV/WebM: 1A 45 DF A3
      const isMKV = header.startsWith('1A45DFA3');
      // AVI: 52 49 46 46 (RIFF) ... 41 56 49 20 (AVI )
      const isAVI = header.startsWith('52494646') && header.substring(16, 24) === '41564920';
      // FLV: 46 4C 56 (FLV)
      const isFLV = header.startsWith('464C56');
      // WMV/ASF: 30 26 B2 75 8E 66 CF 11
      const isWMV = header.startsWith('3026B2758E66CF11');

      resolve(isMP4 || isMKV || isAVI || isFLV || isWMV);
    };
    reader.onerror = () => resolve(false);
    // 读前12个字节足够覆盖常用视频文件的基本魔数（WMV需要至少16字节，但这里前12个字节 '3026B2758E66CF11' 前8个也足够判断，为了严谨我们读取16个字节）
    reader.readAsArrayBuffer(file.slice(0, 16));
  });
};

const handleFileChange = async (uploadFile: UploadFile) => {
  if (uploadFile.raw) {
    const isVideo = uploadFile.raw.type.includes('video/');
    if (!isVideo) {
      ElMessage.error('只能上传视频文件格式！');
      selectedFile.value = null;
      return false;
    }
    const isLt1G = uploadFile.raw.size / 1024 / 1024 < 1024;
    if (!isLt1G) {
      ElMessage.error('视频大小不能超过 1GB！');
      selectedFile.value = null;
      return false;
    }

    // Magic Number 深度校验
    const isValidFormat = await checkVideoMagicNumber(uploadFile.raw);
    if (!isValidFormat) {
      ElMessage.error('文件格式校验失败，请勿伪造视频文件后缀！目前支持 MP4 / MKV / WebM / AVI / FLV / WMV 等主流格式');
      // 清空不合法文件
      selectedFile.value = null;
      return false;
    }

    selectedFile.value = uploadFile.raw;
  }
};

const handleFileRemove = () => {
  selectedFile.value = null;
};

const getVideoDuration = (file: File): Promise<number> => {
    return new Promise((resolve, reject) => {
        const video = document.createElement('video');
        video.preload = 'metadata';
        video.onloadedmetadata = () => {
            window.URL.revokeObjectURL(video.src);
            resolve(Math.round(video.duration * 1000)); // 后端可能需要毫秒作为单位，或者秒作为单位。通常VideoDuration是毫秒
        };
        video.onerror = () => reject('无法读取视频文件时长');
        video.src = window.URL.createObjectURL(file);
    });
};

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择一个视频文件');
    return;
  }

  uploading.value = true;
  uploadProgress.value = 0;
  activeStep.value = 0;
  processStatus.value = 'STATUS_PENDING';

  try {
    // 1. 获取视频时长
    const duration = await getVideoDuration(selectedFile.value);

    // 2. 获取 POST 表单上传签名
    const fileExt = selectedFile.value.name.split('.').pop() || 'mp4';
    const sigRes = await getObsUploadSignatureAPI(fileExt);
    if (!sigRes.success || !sigRes.data) throw new Error(sigRes.message || '无法获取OBS上传签名');
    const sigData = sigRes.data;

    const objectName = sigData.objectName;
    const obsUrl = sigData.obsUrl;

    // 3. 构建 FormData，POST 表单直传到华为云 OBS
    const formData = new FormData();
    formData.append('key', objectName);
    formData.append('policy', sigData.policy);
    formData.append('signature', sigData.signature);
    formData.append('AWSAccessKeyId', sigData.accessKeyId);
    formData.append('x-obs-acl', 'public-read');
    formData.append('file', selectedFile.value);

    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          uploadProgress.value = Math.floor((event.loaded / event.total) * 100);
        }
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve();
        } else {
          reject(new Error(`OBS上传失败 HTTP ${xhr.status}: ${xhr.responseText || '(无法读取响应体)'}`));
        }
      };
      xhr.onerror = () => reject(new Error('OBS上传网络错误（查看浏览器Network面板获取详情）'));
      xhr.ontimeout = () => reject(new Error('OBS上传超时'));
      xhr.open('POST', sigData.postUrl);
      xhr.timeout = 7200000;
      xhr.send(formData);
    });

    uploadProgress.value = 100;
    activeStep.value = 1;

    // 4. 组装元数据交付后端落盘并分析
    const reqDto = {
        originalFileName: selectedFile.value.name,
        ossObjectName: objectName,
        ossUrl: obsUrl,
        fileSize: selectedFile.value.size,
        duration: duration,
        frameworkId: parseInt(frameworkId)
    };

    const processRes = await processVideoAPI(reqDto);
    if (!processRes.success || !processRes.data) {
      throw new Error(processRes.message || '请求后端发部分析任务失败');
    }

    const taskId = processRes.data; // 后端现在返回的是 taskId

    // 添加到任务追踪器
    pendingTasks.value.push({
      id: taskId,
      filename: selectedFile.value.name,
      status: 'STATUS_PENDING'
    });

    // 几秒后自动关闭
    showAutoCloseTip.value = true;
    countdown.value = 3;
    countdownTimer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        uploadDialogVisible.value = false;
        clearInterval(countdownTimer);
        showAutoCloseTip.value = false;
      }
    }, 1000);

    // 提示用户可以自由操作
    ElNotification({
        title: '开始分析任务',
        message: '视频已成功入队分析，您可以查看右下角的小挂件关注进度。',
        type: 'success',
        position: 'bottom-right'
    });

    // 接入 SSE 状态推送
    setupSse(taskId);
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败，请重试');
    uploading.value = false;
  }
};

const setupSse = async (taskId: number) => {
  // Clear existing connection if any
  if (sseConnections.has(taskId)) {
    sseConnections.get(taskId)?.close();
    sseConnections.delete(taskId);
  }

  try {
    // 1. 获取短效会话 Ticket
    const ticketRes = await getSseTicketAPI(taskId);
    if (!ticketRes.success || !ticketRes.data) {
      console.error('Failed to get SSE ticket');
      return;
    }
    const ticket = ticketRes.data;

    // 2. 建立 SSE 连接
    const baseURL = import.meta.env.VITE_API_URL || '/api';
    const eventSource = new EventSource(`${baseURL}/task/${taskId}/subscribe?ticket=${ticket}`);
    sseConnections.set(taskId, eventSource);

    eventSource.onmessage = (event) => {
      const status = event.data;

      // 同步更新追踪器状态
      const taskIdx = pendingTasks.value.findIndex(t => t.id === taskId);
      if (taskIdx > -1) {
        const task = pendingTasks.value[taskIdx];
        if (task) {
          task.status = status;
        }
      }

      // 如果是当前正在上传窗口显示的任务，更新进度步数
      if (uploadDialogVisible.value && processStatus.value !== 'STATUS_SUCCESS') {
         processStatus.value = status;
         if (status === 'STATUS_TRANSCRIPTING') activeStep.value = 1;
         else if (status === 'STATUS_EXTRACTING') activeStep.value = 2;
         else if (status === 'STATUS_SCORING') activeStep.value = 3;
         else if (status === 'STATUS_SUCCESS') activeStep.value = 4;
         else if (status === 'STATUS_FAILED') activeStep.value = -1;
      }

      if (status === 'STATUS_SUCCESS' || status === 'STATUS_FAILED') {
        if (status === 'STATUS_SUCCESS') {
          handleTaskSuccess(taskId);
        }
        eventSource.close();
        sseConnections.delete(taskId);
      }
    };

    eventSource.onerror = (error) => {
      console.error(`SSE Connection error for task ${taskId}:`, error);
      // 浏览器遇到网络断开会自动用原 url 尝试重连（包含 ticket）。
      // 只要 ticket 在后端 Redis 的 30 分钟有效期内，就会被后端放行。
    };

  } catch (error) {
    console.error('Failed to setup SSE for task', taskId, error);
  }
};

const handleTaskSuccess = async (taskId: number) => {
  try {
    const res = await getTaskResultAPI(taskId);
    if (res.success && res.data) {
      processResult.value = res.data;

      // 从追踪器中移除已完成任务
      const taskIdx = pendingTasks.value.findIndex(t => t.id === taskId);
      if (taskIdx > -1) {
        pendingTasks.value.splice(taskIdx, 1);
      }

      if (uploading.value) uploading.value = false;
      fetchGraphData(); // 刷新背景图谱

      // 强提醒，让用户无法忽视
      ElMessageBox.confirm(
        `您的视频《${res.data.video.originalFileName}》AI 分析与知识提炼已完成！是否立即查看分析简报？`,
        '✨ 分析任务完成',
        {
          confirmButtonText: '查看简报',
          cancelButtonText: '稍后再看',
          type: 'success',
          center: true,
          closeOnClickModal: false
        }
      ).then(async () => {
        reportDialogVisible.value = true;
        await markTaskReportViewedAPI(taskId);
      }).catch(async () => {
        await markTaskReportViewedAPI(taskId);
        ElMessage({
          type: 'info',
          message: '你可以稍后在视频详情页查看完整分析记录'
        });
      });
    }
  } catch (e) {
    ElMessage.error('获取分析报告失败');
    uploading.value = false;
  }
};

const handleCancelTask = async (taskId: number, filename: string) => {
    try {
        await ElMessageBox.confirm(
            `确定要取消任务 "${filename}" 吗？\n\n取消后任务将停止处理，已处理的部分数据可能会丢失。`,
            '取消任务确认',
            {
                confirmButtonText: '确定取消',
                cancelButtonText: '继续等待',
                type: 'warning',
                confirmButtonClass: 'el-button--danger'
            }
        );

        cancellingTaskId.value = taskId;

        await cancelTaskAPI(taskId);

        ElMessage.success('任务已取消');

        await fetchPendingTasks();

    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('Cancel task failed:', error);
            ElMessage.error(error.response?.data?.message || '取消任务失败');
        }
    } finally {
        cancellingTaskId.value = null;
    }
};

const closeReportAndGoDetail = () => {
  if (processResult.value?.video.id) {
    router.push(`/video/${processResult.value.video.id}`);
  }
  reportDialogVisible.value = false;
};

onMounted(() => {
  syncCategoryFromQuery(route.query.category);
  initData();
});

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
  sseConnections.forEach(conn => conn.close());
  sseConnections.clear();
});
</script>

<style scoped>
.framework-detail-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
}

.header-section {
  padding: 24px;
  border-radius: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.breadcrumb {
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.breadcrumb .el-icon {
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  transition: background 0.2s;
}

.breadcrumb .el-icon:hover {
  background: rgba(0, 0, 0, 0.05);
}

.divider {
  margin: 0 12px;
  color: #cbd5e1;
}

.content-wrapper {
  display: flex;
  flex-grow: 1;
  gap: 20px;
  min-height: 0;
}

.tree-panel {
  flex: 3;
  min-width: 300px;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  padding: 24px;
  border-radius: 16px;
  overflow: hidden;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
  white-space: nowrap;
}

.tree-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.tree-header-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.tree-add-btn {
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: 10px;
  transition: all 0.2s ease;
  background: white;
  border: 1px solid rgba(79, 70, 229, 0.28);
  color: var(--primary-color);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  white-space: nowrap;
}

.tree-add-btn:hover {
  border-color: var(--primary-color);
  background: rgba(79, 70, 229, 0.06);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.12);
}

.tree-add-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.category-filter {
  width: 180px;
}

.point-search-input {
  width: 100%;
}

/* Header & Action Button Styles */
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
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}
.action-btn:active {
  transform: translateY(0);
}
.edit-btn {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  color: white;
}
.edit-btn:hover {
  filter: brightness(1.03);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.28);
  transform: translateY(-1px);
}
.save-btn {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: white;
}
.save-btn:hover {
  filter: brightness(1.03);
  box-shadow: 0 6px 16px rgba(34, 197, 94, 0.25);
  transform: translateY(-1px);
}
.save-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
  transform: none;
  box-shadow: none;
}
.cancel-btn {
  background: transparent;
  color: #94a3b8;
  box-shadow: none;
}
.cancel-btn:hover {
  background: rgba(148, 163, 184, 0.08);
  color: #64748b;
}
.delete-btn {
  background: transparent;
  color: #94a3b8;
  padding: 6px 8px;
  box-shadow: none;
}
.delete-btn:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}
.top-btn {
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 10px;
  transition: all 0.2s ease;
  background: white;
  border: 1px solid var(--border-color);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.top-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
  background: rgba(79, 70, 229, 0.04);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.12);
}

@media (max-width: 768px) {
  .header-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .actions {
    width: 100%;
    justify-content: flex-start;
  }

  .tree-header-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .tree-header-actions > .el-button,
  .tree-header-actions > .tree-add-btn {
    width: 100%;
    justify-content: flex-start;
    margin-left: 0;
  }
}

.knowledge-tree {
  flex-grow: 1;
  overflow-y: auto;
  background: transparent;
}

/* Tree Override Styles */
:deep(.el-tree-node__content) {
  height: 44px;
  border-radius: 8px;
  margin-bottom: 4px;
  transition: all 0.2s;
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: rgba(79, 70, 229, 0.1) !important;
  color: var(--primary-color) !important;
  font-weight: 600;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
  overflow: hidden;
}

.node-left {
  flex-grow: 1;
  overflow: hidden;
}

.node-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.node-action {
  opacity: 0;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 4px !important;
  background-color: rgba(79, 70, 229, 0.05);
  border-radius: 6px;
  color: var(--primary-color);
}

.custom-tree-node:hover .node-action {
  opacity: 1;
}

.node-action:hover {
  background-color: var(--primary-color) !important;
  color: white !important;
  transform: scale(1.1);
}

:deep(.node-title-preview) {
  padding: 0 !important;
  background: transparent !important;
  font-size: 15px;
  line-height: normal;
}

:deep(.node-title-preview .md-editor-preview-wrapper) {
  padding: 0;
}

:deep(.node-title-preview p) {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-badge {
  background-color: white;
  border: 1px solid var(--border-color);
}

/* Right Detail Panel */
.detail-panel {
  flex: 7;
  display: flex;
  flex-direction: column;
  padding: 32px 40px;
  border-radius: 16px;
  overflow-y: auto;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.detail-panel:not(.has-selection) {
  align-items: center;
  justify-content: center;
}

.empty-selection {
  transform: translateY(-20px);
}

.detail-header {
  margin-bottom: 24px;
}

.header-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.header-actions {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}

:deep(.detail-title-preview) {
  padding: 0 !important;
  background: transparent !important;
  margin-bottom: 12px;
}

:deep(.detail-title-preview .md-editor-preview-wrapper) {
  padding: 0;
}

:deep(.detail-title-preview p) {
  font-size: 32px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.5px;
  margin: 0;
  line-height: 1.3;
}

.title-edit-container {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title-edit-hint {
  font-size: 12px;
  color: var(--primary-color);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0.8;
}

.title-input-edit {
  margin-bottom: 12px;
  width: 100%;
}

.category-select {
  width: 220px;
}

:deep(.title-input-edit .el-textarea__inner) {
  font-size: 32px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.5px;
  line-height: 1.3;
  padding: 8px 12px;
  background: rgba(79, 70, 229, 0.03);
  box-shadow: none !important;
  border: 1px dashed var(--primary-light);
  border-radius: 8px;
  resize: none;
  font-family: inherit;
  overflow: hidden;
  transition: all 0.2s ease;
}

:deep(.title-input-edit .el-textarea__inner:focus) {
  background: white;
  border: 1px solid var(--primary-color);
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.1) !important;
}

:deep(.title-input-edit .el-textarea__inner:hover) {
  background: rgba(79, 70, 229, 0.05);
  border-color: var(--primary-color);
}

.detail-meta {
  display: flex;
  gap: 16px;
  color: var(--text-secondary);
  font-size: 13px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-content {
  flex-grow: 1;
  margin-top: 16px;
}

:deep(.md-editor-previewOnly) {
  background: transparent !important;
}

:deep(.md-editor-previewOnly .md-editor-preview-wrapper) {
  padding: 0;
}

/* Synthetic Relations Section */
.relations-section {
  margin-top: 48px;
  padding-top: 24px;
  border-top: 2px dashed #e2e8f0;
}

.relations-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.relations-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.info-icon {
  color: #94a3b8;
  cursor: help;
}

.relations-placeholder {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: rgba(248, 250, 252, 0.6);
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
}

.relation-group {
  display: flex;
  align-items: center;
  gap: 16px;
}

.rf-title {
  width: 100px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.prerequisite .rf-title { color: #d97706; }
.extends .rf-title { color: #10b981; }

.related .rf-title { color: #3b82f6; }

.rel-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.rel-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

.edit-management-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #edf2f7;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.management-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.m-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.m-label::before {
  content: "";
  width: 4px;
  height: 14px;
  background: var(--primary-color);
  border-radius: 2px;
}

/* Upload Dialog Styles */
.upload-dialog-content {
  padding: 10px 20px;
}
.subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 24px;
  text-align: center;
}
:deep(.el-upload-dragger) {
  border: 2px dashed rgb(203 213 225);
  border-radius: 12px;
  background-color: rgba(255, 255, 255, 0.5);
  transition: all 0.3s;
  padding: 32px 16px;
}
:deep(.el-upload-dragger:hover) {
  border-color: var(--primary-color);
  background-color: rgba(79, 70, 229, 0.02);
}
.el-icon--upload {
  font-size: 48px;
  color: #94a3b8;
  margin-bottom: 12px;
}
.el-upload__text {
  font-size: 15px;
  color: var(--text-secondary);
}
.el-upload__text em {
  font-style: normal;
  color: var(--primary-color);
  font-weight: 600;
}
.file-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.file-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.file-size {
  font-size: 13px;
  color: var(--text-secondary);
}
.mt-2 { margin-top: 8px; }
.mt-4 { margin-top: 16px; }
.text-center { text-align: center; }
.text-gray-500 { color: #6b7280; }
.text-sm { font-size: 0.875rem; }
.text-primary { color: var(--primary-color); }

.process-steps {
  margin: 20px 0;
}

.status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.status-text {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

:deep(.el-step__title) {
  font-size: 13px;
}
:deep(.el-step__description) {
  font-size: 11px;
}

/* Report Dialog Styles */
.report-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.report-content {
  padding: 0;
}

.report-header {
  padding: 30px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.video-info-mini {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 500;
  opacity: 0.9;
}

.score-summary {
  display: flex;
  align-items: center;
  gap: 30px;
}

.score-item {
  text-align: center;
}

.score-value {
  font-size: 28px;
  font-weight: 800;
  line-height: 1;
  margin-bottom: 4px;
}

.score-label {
  font-size: 12px;
  opacity: 0.8;
  font-weight: 500;
}

.score-divider {
  width: 1px;
  height: 30px;
  background: rgba(255, 255, 255, 0.2);
}

.report-section {
  padding: 24px 30px;
  border-bottom: 1px solid #f1f5f9;
}

.section-subtitle {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.points-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.stat-box {
  padding: 16px;
  border-radius: 12px;
  text-align: center;
  transition: all 0.2s;
}

.stat-box.new {
  background: rgba(16, 185, 129, 0.05);
  border: 1px solid rgba(16, 185, 129, 0.1);
}

.stat-box.update {
  background: rgba(59, 130, 246, 0.05);
  border: 1px solid rgba(59, 130, 246, 0.1);
}

.stat-num {
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 4px;
}

.new .stat-num { color: #10b981; }
.update .stat-num { color: #3b82f6; }

.stat-desc {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.extract-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.extract-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.item-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.item-tag.new {
  background: #10b981;
  color: white;
}

.item-tag.update {
  background: #3b82f6;
  color: white;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  flex-grow: 1;
}

.report-footer-msg {
  padding: 20px 30px;
  background: #f8fafc;
}

:deep(.report-dialog .el-dialog__footer) {
  padding: 15px 30px 25px;
  border-top: 1px solid #f1f5f9;
}
/* Task Tracker Widget */
.task-tracker-widget {
  position: fixed;
  right: 40px;
  bottom: 40px;
  z-index: 2000;
}

.tracker-floating-btn {
  background: white;
  padding: 12px 20px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  border: 1px solid var(--primary-light);
  transition: all 0.3s;
}

.tracker-floating-btn:hover {
  transform: translateY(-4px) scale(1.05);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.btn-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.8; }
}

.popover-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--text-primary);
}

.task-list-mini {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-mini-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-header-mini {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.task-status-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cancel-task-btn {
  padding: 6px 12px;
  min-width: auto;
  height: 28px;
  border: 1px solid #fca5a5;
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  color: #dc2626;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(220, 38, 38, 0.1);
}

.cancel-task-btn:hover {
  background: linear-gradient(135deg, #fecaca 0%, #fca5a5 100%);
  color: #b91c1c;
  border-color: #f87171;
  box-shadow: 0 4px 8px rgba(220, 38, 38, 0.2);
  transform: translateY(-1px);
}

.cancel-task-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 4px rgba(220, 38, 38, 0.1);
}

.cancel-task-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: #f3f4f6;
  color: #9ca3af;
  border-color: #d1d5db;
  box-shadow: none;
}

.task-filename {
  font-size: 13px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}

.task-perc {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.task-perc.status_pending { color: #94a3b8; background: #f1f5f9; }
.task-perc.status_transcripting { color: #f59e0b; background: #fef3c7; }
.task-perc.status_extracting { color: #6366f1; background: #e0e7ff; }
.task-perc.status_scoring { color: #0ea5e9; background: #e0f2fe; }
.task-perc.status_success { color: #10b981; background: #dcfce7; }
.task-perc.status_failed { color: #ef4444; background: #fee2e2; }

/* Analysis Report Refined */
.report-container {
  padding: 10px 0;
}

.report-header-refined {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  border: 1px solid #e2e8f0;
}

.video-brief {
  display: flex;
  align-items: center;
  gap: 16px;
}

.pulse-icon {
  font-size: 40px;
  color: var(--primary-color);
  background: white;
  padding: 12px;
  border-radius: 14px;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);
}

.video-name-wrapper {
  display: flex;
  flex-direction: column;
}

.video-label {
  font-size: 12px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.video-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overall-metrics {
  display: flex;
  align-items: center;
  gap: 24px;
}

.metric-content {
  display: flex;
  flex-direction: column;
  line-height: 1;
}

.metric-val {
  font-size: 24px;
  font-weight: 800;
  color: var(--text-primary);
}

.metric-lab {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.metric-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.m-item {
  background: white;
  padding: 10px 16px;
  border-radius: 12px;
  border: 1px solid #edf2f7;
}

.m-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}

.m-val {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.m-lab {
  font-size: 11px;
  color: var(--text-secondary);
}

.incremental-section {
  background: white;
  border-radius: 12px;
}

.section-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  padding: 6px 16px;
  border-radius: 20px;
  margin-bottom: 16px;
}

.section-tag.new { color: #059669; background: #ecfdf5; }
.section-tag.update { color: #2563eb; background: #eff6ff; }
.section-tag.redundant { color: #64748b; background: #f1f5f9; }

.incremental-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.inc-card {
  padding: 14px;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
  transition: all 0.2s;
}

.inc-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
}

.inc-card.new { background: #fdfdfd; border-left: 4px solid #10b981; }
.inc-card.update { background: #fdfdfd; border-left: 4px solid #3b82f6; }
.inc-card.redundant { background: #fdfdfd; border-left: 4px solid #94a3b8; }

.inc-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: var(--text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.inc-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.inc-ts {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-insight-box {
  background: #f8fafc;
  padding: 20px;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
}

.insight-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}

.insight-text {
  font-size: 14px;
  line-height: 1.6;
  color: #64748b;
}

.report-footer {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 10px;
}

.glow-button {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  box-shadow: 0 4px 14px 0 rgba(79, 70, 229, 0.39);
}

.glow-button:hover {
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.45);
}

.auto-close-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
  padding: 8px;
  border-radius: 6px;
}

:deep(.task-tracker-popper) {
  border-radius: 16px !important;
  padding: 20px !important;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04) !important;
}

/* Add Point Dialog Styles */
.add-point-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
}

.video-management-dialog {
  .video-management-content {
    max-height: 500px;
    overflow-y: auto;
  }

  .el-table {
    border-radius: 8px;
    overflow: hidden;
  }

  .el-table th {
    background-color: #f7fafc;
    color: #4a5568;
    font-weight: 600;
  }

  .el-table td {
    padding: 12px 0;
  }
}

.video-segments-dialog :deep(.el-dialog__body) {
  padding: 16px 24px;
  background: var(--bg-color);
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 8px;
}

.segment-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--border-color-light);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);
}

.seg-header {
  margin-bottom: 12px;
}

.seg-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.seg-item-block {
  margin-top: 16px;
  padding-bottom: 16px;
  border-bottom: 1px dashed #e2e8f0;
}

.seg-item-block:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.seg-timestamps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.jump-tag {
  cursor: pointer;
  transition: all 0.2s;
  border-radius: 4px;
}

.jump-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 6px rgba(79, 70, 229, 0.2);
  color: var(--primary-color);
  border-color: var(--primary-light);
}

.seg-summary-box {
  background: var(--bg-color-light);
  border-radius: 8px;
  padding: 12px 16px;
  overflow: hidden;
  border: 1px solid var(--border-color-light);
}

.seg-summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  padding: 4px 0;
  user-select: none;
}

.seg-summary-header:hover .seg-summary-toggle {
  color: var(--primary-color);
}

.seg-summary-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.toggle-icon {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toggle-icon.is-expanded {
  transform: rotate(180deg);
}

.seg-summary-content-wrapper {
  margin-top: 12px;
  border-top: 1px dashed var(--border-color-light);
  padding-top: 8px;
}

.seg-summary-content {
  margin-top: 8px;
}

.seg-summary-box.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 14px;
  background: #f8fafc;
  padding: 24px;
  border: 1px dashed #cbd5e1;
}

.seg-summary-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 0;
}

:deep(.seg-md-preview) {
  background: transparent !important;
}
:deep(.seg-md-preview .md-editor-preview-wrapper) {
  padding: 0;
}
:deep(.seg-md-preview p) {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 4px 0;
}
:deep(.seg-md-preview ul) {
  margin: 4px 0;
  padding-left: 20px;
}
:deep(.seg-md-preview li) {
  font-size: 14px;
  color: var(--text-secondary);
}

.el-empty {
  padding: 40px 0;
}

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

.relation-group.linked-videos {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px dashed var(--border-color);
}

.video-jump-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.video-jump-item {
  background: var(--bg-color-light);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--border-color-light);
}

.video-jump-item .v-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.video-jump-item .v-times {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
</style>
