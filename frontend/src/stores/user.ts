import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { UserVo } from '@/types';

export const useUserStore = defineStore('user', () => {
    const user = ref<UserVo | null>(null);

    // Initialize from localStorage
    const storedUser = localStorage.getItem('user');
    if (storedUser) {
        try {
            user.value = JSON.parse(storedUser);
        } catch (e) {
            console.error('Failed to parse stored user:', e);
            localStorage.removeItem('user');
        }
    }

    const setUser = (userData: UserVo) => {
        user.value = userData;
        localStorage.setItem('user', JSON.stringify(userData));
    };

    const clearUser = () => {
        user.value = null;
        localStorage.removeItem('user');
        localStorage.removeItem('token');
    };

    return {
        user,
        setUser,
        clearUser
    };
});
