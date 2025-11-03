<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <a-card title="个人设置" :bordered="false">
          <!-- 导航样式设置 -->
          <a-card size="small" title="导航样式设置" style="margin-bottom: 24px">
            <a-row :gutter="24">
              <a-col :md="12" :xs="24">
                <a-form-item label="导航显示方式">
                  <a-radio-group 
                    :value="navigationStyle" 
                    @change="handleNavigationStyleChange"
                    style="width: 100%"
                  >
                    <a-radio-button value="breadcrumb" style="margin-right: 8px; margin-bottom: 8px">
                      <a-icon type="home" /> 面包屑导航
                    </a-radio-button>
                    <a-radio-button value="tabs">
                      <a-icon type="appstore" /> 浏览器标签页
                    </a-radio-button>
                  </a-radio-group>
                  <div style="margin-top: 8px; color: #666; font-size: 12px">
                    <div style="margin-bottom: 4px">
                      <strong>面包屑导航：</strong>简洁的层级导航显示，适合传统的导航习惯
                    </div>
                    <div>
                      <strong>浏览器标签页：</strong>多页面标签管理，支持快速切换和状态保持
                    </div>
                  </div>
                </a-form-item>
              </a-col>
              <a-col :md="12" :xs="24">
                <div class="style-preview">
                  <div class="preview-title">样式预览</div>
                  <!-- 面包屑预览 -->
                  <div v-if="navigationStyle === 'breadcrumb'" class="breadcrumb-preview">
                    <div class="preview-breadcrumb">
                      <a-breadcrumb separator=">">
                        <a-breadcrumb-item>首页</a-breadcrumb-item>
                        <a-breadcrumb-item>设置</a-breadcrumb-item>
                        <a-breadcrumb-item>个人设置</a-breadcrumb-item>
                      </a-breadcrumb>
                      <div class="preview-page-header">
                        <h3>个人设置</h3>
                      </div>
                    </div>
                  </div>
                  <!-- 标签页预览 -->
                  <div v-else class="tabs-preview">
                    <div class="preview-tabs">
                      <div class="tab-item">
                        <a-icon type="dashboard" /> 仪表盘
                      </div>
                      <div class="tab-item active">
                        <a-icon type="setting" /> 个人设置
                        <a-icon type="close" class="tab-close" />
                      </div>
                      <div class="tab-item">
                        <a-icon type="user" /> 用户管理
                        <a-icon type="close" class="tab-close" />
                      </div>
                    </div>
                  </div>
                </div>
              </a-col>
            </a-row>
          </a-card>
          
          <!-- WebSocket连接设置 -->
          <a-card size="small" title="WebSocket连接设置" style="margin-bottom: 24px">
            <a-row :gutter="24">
              <a-col :md="12" :xs="24">
                <a-form-item label="自动连接">
                  <a-switch
                    :checked="autoConnect"
                    @change="handleAutoConnectChange"
                    checked-children="开"
                    un-checked-children="关"
                  />
                  <div style="margin-top: 8px; color: #666; font-size: 12px">
                    开启后，登录系统时将自动建立WebSocket连接
                  </div>
                </a-form-item>
              </a-col>
              <a-col :md="12" :xs="24">
                <div class="connection-status">
                  <div class="status-title">当前连接状态</div>
                  <div class="status-info">
                    <a-tag :color="connectionStatusColor">
                      {{ connectionStatus }}
                    </a-tag>
                    <div v-if="connectionTime" class="connection-time">
                      连接时间: {{ formatConnectionTime(connectionTime) }}
                    </div>
                  </div>
                  <div class="connection-actions" style="margin-top: 8px">
                    <a-button
                      size="small"
                      :type="isConnected ? 'default' : 'primary'"
                      @click="toggleWebSocketConnection"
                      :disabled="connectionStatus === '正在连接...'"
                    >
                      <a-icon :type="isConnected ? 'disconnect' : 'link'" />
                      {{ isConnected ? '断开' : '连接' }}
                    </a-button>
                  </div>
                </div>
              </a-col>
            </a-row>
          </a-card>

          <a-row>
            <!-- 信息 -->
            <a-col :md="24" :lg="12">
              <a-form
                layout="vertical"
                :form="infoForm"
                :colon="false"
                @submit="submit"
              >
                <a-form-item label="姓名">
                  <a-input
                    v-decorator="[
                      'name'
                    ]"
                    autocomplete="off"
                    placeholder="请输入自己的姓名"
                  />
                </a-form-item>
                <a-form-item label="手机">
                  <a-input
                    v-decorator="['tel', {rules: [{ required: false, message: '请输入正确的手机号', pattern: /^1[3456789]\d{9}$/ }], validateTrigger: ['change', 'blur']}]"
                    placeholder="请输入手机号码"
                  />
                </a-form-item>
                <a-form-item label="电子邮件">
                  <a-input
                    v-decorator="['email', {rules: [{ required: false, type: 'email', message: '请输入邮箱地址' }], validateTrigger: ['change', 'blur']}]"
                    placeholder="请输入邮箱地址"
                  />
                </a-form-item>
                <a-popover
                  :placement="state.placement"
                  :trigger="['focus']"
                  :getPopupContainer="(trigger) => trigger.parentElement"
                  :visible="state.passwordLevelChecked"
                >
                  <template slot="content">
                    <div :style="{ width: '240px' }" >
                      <div :class="['user-register', passwordLevelClass]">强度：<span>{{ passwordLevelName }}</span></div>
                      <a-progress :percent="state.percent" :showInfo="false" :strokeColor=" passwordLevelColor " />
                      <div style="margin-top: 10px;">
                        <span>请至少输入 6 个字符。请不要使用容易被猜到的密码。</span>
                      </div>
                    </div>
                  </template>
                  <a-form-item label="密码">
                    <a-input-password
                      @click="passwordInputClick"
                      :visibilityToggle="false"
                      placeholder="至少6位密码，区分大小写"
                      v-decorator="['password', {rules: [{ required: state.passwordRequire, message: '至少6位密码，区分大小写'}, { validator: passwordLevel }], validateTrigger: ['change', 'blur']}]"
                    ></a-input-password>
                  </a-form-item>
                </a-popover>
                <a-form-item label="确认密码">
                  <a-input-password
                    placeholder="确认密码"
                    :visibilityToggle="false"
                    v-decorator="['password2', {rules: [{ required: state.passwordRequire, message: '至少6位密码，区分大小写' }, { validator: passwordCheck }], validateTrigger: ['change', 'blur']}]"
                  ></a-input-password>
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" html-type="submit">保存</a-button>
                </a-form-item>
              </a-form>
            </a-col>
            <a-col :md="24" :lg="12">
              <div class="ant-upload-preview" @click="$refs.modal.edit(1)" >
                <a-icon type="cloud-upload-o" class="upload-icon"/>
                <div class="mask">
                  <a-icon type="plus" />
                </div>
                <img :src="user.avatar"/>
              </div>
            </a-col>
          </a-row>
        </a-card>
        <!-- 头像 -->
        <avatar-modal ref="modal" @ok="setavatar" />
      </div>
    </a-layout-content>
  </a-layout>
