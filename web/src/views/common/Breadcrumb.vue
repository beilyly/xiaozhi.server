<template>
  <a-layout-header class="breadcrumb-header">
    <a-breadcrumb>
      <a-breadcrumb-item>
        <router-link to="/dashboard">首页</router-link>
      </a-breadcrumb-item>
      <a-breadcrumb-item v-if="$route.meta.parent">
        {{ $route.meta.parent }}
      </a-breadcrumb-item>
      <a-breadcrumb-item v-if="$route.meta.icon">
        {{ $route.meta.title }}
      </a-breadcrumb-item>
      <a-breadcrumb-item v-else>
        {{ $route.meta.title}}
      </a-breadcrumb-item>
    </a-breadcrumb>
    <a-page-header
      :title="$route.meta.title"
      @back="() => $router.go(-1)"
      style="padding: 0"
    >
    </a-page-header>
  </a-layout-header>
</template>

<script>
export default {
  name: 'Breadcrumb'
}
</script>

<style scoped>
.breadcrumb-header {
  background: #fff;
  padding: 16px 24px;
  border-bottom: 1px solid #e8e8e8;
}

.breadcrumb-header .ant-breadcrumb {
  margin-bottom: 16px;
}

.breadcrumb-header .ant-page-header {
  padding: 0;
}
</style>
