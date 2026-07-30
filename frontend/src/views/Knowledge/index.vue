<template>
  <div class="knowledge-container">
    <div class="header-section glass-panel">
      <div class="header-content">
        <h2 class="page-title">知识库框架管理</h2>
        <p class="subtitle">管理由 AI 分析视频生成的结构化知识图谱，构建您的专属智能记忆库。</p>
      </div>
      <el-button type="primary" size="large" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 创建新框架
      </el-button>
    </div>

    <div class="framework-grid">
      <el-skeleton :rows="5" animated v-if="loading" />
      <el-empty
        description="暂无知识框架，上传视频自动生成或手动创建"
        v-else-if="frameworks.length === 0"
        class="glass-panel empty-state"
      >
        <el-button type="primary" @click="openCreateDialog">手动创建知识库</el-button>
      </el-empty>

      <div
        v-else
        class="framework-card glass-panel"
        v-for="item in frameworks"
        :key="item.id"
        @click="goToDetail(item.id)"
      >
        <div class="card-header">
          <div class="fw-title">{{ item.name }}</div>
          <span class="subject-tag">{{ item.subject || '综合学科' }}</span>
        </div>
        <div class="card-body">
          <p class="fw-desc">{{ item.description || '暂无描述' }}</p>
        </div>
        <div class="card-footer">
          <div class="meta-item">
            <el-icon><Calendar /></el-icon>
            <span>{{ new Date(item.updateTime).toLocaleDateString() }} 更新</span>
          </div>
          <el-button link type="danger" @click.stop="confirmDelete(item.id)"><el-icon><Delete /></el-icon></el-button>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div class="pagination-wrapper" v-if="totalElements > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 36]"
        layout="total, sizes, prev, pager, next"
        :total="totalElements"
        @size-change="fetchFrameworks"
        @current-change="fetchFrameworks"
      />
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" :title="createStep === 1 ? '创建新知识框架' : '自定义分类'" width="640px">
      <el-steps :active="createStep - 1" align-center finish-status="success" class="create-steps">
        <el-step title="框架信息" />
        <el-step title="分类设置" />
      </el-steps>

      <el-form v-if="createStep === 1" :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入知识框架名称 (例如：Java Web 全栈核心)" />
        </el-form-item>
        <el-form-item label="学科分类" prop="subject">
          <el-input v-model="form.subject" placeholder="例如：软件工程" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述该知识框架的核心内容或应用场景"
          />
        </el-form-item>
      </el-form>

      <div v-else class="category-step">
        <div class="category-toolbar">
          <el-button type="primary" plain @click="handleAiSuggest" :loading="aiFilling">AI 自动补充</el-button>
          <el-button @click="addCategoryRow"><el-icon><Plus /></el-icon> 添加分类</el-button>
        </div>

        <div v-for="(item, idx) in categoryDraft" :key="idx" class="category-row">
          <el-input v-model="item.name" placeholder="分类名称" maxlength="64" class="category-name" />
          <el-input v-model="item.definition" placeholder="分类定义" type="textarea" :rows="2" class="category-definition" />
          <el-button type="danger" plain @click="removeCategoryRow(idx)" :disabled="categoryDraft.length <= 1">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeCreateDialog">取消</el-button>
          <el-button v-if="createStep === 2" @click="createStep = 1">上一步</el-button>
          <el-button v-if="createStep === 1" type="primary" @click="goNextStep">下一步</el-button>
          <el-button v-else type="primary" :loading="creating" @click="submitCreate">确认创建</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Plus, Calendar, Delete } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance } from 'element-plus';
import { getMyFrameworksAPI, createFrameworkAPI, deleteFrameworkAPI, suggestFrameworkCategoriesAPI } from '@/api/knowledge';
import type { KnowledgeFrameworkVo, CreateFrameworkRequest, FrameworkCategoryVo } from '@/types';

const router = useRouter();
const loading = ref(true);
const frameworks = ref<KnowledgeFrameworkVo[]>([]);

// Pagination
const currentPage = ref(1);
const pageSize = ref(12);
const totalElements = ref(0);

// Dialog State
const showCreateDialog = ref(false);
const creating = ref(false);
const aiFilling = ref(false);
const createStep = ref(1);
const formRef = ref<FormInstance>();
const defaultCategory = (): FrameworkCategoryVo => ({ name: '其他', definition: '所有的内容都划分到这里' });
const categoryDraft = ref<FrameworkCategoryVo[]>([defaultCategory()]);
const form = ref<CreateFrameworkRequest>({
  name: '',
  subject: '',
  description: ''
});

