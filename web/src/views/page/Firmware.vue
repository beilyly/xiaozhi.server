<template>
  <section class="firmware-page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">Firmware OTA</span>
        <h1>固件上传管理</h1>
        <p>版本号随固件文件绑定，上传后设备 OTA 会优先读取最新固件文件名中的版本。</p>
      </div>
      <a-button :loading="loading" @click="loadFiles">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </header>

    <div class="summary-grid">
      <div class="metric-panel metric-primary">
        <span>当前版本</span>
        <strong>{{ latestFile?.version || '未绑定' }}</strong>
        <small>{{ latestFile?.fileName || '暂无固件' }}</small>
      </div>
      <div class="metric-panel">
        <span>固件数量</span>
        <strong>{{ firmwareFiles.length }}</strong>
        <small>服务端文件记录</small>
      </div>
      <div class="metric-panel">
        <span>占用空间</span>
        <strong>{{ formatFileSize(totalSize) }}</strong>
        <small>全部固件合计</small>
      </div>
    </div>

    <div class="workspace-grid">
      <section class="panel upload-panel">
        <div class="panel-heading">
          <div>
            <h2>上传新固件</h2>
            <p>推荐文件名：firmware_v2.3.0.bin</p>
          </div>
          <FileProtectOutlined class="panel-icon" />
        </div>

        <a-upload-dragger
          class="firmware-uploader"
          accept=".bin"
          :before-upload="beforeUpload"
          :disabled="uploading"
          :max-count="1"
          :multiple="false"
          :show-upload-list="false"
        >
          <p class="ant-upload-drag-icon"><InboxOutlined /></p>
          <p class="ant-upload-text">拖拽或点击选择 .bin 固件</p>
          <p class="ant-upload-hint">选择后会自动识别文件名中的 v版本号</p>
        </a-upload-dragger>

        <div v-if="selectedFile" class="selected-file">
          <div class="selected-main">
            <CheckCircleOutlined />
            <div>
              <strong>{{ selectedFile.name }}</strong>
              <span>{{ formatFileSize(selectedFile.size) }}</span>
            </div>
          </div>
          <a-button type="text" :disabled="uploading" @click="clearSelectedFile">
            移除
          </a-button>
        </div>

        <a-form layout="vertical" class="version-form">
          <a-form-item
            label="绑定版本号"
            :validate-status="versionValidateStatus"
            :help="versionHelp"
          >
            <a-input
              v-model:value="version"
              size="large"
              placeholder="例如 2.3.0 或 v2.3.0"
              :disabled="uploading"
              @change="versionTouched = true"
              @press-enter="handleUpload"
            />
          </a-form-item>
        </a-form>

        <a-alert
          v-if="versionConflict"
          type="warning"
          show-icon
          :message="'版本 ' + normalizedVersion + ' 已存在'"
          :description="'现有文件：' + versionConflict.fileName"
        />

        <a-progress
          v-if="uploading"
          :percent="uploadProgress"
          :show-info="true"
          status="active"
        />

        <div class="upload-actions">
          <a-button :disabled="uploading" @click="resetUpload">重置</a-button>
          <a-button type="primary" :loading="uploading" :disabled="uploadDisabled" @click="handleUpload">
            <template #icon><CloudUploadOutlined /></template>
            上传并绑定
          </a-button>
        </div>
      </section>

      <section class="panel list-panel">
        <div class="panel-heading table-heading">
          <div>
            <h2>固件列表</h2>
            <p>最新上传的版本会作为 OTA 当前版本。</p>
          </div>
          <a-tag v-if="latestFile?.version" color="green">当前 {{ latestFile.version }}</a-tag>
        </div>

        <a-table
          :columns="columns"
          :data-source="firmwareFiles"
          :loading="loading"
          :pagination="{ pageSize: 8, showSizeChanger: false }"
          :row-key="rowKey"
          :row-class-name="rowClassName"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileName'">
              <div class="file-cell">
                <FileProtectOutlined />
                <div>
                  <div class="file-title">
                    <span>{{ record.fileName }}</span>
                    <a-tag v-if="isLatest(record)" color="green">当前</a-tag>
                  </div>
                  <small v-if="record.hash">#{{ record.hash }}</small>
                </div>
              </div>
            </template>

            <template v-else-if="column.key === 'version'">
              <a-tag :color="record.version ? 'blue' : 'default'">
                {{ record.version || '未绑定' }}
              </a-tag>
            </template>

            <template v-else-if="column.key === 'size'">
              {{ formatFileSize(record.size) }}
            </template>

            <template v-else-if="column.key === 'modifyTime'">
              {{ formatDate(record.modifyTime) }}
            </template>

            <template v-else-if="column.key === 'action'">
              <a-space>
                <a-tooltip title="下载">
                  <a-button type="text" @click="downloadFile(record)">
                    <template #icon><DownloadOutlined /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip title="复制链接">
                  <a-button type="text" @click="copyDownloadUrl(record)">
                    <template #icon><LinkOutlined /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip title="删除">
                  <a-button
                    danger
                    type="text"
                    :loading="deletingFileName === record.fileName"
                    @click="confirmDelete(record)"
                  >
                    <template #icon><DeleteOutlined /></template>
                  </a-button>
                </a-tooltip>
              </a-space>
            </template>
          </template>

          <template #emptyText>
            <div class="empty-state">
              <CloudUploadOutlined />
              <span>暂无固件文件</span>
            </div>
          </template>
        </a-table>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal, type UploadProps } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileProtectOutlined,
  InboxOutlined,
  LinkOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import {
  deleteFirmwareFile,
  listFirmwareFiles,
  uploadFirmwareFile,
  type FirmwareFile,
} from '@/services/firmware'

const VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9.-]{0,31}$/
const MAX_FILE_SIZE = 2048 * 1024 * 1024

const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const firmwareFiles = ref<FirmwareFile[]>([])
const selectedFile = ref<File | null>(null)
const detectedVersion = ref('')
const version = ref('')
const versionTouched = ref(false)
const deletingFileName = ref('')

const columns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', minWidth: 280 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 120 },
  { title: '大小', dataIndex: 'size', key: 'size', width: 110 },
  { title: '上传时间', dataIndex: 'modifyTime', key: 'modifyTime', width: 190 },
  { title: '操作', key: 'action', width: 140, align: 'center' },
]

const latestFile = computed(() => firmwareFiles.value[0])
const totalSize = computed(() => firmwareFiles.value.reduce((sum, file) => sum + (file.size || 0), 0))
const normalizedVersion = computed(() => normalizeVersion(version.value))
const versionConflict = computed(() => {
  if (!normalizedVersion.value) return null
  return firmwareFiles.value.find(
    (file) => file.version && file.version.toLowerCase() === normalizedVersion.value.toLowerCase(),
  )
})
const versionInvalid = computed(() => normalizedVersion.value !== '' && !VERSION_PATTERN.test(normalizedVersion.value))
const uploadDisabled = computed(() => {
  return !selectedFile.value || !normalizedVersion.value || versionInvalid.value || !!versionConflict.value
})
const versionValidateStatus = computed(() => {
  if (versionInvalid.value || versionConflict.value) return 'warning'
  if (normalizedVersion.value) return 'success'
  return ''
})
const versionHelp = computed(() => {
  if (versionInvalid.value) return '版本号只能包含字母、数字、点和连字符，长度不超过32位'
  if (versionConflict.value) return '同一版本只能保留一个固件文件，避免 OTA 版本歧义'
  if (detectedVersion.value) return '已从文件名识别版本：' + detectedVersion.value
  return '文件名没有版本时，可在这里手动填写'
})

onMounted(() => {
  loadFiles()
})

async function loadFiles() {
  loading.value = true
  try {
    const res = await listFirmwareFiles()
    if (res.code === 200) {
      firmwareFiles.value = res.files || []
    } else {
      message.error(res.message || '获取固件列表失败')
    }
  } catch (error) {
    console.error('获取固件列表失败:', error)
    message.error('获取固件列表失败')
  } finally {
    loading.value = false
  }
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const rawFile = file as File
  if (!rawFile.name.toLowerCase().endsWith('.bin')) {
    message.error('只能上传 .bin 固件文件')
    return false
  }

  if (rawFile.size > MAX_FILE_SIZE) {
    message.error('固件文件不能超过 2048MB')
    return false
  }

  selectedFile.value = rawFile
  detectedVersion.value = extractVersion(rawFile.name)
  if (detectedVersion.value && !versionTouched.value) {
    version.value = detectedVersion.value
  }
  uploadProgress.value = 0
  return false
}

async function handleUpload() {
  if (uploadDisabled.value || uploading.value || !selectedFile.value) return

  uploading.value = true
  uploadProgress.value = 0
  try {
    const res = await uploadFirmwareFile(selectedFile.value, normalizedVersion.value, (percent) => {
      uploadProgress.value = percent
    })

    if (res.code === 200) {
      message.success('固件上传成功，版本已绑定为 ' + res.version)
      resetUpload()
      await loadFiles()
      return
    }

    if (res.code === 409) {
      const existing = res.data?.fileName ? '：' + res.data.fileName : ''
      message.warning((res.message || '固件已存在') + existing)
      await loadFiles()
      return
    }

    message.error(res.message || '固件上传失败')
  } catch (error) {
    console.error('固件上传失败:', error)
    message.error('固件上传失败')
  } finally {
    uploading.value = false
  }
}

function resetUpload() {
  selectedFile.value = null
  detectedVersion.value = ''
  version.value = ''
  versionTouched.value = false
  uploadProgress.value = 0
}

function clearSelectedFile() {
  selectedFile.value = null
  detectedVersion.value = ''
  uploadProgress.value = 0
}

function confirmDelete(file: FirmwareFile) {
  Modal.confirm({
    title: '删除固件文件',
    content: '删除后该文件将无法作为 OTA 固件下载：' + file.fileName,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      deletingFileName.value = file.fileName
      try {
        const res = await deleteFirmwareFile(file.fileName)
        if (res.code === 200) {
          message.success('固件已删除')
          await loadFiles()
        } else {
          message.error(res.message || '删除失败')
        }
      } finally {
        deletingFileName.value = ''
      }
    },
  })
}

