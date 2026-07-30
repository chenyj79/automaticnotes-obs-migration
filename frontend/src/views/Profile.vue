<template>
  <div class="profile-page-wrapper">
    <div class="profile-dashboard glass-panel">

      <!-- Top user summary header -->
      <div class="profile-header">
        <div class="avatar-container" @click="triggerAvatarUpload">
          <el-avatar
            :size="96"
            :src="profileForm.avatarUrl"
            class="user-avatar"
          />
          <div class="avatar-overlay">
            <el-icon v-if="uploadingAvatar"><Loading /></el-icon>
            <el-icon v-else><Camera /></el-icon>
          </div>
          <input
            type="file"
            ref="avatarInput"
            class="hidden-input"
            accept="image/*"
            @change="onAvatarFileChange"
          />
        </div>
        <div class="user-intro">
          <h2 class="user-name">{{ profileForm.username || '加载中...' }}</h2>
          <div class="user-badges">
            <el-tag :type="profileForm.role === 'ROLE_ADMIN' ? 'danger' : 'success'" round effect="light" class="role-tag">
              <el-icon class="el-icon--left">
                <Avatar v-if="profileForm.role === 'ROLE_ADMIN'" />
                <User v-else />
              </el-icon>
              {{ profileForm.role === 'ROLE_ADMIN' ? '超级管理员' : '普通用户' }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- Main Config Tabs -->
      <div class="profile-body">
        <el-tabs v-model="activeTab" class="custom-tabs" stretch>
          <!-- Tab 1: Basic Profile -->
          <el-tab-pane name="basic">
            <template #label>
              <span class="tab-label">
                <el-icon><Postcard /></el-icon>
                <span>基本资料</span>
              </span>
            </template>

            <div class="form-section">
              <el-form
                :model="profileForm"
                ref="profileFormRef"
                label-width="80px"
                label-position="top"
                v-loading="loading"
                size="large"
                class="settings-form"
              >
                <el-row :gutter="32">
                  <el-col :span="24">
                    <el-form-item label="登录账号 (不可修改)">
                      <el-input v-model="profileForm.username" disabled>
                        <template #prefix><el-icon><User /></el-icon></template>
                      </el-input>
                    </el-form-item>
                  </el-col>

                  <el-col :md="12" :sm="24">
                    <el-form-item label="联系邮箱">
                      <el-input v-model="profileForm.email" placeholder="example@domain.com">
                        <template #prefix><el-icon><Message /></el-icon></template>
                      </el-input>
                    </el-form-item>
                  </el-col>

                  <el-col :md="12" :sm="24">
                    <el-form-item label="手机号码">
                      <el-input v-model="profileForm.phone" placeholder="请输入11位手机号">
                        <template #prefix><el-icon><Phone /></el-icon></template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>

                <div class="form-actions">
                  <el-button type="primary" size="large" @click="handleUpdateProfile" :loading="updating" class="save-btn">
                    保存个人资料
                  </el-button>
                </div>
              </el-form>
            </div>
          </el-tab-pane>

          <!-- Tab 2: Security & Password -->
          <el-tab-pane name="security">
            <template #label>
              <span class="tab-label">
                <el-icon><Lock /></el-icon>
                <span>安全设置</span>
              </span>
            </template>

            <div class="form-section security-section">
              <div class="security-intro">
                <p>为了保障您的账号安全，定期修改密码是个好习惯。</p>
              </div>

              <el-form
                :model="passwordForm"
                :rules="passwordRules"
                ref="passwordFormRef"
                label-width="120px"
                label-position="top"
                size="large"
                class="settings-form"
              >
                <el-form-item label="当前原密码" prop="oldPassword">
                  <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码">
                    <template #prefix><el-icon><Key /></el-icon></template>
                  </el-input>
                </el-form-item>

                <el-form-item label="设置新密码" prop="newPassword">
                  <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="长度需至少为6个字符">
                    <template #prefix><el-icon><Warning /></el-icon></template>
                  </el-input>
                </el-form-item>

                <el-form-item label="确认新密码" prop="confirmPassword">
                  <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入以确认">
                    <template #prefix><el-icon><Check /></el-icon></template>
                  </el-input>
                </el-form-item>

                <div class="form-actions">
                  <el-button color="#ef4444" size="large" @click="handleChangePassword" :loading="changingPwd" class="change-pwd-btn text-white">
                    确认修改密码
                  </el-button>
                </div>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { User, Message, Phone, Lock, Postcard, Key, Warning, Check, Camera, Avatar } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { FormInstance } from 'element-plus';
import { getCurrentUserAPI, updateProfileAPI, changePasswordAPI, uploadAvatarAPI } from '@/api/user';
import type { UpdateProfileRequest, ChangePasswordRequest } from '@/api/user';

import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const activeTab = ref('basic');
const loading = ref(false);
const updating = ref(false);
const changingPwd = ref(false);
const uploadingAvatar = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);

const profileFormRef = ref<FormInstance>();
const passwordFormRef = ref<FormInstance>();

const profileForm = reactive({
  username: '',
  email: '',
  phone: '',
  role: '',
  avatarUrl: ''
});

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const checkConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'));
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致!'));
  } else {
    callback();
  }
};

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: checkConfirmPassword, trigger: 'blur' }
  ]
};

