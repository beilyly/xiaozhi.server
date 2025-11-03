import Vue from 'vue'
import Vuex from 'vuex'
import Cookies from 'js-cookie'

Vue.use(Vuex)

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

const store = new Vuex.Store({
  state: {
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
        const unifiedConfig = getStoredData('websocketConfig');
        if (unifiedConfig) {
          return unifiedConfig;
        }

        // 如果没有统一配置，读取分散的配置
        return {
          url: localStorage.getItem('xiaozhi_server_url') || 'ws://127.0.0.1:8091/ws/xiaozhi/v1/',
          deviceId: localStorage.getItem('xiaozhi_device_id') || `web_test`,
          deviceName: localStorage.getItem('xiaozhi_device_name') || 'Web客户端',
          token: localStorage.getItem('xiaozhi_token') || ''
        };
      })(),
      autoConnect: getStoredData('websocketAutoConnect') || false
    }
  },
  getters: {
    USER_INFO: state => {
      return state.info
    },
    MOBILE_TYPE: state => {
      return state.isMobile
    },
    NAVIGATION_STYLE: state => {
      return state.navigationStyle
    },
    // WebSocket相关getters
    WS_IS_CONNECTED: state => state.websocket.isConnected,
    WS_CONNECTION_STATUS: state => state.websocket.connectionStatus,
    WS_CONNECTION_TIME: state => state.websocket.connectionTime,
    WS_SESSION_ID: state => state.websocket.sessionId,
    WS_SERVER_CONFIG: state => state.websocket.serverConfig,
    WS_AUTO_CONNECT: state => state.websocket.autoConnect
  },
  mutations: {
    USER_INFO: (state, info) => {
      state.info = info
    },
    MOBILE_TYPE: (state, isMobile) => {
      state.isMobile = isMobile
    },
    NAVIGATION_STYLE: (state, navigationStyle) => {
      Cookies.set('navigationStyle', JSON.stringify(navigationStyle))
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
  },
  actions: {
    // WebSocket连接动作
    async WS_CONNECT({ commit, state }) {
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

    async WS_DISCONNECT({ commit }) {
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
    SYNC_WS_CONFIG({ commit, state }) {
      const currentConfig = state.websocket.serverConfig

      // 如果检测到配置且统一配置不存在，则保存统一配置
      if (currentConfig && !getStoredData('websocketConfig')) {
        localStorage.setItem('websocketConfig', JSON.stringify(currentConfig))
      }
         }
   }
 })

export default store
