<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="aside-menu glass-panel">
      <div class="logo-box">
        <el-icon :size="28" color="var(--primary-color)"><Monitor /></el-icon>
        <span class="logo-text">AutoNotes 智能笔记</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical"
        router
        background-color="transparent"
        text-color="var(--text-secondary)"
        active-text-color="var(--primary-color)"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Reading /></el-icon>
          <span>知识管理</span>
        </el-menu-item>
        <el-menu-item index="/drafts">
          <el-icon><Message /></el-icon>
          <span>草稿审核</span>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon>
          <span>全文检索</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="main-wrapper">
      <el-header class="header glass-panel">
        <div class="breadcrumb-container">
          <h2 class="page-title">{{ pageTitle }}</h2>
        </div>
        <div class="user-container">
          <el-dropdown @command="handleCommand">
            <span class="el-dropdown-link user-info">
              <el-avatar :size="32" :src=userAvatar />
              <span class="username">{{ username }}</span>
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Monitor, DataLine, ArrowDown, Reading, Message, Search } from '@element-plus/icons-vue';

import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const activeMenu = computed(() => {
  if (route.path.includes('/video/')) {
    return '/dashboard'; // highlight dashboard when in video detail
  }
  return route.path;
});

const pageTitle = computed(() => {
  switch (route.name) {
    case 'dashboard': return '仪表盘';
    case 'videoDetail': return '视频详情与笔记';
    case 'profile': return '个人中心';
    case 'knowledge': return '知识管理';
    case 'frameworkDetail': return '知识框架详情';
    case 'drafts': return '草稿审核';
    case 'draftDetail': return '草稿详情';
    case 'search': return '全文检索';
    default: return 'AutoNotes 智能笔记';
  }
});

const username = computed(() => userStore.user?.username || '用户');

const userAvatar = computed(() => userStore.user?.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png');

const handleCommand = (command: string) => {
  if (command === 'logout') {
    userStore.clearUser();
    router.push('/login');
  } else if (command === 'profile') {
    router.push('/profile');
  }
};
</script>

<style scoped>
.layout-container {
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-color);
  background-image: radial-gradient(at 0% 0%, hsla(253,16%,7%,0.03) 0, transparent 50%), radial-gradient(at 50% 0%, hsla(225,39%,30%,0.03) 0, transparent 50%), radial-gradient(at 100% 0%, hsla(339,49%,30%,0.03) 0, transparent 50%);
}

.aside-menu {
  margin: 16px;
  border-radius: 16px;
  border-right: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo-box {
  height: var(--header-h);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-weight: 700;
  font-size: 19px;
  color: var(--text-primary);
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  margin-bottom: 20px;
}

.logo-text {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.el-menu-vertical {
  border-right: none;
}

.el-menu-item {
  margin: 0 12px 8px 12px;
  border-radius: 8px;
  height: 48px;
  line-height: 48px;
}

.el-menu-item.is-active {
  background-color: rgba(79, 70, 229, 0.1) !important;
  font-weight: 600;
}

.main-wrapper {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 16px 16px 0;
}

.header {
  height: var(--header-h);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.user-container {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px 4px 4px;
  border-radius: 20px;
  transition: background 0.2s;
}

.user-info:hover {
  background: rgba(0, 0, 0, 0.05);
}

.username {
  font-weight: 500;
  color: var(--text-primary);
}

.main-content {
  padding: 0;
  overflow-y: auto;
  border-radius: 16px;
  height: calc(100vh - var(--header-h) - 48px);
}

/* Transitions */
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