const fetchUserProfile = async () => {
  loading.value = true;
  try {
    const res = await getCurrentUserAPI();
    if (res.data) {
      profileForm.username = res.data.username || '';
      profileForm.email = res.data.email || '';
      profileForm.phone = res.data.phone || '';
      profileForm.role = res.data.role || 'ROLE_USER';
      profileForm.avatarUrl = res.data.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

      localStorage.setItem('user', JSON.stringify(res.data));
    }
  } catch (error) {
    console.error('Failed to load profile', error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchUserProfile();
});

const handleUpdateProfile = async () => {
  if (!profileFormRef.value) return;
  updating.value = true;
  try {
    const req: UpdateProfileRequest = {
      email: profileForm.email,
      phone: profileForm.phone
    };
    const res = await updateProfileAPI(req);
    if (res.success) {
      ElMessage.success('个人资料保存成功！');
      if (res.data) {
        localStorage.setItem('user', JSON.stringify(res.data));
      }
    }
  } catch (error) {
    console.error(error);
  } finally {
    updating.value = false;
  }
};

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return;
  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      changingPwd.value = true;
      try {
        const req: ChangePasswordRequest = {
          oldPassword: passwordForm.oldPassword,
          newPassword: passwordForm.newPassword
        };
        const res = await changePasswordAPI(req);
        if (res.success) {
          ElMessage.success('安全凭证更新成功，须重新登录');
          passwordFormRef.value?.resetFields();
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          setTimeout(() => {
             router.push('/login');
          }, 1500);
        }
      } catch (error) {
        console.error(error);
      } finally {
        changingPwd.value = false;
      }
    }
  });
};

const triggerAvatarUpload = () => {
    if (uploadingAvatar.value) return;
    avatarInput.value?.click();
};

const onAvatarFileChange = async (event: Event) => {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    if (!file) return;

    // Validate image
    if (!file.type.startsWith('image/')) {
        ElMessage.error('请选择图片文件');
        return;
    }
    if (file.size > 2 * 1024 * 1024) {
        ElMessage.error('图片大小不能超过 2MB');
        return;
    }

    uploadingAvatar.value = true;
    try {
        const res = await uploadAvatarAPI(file);
        if (res.success && res.data) {
            profileForm.avatarUrl = res.data;
            const updatedUser = { ...userStore.user, avatarUrl: res.data } as any;
            userStore.setUser(updatedUser);
            ElMessage.success('头像更新成功');
        }
    } catch (error) {
        console.error('Avatar upload failed:', error);
    } finally {
        uploadingAvatar.value = false;
        if (avatarInput.value) {
            avatarInput.value.value = ''; // Reset input
        }
    }
};
</script>

<style scoped>
.profile-page-wrapper {
  padding: 32px 16px;
  min-height: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.profile-dashboard {
  width: 100%;
  max-width: 720px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: transform 0.3s;
}

/* User Header Banner */
.profile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 24px 24px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08) 0%, rgba(139, 92, 246, 0.05) 100%);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.avatar-container {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  padding: 4px;
  background: white;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
  margin-bottom: 16px;
}

.user-avatar {
  display: block;
}

.avatar-overlay {
  position: absolute;
  top: 4px;
  left: 4px;
  right: 4px;
  bottom: 4px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-container:hover .avatar-overlay {
  opacity: 1;
}

.user-intro {
  text-align: center;
}

.hidden-input {
  display: none;
}

.user-name {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.user-badges {
  display: flex;
  justify-content: center;
}

.role-tag {
  font-weight: 600;
  border: none;
  padding: 0 16px;
  height: 28px;
}

/* Tabs & Forms */
.profile-body {
  padding: 8px 24px 32px;
}

.custom-tabs {
  --el-tabs-header-height: 60px;
}

.custom-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  color: var(--text-secondary);
  transition: color 0.3s;
}

.custom-tabs :deep(.el-tabs__item.is-active) {
  color: var(--primary-color);
  font-weight: 600;
}

.custom-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px;
  background-color: var(--primary-color);
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-section {
  padding: 24px 16px 8px;
}

.settings-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--text-primary);
  padding-bottom: 8px;
}

.settings-form :deep(.el-input__wrapper) {
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  background-color: #f9fafb;
}

.settings-form :deep(.el-input__wrapper.is-focus) {
  background-color: #fff;
  box-shadow: 0 0 0 1px var(--primary-color) inset;
}

.form-actions {
  margin-top: 32px;
  display: flex;
  justify-content: flex-end;
}

.save-btn {
  padding: 0 32px;
  border-radius: 8px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.3);
  transition: transform 0.2s, box-shadow 0.2s;
}

.save-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.4);
}

.change-pwd-btn {
  padding: 0 32px;
  border-radius: 8px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(239, 68, 68, 0.2);
}

.security-intro {
  margin-bottom: 24px;
  padding: 16px;
  background: rgba(243, 244, 246, 0.8);
  border-radius: 12px;
  color: var(--text-secondary);
  font-size: 14px;
  display: flex;
  align-items: center;
  border-left: 4px solid var(--text-secondary);
}

.security-intro p {
  margin: 0;
}
</style>
