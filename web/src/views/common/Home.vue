<template>
  <div class="wrapper">
    <!-- <a-layout v-if="!!userInfo"> -->
    <a-layout>
      <!-- 占位 -->
      <div :style="`width: ${width}px; overflow: hidden; flex: 0 0 ${width}px; max-width: ${width}px; min-width: ${width}px;`"></div>
      <!-- 侧边栏 -->
      <a-layout-sider
        breakpoint="sm"
        collapsed-width="0"
        @breakpoint="onBreakpoint($event, 'sm')"
        style="display: none"
      >
      </a-layout-sider>
      <a-layout-sider
        theme="light"
        breakpoint="lg"
        :collapsed-width="collapseWidth"
        @breakpoint="onBreakpoint($event, 'lg')"
        class="fixed-sidebar"
        :zeroWidthTriggerStyle="{ top: '100px' }"
      >
        <v-sidebar></v-sidebar>
      </a-layout-sider>
      <a-layout>
        <!-- 页眉 -->
        <a-layout-header style="padding: 0; height: auto; line-height: auto">
          <v-header></v-header>
        </a-layout-header>
        <a-layout>
          <!-- 主要内容 -->
          <a-layout-content>
            <!-- 根据用户选择渲染不同的导航组件 -->
            <v-breadcrumb v-if="navigationStyle === 'breadcrumb'"></v-breadcrumb>
            <v-tabs-navigation v-else></v-tabs-navigation>
            <router-view />
          </a-layout-content>
          <!-- 页脚 -->
          <a-layout-footer>
            <v-footer></v-footer>
          </a-layout-footer>
        </a-layout>
      </a-layout>
    </a-layout>

    <!-- 全局聊天功能 -->
    <template v-if="showGlobalChat">
      <!-- 聊天按钮 -->
      <GlobalChatButton
        :is-active="showChatPopup"
        :unread-count="unreadMessageCount"
        @click="toggleChatPopup"
      />

      <!-- 聊天弹框 -->
      <GlobalChatPopup
        :visible="showChatPopup"
        @close="closeChatPopup"
        @go-to-chat="goToChatPage"
      />
    </template>
  </div>
</template>

<script>
import vSidebar from './Sidebar.vue'
import vHeader from './Header.vue'
import vFooter from './Footer.vue'
import vBreadcrumb from './Breadcrumb.vue'
import vTabsNavigation from './TabsNavigation.vue'

import { messages } from '@/services/websocketService'
import websocketMixin from '@/mixins/websocketMixin'

import GlobalChatButton from '@/components/GlobalChatButton'
import GlobalChatPopup from '@/components/GlobalChatPopup'

