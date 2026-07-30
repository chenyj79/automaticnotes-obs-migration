<template>
  <div class="relation-manager">
    <transition-group name="list" tag="div" class="relations-container">
      <template v-for="node in flattenedNodes" :key="node.key">
        <!-- 分组标题节点 -->
        <div v-if="node.type === 'title'" class="group-title">
          <el-icon><component :is="node.icon" /></el-icon>
          {{ node.title }}
        </div>

        <!-- 关系行节点 -->
        <div v-else class="relation-row glass-row">
          <div class="rel-info" v-if="node.displayInfo">
            <el-tag :type="node.displayInfo.tagType" effect="light" class="rel-type-tag">
              {{ node.displayInfo.label }}
            </el-tag>
            <el-icon class="rel-arrow">
              <component :is="node.displayInfo.icon" />
            </el-icon>
            <span class="rel-target-name">{{ getTargetName(node.rel!) }}</span>
          </div>
          <el-button type="danger" link @click="removeRelation(node.originalIndex!)" class="delete-btn">
            <el-icon>
              <Delete/>
            </el-icon>
          </el-button>
        </div>
      </template>
    </transition-group>

    <div class="add-relation-form glass-card" v-if="isAdding">
      <div class="form-row">
        <el-select v-model="newRel.relationType" placeholder="选择关系" style="width: 110px"
                   size="small">
          <el-option label="包含" :value="RelationType.CONTAINS"/>
          <el-option label="前置" :value="RelationType.PREREQUISITE"/>
          <el-option label="相关" :value="RelationType.RELATED"/>
          <el-option label="拓展" :value="RelationType.EXTENSION"/>
        </el-select>
        <el-icon class="rel-arrow">
          <Right/>
        </el-icon>
        <el-select
          v-model="newRel.targetPointId"
          placeholder="搜索目标知识点并添加"
          filterable
          style="flex-grow: 1"
          size="small"
          @change="confirmAdd"
        >
          <el-option
            v-for="p in availablePoints"
            :key="p.id"
            :label="p.title"
            :value="p.id"
            :disabled="p.id === currentPointId || isTargetDisabled(p.id, newRel.relationType)"
          />
        </el-select>
        <el-button size="small" circle @click="isAdding = false" class="close-form-btn">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <el-button v-else type="primary" link @click="isAdding = true" class="add-rel-btn">
      <el-icon>
        <Plus/>
      </el-icon>
      建立知识关联
    </el-button>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, reactive} from 'vue';
import {
  Delete,
  Plus,
  Right,
  Back,
  Close,
  Connection,
  Folder,
  TopRight,
  Files
} from '@element-plus/icons-vue';
import {RelationType} from '@/types';
import type {KnowledgePointVo, KnowledgeRelationVo} from '@/types';

interface Relation {
  sourcePointId: number;
  targetPointId: number;
  relationType: RelationType;
}

const props = defineProps<{
  relations: Relation[];
  allPoints: KnowledgePointVo[];
  allRelations: KnowledgeRelationVo[];
  currentPointId?: number; // Optional for new points
  contextParentId?: number | null; // From Add Dialog
}>();

const emit = defineEmits(['update:relations', 'on-add', 'on-remove']);

const isAdding = ref(false);
const newRel = reactive({
  relationType: RelationType.RELATED,
  targetPointId: null as number | null
});

const availablePoints = computed(() => {
  return props.allPoints;
});

