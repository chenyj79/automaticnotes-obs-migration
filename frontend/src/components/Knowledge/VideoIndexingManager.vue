<template>
  <div class="video-indexing-manager">
    <div v-for="(vidRef, vIdx) in videoRefs" :key="vIdx" class="video-ref-card glass-card">
      <div class="video-ref-header">
        <div class="video-info">
          <el-icon class="v-icon">
            <VideoCamera/>
          </el-icon>
          <span class="v-title">{{ getVideoTitle(vidRef.videoId) }}</span>
        </div>
        <el-button type="danger" link @click="removeVideo(vIdx)">
          <el-icon>
            <Delete/>
          </el-icon>
        </el-button>
      </div>

      <div class="segments-list">
        <div v-for="(seg, sIdx) in vidRef.segments" :key="sIdx" class="segment-row">
          <div class="form-row">
            <div class="time-range-column" v-if="allVideos.length > 0">
              <el-time-picker
                :model-value="[seg.startStr, seg.endStr]"
                is-range
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                size="small"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                class="time-range-picker"
                :clearable=false
                :disabled-hours="() => getDisabledHours(vidRef.videoId)"
                :disabled-minutes="buildDisabledMinutes(vidRef.videoId)"
                :disabled-seconds="buildDisabledSeconds(vidRef.videoId)"
                @update:model-value="onRangeUpdate(seg, $event)"
              />
            </div>
            <el-button size="small" circle @click="removeSegment(vIdx, sIdx)" class="remove-seg">
              <el-icon>
                <Close/>
              </el-icon>
            </el-button>
          </div>
        </div>

        <el-button type="primary" link @click="addSegment(vIdx)" class="add-seg-link">
          <el-icon>
            <Plus/>
          </el-icon>
          添加时间片段
        </el-button>
      </div>
    </div>

    <div class="manager-footer">
      <el-dropdown trigger="click" @command="addVideo" v-if="availableVideos.length > 0">
        <el-button type="success" size="small" class="add-video-btn">
          <el-icon>
            <Plus/>
          </el-icon>
          关联新视频
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="v in availableVideos"
              :key="v.id"
              :command="v.id"
            >
              {{ v.originalFileName }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <div v-else class="no-videos-hint">
        暂无更多可选视频
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {VideoCamera, Delete, Plus, Close} from '@element-plus/icons-vue';
import type {VideoVo} from '@/types';

interface Segment {
  startStr: string;
  endStr: string;
}

interface VideoRef {
  videoId: number;
  segments: Segment[];
}

const props = defineProps<{
  videoRefs: VideoRef[];
  allVideos: VideoVo[];
}>();

const emit = defineEmits(['update:videoRefs']);

const availableVideos = computed(() => {
  const selectedIds = new Set(props.videoRefs.map(r => r.videoId));
  return props.allVideos.filter(v => !selectedIds.has(v.id));
});

const getVideoTitle = (id: number) => {
  const v = props.allVideos.find(v => v.id === id);
  if (!v) return '未知视频';
  return v.originalFileName || '无标题视频';
};


const formatSecondsToTime = (seconds: number): string => {
  const h = Math.floor(seconds / 3600).toString().padStart(2, '0');
  const m = Math.floor((seconds % 3600) / 60).toString().padStart(2, '0');
  const s = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
};

const handleRangeChange = (seg: Segment, val: [string, string] | null) => {
  if (val && val.length === 2) {
    seg.startStr = val[0];
    seg.endStr = val[1];
  }
};

const onRangeUpdate = (seg: Segment, val: string[] | null) => {
  if (!val || val.length < 2) return;
  handleRangeChange(seg, [val[0] ?? '00:00:00', val[1] ?? '00:00:00']);
};

const addVideo = (videoId: number) => {
  const duration = getDuration(videoId);
  const endStr = formatSecondsToTime(duration);
  const newRefs = [...props.videoRefs, {
    videoId,
    segments: [{startStr: '00:00:00', endStr}]
  }];
  emit('update:videoRefs', newRefs);
};

const removeVideo = (index: number) => {
  const newRefs = [...props.videoRefs];
  newRefs.splice(index, 1);
  emit('update:videoRefs', newRefs);
};

const addSegment = (vIdx: number) => {
  const ref = props.videoRefs[vIdx];
  if (!ref) return;
  const duration = getDuration(ref.videoId);
  const endStr = formatSecondsToTime(duration);
  ref.segments.push({startStr: '00:00:00', endStr});
};

const removeSegment = (vIdx: number, sIdx: number) => {
  const ref = props.videoRefs[vIdx];
  if (!ref) return;
  ref.segments.splice(sIdx, 1);
  if (ref.segments.length === 0) {
    removeVideo(vIdx);
  }
};

const buildDisabledMinutes = (videoId: number) => {
  return (hour: number) => getDisabledMinutes(videoId, hour);
};

const buildDisabledSeconds = (videoId: number) => {
  return (hour: number, minute: number) => getDisabledSeconds(videoId, hour, minute);
};

const getDuration = (videoId: number) => {
  const v = props.allVideos.find(v => v.id === videoId);
  return v?.duration || 0;
};

const getDisabledHours = (videoId: number) => {
  const durationSecs = getDuration(videoId);
  const maxHours = Math.floor(durationSecs / 3600);
  const disabled = [];
  for (let i = maxHours + 1; i < 24; i++) {
    disabled.push(i);
  }
  return disabled;
};

const getDisabledMinutes = (videoId: number, hour: number) => {
  const durationSecs = getDuration(videoId);
  const maxHours = Math.floor(durationSecs / 3600);
  const maxMins = Math.floor((durationSecs % 3600) / 60);

  if (hour < maxHours) return [];
  if (hour > maxHours) {
    const all = [];
    for (let i = 0; i < 60; i++) all.push(i);
    return all;
  }

  const disabled = [];
  for (let i = maxMins + 1; i < 60; i++) {
    disabled.push(i);
  }
  return disabled;
};

const getDisabledSeconds = (videoId: number, hour: number, minute: number) => {
  const durationSecs = getDuration(videoId);
  const maxHours = Math.floor(durationSecs / 3600);
  const maxMins = Math.floor((durationSecs % 3600) / 60);
  const maxSecs = Math.floor(durationSecs % 60);

  if (hour < maxHours || (hour === maxHours && minute < maxMins)) return [];
  if (hour > maxHours || (hour === maxHours && minute > maxMins)) {
    const all = [];
    for (let i = 0; i < 60; i++) all.push(i);
    return all;
  }

  const disabled = [];
  for (let i = maxSecs + 1; i < 60; i++) {
    disabled.push(i);
  }
  return disabled;
};
</script>

<style scoped>
.video-indexing-manager {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.video-ref-card {
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.video-ref-card:hover {
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.video-ref-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.video-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.v-icon {
  color: var(--el-color-primary);
}

.v-title {
  font-size: 14px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 20px;
}

.segment-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.time-range-column {
  flex-grow: 1;
  max-width: 320px;
}

.time-range-picker {
  width: 100% !important;
}

.time-range-picker :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.6) !important;
  box-shadow: 1px solid rgba(0, 0, 0, 0.05) !important;
  border-radius: 8px;
}

.separator {
  color: #94a3b8;
  font-size: 12px;
  margin: 0 2px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 38px;
}

.remove-seg {
  border: none;
  background: rgba(0, 0, 0, 0.05);
  color: #94a3b8;
}

.remove-seg:hover {
  background: rgba(0, 0, 0, 0.1);
  color: var(--el-color-danger);
}

.add-seg-link {
  font-size: 13px;
  padding: 0;
  height: auto;
  margin-top: 8px;
  margin-left: -24px; /* Pull back to align with video title/icon (which are at root of 12px padding card) */
  opacity: 0.8;
}

.add-seg-link:hover {
  opacity: 1;
}

.manager-footer {
  margin-top: 12px;
  display: flex;
  justify-content: flex-start;
  padding-left: 12px; /* Standardize with card content start (12px) */
}

.add-video-btn {
  border-radius: 20px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2);
}

.no-videos-hint {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 8px;
}

.glass-card {
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
</style>
