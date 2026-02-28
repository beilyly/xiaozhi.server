<template>
  <div class="chat-container">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <!-- 左侧连接按钮 -->
      <div class="header-left">
        <a-button 
          type="text" 
          class="connection-btn" 
          @click="toggleConnection" 
          :disabled="localConnectionStatus === '正在连接...'"
        >
          <a-icon 
            :type="localIsConnected ? 'disconnect' : 'link'" 
            :style="{ color: localIsConnected ? '#52c41a' : '#ff4d4f' }"
          />
        </a-button>
      </div>
      
      <div class="header-title">
        {{ chatTitle }}
        <a-tag v-if="showWebSocketStatus" :color="connectionStatusColor">{{ localConnectionStatus }}</a-tag>
        <a-tag v-else-if="showDeviceInfo" color="blue">设备通信模式</a-tag>
      </div>
      
      <div class="header-right">
        <a-dropdown>
          <template #overlay>
            <a-menu>
              <a-menu-item key="1" @click="showServerConfig = true">
                <a-icon type="setting" />
                服务器设置
              </a-menu-item>
              <a-menu-item key="2" @click="showDebugPanel = !showDebugPanel">
                <a-icon type="bug" />
                调试面板
              </a-menu-item>
              <a-menu-item key="3">
                <a-popconfirm
                  title="确定要清空所有对话记录吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  placement="leftBottom"
                  :overlay-style="{ maxWidth: '300px' }"
                  @confirm="handleClearMessages"
                >
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <a-icon type="delete" />
                    <span>清空聊天</span>
                  </div>
                </a-popconfirm>
              </a-menu-item>
            </a-menu>
          </template>
          <a-button type="text">
            <a-icon type="more" />
          </a-button>
        </a-dropdown>
      </div>
    </div>

    <!-- 目标设备信息显示 -->
    <div v-if="showDeviceInfo" class="target-device-info">
      <a-card size="small" :bordered="false">
        <div class="device-info-content">
          <a-icon type="robot" style="color: #1890ff; margin-right: 8px;" />
          <span class="device-name">{{ targetDevice.deviceName }}</span>
          <a-tag v-if="targetDevice.roleName" color="blue" size="small">{{ targetDevice.roleName }}</a-tag>
          <a-button 
            type="link" 
            size="small" 
            @click="switchToAiMode"
            style="float: right; padding: 0;"
          >
            切换到AI助手
          </a-button>
        </div>
      </a-card>
    </div>

    <!-- 使用ChatComponent替换原来的聊天内容和输入区域 -->
    <ChatComponent
      ref="chatComponentRef"
      :message-list="displayMessages"
      :show-input="true"
      :show-voice-toggle="chatMode === 'ai'"
      :show-read-status="chatMode === 'device' && !!targetDevice"
      :message-clickable="chatMode === 'device' && !!targetDevice"
      :user-avatar="userAvatar"
      :ai-avatar="aiAvatar"
      :input-placeholder="getInputPlaceholder()"
      :empty-text="getEmptyText()"
      :is-connected-prop="chatMode === 'ai' ? localIsConnected : true"
      @recording-start="handleRecordingStart"
      @recording-stop="handleRecordingStop"
      @recording-error="handleRecordingError"
      @mode-change="handleModeChange"
      @send-message="handleSendMessage"
      @mark-read="handleMarkRead"
    />

    <!-- 连接提示 -->
    <a-alert
      v-if="connectionAlert.show"
      :message="connectionAlert.title"
      :description="connectionAlert.message"
      :type="connectionAlert.type"
      class="connection-alert"
      closable
      @close="connectionAlert.show = false"
    >
      <template slot="action">
        <div class="connection-actions">
          <a-button size="small" @click="connect">连接</a-button>
          <a-button size="small" type="primary" @click="showServerConfig = true">配置服务器</a-button>
        </div>
      </template>
    </a-alert>

    <!-- 服务器配置对话框 -->
    <a-modal
      v-model="showServerConfig"
      title="服务器配置"
      @ok="saveServerConfig"
      @cancel="initTempServerConfig"
      okText="保存"
      cancelText="取消"
    >
      <a-form :form="form" layout="vertical">
        <a-form-item label="服务器地址">
          <a-input v-model="tempServerConfig.url" placeholder="ws://服务器地址:端口/路径" />
        </a-form-item>
        <a-form-item label="设备ID">
          <a-input v-model="tempServerConfig.deviceId" placeholder="设备唯一标识" />
        </a-form-item>
        <a-form-item label="设备名称">
          <a-input v-model="tempServerConfig.deviceName" placeholder="设备名称" />
        </a-form-item>
        <a-form-item label="认证令牌">
          <a-input v-model="tempServerConfig.token" placeholder="认证令牌" />
        </a-form-item>
      </a-form>

      <!-- 自动连接设置 -->
      <a-form-item label="自动连接">
        <a-switch
          v-model="autoConnectSwitch"
          checked-children="开"
          un-checked-children="关"
        />
        <span style="margin-left: 8px; color: #666;">登录后自动连接WebSocket</span>
      </a-form-item>
    </a-modal>

    <!-- 调试面板 -->
    <div class="debug-panel-container" :class="{ show: showDebugPanel }">
      <div class="debug-panel-header">
        <span>调试面板</span>
        <a-button type="text" @click="showDebugPanel = false">
          <a-icon type="close" />
        </a-button>
      </div>
      <div class="debug-panel">
        <a-space direction="vertical" style="width: 100%">
          <a-card size="small" title="连接状态">
            <p><strong>状态:</strong> {{ localConnectionStatus }}</p>
            <p v-if="connectionTime"><strong>连接时间:</strong> {{ formatTimestamp(connectionTime) }}</p>
            <p v-if="sessionId"><strong>会话ID:</strong> {{ sessionId }}</p>
            <a-space>
              <a-button
                size="small"
                :type="localIsConnected ? 'default' : 'primary'"
                @click="toggleConnection"
                :disabled="localConnectionStatus === '正在连接...'"
              >
                <a-icon :type="localIsConnected ? 'disconnect' : 'link'" />
                {{ localIsConnected ? '断开' : '连接' }}
              </a-button>
              <a-button size="small" @click="handleReconnect" :disabled="localIsConnected">重连</a-button>
              <a-button size="small" @click="handleStopReconnect" type="danger">停止重连</a-button>
            </a-space>
          </a-card>

          <a-card size="small" title="日志">
            <div class="log-container" ref="logContainerRef">
              <div v-for="(entry, index) in logs" :key="index" class="log-entry" :class="`log-${entry.type}`">
                <span class="log-time">{{ formatLogTime(entry.time) }}</span>
                <span class="log-message">{{ entry.message }}</span>
              </div>
            </div>
          </a-card>

          <a-card size="small" title="音频可视化">
            <canvas ref="audioVisualizerRef" class="audio-visualizer"></canvas>
          </a-card>
        </a-space>
      </div>
    </div>

    <!-- 添加一个悬浮的重连按钮，当连接断开时显示 -->
    <div v-if="!localIsConnected && !connectionAlert.show" class="floating-reconnect-btn">
      <a-button type="primary" shape="circle" @click="connect" title="重新连接">
        <a-icon type="reload" />
      </a-button>
    </div>
  </div>