// Tree Topology Calculation
const topology = computed(() => {
  const hasParentSet = new Set<number>();
  const parentMap = new Map<number, number>();
  const linkedNodes = new Set<number>();

  // Helper to add a CONTAINS relation to the topology
  const addEntry = (sourceId: number, targetId: number) => {
    if (!hasParentSet.has(targetId)) {
      hasParentSet.add(targetId);
      parentMap.set(targetId, sourceId);
    }
  };

  // 1. Process all relations in the framework, but EXCLUDE those involving the currentPointId
  // because the local 'props.relations' will provide the authoritative state for the current node.
  props.allRelations.forEach(r => {
    if (r.relationType === RelationType.CONTAINS &&
        r.sourcePointId !== props.currentPointId &&
        r.targetPointId !== props.currentPointId) {
      addEntry(r.sourcePointId, r.targetPointId);
    }
  });

  // 2. Add local relations (this includes existing relations that were preserved AND new ones)
  props.relations.forEach(r => {
    if (r.relationType === RelationType.CONTAINS) {
      addEntry(r.sourcePointId, r.targetPointId);
    }

    // Track all nodes already linked to the current point (or the point being added)
    const currentId = props.currentPointId || 0;
    if (r.sourcePointId === currentId) linkedNodes.add(r.targetPointId);
    if (r.targetPointId === currentId) linkedNodes.add(r.sourcePointId);
  });

  // 3. Add context parent to the hierarchy (in Add mode)
  if (props.contextParentId) {
    linkedNodes.add(props.contextParentId);
    addEntry(props.contextParentId, props.currentPointId || 0)
  }

  return {hasParentSet, parentMap, linkedNodes};
});

const isAncestor = (ancestorId: number, childId: number): boolean => {
  let curr: number | undefined = childId;
  const map = topology.value.parentMap;
  const visited = new Set<number>(); // Multi-parent safety

  while (curr !== undefined && !visited.has(curr)) {
    if (curr === ancestorId) return true;
    visited.add(curr);
    curr = map.get(curr);
  }
  return false;
};

const isTargetDisabled = (targetId: number, relType: RelationType) => {
  // 0. Duo-relation prevention: Only one relationship allowed between two nodes
  if (topology.value.linkedNodes.has(targetId)) return true;

  // 1. Enforce strict tree structure for CONTAINS
  if (relType === RelationType.CONTAINS) {
    if (topology.value.hasParentSet.has(targetId)) return true;
    // 2. Prevent cycles: cannot add an ancestor as a child
    const currentId = props.currentPointId || 0;
    if (isAncestor(targetId, currentId)) return true;
  }

  return false;
};

const getRelDisplayInfo = (rel: Relation) => {
  const isTarget = rel.targetPointId === props.currentPointId;
  const type = rel.relationType;

  if (!isTarget) {
    let label = '';
    let tagType = '';
    let icon: any = Right;
    switch (type) {
      case RelationType.CONTAINS:
        label = '包含';
        tagType = 'danger';
        icon = Files;
        break;
      case RelationType.PREREQUISITE:
        label = '前置';
        tagType = 'warning';
        icon = Back;
        break;
      case RelationType.RELATED:
        label = '相关';
        tagType = 'info';
        icon = Connection;
        break;
      default:
        label = '关联';
        tagType = 'info';
        icon = Right;
    }
    return { label, tagType, icon };
  } else {
    let label = '';
    let tagType = '';
    let icon: any = Right;
    switch (type) {
      case RelationType.CONTAINS:
        label = '属于';
        tagType = 'primary';
        icon = TopRight;
        break;
      case RelationType.PREREQUISITE:
        label = '拓展';
        tagType = 'success';
        icon = Right;
        break;
      case RelationType.RELATED:
        label = '相关';
        tagType = 'info';
        icon = Connection;
        break;
      default:
        tagType = 'info';
        label = '关联';
        icon = Right;
    }
    return { label, tagType, icon };
  }
};

const getTargetName = (rel: Relation) => {
  const targetId = rel.targetPointId === props.currentPointId ? rel.sourcePointId : rel.targetPointId;
  const p = props.allPoints.find(p => p.id === targetId);
  return p ? p.title : '未知节点';
};

const removeRelation = (index: number) => {
  const removed = props.relations[index];
  const newRels = [...props.relations];
  newRels.splice(index, 1);
  emit('update:relations', newRels);
  emit('on-remove', removed);
};

