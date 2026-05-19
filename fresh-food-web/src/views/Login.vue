<template>
  <main class="login-page">
    <section class="login-panel" aria-label="登录">
      <div class="brand-mark">溯</div>
      <h1>生鲜溯源管理系统</h1>
      <p class="subtitle">企业管理后台登录</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <label class="field">
          <span>用户名</span>
          <input
              v-model.trim="loginForm.username"
              autocomplete="username"
              placeholder="请输入用户名"
              :disabled="loading"
          >
        </label>

        <label class="field">
          <span>密码</span>
          <input
              v-model="loginForm.password"
              autocomplete="current-password"
              type="password"
              placeholder="请输入密码"
              :disabled="loading"
          >
        </label>

        <p v-if="message" class="message" role="alert">{{ message }}</p>

        <button class="login-button" type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <button class="trace-link" type="button" @click="goToQuery">
        返回消费者查询页面
      </button>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const message = ref('')

const loginForm = reactive({
  username: '',
  password: ''
})

const apiBase = import.meta.env.VITE_API_BASE || '/api'

const handleLogin = async () => {
  message.value = ''

  if (loginForm.username.length < 3) {
    message.value = '请输入至少 3 个字符的用户名'
    return
  }
  if (loginForm.password.length < 6) {
    message.value = '请输入至少 6 个字符的密码'
    return
  }

  loading.value = true
  try {
    const response = await fetch(`${apiBase}/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: loginForm.username,
        password: loginForm.password
      })
    })

    const responseText = await response.text()
    let res = {}
    try {
      res = responseText ? JSON.parse(responseText) : {}
    } catch (error) {
      res = {}
    }

    if (!response.ok) {
      message.value = res.message || res.error || `HTTP ${response.status}`
      return
    }

    if (res.code === 200 && res.data) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('userInfo', JSON.stringify(res.data.user))
      router.push('/admin/dashboard')
      return
    }

    message.value = res.message || '登录失败'
  } catch (error) {
    message.value = `登录失败：${error.message || '网络错误'}`
  } finally {
    loading.value = false
  }
}

const goToQuery = () => {
  router.push('/trace')
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
      radial-gradient(circle at 18% 18%, rgba(37, 99, 235, 0.12), transparent 28%),
      linear-gradient(135deg, #f6fbff 0%, #eef4f8 100%);
  color: #172033;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.login-panel {
  width: min(420px, 100%);
  padding: 44px 38px 34px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 22px 70px rgba(15, 23, 42, 0.12);
  text-align: center;
}

.brand-mark {
  width: 58px;
  height: 58px;
  display: inline-grid;
  place-items: center;
  margin-bottom: 18px;
  border-radius: 18px;
  background: linear-gradient(135deg, #2563eb, #0f766e);
  color: #fff;
  font-size: 27px;
  font-weight: 800;
}

h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
}

.subtitle {
  margin: 9px 0 30px;
  color: #697586;
  font-size: 14px;
}

.login-form {
  display: grid;
  gap: 16px;
  text-align: left;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: #465467;
  font-size: 13px;
  font-weight: 600;
}

.field input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border: 1px solid #d7dee8;
  border-radius: 12px;
  background: #fbfdff;
  color: #172033;
  font: inherit;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.field input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.message {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fef2f2;
  color: #b42318;
  font-size: 13px;
}

.login-button {
  height: 46px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb, #0f766e);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.login-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.trace-link {
  margin-top: 24px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
}

@media (max-width: 520px) {
  .login-panel {
    padding: 36px 24px 28px;
  }

  h1 {
    font-size: 24px;
  }
}
</style>