function downloadFile(file: FirmwareFile) {
  const link = document.createElement('a')
  link.href = file.downloadUrl
  link.download = file.fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

async function copyDownloadUrl(file: FirmwareFile) {
  try {
    await navigator.clipboard.writeText(file.downloadUrl)
    message.success('下载链接已复制')
  } catch (error) {
    console.error('复制下载链接失败:', error)
    message.error('复制失败')
  }
}

function rowClassName(record: FirmwareFile) {
  return isLatest(record) ? 'firmware-row-latest' : ''
}

function rowKey(record: FirmwareFile) {
  return record.fileName
}

function isLatest(record: FirmwareFile) {
  return latestFile.value?.fileName === record.fileName
}

function extractVersion(fileName: string) {
  const baseName = fileName.replace(/\.bin$/i, '')
  const match = baseName.match(/(?:^|[_-])(?:v|version[_-]?)([0-9][A-Za-z0-9.-]{0,31})(?:[_-]|$)/i)
  const versionMatch = match?.[1]
  return versionMatch ? normalizeVersion(versionMatch) : ''
}

function normalizeVersion(value: string) {
  return value.trim().replace(/^v/i, '')
}

function formatFileSize(bytes = 0) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return (bytes / Math.pow(1024, index)).toFixed(index === 0 ? 0 : 1) + ' ' + units[index]
}

function formatDate(date: string | number | Date) {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.firmware-page {
  --firmware-bg: #f4f7f6;
  --firmware-panel: #ffffff;
  --firmware-ink: #14201d;
  --firmware-muted: #65736f;
  --firmware-line: #dbe4e1;
  --firmware-teal: #0f8b7d;
  --firmware-blue: #2f6fbd;
  --firmware-amber: #b87514;
  min-height: 100vh;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(15, 139, 125, 0.12), transparent 36%),
    linear-gradient(180deg, #f7faf9 0%, var(--firmware-bg) 100%);
  color: var(--firmware-ink);
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--firmware-teal);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.page-heading h1,
.panel-heading h2 {
  margin: 0;
  color: var(--firmware-ink);
}

.page-heading h1 {
  font-size: 28px;
  font-weight: 760;
}

.page-heading p,
.panel-heading p,
.metric-panel small {
  margin: 6px 0 0;
  color: var(--firmware-muted);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-panel,
.panel {
  border: 1px solid var(--firmware-line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 28px rgba(36, 54, 49, 0.08);
}

.metric-panel {
  display: flex;
  min-height: 112px;
  flex-direction: column;
  justify-content: center;
  padding: 18px;
}

.metric-panel span {
  color: var(--firmware-muted);
  font-size: 13px;
}

.metric-panel strong {
  margin-top: 8px;
  overflow: hidden;
  color: var(--firmware-ink);
  font-size: 26px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-primary {
  border-color: rgba(15, 139, 125, 0.28);
  background: linear-gradient(135deg, rgba(15, 139, 125, 0.13), rgba(255, 255, 255, 0.95));
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  padding: 18px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-heading h2 {
  font-size: 18px;
}

.panel-icon {
  color: var(--firmware-teal);
  font-size: 28px;
}

.firmware-uploader :deep(.ant-upload-drag) {
  border-color: #b9d6d0;
  background: linear-gradient(180deg, #fbfefd, #f1f8f6);
}

.firmware-uploader :deep(.ant-upload-drag:hover) {
  border-color: var(--firmware-teal);
}

.firmware-uploader :deep(.ant-upload-drag-icon .anticon) {
  color: var(--firmware-teal);
}

.selected-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding: 12px;
  border: 1px solid rgba(15, 139, 125, 0.22);
  border-radius: 8px;
  background: #f5fbf9;
}

.selected-main {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.selected-main .anticon {
  flex: 0 0 auto;
  color: var(--firmware-teal);
}

.selected-main strong,
.selected-main span {
  display: block;
}

.selected-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-main span {
  color: var(--firmware-muted);
  font-size: 12px;
}

.version-form {
  margin-top: 16px;
}

.upload-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.list-panel {
  min-width: 0;
}

.table-heading {
  align-items: center;
}

.file-cell {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 10px;
}

.file-cell > .anticon {
  margin-top: 3px;
  color: var(--firmware-blue);
}

.file-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.file-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-cell small {
  color: var(--firmware-muted);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 42px 0;
  color: var(--firmware-muted);
}

.empty-state .anticon {
  color: var(--firmware-amber);
  font-size: 28px;
}

:deep(.firmware-row-latest td) {
  background: #f2faf7 !important;
}

@media (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .firmware-page {
    padding: 16px;
  }

  .page-heading {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .upload-actions {
    flex-direction: column-reverse;
  }

  .upload-actions .ant-btn {
    width: 100%;
  }
}
</style>