</template>

<script>
import axios from '@/services/axios'
import api from '@/services/api'
import AvatarModal from './AvatarModal'
import Cookies from 'js-cookie'

const levelNames = {
  0: '低',
  1: '低',
  2: '中',
  3: '强'
}
const levelClass = {
  0: 'error',
  1: 'error',
  2: 'warning',
  3: 'success'
}
const levelColor = {
  0: '#ff0000',
  1: '#ff0000',
  2: '#ff7e05',
  3: '#52c41a'
}

export default {
  components: {
    AvatarModal
  },
  data () {
    return {
      // 密码状态
      state: {
        placement: 'rightTop',
        passwordLevel: 0,
        passwordLevelChecked: false,
        percent: 10,
        progressColor: '#FF0000',
        passwordRequire: false
      },
      // 用户信息
      user: {},
      infoForm: this.$form.createForm(this)
    }
  },
  computed: {
    /* 判断设备 */
    isMobile () {
      return this.$store.getters.MOBILE_TYPE
    },
    /* 人员信息 */
    info () {
      return this.$store.getters.USER_INFO
    },
    /* 导航样式 */
    navigationStyle () {
      return this.$store.getters.NAVIGATION_STYLE
    },

    /* WebSocket相关状态 */
    autoConnect () {
      return this.$store.getters.WS_AUTO_CONNECT
    },
    isConnected () {
      return this.$store.getters.WS_IS_CONNECTED
    },
    connectionStatus () {
      return this.$store.getters.WS_CONNECTION_STATUS
    },
    connectionTime () {
      return this.$store.getters.WS_CONNECTION_TIME
    },
    serverConfig () {
      return this.$store.getters.WS_SERVER_CONFIG
    },
    connectionStatusColor () {
      if (this.isConnected) return 'green'
      if (this.connectionStatus && (
        this.connectionStatus.includes('错误') ||
        this.connectionStatus.includes('失败')
      )) return 'red'
      if (this.connectionStatus === '正在连接...') return 'blue'
      return 'default'
    },

    /* 密码状态 */
    passwordLevelClass () {
      return levelClass[this.state.passwordLevel]
    },
    passwordLevelName () {
      return levelNames[this.state.passwordLevel]
    },
    passwordLevelColor () {
      return levelColor[this.state.passwordLevel]
    }
  },
  created () {
    this.user = this.info
  },
  methods: {
    /* 自定义验证规则 */
    passwordLevel (rules, value, callback) {
      let level = 0
      if (!value) {
        this.state.passwordRequire = false
        return callback()
      } else {
        this.state.passwordRequire = true
      }
      // 判断这个字符串中有没有数字
      if (/[0-9]/.test(value)) {
        level++
      }
      // 判断字符串中有没有字母
      if (/[a-zA-Z]/.test(value)) {
        level++
      }
      // 判断字符串中有没有特殊符号
      if (/[^0-9a-zA-Z_]/.test(value)) {
        level++
      }
      // 判断字符串长度
      if (value.length < 6) {
        level = 0
      }
      this.state.passwordLevel = level
      this.state.percent = level * 30
      if (level >= 2) {
        if (level >= 3) {
          this.state.percent = 100
        }
        callback()
      } else {
        if (level === 0) {
          this.state.percent = 10
        }
        callback(new Error('密码强度不够'))
      }
    },
    /* 二次密码确认 */
    passwordCheck (rule, value, callback) {
      const password = this.infoForm.getFieldValue('password')
      if (value && password && value.trim() !== password.trim()) {
        callback(new Error('两次密码不一致'))
      }
      callback()
    },
    /* 手机端则不显示密码强度框 */
    passwordInputClick () {
      if (this.isMobile) {
        this.state.passwordLevelChecked = false
      } else {
        this.state.passwordLevelChecked = true
      }
    },
    /* 提交按钮 */
    submit (e) {
      e.preventDefault()
      this.infoForm.validateFields((err, values) => {
        if (!err && this.infoForm.isFieldsTouched()) {
          axios
            .jsonPost({
              url: api.user.update,
              data: {
                username: this.user.username,
                ...values
              }
            }).then(res => {
              if (res.code === 200) {
                localStorage.setItem('userInfo', JSON.stringify(res.data), { expires: 30 })
                this.$store.commit('USER_INFO', res.data)
                this.$message.success(res.message)
              } else {
                this.showError(res.message)
              }
            }).catch(() => {
              this.showError();
            })
        }
      })
    },
    setavatar (url) {
      this.user.avatar = url
      localStorage.setItem('userInfo', JSON.stringify(this.user), { expires: 30 })
      this.$store.commit('USER_INFO', this.user)
    },
    /* 处理导航样式变化 */
    handleNavigationStyleChange (e) {
      const newStyle = e.target.value
      const styleName = newStyle === 'breadcrumb' ? '面包屑导航' : '浏览器标签页'

      this.$store.commit('NAVIGATION_STYLE', newStyle)

      // 显示成功消息和提示
      this.$message.success(`已切换为${styleName}`)

      // 延迟显示重新加载提示
      setTimeout(() => {
        this.$notification.info({
          message: '导航样式已更新',
          description: '为了获得最佳体验，建议刷新页面应用新的导航样式。',
          duration: 4.5,
          btn: h => {
            return h('a-button', {
              props: {
                type: 'primary',
                size: 'small'
              },
              on: {
                click: () => {
                  this.$notification.destroy()
                  window.location.reload()
                }
              }
            }, '立即刷新')
          }
        })
      }, 1000)
    },

    /* WebSocket连接相关方法 */
    handleAutoConnectChange (checked) {
      this.$store.commit('SET_WS_AUTO_CONNECT', checked)
      this.$message.success(`已${checked ? '开启' : '关闭'}WebSocket自动连接`)
    },

    async connectWebSocket () {
      try {
        const success = await this.$store.dispatch('WS_CONNECT')
        if (success) {
          this.$message.success('WebSocket连接成功')
        } else {
          this.$message.error('WebSocket连接失败')
        }
      } catch (error) {
        this.$message.error(`WebSocket连接错误: ${error.message}`)
      }
    },

    async disconnectWebSocket () {
      try {
        await this.$store.dispatch('WS_DISCONNECT')
        this.$message.success('WebSocket连接已断开')
      } catch (error) {
        this.$message.error(`WebSocket断开失败: ${error.message}`)
      }
    },

    async toggleWebSocketConnection() {
      if (this.isConnected) {
        await this.disconnectWebSocket()
      } else {
        await this.connectWebSocket()
      }
    },

    formatConnectionTime (time) {
      if (!time) return ''
      const date = new Date(time)
      return date.toLocaleString()
    }
  }
}
</script>

