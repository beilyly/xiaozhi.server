<template>
  <div class="chat-container">
    <!-- 聊天头部（仅设备留言） -->
    <div class="chat-header">
      <div class="header-left" />

      <div class="header-title">
        {{ chatTitle }}
        <a-tag v-if="targetDevice" color="blue">设备留言</a-tag>
      </div>

      <div class="header-right" />
    </div>

    <!-- 目标设备信息显示 -->
    <div v-if="targetDevice" class="target-device-info">
      <a-card size="small" :bordered="false">
        <div class="device-info-content">
          <a-icon type="robot" style="color: #1890ff; margin-right: 8px" />
          <span class="device-name">{{ targetDevice.deviceName }}</span>
          <a-tag v-if="targetDevice.roleName" color="blue" size="small">
            {{ targetDevice.roleName }}
          </a-tag>
        </div>
      </a-card>
    </div>

    <!-- 聊天区域（本地消息 + 文本输入） -->
    <div class="chat-content">
      <a-empty v-if="messages.length === 0" :description="emptyText" />
      <div v-else class="message-list">
        <div v-for="msg in messages" :key="msg.id" class="message-item">
          <a-avatar :src="userAvatar" size="large" class="message-avatar" />
          <div class="message-body">
            <div class="message-meta">
              <span class="message-from">me</span>
              <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
            </div>
            <div class="message-bubble">
              {{ msg.content }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <a-textarea
        v-model:value="inputMessage"
        :placeholder="inputPlaceholder"
        :auto-size="{ minRows: 1, maxRows: 4 }"
        @keypress.enter.exact.prevent="handleEnter"
      />
      <div class="input-actions">
        <a-button
          type="primary"
          :disabled="!canSend"
          @click="handleClickSend"
        >
          发送
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import axios from '@/services/axios'
import api from '@/services/api'

const { t } = useI18n()

type DeviceInfo = {
  deviceId: string
  deviceName: string
  roleId?: number
  roleName?: string
}

// 目标设备信息
const targetDevice = ref<DeviceInfo | null>(null)

// 本地消息列表（只记录用户发送的内容）
const messages = ref<
  {
    id: string
    content: string
    type: 'text'
    isUser: boolean
    timestamp: Date
    isLoading: boolean
  }[]
>([])

// 输入框内容
const inputMessage = ref('')

// 头像
const userAvatar = '/assets/user-avatar.png'

// 标题 / 占位文案
const chatTitle = computed(() => {
  if (targetDevice.value) {
    return t('chat.deviceTitleWithName', { name: targetDevice.value.deviceName })
  }
  return t('chat.deviceTitle')
})

const inputPlaceholder = computed(() => {
  if (targetDevice.value) {
    return t('chat.inputPlaceholderWithName', { name: targetDevice.value.deviceName })
  }
  return t('chat.inputPlaceholder')
})

const emptyText = computed(() => {
  if (targetDevice.value) {
    return t('chat.emptyTextWithName', { name: targetDevice.value.deviceName })
  }
  return t('chat.emptyText')
})

// 按钮可点击状态
const canSend = computed(() => {
  return !!targetDevice.value && !!inputMessage.value.trim()
})

// 从 sessionStorage 读取目标设备信息（由 DeviceView 跳转时写入）
function loadTargetDevice() {
  try {
    const raw = sessionStorage.getItem('targetDevice')
    if (!raw) {
      return
    }

    const parsed = JSON.parse(raw)
    if (!parsed || !parsed.deviceId) {
      console.warn('目标设备信息不完整，忽略')
      sessionStorage.removeItem('targetDevice')
      return
    }

    targetDevice.value = {
      deviceId: parsed.deviceId,
      deviceName: parsed.deviceName || parsed.deviceId,
      roleId: parsed.roleId,
      roleName: parsed.roleName
    }

    // 用完即删，避免污染后续会话
    sessionStorage.removeItem('targetDevice')

    message.success(t('chat.switchToDeviceSuccess', { name: targetDevice.value.deviceName }))
  } catch (err) {
    console.error('解析目标设备信息失败:', err)
    message.error('加载设备信息失败')
    sessionStorage.removeItem('targetDevice')
  }
}

// 发送留言到设备
async function sendMessageToDevice(content: string) {
  if (!targetDevice.value) {
    message.error('目标设备信息不存在，无法发送留言')
    return
  }

  try {
    const response = await axios.jsonPost({
      url: api.deviceMessage.send,
      data: {
        deviceId: targetDevice.value.deviceId,
        content,
        type: 'text',
        timestamp: new Date().toISOString()
      }
    })

    if (response.code === 200) {
      message.success(t('chat.sendSuccess'))
      // 本地追加一条用户消息
      messages.value.push({
        id: `msg_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
        content,
        type: 'text',
        isUser: true,
        timestamp: new Date(),
        isLoading: false
      })
    } else {
      message.error(response.message || '消息发送失败')
    }
  } catch (err) {
    console.error('发送消息到设备失败:', err)
    message.error(t('chat.sendFailedRetry'))
  }
}

// 发送入口（点击按钮）
function handleClickSend() {
  const text = inputMessage.value.trim()
  if (!text) return
  sendMessageToDevice(text)
  inputMessage.value = ''
}

// 回车发送（单行）
function handleEnter() {
  handleClickSend()
}

// 时间格式化
function formatTime(ts: Date) {
  const d = ts instanceof Date ? ts : new Date(ts)
  return d.toLocaleTimeString()
}

onMounted(() => {
  loadTargetDevice()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 80vh;
  background-color: #ededed;
  position: relative;
  max-width: 800px;
  margin: 0 auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fff 0%, #f8f9fa 100%);
  border-bottom: 1px solid #e0e0e0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  z-index: 10;
  backdrop-filter: blur(10px);
}

.header-left {
  flex: 0 0 auto;
  width: 40px;
  display: flex;
  justify-content: flex-start;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #333;
  letter-spacing: 0.5px;
}

.header-title .ant-tag {
  margin-left: 10px;
  font-size: 11px;
  padding: 2px 8px;
  height: 20px;
  line-height: 16px;
  border-radius: 10px;
  font-weight: 500;
}

.header-right {
  flex: 0 0 auto;
  width: 40px;
  display: flex;
  justify-content: flex-end;
}

/* 目标设备信息区域 */
.target-device-info {
  margin: 0 0 16px 0;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 8px;
  border: 1px solid #bae6fd;
}

.device-info-content {
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.device-name {
  font-weight: 500;
  color: #0369a1;
  margin-right: 8px;
}

.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: linear-gradient(180deg, #f6f8fb 0%, #eef2f7 100%);
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.message-avatar {
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.2);
}

.message-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 16px;
  padding: 12px 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.message-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.message-from {
  font-weight: 600;
  color: #0f172a;
}

.message-time {
  font-size: 11px;
}

.message-bubble {
  background: linear-gradient(135deg, #d9f99d 0%, #bef264 100%);
  border-radius: 12px;
  padding: 10px 14px;
  line-height: 1.5;
  color: #1a2b4b;
  word-break: break-word;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.chat-input-area {
  padding: 16px 20px 20px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-input-area :deep(.ant-input) {
  border-radius: 14px;
  border: 1px solid #cbd5f5;
  padding: 10px 14px;
  font-size: 14px;
  transition: all 0.2s ease;
}

.chat-input-area :deep(.ant-input:focus) {
  border-color: #95ec69;
  box-shadow: 0 0 0 2px rgba(149, 236, 105, 0.3);
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

.input-actions .ant-btn {
  min-width: 100px;
  border-radius: 22px;
  font-weight: 600;
}
</style>


