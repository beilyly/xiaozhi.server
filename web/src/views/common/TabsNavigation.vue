<template>
  <div class="tabs-navigation">
    <a-tabs 
      :activeKey="currentPath" 
      type="editable-card" 
      @change="switchTab"
      @edit="handleTabEdit"
      :hideAdd="true"
    >
      <a-tab-pane 
        v-for="tab in tabs" 
        :key="tab.path" 
        :closable="tab.closable"
      >
        <span slot="tab">
          <a-icon v-if="tab.icon" :type="tab.icon" />
          {{ tab.title }}
        </span>
      </a-tab-pane>
      
      <!-- 标签页操作菜单 -->
      <div slot="tabBarExtraContent" class="tabs-actions">
        <a-dropdown placement="bottomRight">
          <a-button size="small" type="text">
            <a-icon type="more" />
          </a-button>
          <a-menu slot="overlay">
            <a-menu-item key="refresh" @click="refreshCurrentTab">
              <a-icon type="reload" />
              刷新当前页
            </a-menu-item>
            <a-menu-item key="close-other" @click="closeOtherTabs">
              <a-icon type="close" />
              关闭其他标签页
            </a-menu-item>
            <a-menu-item key="close-all" @click="closeAllTabs">
              <a-icon type="close-circle" />
              关闭所有标签页
            </a-menu-item>
          </a-menu>
        </a-dropdown>
      </div>
    </a-tabs>
  </div>
</template>