<style lang="scss" scoped>
.ant-form-vertical >>> .ant-form-item {
  margin-bottom: 24px;
}

.ant-upload-preview {
    position: relative;
    margin: 0 auto;
    width: 100%;
    max-width: 180px;
    border-radius: 50%;
    box-shadow: 0 0 4px #ccc;

    .upload-icon {
      position: absolute;
      top: 0;
      right: 10px;
      font-size: 1.4rem;
      padding: 0.5rem;
      background: rgba(222, 221, 221, 0.7);
      border-radius: 50%;
      border: 1px solid rgba(0, 0, 0, 0.2);
    }
    .mask {
      opacity: 0;
      position: absolute;
      background: rgba(0,0,0,0.4);
      cursor: pointer;
      transition: opacity 0.4s;

      &:hover {
        opacity: 1;
      }

      i {
        font-size: 2rem;
        position: absolute;
        top: 50%;
        left: 50%;
        margin-left: -1rem;
        margin-top: -1rem;
        color: #d6d6d6;
      }
    }

    img, .mask {
      width: 100%;
      max-width: 180px;
      height: 100%;
      border-radius: 50%;
      overflow: hidden;
    }
  }

.user-register {

  &.error {
    color: #ff0000;
  }

  &.warning {
    color: #ff7e05;
  }

  &.success {
    color: #52c41a;
  }

}

