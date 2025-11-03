<template>
  <a-config-provider :locale="locale.zh_CN">
    <div id="app">
      <router-view></router-view>
    </div>
  </a-config-provider>
</template>

<script>
export default {
  data () {
    return {
      locale: window.antd.locales
    }
  }
}
</script>
