<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <a-row :gutter="[20, 20]">
          <a-col :md="24" :lg="7">
            <a-card :bordered="false">
              <div class="account-center-avatarHolder">
                <div class="avatar">
                  <img :src="user.avatar" />
                </div>
                <div class="name">{{ user.name }}</div>
                <div class="username">{{ user.userName }}</div>
              </div>
              <a-divider />
              <div class="account-center-detail">
                <div class="detailTitle">个人信息</div>
                <p>账户: {{ 'Admin' }}</p>
                <p>注册时间: {{ user.createTime }}</p>
                <p>手机: {{ user.tel }}</p>
                <p>邮箱: {{ user.email }}</p>
              </div>
              <a-divider />
              <div class="account-center-detail">
                <div class="detailTitle">安全信息</div>
                <p>上次登录地点: {{ user.address }}</p>
                <p>上次登录时间: {{ user.loginTime }}</p>
              </div>
            </a-card>
          </a-col>
          <a-col :md="24" :lg="17">
            <a-card :bordered="false">
              <a-skeleton :loading="loading" active :paragraph="{ rows: 10 }">
                <bar :data="data" title="对话统计" />
              </a-skeleton>
            </a-card>
          </a-col>
        </a-row>
      </div>
    </a-layout-content>
  </a-layout>
</template>

<script>
import axios from '@/services/axios'
import api from '@/services/api'
import Bar from '@/components/Bar'

export default {
  components: {
    Bar
  },
  data () {
    return {
      data: {
        columns: ['日期', '用户数', '设备', '对话'],
        rows: [
          { 日期: '4/18', 用户数: 1111, 设备: 1000, 对话: 900 },
          { 日期: '5/18', 用户数: 2222, 设备: 1600, 对话: 987 },
          { 日期: '6/17', 用户数: 2333, 设备: 1700, 对话: 1200 },
          { 日期: '7/17', 用户数: 4444, 设备: 1600, 对话: 1300 },
          { 日期: '8/16', 用户数: 5555, 设备: 1800, 对话: 1100 },
          { 日期: '9/18', 用户数: 6666, 设备: 1500, 对话: 1450 }
        ]
      },
      user: {},
      // 遮罩层
      loading: false
    }
  },
  computed: {
    info () {
      return this.$store.getters.USER_INFO
    }
  },
  created () {
    this.user = this.info
  },
  methods: {}
}
</script>

<style lang="scss" scoped>
.account-center-avatarHolder {
  text-align: center;
  margin-bottom: 24px;

  & > .avatar {
    margin: 0 auto;
    width: 104px;
    height: 104px;
    margin-bottom: 20px;
    border-radius: 50%;
    overflow: hidden;
    img {
      height: 100%;
      width: 100%;
    }
  }

  .name {
    color: rgba(0, 0, 0, 0.85);
    font-size: 20px;
    line-height: 28px;
    font-weight: 500;
    margin-bottom: 4px;
  }
}
.account-center-detail {
  p {
    margin-bottom: 8px;
    // padding-left: 26px;
    position: relative;
  }
}
.detailTitle {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 12px;
}
</style>
