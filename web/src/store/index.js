// 轻量 Cookies 兼容实现，避免依赖 js-cookie 包
const Cookies = {
  getJSON(key) {
    try {
      const raw = localStorage.getItem(key)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value))
    } catch {
      // 忽略本地存储错误
    }
  }
}

// 辅助函数：从localStorage获取JSON数据
function getStoredData(key) {
  try {
    const data = localStorage.getItem(key)
    return data ? JSON.parse(data) : null
  } catch (error) {
    console.warn(`读取存储数据失败 (${key}):`, error)
    return null
  }
}

// -------------------------
// 基础 state（沿用原 Vuex 结构）
// -------------------------
const state = {
  info: Cookies.getJSON('userInfo'),
  isMobile: true,
  navigationStyle: Cookies.getJSON('navigationStyle') || 'tabs',
  // WebSocket连接状态
  websocket: {
    isConnected: false,
    connectionStatus: '未连接',
    connectionTime: null,
    sessionId: null,
    serverConfig: (() => {
      // 优先读取统一配置
      const unifiedConfig = getStoredData('websocketConfig')
      if (unifiedConfig) {
        return unifiedConfig
      }

      // 如果没有统一配置，读取分散的配置
      return {
        url: localStorage.getItem('xiaozhi_server_url') || 'ws://127.0.0.1:8091/ws/xiaozhi/v1/',
        deviceId: localStorage.getItem('xiaozhi_device_id') || `web_test`,
        deviceName: localStorage.getItem('xiaozhi_device_name') || 'Web客户端',
        token: localStorage.getItem('xiaozhi_token') || ''
      }
    })(),
    autoConnect: getStoredData('websocketAutoConnect') || false
  }
}

// -------------------------
// getters（按原约定返回当前值）
// -------------------------
const rawGetters = {
  USER_INFO: (state) => state.info,
  MOBILE_TYPE: (state) => state.isMobile,
  NAVIGATION_STYLE: (state) => state.navigationStyle,
  // WebSocket相关getters
  WS_IS_CONNECTED: (state) => state.websocket.isConnected,
  WS_CONNECTION_STATUS: (state) => state.websocket.connectionStatus,
  WS_CONNECTION_TIME: (state) => state.websocket.connectionTime,
  WS_SESSION_ID: (state) => state.websocket.sessionId,
  WS_SERVER_CONFIG: (state) => state.websocket.serverConfig,
  WS_AUTO_CONNECT: (state) => state.websocket.autoConnect
}

// -------------------------
// mutations（直接修改 state）
// -------------------------
const mutations = {
  USER_INFO: (state, info) => {
    state.info = info
  },
  MOBILE_TYPE: (state, isMobile) => {
    state.isMobile = isMobile
  },
  NAVIGATION_STYLE: (state, navigationStyle) => {
    Cookies.set('navigationStyle', navigationStyle)
    state.navigationStyle = navigationStyle
  },
  // WebSocket相关mutations
  SET_WS_CONNECTION_STATUS: (state, { isConnected, status, connectionTime, sessionId }) => {
    state.websocket.isConnected = isConnected
    state.websocket.connectionStatus = status
    if (connectionTime !== undefined) {
      state.websocket.connectionTime = connectionTime
    }
    if (sessionId !== undefined) {
      state.websocket.sessionId = sessionId
    }
  },
  SET_WS_SERVER_CONFIG: (state, config) => {
    state.websocket.serverConfig = { ...state.websocket.serverConfig, ...config }
    // 保存到新的统一配置key
    localStorage.setItem('websocketConfig', JSON.stringify(state.websocket.serverConfig))
    // 同时保存到原有的分散key中（为了兼容）
    if (config.url !== undefined) {
      localStorage.setItem('xiaozhi_server_url', config.url)
    }
    if (config.deviceId !== undefined) {
      localStorage.setItem('xiaozhi_device_id', config.deviceId)
    }
    if (config.deviceName !== undefined) {
      localStorage.setItem('xiaozhi_device_name', config.deviceName)
    }
    if (config.token !== undefined) {
      localStorage.setItem('xiaozhi_token', config.token)
    }
  },
  SET_WS_AUTO_CONNECT: (state, autoConnect) => {
    state.websocket.autoConnect = autoConnect
    localStorage.setItem('websocketAutoConnect', JSON.stringify(autoConnect))
  }
}

// -------------------------
// actions（内部动态引入 websocketService）
// -------------------------
const actions = {
  // WebSocket连接动作
  async WS_CONNECT({ state }) {
    const { connectToServer } = await import('@/services/websocketService')
    try {
      // 连接状态会通过状态变更回调自动更新，这里不需要手动设置
      const success = await connectToServer(state.websocket.serverConfig)
      return success
    } catch (error) {
      console.error('WebSocket连接失败:', error)
      return false
    }
  },

  async WS_DISCONNECT() {
    const { disconnectFromServer } = await import('@/services/websocketService')
    try {
      await disconnectFromServer()
      // 断开状态会通过状态变更回调自动更新
      return true
    } catch (error) {
      console.error('断开WebSocket连接失败:', error)
      return false
    }
  },

  // 同步旧配置到新配置
  SYNC_WS_CONFIG({ state }) {
    const currentConfig = state.websocket.serverConfig

    // 如果检测到配置且统一配置不存在，则保存统一配置
    if (currentConfig && !getStoredData('websocketConfig')) {
      localStorage.setItem('websocketConfig', JSON.stringify(currentConfig))
    }
  }
}

// -------------------------
// 简易 store：提供 getters / commit / dispatch 接口
// -------------------------
const store = {
  state,
  get getters() {
    return {
      USER_INFO: rawGetters.USER_INFO(state),
      MOBILE_TYPE: rawGetters.MOBILE_TYPE(state),
      NAVIGATION_STYLE: rawGetters.NAVIGATION_STYLE(state),
      WS_IS_CONNECTED: rawGetters.WS_IS_CONNECTED(state),
      WS_CONNECTION_STATUS: rawGetters.WS_CONNECTION_STATUS(state),
      WS_CONNECTION_TIME: rawGetters.WS_CONNECTION_TIME(state),
      WS_SESSION_ID: rawGetters.WS_SESSION_ID(state),
      WS_SERVER_CONFIG: rawGetters.WS_SERVER_CONFIG(state),
      WS_AUTO_CONNECT: rawGetters.WS_AUTO_CONNECT(state)
    }
  },
  commit(type, payload) {
    const mutation = mutations[type]
    if (mutation) {
      mutation(state, payload)
    } else {
      console.warn(`[store] 未找到 mutation：${type}`)
    }
  },
  dispatch(type, payload) {
    const action = actions[type]
    if (action) {
      return action({ state, commit: this.commit.bind(this) }, payload)
    } else {
      console.warn(`[store] 未找到 action：${type}`)
      return Promise.resolve(false)
    }
  }
}

export default store
