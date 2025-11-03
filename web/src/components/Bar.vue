<template>
  <div :style="{ padding: '0' }">
    <ve-line :data="data" ref="charts" :judge-width="true"></ve-line>
  </div>
</template>

<script>
export default {
  name: "Bar",
  props: {
    data: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  data() {
    return {};
  },
};
</script>