const rules = {
  name: [
    { required: true, message: '请输入知识框架名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  subject: [
    { required: true, message: '请输入学科分类', trigger: 'blur' }
  ]
};

const fetchFrameworks = async () => {
  loading.value = true;
  try {
    const res = await getMyFrameworksAPI({ page: currentPage.value - 1, size: pageSize.value });
    if (res.data) {
      frameworks.value = res.data.content;
      totalElements.value = res.data.totalElements;
    }
  } catch (error) {
    console.error('Failed to fetch knowledge frameworks', error);
  } finally {
    loading.value = false;
  }
};

const goToDetail = (id: number | string) => {
  router.push(`/knowledge/${id}`);
};

const resetCreateState = () => {
  createStep.value = 1;
  categoryDraft.value = [defaultCategory()];
  form.value = { name: '', subject: '', description: '' };
  formRef.value?.clearValidate();
};

const openCreateDialog = () => {
  resetCreateState();
  showCreateDialog.value = true;
};

const closeCreateDialog = () => {
  showCreateDialog.value = false;
};

const goNextStep = async () => {
  if (!formRef.value) return;
  await formRef.value.validate((valid) => {
    if (!valid) return;
    createStep.value = 2;
  });
};

const addCategoryRow = () => {
  categoryDraft.value.push({ name: '', definition: '' });
};

const removeCategoryRow = (index: number) => {
  categoryDraft.value.splice(index, 1);
};

const normalizeCategories = (categories: FrameworkCategoryVo[]): FrameworkCategoryVo[] => {
  const dedup = new Map<string, FrameworkCategoryVo>();
  categories.forEach((item) => {
    const name = (item.name || '').trim();
    const definition = (item.definition || '').trim();
    if (!name || !definition) return;
    const key = name.toLowerCase();
    if (!dedup.has(key)) {
      dedup.set(key, { name, definition });
    }
  });
  return Array.from(dedup.values());
};

const validateCategories = (): boolean => {
  const normalized = normalizeCategories(categoryDraft.value);
  categoryDraft.value = normalized;
  return true;
};

const handleAiSuggest = async () => {
  if (!form.value.name.trim()) {
    ElMessage.warning('请先在上一步填写框架名称');
    return;
  }
  aiFilling.value = true;
  try {
    const res = await suggestFrameworkCategoriesAPI({
      name: form.value.name,
      subject: form.value.subject || '',
      description: form.value.description || ''
    });
    if (res.data) {
      categoryDraft.value = normalizeCategories(res.data);
      ElMessage.success('AI 已自动补充分类');
    }
  } catch (error) {
    ElMessage.error('AI 补充失败，请稍后重试');
  } finally {
    aiFilling.value = false;
  }
};

const submitCreate = async () => {
  if (!validateCategories()) return;

  creating.value = true;
  try {
    await createFrameworkAPI({
      ...form.value,
      categories: normalizeCategories(categoryDraft.value)
    });
    ElMessage.success('知识框架创建成功');
    showCreateDialog.value = false;
    fetchFrameworks();
  } catch (error) {
    ElMessage.error('创建失败，请重试');
  } finally {
    creating.value = false;
  }
};

const confirmDelete = (id: number) => {
  ElMessageBox.confirm(
    '删除该知识框架将同步删除其包含的所有知识点及增量笔记，是否继续？',
    '危险操作警告',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await deleteFrameworkAPI(id);
      ElMessage.success('删除成功');
      fetchFrameworks();
    } catch (error) {
      ElMessage.error('删除失败');
    }
  }).catch(() => {});
};

onMounted(() => {
  fetchFrameworks();
});
</script>

<style scoped>
.knowledge-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  height: 100%;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32px;
  border-radius: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 15px;
}

.framework-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 64px 0;
}

.framework-card {
  padding: 24px;
  border-radius: 16px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  border: 1px solid transparent;
}

.framework-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 20px -8px rgba(79, 70, 229, 0.15);
  border-color: rgba(79, 70, 229, 0.2);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}

.fw-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
  flex-grow: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.subject-tag {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 6px;
  background: rgba(79, 70, 229, 0.08);
  color: var(--primary-color);
  line-height: 1.2;
  flex-shrink: 0;
}

.card-body {
  flex-grow: 1;
  margin-bottom: 24px;
}

.fw-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #94a3b8;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 24px;
}

.create-steps {
  margin-bottom: 16px;
}

.category-step {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.category-row {
  display: grid;
  grid-template-columns: 160px 1fr auto;
  gap: 8px;
  align-items: start;
}
</style>
