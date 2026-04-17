<script setup>
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { userLogin, userRegister } from '@/api/user'
import { useAuthStore } from '@/utils/auth'

const isRegister = ref(false)
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^\S{5,16}$/, message: '用户名长度为5-16位', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { pattern: /^[\u4e00-\u9fa5a-zA-Z0-9]{2,10}$/, message: '昵称2-10位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^\S{5,16}$/, message: '密码长度为5-16位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const formRef = ref(null)
const loading = ref(false)

const handleSubmit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      if (isRegister.value) {
        const res = await userRegister(form.username, form.nickname, form.password, 1)
        if (res.code === 200) {
          ElMessage.success('注册成功，请登录')
          isRegister.value = false
          form.password = ''
          form.confirmPassword = ''
        }
      } else {
        const res = await userLogin(form.username, form.password)
        if (res.code === 200) {
          authStore.login(res.data.token, res.data.userInfo)
          await router.push('/homepage/default')
        }
      }
    } finally {
      loading.value = false
    }
  })
}

const switchMode = () => {
  isRegister.value = !isRegister.value
  formRef.value?.resetFields()
}
</script>

<template>
  <div class="page">

    <!-- ══ 左侧：视觉品牌区 ══ -->
    <div class="left">
      <!-- 背景光晕层 -->
      <div class="glow g1"></div>
      <div class="glow g2"></div>
      <div class="glow g3"></div>

      <!-- 网格纹理遮罩 -->
      <div class="grid-mask"></div>

      <!-- 内容 -->
      <div class="left-body">
        <!-- Logo -->
        <div class="logo-row">
          <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
            <circle cx="18" cy="18" r="18" fill="white" fill-opacity="0.15"/>
            <path d="M18 10 C18 10 10 15 10 21 C10 24.3 13.7 27 18 27 C22.3 27 26 24.3 26 21 C26 15 18 10 18 10Z"
                  fill="white" fill-opacity="0.9"/>
            <path d="M15 21 L17 23 L21 19" stroke="#409EFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span class="logo-text">Smart Volunteer</span>
        </div>

        <!-- 主标语 -->
        <div class="hero">
          <h1 class="hero-title">让公益<br>更智能</h1>
          <p class="hero-sub">连接志愿者与公益项目<br>AI 驱动，精准高效</p>
        </div>

        <!-- 数据卡片 -->
        <div class="stat-cards">
          <div class="stat-card">
            <div class="stat-num">10,000+</div>
            <div class="stat-label">注册志愿者</div>
          </div>
          <div class="stat-card">
            <div class="stat-num">500+</div>
            <div class="stat-label">公益活动</div>
          </div>
          <div class="stat-card">
            <div class="stat-num">98%</div>
            <div class="stat-label">好评率</div>
          </div>
        </div>
      </div>

      <!-- 底部标语 -->
      <div class="left-footer">
        每一份善意，都值得被认真对待
      </div>
    </div>

    <!-- ══ 右侧：登录表单区 ══ -->
    <div class="right">
      <div class="form-box">

        <!-- 标题 -->
        <div class="form-head">
          <h2 class="form-title">{{ isRegister ? '创建账号' : '欢迎回来' }}</h2>
          <p class="form-desc">
            {{ isRegister ? '填写以下信息完成注册' : '登录继续您的志愿之旅' }}
          </p>
        </div>

        <!-- 表单 -->
        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="handleSubmit">

          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              :prefix-icon="User"
              clearable
              size="large"
              class="inp"
            />
          </el-form-item>

          <el-form-item v-if="isRegister" prop="nickname">
            <el-input
              v-model="form.nickname"
              placeholder="昵称"
              :prefix-icon="User"
              clearable
              size="large"
              class="inp"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              clearable
              size="large"
              class="inp"
            />
          </el-form-item>

          <el-form-item v-if="isRegister" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              :prefix-icon="Lock"
              show-password
              clearable
              size="large"
              class="inp"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="submit-btn"
              @click="handleSubmit"
            >
              {{ isRegister ? '注册' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 切换 -->
        <div class="switch-row">
          <span class="switch-tip">{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
          <button class="switch-btn" @click="switchMode">
            {{ isRegister ? '去登录' : '免费注册' }}
          </button>
        </div>

      </div>

      <!-- 底部版权 -->
      <div class="right-footer">© 2026 Smart Volunteer 智能志愿者管理平台</div>
    </div>

  </div>
</template>

<style scoped>
/* ── Reset & Base ── */
* { box-sizing: border-box; margin: 0; padding: 0; }

.page {
  display: flex;
  min-height: 100vh;
  font-family: 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
}

/* ══════════════════════════
   左侧
══════════════════════════ */
.left {
  position: relative;
  flex: 0 0 52%;
  background: #0a0f1e;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

/* 光晕 */
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}
.g1 {
  width: 500px; height: 500px;
  top: -120px; left: -100px;
  background: radial-gradient(circle, rgba(64,158,255,0.35) 0%, transparent 70%);
}
.g2 {
  width: 400px; height: 400px;
  bottom: -80px; right: -60px;
  background: radial-gradient(circle, rgba(37,99,235,0.25) 0%, transparent 70%);
}
.g3 {
  width: 300px; height: 300px;
  top: 45%; left: 55%;
  background: radial-gradient(circle, rgba(96,165,250,0.12) 0%, transparent 70%);
}

/* 细网格 */
.grid-mask {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

/* 主内容 */
.left-body {
  position: relative;
  z-index: 2;
  padding: 52px 60px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
}

/* Logo */
.logo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 80px;
  justify-content: center;
}
.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
  letter-spacing: 0.5px;
}

/* 主标语 */
.hero { margin-bottom: 60px; width: 100%; }

.hero-title {
  font-size: 64px;
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -1px;
  background: linear-gradient(135deg, #ffffff 0%, #93c5fd 60%, #60a5fa 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 20px;
}

.hero-sub {
  font-size: 17px;
  color: rgba(255,255,255,0.5);
  line-height: 1.8;
}

/* 数据卡片 */
.stat-cards {
  display: flex;
  gap: 16px;
  width: 100%;
  justify-content: center;
}

.stat-card {
  flex: 1;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px;
  padding: 20px 16px;
  backdrop-filter: blur(8px);
  transition: background 0.2s;
}

.stat-card:hover {
  background: rgba(255,255,255,0.08);
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #60a5fa;
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}

.stat-label {
  font-size: 13px;
  color: rgba(255,255,255,0.45);
}

/* 左侧底部 */
.left-footer {
  position: relative;
  z-index: 2;
  padding: 28px 60px;
  font-size: 13px;
  color: rgba(255,255,255,0.25);
  border-top: 1px solid rgba(255,255,255,0.06);
}

/* ══════════════════════════
   右侧
══════════════════════════ */
.right {
  flex: 1;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  position: relative;
}

.form-box {
  width: 100%;
  max-width: 360px;
}

/* 表单头 */
.form-head { margin-bottom: 40px; }

.form-title {
  font-size: 30px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.5px;
  margin-bottom: 8px;
}

.form-desc {
  font-size: 15px;
  color: #94a3b8;
}

/* 输入框 */
.inp :deep(.el-input__wrapper) {
  height: 50px;
  border-radius: 12px;
  box-shadow: 0 0 0 1.5px #e2e8f0;
  transition: box-shadow 0.2s;
  background: #fafafa;
}
.inp :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1.5px #94a3b8;
}
.inp :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #409EFF;
  background: #fff;
}
.inp :deep(.el-input__inner) {
  font-size: 15px;
  color: #0f172a;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
  background: #409EFF;
  border: none;
  letter-spacing: 2px;
  transition: background 0.2s, transform 0.15s, box-shadow 0.2s;
  box-shadow: 0 4px 14px rgba(64,158,255,0.25);
}
.submit-btn:hover {
  background: #337ecc;
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(64,158,255,0.35);
}
.submit-btn:active {
  transform: translateY(0);
}

/* 切换模式 */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 24px;
}
.switch-tip {
  font-size: 14px;
  color: #94a3b8;
}
.switch-btn {
  font-size: 14px;
  font-weight: 600;
  color: #409EFF;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s;
}
.switch-btn:hover {
  color: #337ecc;
  text-decoration: underline;
}

/* 版权 */
.right-footer {
  position: absolute;
  bottom: 28px;
  font-size: 12px;
  color: #cbd5e1;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .left { display: none; }
  .right { padding: 40px 24px; }
}
</style>