/* 导航样式预览 */
.style-preview {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  background: #fafafa;

  .preview-title {
    padding: 12px 16px;
    background: #f5f5f5;
    border-bottom: 1px solid #e8e8e8;
    font-weight: 500;
    font-size: 14px;
    color: #333;
  }
}

/* 面包屑预览样式 */
.breadcrumb-preview {
  padding: 16px;
  background: #fff;

  .preview-breadcrumb {
    .ant-breadcrumb {
      margin-bottom: 16px;
    }

    .preview-page-header {
      h3 {
        margin: 0;
        color: #333;
        font-size: 18px;
        font-weight: 500;
      }
    }
  }
}

/* 标签页预览样式 */
.tabs-preview {
  padding: 16px;
  background: #fff;

  .preview-tabs {
    display: flex;
    border-bottom: 1px solid #e8e8e8;

    .tab-item {
      display: flex;
      align-items: center;
      padding: 8px 16px;
      border: 1px solid #e8e8e8;
      border-bottom: none;
      border-radius: 4px 4px 0 0;
      background: #f5f5f5;
      margin-right: 4px;
      font-size: 14px;
      color: #666;
      position: relative;

      .anticon:first-child {
        margin-right: 6px;
      }

      .tab-close {
        margin-left: 8px;
        font-size: 12px;
        color: #999;

        &:hover {
          color: #ff4d4f;
        }
      }

      &.active {
        background: #fff;
        border-color: #1890ff;
        color: #333;
        z-index: 1;

        &::after {
          content: '';
          position: absolute;
          bottom: -1px;
          left: 0;
          right: 0;
          height: 2px;
          background: #1890ff;
        }
      }
    }
  }
}

/* WebSocket连接状态样式 */
.connection-status {
  .status-title {
    font-weight: 500;
    margin-bottom: 8px;
    color: #333;
  }

  .status-info {
    .connection-time {
      margin-top: 4px;
      font-size: 12px;
      color: #666;
    }
  }

  .connection-actions {
    display: flex;
    gap: 8px;
  }
}
</style>