<script>
export default {
  name: 'TabsNavigation',
  data() {
    return {
      tabs: [],
      currentPath: '',
      saveTimer: null
    }
  },
  computed: {
    // 根据用户角色动态设置不可关闭的标签页
    unclosableTabs() {
      const userInfo = this.$store.getters.USER_INFO
      const isAdmin = userInfo && userInfo.isAdmin == 1
      return isAdmin ? ['/dashboard'] : ['/agents']
    },
    
    // 根据用户信息生成存储键名
    storageKey() {
      const userInfo = this.$store.getters.USER_INFO
      const userId = userInfo ? userInfo.id || userInfo.userId : 'anonymous'
      return `tabs-navigation-state-${userId}`
    }
  },
  mounted() {
    this.initTabs()
    this.currentPath = this.$route.path
  },
  beforeDestroy() {
    // 清理定时器
    if (this.saveTimer) {
      clearTimeout(this.saveTimer)
    }
    // 保存标签页状态
    this.saveTabsState()
  },
  watch: {
    $route: {
      handler(newRoute, oldRoute) {
        this.addTab(newRoute)
        this.currentPath = newRoute.path
        // 延迟保存标签页状态，避免频繁保存
        this.debouncedSave()
      },
      immediate: true
    },
    // 监听导航样式变化，如果切换到面包屑模式，清理标签页状态
    '$store.getters.NAVIGATION_STYLE': {
      handler(newStyle, oldStyle) {
        if (oldStyle === 'tabs' && newStyle === 'breadcrumb') {
          // 从标签页切换到面包屑，保存当前状态
          this.saveTabsState()
        } else if (oldStyle === 'breadcrumb' && newStyle === 'tabs') {
          // 从面包屑切换到标签页，恢复标签页状态
          this.loadTabsState()
          // 确保当前页面在标签页中
          if (!this.tabs.find(tab => tab.path === this.$route.path)) {
            this.addTab(this.$route)
          }
        }
      }
    }
  },
  methods: {
    // 初始化标签页
    initTabs() {
      // 从本地存储恢复标签页状态
      this.loadTabsState()
      
      // 如果没有加载到任何标签页，或者当前路由不在标签页中，则添加当前路由
      if (this.tabs.length === 0 || !this.tabs.find(tab => tab.path === this.$route.path)) {
        this.addTab(this.$route)
      }
    },
    
    // 保存标签页状态到本地存储
    saveTabsState() {
      try {
        const state = {
          tabs: this.tabs,
          currentPath: this.currentPath,
          timestamp: Date.now()
        }
        localStorage.setItem(this.storageKey, JSON.stringify(state))
      } catch (error) {
        console.warn('保存标签页状态失败:', error)
      }
    },
    
    // 防抖保存
    debouncedSave() {
      clearTimeout(this.saveTimer)
      this.saveTimer = setTimeout(() => {
        this.saveTabsState()
      }, 300)
    },
    
    // 从本地存储加载标签页状态
    loadTabsState() {
      try {
        const stateStr = localStorage.getItem(this.storageKey)
        if (stateStr) {
          const state = JSON.parse(stateStr)
          // 检查数据是否过期（24小时）
          const isExpired = Date.now() - state.timestamp > 24 * 60 * 60 * 1000
          if (!isExpired && state.tabs && Array.isArray(state.tabs)) {
            // 重新设置每个标签页的 closable 属性（因为用户角色可能变化）
            this.tabs = state.tabs.map(tab => ({
              ...tab,
              closable: !this.unclosableTabs.includes(tab.path)
            }))
          }
        }
      } catch (error) {
        console.warn('加载标签页状态失败:', error)
      }
    },
    
    // 添加标签页
    addTab(route) {
      const { path, meta } = route
      
      // 检查标签页是否已存在
      const existingTab = this.tabs.find(tab => tab.path === path)
      if (existingTab) {
        // 更新已存在的标签页信息
        existingTab.title = meta.title || '未知页面'
        existingTab.icon = meta.icon
        existingTab.closable = !this.unclosableTabs.includes(path)
        return
      }
      
      // 创建新标签页
      const newTab = {
        path,
        title: meta.title || '未知页面',
        icon: meta.icon,
        closable: !this.unclosableTabs.includes(path)
      }
      
      this.tabs.push(newTab)
      
      // 延迟保存标签页状态
      this.debouncedSave()
    },
    
    // 切换标签页
    switchTab(path) {
      if (path !== this.currentPath) {
        this.$router.push(path)
      }
    },
    
    // 处理标签页编辑（关闭）
    handleTabEdit(targetKey, action) {
      if (action === 'remove') {
        const tabIndex = this.tabs.findIndex(tab => tab.path === targetKey)
        if (tabIndex > -1) {
          this.closeTab(this.tabs[tabIndex], tabIndex)
        }
      }
    },
    
    // 关闭标签页
    closeTab(tab, index) {
      if (!tab.closable) {
        this.$message.warning('该标签页无法关闭')
        return
      }
      
      // 如果关闭的是当前标签页，需要切换到其他标签页
      if (tab.path === this.currentPath) {
        // 切换到前一个或后一个标签页
        const nextIndex = index > 0 ? index - 1 : index + 1
        if (this.tabs[nextIndex]) {
          this.$router.push(this.tabs[nextIndex].path)
        } else {
          // 如果没有其他标签页，跳转到默认页面
          const userInfo = this.$store.getters.USER_INFO
          const defaultPath = userInfo && userInfo.isAdmin == 1 ? '/dashboard' : '/agents'
          this.$router.push(defaultPath)
        }
      }
      
      // 移除标签页
      this.tabs.splice(index, 1)
      
      // 延迟保存标签页状态
      this.debouncedSave()
    },
    
    // 刷新当前标签页
    refreshCurrentTab() {
      this.$router.go(0)
    },
    
    // 关闭其他标签页
    closeOtherTabs() {
      const currentTab = this.tabs.find(tab => tab.path === this.currentPath)
      if (currentTab) {
        this.tabs = [currentTab]
        this.debouncedSave()
      }
    },
    
    // 关闭所有标签页
    closeAllTabs() {
      // 保留不可关闭的标签页
      this.tabs = this.tabs.filter(tab => !tab.closable)
      
      // 如果当前页面被关闭，跳转到默认页面
      const currentTabExists = this.tabs.some(tab => tab.path === this.currentPath)
      if (!currentTabExists) {
        const userInfo = this.$store.getters.USER_INFO
        const defaultPath = userInfo && userInfo.isAdmin == 1 ? '/dashboard' : '/agents'
        this.$router.push(defaultPath)
      }
      
      // 延迟保存标签页状态
      this.debouncedSave()
    }
  }
}
</script>

<style scoped lang="scss">
.tabs-navigation {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  
  // 自定义 Ant Design 标签页样式
  ::v-deep .ant-tabs {
    .ant-tabs-bar {
      margin: 0;
      border-bottom: none;
    }
    
    .ant-tabs-tab {
      margin-right: 4px;
      padding: 8px 16px;
      border-radius: 4px 4px 0 0;
      transition: all 0.3s;
      
      &:hover {
        color: #1890ff;
      }
      
      &.ant-tabs-tab-active {
        background: #f0f2f5;
        border-color: #1890ff;
        
        &::before {
          border-color: #1890ff;
        }
      }
    }
    
    .ant-tabs-tab-btn {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    
    .ant-tabs-content-holder {
      display: none; // 隐藏内容区域，因为我们只使用标签页导航
    }
  }
}

.tabs-actions {
  display: flex;
  align-items: center;
  margin-left: 12px;
  
  .ant-btn {
    border: none;
    box-shadow: none;
    
    &:hover {
      background: #f0f0f0;
    }
  }
}
</style> 