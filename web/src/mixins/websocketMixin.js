// WebSocket管理 Mixin - 统一处理WebSocket相关的重复逻辑
import { 
  registerMessageHandler, 
  unregisterMessageHandler,
  registerStatusChangeCallback,
  unregisterStatusChangeCallback,
  connectToServer,
  isWebSocketConnected
} from '@/services/websocketService'

// 全局标记，确保只有一个组件监听页面可见性变化
let visibilityListenerActive = false

export default {
  data() {
    return {
      // 内部处理器引用，避免重复绑定
      _wsMessageHandler: null,
      _wsStatusChangeHandler: null,
      _audioHandler: null,
      _hasVisibilityListener: false
    }
  },
  
  computed: {
    // 统一的WebSocket状态获取
    wsIsConnected() {
      return this.$store.getters.WS_IS_CONNECTED
    },
    
    wsConnectionStatus() {
      return this.$store.getters.WS_CONNECTION_STATUS
    },
    
    wsServerConfig() {
      return this.$store.getters.WS_SERVER_CONFIG
    },
    
    wsAutoConnect() {
      return this.$store.getters.WS_AUTO_CONNECT
    }
  },
  
  methods: {
    // 统一的消息处理器注册
    registerWebSocketHandlers() {
      // 注册消息处理器
      if (this.handleWebSocketMessage && !this._wsMessageHandler) {
        this._wsMessageHandler = this.handleWebSocketMessage.bind(this)
        registerMessageHandler(this._wsMessageHandler)
      }
      
      // 注册状态变更回调
      if (this.handleWebSocketStatusChange && !this._wsStatusChangeHandler) {
        this._wsStatusChangeHandler = this.handleWebSocketStatusChange.bind(this)
        registerStatusChangeCallback(this._wsStatusChangeHandler)
      }
      
      // 注册音频处理器
      this.registerAudioHandler()
    },
    
    // 音频处理器注册
    registerAudioHandler() {
      if (this.handleBinaryAudioMessage && !this._audioHandler) {
        this._audioHandler = this.handleBinaryAudioMessage.bind(this)
        window.currentAudioHandler = this._audioHandler
      }
    },
    
    // 统一的处理器清理
    unregisterWebSocketHandlers() {
      // 清理消息处理器
      if (this._wsMessageHandler) {
        unregisterMessageHandler(this._wsMessageHandler)
        this._wsMessageHandler = null
      }
      
      // 清理状态变更回调
      if (this._wsStatusChangeHandler) {
        unregisterStatusChangeCallback(this._wsStatusChangeHandler)
        this._wsStatusChangeHandler = null
      }
      
      // 清理音频处理器
      if (this._audioHandler && window.currentAudioHandler === this._audioHandler) {
        window.currentAudioHandler = null
        this._audioHandler = null
      }
    },
    
    // 统一的自动连接逻辑
    async checkAndAutoConnect() {
      // 检查是否已连接
      if (this.wsIsConnected || isWebSocketConnected()) {
        return true
      }
      
      const config = this.wsServerConfig
      const autoConnect = this.wsAutoConnect
      const userInfo = this.$store.getters.USER_INFO
      
      // 检查是否满足自动连接条件
      if (userInfo && autoConnect && config && config.url) {
        try {
          console.log('自动连接WebSocket...')
          const success = await this.$store.dispatch('WS_CONNECT')
          if (success) {
            console.log('自动连接成功')
          }
          return success
        } catch (error) {
          console.error('自动连接失败:', error)
          return false
        }
      }
      
      return false
    },
    
    // 处理页面可见性变化时的重连
    handleVisibilityChange() {
      if (!document.hidden) {
        // 页面变为可见时，检查连接状态并尝试重连
        setTimeout(() => {
          this.checkAndAutoConnect()
        }, 1000)
      }
    },
    
    // 统一的连接方法 - 带消息提示
    async connectWebSocket() {
      try {
        const success = await this.$store.dispatch('WS_CONNECT')
        if (success) {
          this.$message.success('已连接到服务器')
        } else {
          this.$message.error('连接失败，请检查配置')
        }
        return success
      } catch (error) {
        this.$message.error(`连接失败: ${error.message}`)
        return false
      }
    },
    
    // 统一的断开连接方法
    async disconnectWebSocket() {
      try {
        await this.$store.dispatch('WS_DISCONNECT')
        this.$message.info('已断开连接')
        return true
      } catch (error) {
        this.$message.error(`断开连接失败: ${error.message}`)
        return false
      }
    },
    
    // 安静的自动连接方法（不显示过多消息提示）
    async quietConnectWebSocket() {
      try {
        const success = await this.$store.dispatch('WS_CONNECT')
        return success
      } catch (error) {
        console.error('自动连接失败:', error)
        return false
      }
    },
    
    // 设置页面可见性监听器
    setupVisibilityListener() {
      if (!visibilityListenerActive && (this.$options.name === 'Chat' || this.$options.name === 'Home')) {
        document.addEventListener('visibilitychange', this.handleVisibilityChange)
        visibilityListenerActive = true
        this._hasVisibilityListener = true
      }
    },
    
    // 清理页面可见性监听器
    cleanupVisibilityListener() {
      if (this._hasVisibilityListener) {
        document.removeEventListener('visibilitychange', this.handleVisibilityChange)
        visibilityListenerActive = false
        this._hasVisibilityListener = false
      }
    }
  },
  
  // 在组件挂载时自动注册处理器
  mounted() {
    this.registerWebSocketHandlers()
    
    // 检查是否需要自动连接
    this.$nextTick(() => {
      this.checkAndAutoConnect()
    })
    
    // 设置页面可见性监听器
    this.setupVisibilityListener()
  },
  
  // 在组件销毁前自动清理处理器
  beforeDestroy() {
    this.unregisterWebSocketHandlers()
    this.cleanupVisibilityListener()
  }
} 