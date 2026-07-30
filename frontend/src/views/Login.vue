<template>
  <div class="login-container">
    <div class="glass-panel login-box">
      <div class="login-header">
        <el-icon :size="40" color="var(--primary-color)"><Monitor /></el-icon>
        <h2>AutoNotes 智能笔记</h2>
        <p class="subtitle">AI驱动的教学视频知识分析引擎</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" size="large" class="login-form" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Lock" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
            {{ isLogin ? '登录' : '注册并登录' }}
          </el-button>
        </el-form-item>

        <div class="toggle-mode">
          <span class="text">{{ isLogin ? "还没有账号？" : "已有账号？" }}</span>
          <el-link type="primary" :underline="false" @click="toggleMode">
            {{ isLogin ? '立即注册' : '返回登录' }}
          </el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Lock, Monitor } from '@element-plus/icons-vue';
import { loginAPI, registerAPI } from '@/api/auth';
import { getCurrentUserAPI } from '@/api/user';
import type { FormInstance } from 'element-plus';
import type { LoginRequestData, RegisterRequestData } from '@/api/auth';

import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const isLogin = ref(true);
const loading = ref(false);

const form = reactive<LoginRequestData>({
  username: '',
  password: ''
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度至少为 6 位', trigger: 'blur' }
  ]
};

const toggleMode = () => {
  isLogin.value = !isLogin.value;
  formRef.value?.resetFields();
};

const handleLogin = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        let res;
        if (isLogin.value) {
          res = await loginAPI(form);
        } else {
          // Send a dummy email in case backend entity demands it, otherwise just user/pass
          const registerData: RegisterRequestData = { ...form, email: form.username + '@example.com' };
          res = await registerAPI(registerData);
          ElMessage.success('注册成功，请使用新账号登录');
          toggleMode();
          loading.value = false;
          return;
        }

        if (res.data) {
          localStorage.setItem('token', res.data.token);
          // Fetch full profile now that we have the token
          try {
            const userRes = await getCurrentUserAPI();
            if (userRes.data) {
              userStore.setUser(userRes.data);
            } else {
              userStore.setUser({ username: res.data.username, role: res.data.role } as any);
            }
          } catch (e) {
            userStore.setUser({ username: res.data.username, role: res.data.role } as any);
          }
          ElMessage.success('欢迎回来！');
          router.push('/dashboard');
        }
      } catch (error) {
        // Interceptor handles error messages
        console.error(error);
      } finally {
        loading.value = false;
      }
    }
  });
};
</script>

<style scoped>
.login-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-color);
  background-image: radial-gradient(at 100% 100%, hsla(253,16%,7%,0.05) 0, transparent 50%), radial-gradient(at 0% 0%, hsla(225,39%,30%,0.05) 0, transparent 50%);
}

.login-box {
  width: 100%;
  max-width: 420px;
  padding: 48px;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  flex-direction: column;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-header h2 {
  margin-top: 20px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.subtitle {
  color: var(--text-secondary);
  margin-top: 10px;
  font-size: 15px;
}

.login-form {
  margin-top: 12px;
}

.submit-btn {
  width: 100%;
  border-radius: 8px;
  margin-top: 12px;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.25);
}

.toggle-mode {
  text-align: center;
  margin-top: 12px;
  font-size: 14px;
}

.toggle-mode .text {
  color: var(--text-secondary);
  margin-right: 6px;
}
</style>