</template>

<script>
import {
  // WebSocket相关
  startDirectRecording,
  stopDirectRecording,
  reconnectToServer,
  stopAutoReconnect,
  sendTextMessage as wsSendTextMessage,

  // 日志相关
  log,
  getLogs,

  // 消息相关
  messages,
  clearMessages
} from '@/services/websocketService';

import axios from '@/services/axios';
import api from '@/services/api';

import {
  initAudio,
  handleBinaryMessage,
  getAudioState
} from '@/services/audioService';

// 导入ChatComponent和mixin
import ChatComponent from '@/components/ChatComponent';
import websocketMixin from '@/mixins/websocketMixin';

export default {
  name: 'Chat',
  mixins: [websocketMixin],
  components: {
    ChatComponent
  },
  data() {
    return {
      // 状态变量
      isVoiceMode: false, // 默认为文字输入模式
      messages: messages, // 使用websocketService中的消息列表
      logs: [],
      showDebugPanel: false,
      showServerConfig: false,
      connectionAlert: {
        show: false,
        title: '未连接到服务器',
        message: '请配置并连接到小智服务器',
        type: 'info'
      },
      // 头像
      userAvatar: '/assets/user-avatar.png',
      aiAvatar: '/assets/ai-avatar.png',
      form: this.$form.createForm(this),
      // 临时服务器配置（用于编辑）
      tempServerConfig: {},
      // 目标设备信息
      targetDevice: null,
      // 聊天模式：'ai' - AI助手模式, 'device' - 设备通信模式
      chatMode: 'ai',
      // 设备模式下的消息列表（含已读状态，持久化到 localStorage）
      deviceMessages: [],
      // 本次会话是否已因“全部已读”发送过 standby，避免重复发送
      deviceStandbySentForSession: false
    };
  },
  computed: {
    // 本地别名（为了兼容现有模板）
    localIsConnected() {
      return this.wsIsConnected;
    },
    localConnectionStatus() {
      return this.wsConnectionStatus;
    },
    connectionTime() {
      return this.$store.getters.WS_CONNECTION_TIME;
    },
    sessionId() {
      return this.$store.getters.WS_SESSION_ID;
    },

    // 自动连接开关的双向绑定
    autoConnectSwitch: {
      get() {
        return this.wsAutoConnect;
      },
      set(value) {
        this.$store.commit('SET_WS_AUTO_CONNECT', value);
      }
    },

    // 使用计算属性获取最新的连接状态
    connectionStatusColor() {
      if (this.localIsConnected) return 'green';
      if (this.localConnectionStatus && (
          this.localConnectionStatus.includes('错误') ||
          this.localConnectionStatus.includes('失败')
      )) return 'red';
      if (this.localConnectionStatus === '正在连接...') return 'blue';
      return 'red';
    },
    
    // 聊天标题
    chatTitle() {
      if (this.targetDevice) {
        return `与 ${this.targetDevice.deviceName} 的对话`;
      }
      return '小智助手';
    },
    
    // 是否显示WebSocket连接状态
    showWebSocketStatus() {
      return this.chatMode === 'ai';
    },
    
    // 是否显示设备信息
    showDeviceInfo() {
      return this.chatMode === 'device' && this.targetDevice;
    },

    // 当前展示的消息列表：设备模式用 deviceMessages，否则用 WebSocket messages
    displayMessages() {
      if (this.chatMode === 'device' && this.targetDevice) {
        return this.deviceMessages;
      }
      return this.messages;
    }
  },
  watch: {
    // 监听连接状态变化
    localIsConnected: {
      handler(newValue) {
        if (newValue) {
          // 连接成功
          this.connectionAlert.show = false;
        } else {
          // 连接断开，但不显示警告（全局管理会处理重连）
          this.connectionAlert.show = false;
        }
      },
      immediate: true
    },

    // 监听连接状态文本变化，用于错误提示
    localConnectionStatus: {
      handler(newValue) {
        if (newValue && (newValue.includes('错误') || newValue.includes('失败'))) {
          this.connectionAlert.show = true;
          this.connectionAlert.title = '连接问题';
          this.connectionAlert.message = newValue;
          this.connectionAlert.type = 'error';
        } else if (newValue === '已连接') {
          this.connectionAlert.show = false;
        }
      },
      immediate: true
    },

    // 监听配置对话框打开
    showServerConfig: {
      handler(newValue) {
        if (newValue) {
          this.initTempServerConfig();
        }
      }
    },

    // 设备模式且目标设备变化时加载该设备的历史消息
    targetDevice: {
      handler(device) {
        if (device && device.deviceId) {
          this.loadDeviceHistory();
          this.deviceStandbySentForSession = false;
        } else {
          this.deviceMessages = [];
        }
      },
      immediate: false
    }
  },
  mounted() {
    // 检查是否有目标设备信息
    this.checkTargetDevice();

    // 初始化音频可视化
    this.initAudioVisualizer();

    // 加载日志
    this.logs = getLogs();

    // 设置当前活动的音频处理器已通过mixin处理

    // 初始化音频服务
    this.initAudioService();

    // 监听窗口大小变化
    window.addEventListener('resize', this.initAudioVisualizer);

    // 监听日志更新
    this.$watch(
      () => getLogs(),
      (newLogs) => {
        this.logs = newLogs;

        // 滚动日志到底部
        this.$nextTick(() => {
          if (this.$refs.logContainerRef) {
            this.$refs.logContainerRef.scrollTop = this.$refs.logContainerRef.scrollHeight;
          }
        });
      },
      { deep: true }
    );

    // 初始化临时配置
    this.initTempServerConfig();
  },
  beforeDestroy() {
    // 清除窗口事件监听
    window.removeEventListener('resize', this.initAudioVisualizer);

    // WebSocket处理器清理由mixin处理
  },
  methods: {
    // 检查目标设备信息
    checkTargetDevice() {
      try {
        const targetDeviceStr = sessionStorage.getItem('targetDevice');
        if (targetDeviceStr) {
          this.targetDevice = JSON.parse(targetDeviceStr);
          
          // 验证设备信息
          if (!this.targetDevice.deviceId) {
            console.warn('目标设备信息不完整，忽略');
            this.targetDevice = null;
            return;
          }
          
          // 清除sessionStorage中的目标设备信息
          sessionStorage.removeItem('targetDevice');
          
          // 切换到设备通信模式
          this.chatMode = 'device';
          this.loadDeviceHistory();
          this.deviceStandbySentForSession = false;
          this.$message.success(`已切换到设备 ${this.targetDevice.deviceName} 的对话模式`);
        }
      } catch (error) {
        console.error('解析目标设备信息失败:', error);
        this.$message.error('加载设备信息失败');
        // 清除可能损坏的数据
        sessionStorage.removeItem('targetDevice');
      }
    },
    
    // 更新WebSocket配置以连接到目标设备
    updateWebSocketConfigForTargetDevice() {
      if (this.targetDevice) {
        // 更新store中的WebSocket配置
        const newConfig = {
          ...this.wsServerConfig,
          deviceId: this.targetDevice.deviceId,
          deviceName: this.targetDevice.deviceName
        };
        
        this.$store.commit('SET_WS_SERVER_CONFIG', newConfig);
        
        // 如果当前已连接，提示用户重新连接
        if (this.localIsConnected) {
          this.$message.info(`已切换到设备 ${this.targetDevice.deviceName}，请重新连接`);
        }
      }
    },
    
    // 初始化临时配置
    initTempServerConfig() {
      this.tempServerConfig = { ...this.wsServerConfig };
    },

    // 切换连接状态
    async toggleConnection() {
      if (this.localIsConnected) {
        await this.disconnectFromServer();
      } else {
        await this.connect();
      }
    },

    // 初始化音频服务
    async initAudioService() {
      try {
        await initAudio();
      } catch (error) {
        log(`音频初始化失败: ${error.message}`, 'error');
      }
    },

    // 初始化音频可视化
    initAudioVisualizer() {
      if (!this.$refs.audioVisualizerRef) return;

      const canvas = this.$refs.audioVisualizerRef;
      canvas.width = canvas.clientWidth;
      canvas.height = canvas.clientHeight;

      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.fillStyle = '#fafafa';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
      }
    },

    // 处理WebSocket消息（由mixin调用）
    handleWebSocketMessage(data) {
      if (!data || !data.type) return;
      // 处理消息逻辑保持不变
    },

    // 处理二进制音频消息
    async handleBinaryAudioMessage(data) {
      try {
        // 调用ChatComponent中的处理方法
        if (this.$refs.chatComponentRef) {
          return await this.$refs.chatComponentRef.handleBinaryAudioMessage(data);
        } else {
          // 如果ChatComponent还没准备好，则使用audioService中的方法
          return await handleBinaryMessage(data);
        }
      } catch (error) {
        log(`处理二进制音频消息失败: ${error.message}`, 'error');
        return false;
      }
    },

    // 连接到服务器（使用mixin中的方法）
    async connect() {
      return await this.connectWebSocket();
    },

    // 显示连接错误
    showConnectionError(title, message) {
      this.connectionAlert.show = true;
      this.connectionAlert.title = title;
      this.connectionAlert.message = message;
      this.connectionAlert.type = 'error';
    },

    // 处理录音开始
    handleRecordingStart() {
      log('录音开始', 'info');
    },

    // 处理录音结束
    handleRecordingStop() {
      log('录音结束', 'info');
    },

    // 处理录音错误
    handleRecordingError(error) {
      log(`录音错误: ${error.message}`, 'error');
    },

    // 处理输入模式变化
    handleModeChange(isVoiceMode) {
      this.isVoiceMode = isVoiceMode;
      log(`切换到${isVoiceMode ? '语音' : '文字'}输入模式`, 'info');
    },

    // 保存服务器配置
    async saveServerConfig() {
      // 更新store中的配置
      this.$store.commit('SET_WS_SERVER_CONFIG', this.tempServerConfig);

      this.showServerConfig = false;

      // 如果已连接但配置改变，提示重新连接
      if (this.localIsConnected) {
        this.$message.info('配置已保存，需要重新连接才能生效');
        // 先断开再重新连接
        await this.disconnectWebSocket();
        setTimeout(() => {
          this.connectWebSocket();
        }, 1000);
      } else {
        // 尝试连接
        this.connectWebSocket();
      }
    },

    // 格式化时间戳
    formatTimestamp(timestamp) {
      if (!timestamp) return '';

      const date = timestamp instanceof Date ? timestamp : new Date(timestamp);
      return date.toLocaleString();
    },

    // 格式化日志时间
    formatLogTime(time) {
      if (!time) return '';

      const date = time instanceof Date ? time : new Date(time);
      return date.toLocaleTimeString();
    },

    // 清空消息
    handleClearMessages() {
      if (this.chatMode === 'device' && this.targetDevice) {
        this.deviceMessages = [];
        this.saveDeviceMessages();
        this.deviceStandbySentForSession = false;
      } else {
        clearMessages();
      }
      this.$message.success('已清空对话记录');
    },

    // 连接到服务器（调用websocketService）
    connectToServer() {
      return this.connectWebSocket();
    },

    // 断开连接（使用mixin中的方法）
    async disconnectFromServer() {
      return await this.disconnectWebSocket();
    },

    // 手动重连
    async handleReconnect() {
      try {
        this.$message.info('正在重连...');
        const success = await reconnectToServer();
        if (success) {
          this.$message.success('重连成功');
        } else {
          this.$message.error('重连失败');
        }
      } catch (error) {
        this.$message.error(`重连失败: ${error.message}`);
      }
    },

    // 停止自动重连
    handleStopReconnect() {
      try {
        stopAutoReconnect();
        this.$message.info('已停止自动重连');
      } catch (error) {
        this.$message.error(`停止重连失败: ${error.message}`);
      }
    },
    
    // 切换到AI助手模式
    switchToAiMode() {
      this.chatMode = 'ai';
      this.targetDevice = null;
      
      // 重置WebSocket配置为默认配置
      const defaultConfig = {
        url: 'ws://127.0.0.1:8091/ws/xiaozhi/v1/',
        deviceId: 'web_test',
        deviceName: 'Web客户端',
        token: ''
      };
      
      this.$store.commit('SET_WS_SERVER_CONFIG', defaultConfig);
      
      // 如果当前已连接，提示用户重新连接
      if (this.localIsConnected) {
        this.$message.info('已切换到AI助手模式，请重新连接');
      }
    },
    
    // 获取输入框占位符
    getInputPlaceholder() {
      if (this.chatMode === 'device' && this.targetDevice) {
        return `向 ${this.targetDevice.deviceName} 发送消息...`;
      }
      return '输入消息...';
    },
    
    // 获取空状态文本
    getEmptyText() {
      if (this.chatMode === 'device' && this.targetDevice) {
        return `开始与 ${this.targetDevice.deviceName} 的对话`;
      }
      return '暂无对话，开始聊天吧';
    },
    
    // 处理发送消息
    handleSendMessage(message) {
      if (this.chatMode === 'device') {
        this.sendMessageToDevice(message);
      } else {
        // AI模式的消息发送通过WebSocket处理
        this.sendMessageToAi(message);
      }
    },
    
    // 发送消息到AI助手
    async sendMessageToAi(message) {
      try {
        // 确保WebSocket连接
        const connected = await this.ensureWebSocketConnection();
        if (!connected) return;

        // 通过WebSocket发送消息
        const success = wsSendTextMessage(message.content);

        if (success) {
          // 添加用户消息到聊天记录
          this.addUserMessage(message.content);
        } else {
          this.$message.error('发送失败，请检查连接状态');
        }
      } catch (error) {
        console.error('发送消息到AI失败:', error);
        this.$message.error('消息发送失败，请重试');
      }
    },
    
    // 发送消息到设备
    async sendMessageToDevice(message) {
      if (!this.targetDevice) {
        this.$message.error('目标设备信息不存在');
        return;
      }
      
      try {
        this.loading = true;
        
        const response = await axios.jsonPost({
          url: api.deviceMessage.send,
          data: {
            deviceId: this.targetDevice.deviceId,
            content: message.content,
            type: 'text',
            timestamp: new Date().toISOString()
          }
        });
        
        if (response.code === 200) {
          this.$message.success('消息发送成功');
          const newMsg = {
            id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
            content: message.content,
            type: 'text',
            isUser: true,
            timestamp: new Date(),
            isLoading: false,
            read: false
          };
          this.deviceMessages.push(newMsg);
          this.saveDeviceMessages();
          this.checkAllReadAndSendStandby();
        } else {
          this.$message.error(response.message || '消息发送失败');
        }
      } catch (error) {
        console.error('发送消息到设备失败:', error);
        this.$message.error('消息发送失败，请重试');
      } finally {
        this.loading = false;
      }
    },
    
    // 添加用户消息到聊天记录
    addUserMessage(content) {
      const userMessage = {
        id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
        content: content,
        type: 'text',
        isUser: true,
        timestamp: new Date(),
        isLoading: false
      };
      
      this.messages.push(userMessage);
    },

    getDeviceStorageKey() {
      if (!this.targetDevice || !this.targetDevice.deviceId) return null;
      const userInfo = this.$store.getters.USER_INFO;
      const userId = (userInfo && (userInfo.userId || userInfo.id)) || 'anonymous';
      return `device_chat_${userId}_${this.targetDevice.deviceId}`;
    },

    loadDeviceHistory() {
      const key = this.getDeviceStorageKey();
      if (!key) return;
      try {
        const raw = localStorage.getItem(key);
        if (raw) {
          const { messages: list = [] } = JSON.parse(raw);
          this.deviceMessages = (list || []).map(m => ({
            ...m,
            timestamp: m.timestamp ? new Date(m.timestamp) : new Date(),
            read: m.read === undefined ? true : m.read
          }));
        } else {
          this.deviceMessages = [];
        }
        const deviceId = this.targetDevice?.deviceId;
        if (deviceId) {
          axios.get({ url: api.deviceMessage.query, data: { deviceId } })
            .then(res => {
              if (res.code === 200 && res.data && Array.isArray(res.data.messages) && res.data.messages.length > 0) {
                const fromApi = res.data.messages.map(m => ({
                  id: m.id || `msg_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
                  content: m.content || '',
                  type: m.type || 'text',
                  isUser: !!m.isUser,
                  timestamp: m.timestamp ? new Date(m.timestamp) : new Date(),
                  read: !!m.read
                }));
                this.deviceMessages = fromApi;
                this.saveDeviceMessages();
              }
            })
            .catch(() => {});
        }
      } catch (e) {
        this.deviceMessages = [];
      }
    },

    saveDeviceMessages() {
      const key = this.getDeviceStorageKey();
      if (!key) return;
      try {
        const list = this.deviceMessages.map(m => ({
          ...m,
          timestamp: m.timestamp instanceof Date ? m.timestamp.toISOString() : m.timestamp
        }));
        localStorage.setItem(key, JSON.stringify({ messages: list, updatedAt: new Date().toISOString() }));
      } catch (e) {}
    },

    handleMarkRead(message) {
      if (this.chatMode !== 'device' || !this.targetDevice) return;
      const idx = this.deviceMessages.findIndex(m => m.id === message.id);
      if (idx === -1) return;
      this.$set(this.deviceMessages[idx], 'read', true);
      this.saveDeviceMessages();
      this.checkAllReadAndSendStandby();
    },

    checkAllReadAndSendStandby() {
      if (this.chatMode !== 'device' || !this.targetDevice || this.deviceStandbySentForSession) return;
      if (this.deviceMessages.length === 0) return;
      const allRead = this.deviceMessages.every(m => m.read);
      if (!allRead) return;
      this.sendStandbyCommand();
    },

    async sendStandbyCommand() {
      if (!this.targetDevice || this.deviceStandbySentForSession) return;
      try {
        const response = await axios.jsonPost({
          url: api.deviceMessage.standby,
          data: { deviceId: this.targetDevice.deviceId }
        });
        if (response.code === 200) {
          this.deviceStandbySentForSession = true;
          this.$message.success('已发送待命指令，设备将切换至待命界面');
        }
      } catch (e) {
        this.$message.error('发送待命指令失败');
      }
    },
  }
};
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

/* 连接按钮样式 */
.connection-btn {
  height: 36px;
  width: 36px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e0e0e0;
  transition: all 0.2s ease;
}

.connection-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-color: #95ec69;
}

.connection-btn .anticon {
  font-size: 18px;
}

.connection-alert {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 500px;
  z-index: 100;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  border-radius: 12px;
  backdrop-filter: blur(10px);
}

.connection-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.connection-actions .ant-btn {
  border-radius: 18px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.connection-actions .ant-btn:hover {
  transform: translateY(-1px);
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

/* 悬浮重连按钮 */
.floating-reconnect-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 90;
}

.floating-reconnect-btn .ant-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #95ec69 0%, #7ed321 100%);
  border: none;
  box-shadow: 0 4px 16px rgba(149, 236, 105, 0.4);
  transition: all 0.3s ease;
}

.floating-reconnect-btn .ant-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(149, 236, 105, 0.5);
}

.floating-reconnect-btn .anticon {
  font-size: 24px;
  color: #333;
}

.debug-panel-container {
  position: fixed;
  top: 0;
  right: -350px;
  width: 350px;
  height: 100vh;
  background-color: #fff;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.15);
  transition: right 0.3s;
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.debug-panel-container.show {
  right: 0;
}

.debug-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  font-weight: 500;
}

.debug-panel {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.log-container {
  height: 200px;
  overflow-y: auto;
  background-color: #f5f5f5;
  border-radius: 4px;
  padding: 8px;
  font-family: monospace;
  font-size: 12px;
}

.log-entry {
  margin-bottom: 4px;
  line-height: 1.4;
}

.log-time {
  color: #888;
  margin-right: 6px;
}

.log-info {
  color: #333;
}

.log-error {
  color: #f5222d;
}

.log-warning {
  color: #faad14;
}

.log-success {
  color: #52c41a;
}

.log-debug {
  color: #8c8c8c;
}

.audio-visualizer {
  width: 100%;
  height: 100px;
  background-color: #fafafa;
  border-radius: 4px;
}

/* 美化确认提示框 */
:deep(.ant-popover.ant-popconfirm) {
  .ant-popover-inner-content {
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
    border: 1px solid #e8e8e8;
  }

  .ant-popover-message {
    padding: 12px 16px 8px;
    color: #333;
    font-size: 14px;
    line-height: 1.5;
  }

  .ant-popover-message-icon {
    color: #faad14;
    font-size: 16px;
  }

  .ant-popover-message-title {
    color: #333;
    font-weight: 500;
  }

  .ant-popover-buttons {
    padding: 8px 16px 12px;
    gap: 8px;
  }

  .ant-btn {
    border-radius: 6px;
    font-size: 12px;
    height: 28px;
    padding: 0 12px;
    font-weight: 500;
    transition: all 0.2s ease;
  }

  .ant-btn-primary {
    background: linear-gradient(135deg, #ff6b6b 0%, #ff5252 100%);
    border: none;
    color: #fff;
    box-shadow: 0 2px 4px rgba(255, 107, 107, 0.3);
  }

  .ant-btn-primary:hover {
    background: linear-gradient(135deg, #ff5252 0%, #e53935 100%);
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(255, 107, 107, 0.4);
  }

  .ant-btn-default {
    background: #f5f5f5;
    border: 1px solid #d9d9d9;
    color: #666;
  }

  .ant-btn-default:hover {
    background: #e8e8e8;
    border-color: #b3b3b3;
    color: #333;
    transform: translateY(-1px);
  }
}
</style>