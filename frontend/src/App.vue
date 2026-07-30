<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <el-config-provider>
    <RouterView />
  </el-config-provider>
</template>

<style>
/* Global styles can be added here if needed, but we rely on main.css */
</style>
