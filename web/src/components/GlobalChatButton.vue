<template>
  <div class="global-chat-button">
    <a-button
      type="primary"
      size="large"
      shape="circle"
      @click="handleClick"
      :class="{ 'button-active': isActive }"
    >
      <a-icon :type="isActive ? 'close' : 'message'" />
    </a-button>
    
    <!-- 未读消息提示 -->
    <a-badge 
      v-if="unreadCount > 0 && !isActive"
      :count="unreadCount > 99 ? '99+' : unreadCount"
      class="unread-badge"
    />
    
    <!-- 连接状态指示器 -->
    <div 
      class="connection-indicator"
      :class="{ 'connected': isConnected, 'disconnected': !isConnected }"
      :title="isConnected ? '已连接' : '未连接'"
    />
  </div>
</template>

<script>
export default {
  name: 'GlobalChatButton',
  props: {
    isActive: {
      type: Boolean,
      default: false
    },
    unreadCount: {
      type: Number,
      default: 0
    }
  },
  computed: {
    isConnected() {
      return this.$store.getters.WS_IS_CONNECTED
    }
  },
  methods: {
    handleClick() {
      this.$emit('click')
    }
  }
}
</script>

<style scoped>
.global-chat-button {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.global-chat-button .ant-btn {
  width: 56px;
  height: 56px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: none;
  background: #1890ff;
  transition: all 0.3s ease;
}

.global-chat-button .ant-btn:hover {
  background: #40a9ff;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

.global-chat-button .ant-btn.button-active {
  background: #ff4d4f;
  transform: rotate(90deg);
}

.global-chat-button .ant-btn.button-active:hover {
  background: #ff7875;
}

.global-chat-button .ant-btn .anticon {
  font-size: 24px;
  color: white;
}

.unread-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  z-index: 1001;
}

.connection-indicator {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid white;
  z-index: 1001;
}

.connection-indicator.connected {
  background-color: #52c41a;
}

.connection-indicator.disconnected {
  background-color: #ff4d4f;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .global-chat-button {
    bottom: 16px;
    right: 16px;
  }
  
  .global-chat-button .ant-btn {
    width: 48px;
    height: 48px;
  }
  
  .global-chat-button .ant-btn .anticon {
    font-size: 20px;
  }
}
</style> 