export default {
  mixins: [websocketMixin],
  components: {
    vHeader,
    vSidebar,
    vFooter,
    vBreadcrumb,
    vTabsNavigation,
    GlobalChatButton,
    GlobalChatPopup
  },
  data () {
    return {
      // 占位宽度
      width: 0,
      // 折叠宽度
      collapseWidth: 0,
      // 判断是否为手机
      clientWidth: document.body.clientWidth,
      // 全局聊天状态
      showChatPopup: false,
      unreadMessageCount: 0,
      lastMessageCount: 0
    }
  },
  mounted () {
    window.onresize = () => {
      return (() => {
        this.clientWidth = document.body.clientWidth
      })()
    }
    // 没有登录过或者已退出登录的情况下直接访问页面会跳转到登录页面
    if (!this.userInfo) this.$router.push('/login')

    // 设置全局WebSocket配置获取函数
    window.__getWebSocketConfig = () => {
      return this.$store.getters.WS_SERVER_CONFIG
    }

    // 同步WebSocket配置
    this.$store.dispatch('SYNC_WS_CONFIG')

    // 检查自动连接
    this.checkAndAutoConnect()

    // 监听页面卸载
    window.addEventListener('beforeunload', this.handleBeforeUnload)
  },
  beforeDestroy() {
    // 清理全局函数
    if (window.__getWebSocketConfig) {
      delete window.__getWebSocketConfig
    }

    // 清理事件监听器
    window.removeEventListener('beforeunload', this.handleBeforeUnload)
  },
  computed: {
    isMobile () {
      return this.$store.getters.MOBILE_TYPE
    },
    userInfo () {
      return this.$store.getters.USER_INFO
    },
    navigationStyle() {
      // 获取用户选择的导航样式
      return this.$store.getters.NAVIGATION_STYLE
    },
    // 是否显示全局聊天
    showGlobalChat() {
      // 只有在用户已登录且不在聊天页面时才显示
      const isLoggedIn = !!this.userInfo
      const isInChatPage = this.$route.name === 'Chat'
      return isLoggedIn && !isInChatPage
    },

    // 消息数量
    messageCount() {
      return messages.length
    }
  },
  watch: {
    clientWidth (newVal, oldVal) {
      this.$store.commit('MOBILE_TYPE', newVal < 480)
    },
    // 监听用户登录状态变化
    userInfo: {
      handler(newUser, oldUser) {
        if (newUser && !oldUser) {
          // 用户刚登录，检查是否需要自动连接
          this.checkAndAutoConnect()
        } else if (!newUser && oldUser) {
          // 用户退出登录，断开WebSocket连接
          this.$store.dispatch('WS_DISCONNECT')
          // 关闭聊天弹框
          this.closeChatPopup()
        }
      },
      immediate: true
    },

    // 监听路由变化
    '$route'(to, from) {
      // 如果从聊天页面离开，重置未读消息计数
      if (from.name === 'Chat' && to.name !== 'Chat') {
        this.resetUnreadCount()
      }
      // 如果进入聊天页面，关闭弹框
      if (to.name === 'Chat') {
        this.closeChatPopup()
      }
    },

    // 监听消息数量变化
    messageCount: {
      handler(newCount) {
        if (newCount > this.lastMessageCount && !this.showChatPopup && this.$route.name !== 'Chat') {
          // 有新消息且弹框未开启且不在聊天页面
          this.unreadMessageCount = newCount - this.lastMessageCount
        }
        this.lastMessageCount = newCount
      },
      immediate: true
    }
  },
  methods: {
    /* 侧边栏切换操作 */
    onCollase (collapsed, type) {
      this.collapseWidth = 80
      this.width = 80
      if (type === 'lg' && !collapsed) {
        this.collapseWidth = 200
        this.width = 200
        this.siderCheck = true
      } else if ((type === 'sm' && collapsed) || this.isMobile) {
        this.collapseWidth = 0
        this.width = 0
      }
    },
    onBreakpoint (broken, type) {
      this.onCollase(broken, type)
    },

    // WebSocket相关方法
    // WebSocket消息处理（由mixin调用）
    handleWebSocketMessage(data) {
      // 更新会话ID
      if (data.session_id && data.session_id !== this.$store.getters.WS_SESSION_ID) {
        this.$store.commit('SET_WS_CONNECTION_STATUS', {
          isConnected: this.$store.getters.WS_IS_CONNECTED,
          status: this.$store.getters.WS_CONNECTION_STATUS,
          sessionId: data.session_id
        })
      }

      // 这里可以添加其他全局消息处理逻辑
      console.log('全局WebSocket消息:', data)
    },



    // 处理二进制音频消息（由mixin调用）
    handleBinaryAudioMessage(audioData) {
      // 这里可以添加全局音频处理逻辑
      // 或者转发给当前活动的音频处理组件
      if (typeof window.currentAudioHandler === 'function') {
        window.currentAudioHandler(audioData)
      }
    },

    // 处理WebSocket状态变更（由mixin调用）
    handleWebSocketStatusChange(status) {
      // 同步websocketService的状态到store
      this.$store.commit('SET_WS_CONNECTION_STATUS', {
        isConnected: status.isConnected,
        status: status.connectionStatus,
        connectionTime: status.connectionTime,
        sessionId: status.sessionId
      })
    },

    // 处理页面可见性变化（由mixin处理）
    // handleVisibilityChange方法现在由websocketMixin提供

    // 处理页面卸载前
    handleBeforeUnload() {
      // 注意：这里不主动断开连接，让用户在重新打开页面时能快速恢复
      console.log('页面即将卸载，保持WebSocket连接')
    },

    // 全局聊天相关方法
    // 切换聊天弹框
    toggleChatPopup() {
      this.showChatPopup = !this.showChatPopup
      if (this.showChatPopup) {
        // 打开弹框时重置未读消息计数
        this.resetUnreadCount()
      }
    },

    // 关闭聊天弹框
    closeChatPopup() {
      this.showChatPopup = false
    },

    // 跳转到聊天页面
    goToChatPage() {
      this.closeChatPopup()
      this.$router.push({ name: 'Chat' }).catch(err => {
        if (err.name !== 'NavigationDuplicated') {
          console.error('跳转到聊天页面失败:', err)
        }
      })
    },

    // 重置未读消息计数
    resetUnreadCount() {
      this.unreadMessageCount = 0
      this.lastMessageCount = this.messageCount
    },


  }
}
</script>

<style scoped lang="scss">
.wrapper {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100%;
}
.fixed-sidebar {
  box-shadow: 0 2px 8px #f0f1f2;
  height: 100vh;
  z-index: 99;
  position: fixed;
  left: 0;
}
</style>