const confirmAdd = () => {
  if (!newRel.targetPointId) return;

  const type = newRel.relationType;
  let added: Relation;

  if (type === RelationType.EXTENSION) {
    added = {
      sourcePointId: newRel.targetPointId,
      targetPointId: props.currentPointId || 0,
      relationType: RelationType.PREREQUISITE
    };
  } else {
    added = {
      sourcePointId: props.currentPointId || 0, // 0 means new point, parent will handle
      targetPointId: newRel.targetPointId,
      relationType: type
    };
  }

  const newRels = [...props.relations, added];
  emit('update:relations', newRels);
  emit('on-add', added);

  newRel.targetPointId = null;
  isAdding.value = false;
};

const flattenedNodes = computed(() => {
  const categories = [
    { name: 'contains', title: '层级结构', icon: Folder },
    { name: 'prerequisite', title: '前置基础', icon: Back },
    { name: 'related', title: '相关知识', icon: Connection },
    { name: 'extension', title: '拓展延伸', icon: Right }
  ];

  const result: any[] = [];

  categories.forEach(cat => {
    // 找出该分类下的所有关系
    const itemsInCategory = props.relations.map((rel, index) => {
      const isTarget = rel.targetPointId === props.currentPointId;
      let matched = false;

      if (cat.name === 'contains') {
        matched = rel.relationType === RelationType.CONTAINS;
      } else if (cat.name === 'related') {
        matched = rel.relationType === RelationType.RELATED;
      } else if (cat.name === 'prerequisite') {
        matched = rel.relationType === RelationType.PREREQUISITE && !isTarget;
      } else if (cat.name === 'extension') {
        matched = rel.relationType === RelationType.PREREQUISITE && isTarget;
      }

      if (matched) {
        return {
          type: 'item',
          key: `item-${rel.sourcePointId}-${rel.targetPointId}-${rel.relationType}`,
          originalIndex: index,
          rel,
          displayInfo: getRelDisplayInfo(rel)
        };
      }
      return null;
    }).filter(i => i !== null);

    if (itemsInCategory.length > 0) {
      // 添加标题节点
      result.push({
        type: 'title',
        key: `title-${cat.name}`,
        title: cat.title,
        icon: cat.icon
      });
      // 添加内容节点
      result.push(...itemsInCategory);
    }
  });

  return result;
});
</script>

<style scoped>
.relation-manager {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.relations-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #64748b);
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 4px;
  margin-top: 8px; /* 给分组之间留点间距 */
}

.group-title:first-child {
  margin-top: 0;
}

.relation-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.relation-row:hover {
  border-color: #e2e8f0;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  transform: translateY(-1px);
}

.rel-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rel-type-tag {
  font-size: 12px;
  padding: 0 10px;
  height: 24px;
  line-height: 24px;
  border-radius: 5px;
  font-weight: 500;
  border-color: transparent;
}

.rel-arrow {
  font-size: 14px;
  color: #94a3b8;
  display: flex;
  align-items: center;
}

.rel-target-name {
  font-size: 14px;
  color: #334155;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.delete-btn {
  opacity: 0.6;
  transition: opacity 0.2s;
}

.relation-row:hover .delete-btn {
  opacity: 1;
}

.add-relation-form {
  padding: 12px;
  border-radius: 12px;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid #f1f5f9;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.close-form-btn {
  border: none;
  background: rgba(0, 0, 0, 0.05);
  color: #94a3b8;
}

.close-form-btn:hover {
  background: rgba(0, 0, 0, 0.1);
  color: var(--el-color-danger);
}

.add-rel-btn {
  font-size: 13px;
  padding: 0 12px;
  height: auto;
  opacity: 0.8;
}

.add-rel-btn:hover {
  opacity: 1;
}

.glass-row {
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
}

.list-move,
.list-enter-active,
.list-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}
.list-leave-active {
  position: absolute;
  width: 100%;
}
</style>
