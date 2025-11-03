<template>
  <div class="firmware-management">
    <div class="page-header">
      <h2>固件管理</h2>
      <p>管理设备固件文件，支持上传、下载和删除操作</p>
    </div>

    <!-- 上传区域 -->
    <div class="upload-section">
      <el-card class="upload-card">
        <div slot="header" class="card-header">
          <span>上传固件</span>
        </div>
        <el-form :model="uploadForm" :rules="uploadRules" ref="uploadForm" label-width="100px">
          <el-form-item label="固件文件" prop="file">
            <!-- 原生文件选择器作为备选方案 -->
            <div class="file-upload-container" 
                 @dragover.prevent
                 @dragenter.prevent
                 @drop.prevent="handleDrop">
              <input 
                ref="fileInput"
                type="file" 
                accept=".bin"
                @change="handleNativeFileChange"
                style="display: none;">
              
              <el-upload
                ref="upload"
                :auto-upload="false"
                :on-change="handleFileChange"
                :before-upload="beforeUpload"
                :on-remove="handleFileRemove"
                :file-list="uploadFileList"
                accept=".bin"
                drag
                :multiple="false"
                :limit="1"
                :on-exceed="handleExceed"
                action="#"
                :show-file-list="true"
                :disabled="uploading">
                <i class="el-icon-upload"></i>
                <div class="el-upload__text">将固件文件拖到此处，或<em>点击上传</em></div>
                <div class="el-upload__tip" slot="tip">只能上传.bin格式的固件文件</div>
              </el-upload>
              
              <!-- 备选按钮 -->
              <div class="upload-fallback" v-if="!uploadFileList.length">
                <el-button type="primary" @click="triggerFileSelect" :disabled="uploading">
                  <i class="el-icon-upload"></i> 选择固件文件
                </el-button>
                <p class="upload-tip">或者拖拽文件到上方区域</p>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="版本号" prop="version">
            <el-input v-model="uploadForm.version" placeholder="请输入固件版本号，如：1.0.0"></el-input>
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="uploadForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入固件描述信息">
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitUpload" :loading="uploading">
              <i class="el-icon-upload"></i> 上传固件
            </el-button>
            <el-button type="default" @click="resetUpload" :disabled="uploading">
              <i class="el-icon-refresh-left"></i> 重置
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- 文件列表 -->
    <div class="file-list-section">
      <el-card>
        <div slot="header" class="card-header">
          <span>固件文件列表 ({{ fileList.length }} 个文件)</span>
          <el-button type="primary" size="small" @click="refreshFileList" :loading="loading">
            <i class="el-icon-refresh"></i> 刷新
          </el-button>
        </div>
        
        <!-- 简化的表格，用于测试 -->
        <div v-if="fileList.length > 0">
          <h4>文件列表 (简化显示):</h4>
          <div v-for="(file, index) in fileList" :key="index" style="padding: 10px; border: 1px solid #ddd; margin: 5px 0;">
            <p><strong>文件名:</strong> {{ file.fileName }}</p>
            <p><strong>大小:</strong> {{ formatFileSize(file.size) }}</p>
            <p><strong>时间:</strong> {{ formatDate(file.modifyTime) }}</p>
            <p><strong>下载链接:</strong> {{ file.downloadUrl }}</p>
            <el-button type="primary" size="mini" @click="downloadFile(file)">
              <i class="el-icon-download"></i> 下载
            </el-button>
            <el-button type="danger" size="mini" @click="deleteFile(file)">
              <i class="el-icon-delete"></i> 删除
            </el-button>
          </div>
        </div>
        
        <el-table
          :data="fileList"
          :key="tableKey"
          v-loading="loading"
          stripe
          style="width: 100%"
          :empty-text="loading ? '加载中...' : '暂无数据'">
          <el-table-column prop="fileName" label="文件名" min-width="200">
            <template slot-scope="scope">
              <i class="el-icon-document"></i>
              <span style="margin-left: 8px">{{ scope.row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="size" label="文件大小" width="120">
            <template slot-scope="scope">
              {{ formatFileSize(scope.row.size) }}
            </template>
          </el-table-column>
          <el-table-column prop="modifyTime" label="上传时间" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.modifyTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template slot-scope="scope">
              <el-button
                type="primary"
                size="mini"
                @click="downloadFile(scope.row)">
                <i class="el-icon-download"></i> 下载
              </el-button>
              <el-button
                type="danger"
                size="mini"
                @click="deleteFile(scope.row)"
                :loading="scope.row.deleting">
                <i class="el-icon-delete"></i> 删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="fileList.length === 0 && !loading" class="empty-state">
          <i class="el-icon-document"></i>
          <p>暂无固件文件</p>
        </div>
        
        <!-- 调试信息 -->
        <div v-if="fileList.length > 0" style="margin-top: 10px; padding: 10px; background: #f0f0f0; border-radius: 4px;">
          <p><strong>调试信息:</strong></p>
          <p>文件数量: {{ fileList.length }}</p>
          <p>第一个文件: {{ fileList[0] ? fileList[0].fileName : '无' }}</p>
          <p>表格Key: {{ tableKey }}</p>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script>
import axios from '@/services/axios'
import api from '@/services/api'

export default {
  name: 'Firmware',
  data() {
    return {
      loading: false,
      uploading: false,
      fileList: [],
      uploadFileList: [],
      tableKey: 0,
      uploadForm: {
        file: null,
        version: '',
        description: ''
      },
      uploadRules: {
        file: [
          { required: true, message: '请选择固件文件', trigger: 'change' }
        ]
      }
    }
  },
  mounted() {
    this.loadFileList()
    // 测试上传组件是否正常初始化
    this.$nextTick(() => {
      console.log('上传组件引用:', this.$refs.upload)
      if (this.$refs.upload) {
        console.log('上传组件已正确初始化')
      } else {
        console.error('上传组件初始化失败')
      }
    })
  },
  watch: {
    fileList: {
      handler(newVal) {
        console.log('fileList 数据变化:', newVal)
        console.log('fileList 类型:', typeof newVal)
        console.log('fileList 长度:', newVal ? newVal.length : 'undefined')
      },
      deep: true
    }
  },
  methods: {
    // 加载文件列表
    loadFileList() {
      this.loading = true
      axios
        .get({
          url: api.firmware.list
        }).then(res => {
          this.loading = false
          console.log('文件列表响应:', res)
          console.log('响应数据:', res.data)
          console.log('响应代码:', res.code)
          
          // 检查响应格式，可能是res.data或res
          const responseData = res.data || res;
          const responseCode = responseData.code;
          const responseFiles = responseData.files;
          
          if (responseCode === 200) {
            // 直接赋值，不使用Vue.set
            this.fileList = responseFiles || []
            console.log('解析后的文件列表:', this.fileList)
            console.log('文件列表长度:', this.fileList.length)
            console.log('文件列表详情:', JSON.stringify(this.fileList, null, 2))
            
            // 更新表格key强制重新渲染
            this.tableKey += 1
            
            // 强制更新视图
            this.$forceUpdate()
            
            // 空文件列表是正常的，不需要显示错误
            if (this.fileList.length === 0) {
              console.log('文件列表为空，这是正常状态')
            }
          } else {
            this.$message.error('获取文件列表失败: ' + (responseData.message || '未知错误'))
          }
        }).catch((error) => {
          this.loading = false
          console.error('获取文件列表失败:', error)
          this.$message.error('获取文件列表失败')
        })
    },

    // 刷新文件列表
    refreshFileList() {
      this.loadFileList()
    },

    // 文件选择变化
    handleFileChange(file, fileList) {
      console.log('=== 文件变化事件触发 ===')
      console.log('文件对象:', file)
      console.log('文件列表:', fileList)
      console.log('文件状态:', file.status)
      console.log('文件名称:', file.name)
      console.log('文件大小:', file.size)
      console.log('文件原始对象:', file.raw)
      
      // 更新文件列表
      this.uploadFileList = fileList
      
      // 验证文件格式
      if (file.name && !file.name.toLowerCase().endsWith('.bin')) {
        this.$message.error('只能选择.bin格式的固件文件')
        // 移除不符合格式的文件
        this.uploadFileList = this.uploadFileList.filter(f => f.name.toLowerCase().endsWith('.bin'))
        return false
      }
      
      // 确保文件对象正确设置
      if (file.raw) {
        this.uploadForm.file = file.raw
        console.log('文件已设置到uploadForm.file:', this.uploadForm.file)
        this.$message.success('文件选择成功: ' + file.name)
      } else if (file.origin) {
        // 有些情况下文件对象可能使用origin属性
        this.uploadForm.file = file.origin
        console.log('文件已设置到uploadForm.file (origin):', this.uploadForm.file)
        this.$message.success('文件选择成功: ' + file.name)
      } else {
        console.warn('文件对象中没有raw或origin属性')
        console.log('尝试直接使用file对象:', file)
        this.uploadForm.file = file
        this.$message.success('文件选择成功: ' + file.name)
      }
    },

    // 文件移除
    handleFileRemove(file, fileList) {
      console.log('文件移除:', file, fileList)
      this.uploadForm.file = null
      this.uploadFileList = fileList
    },

    // 文件超出限制处理
    handleExceed(files, fileList) {
      console.log('文件超出限制:', files, fileList)
      this.$message.warning('只能选择一个固件文件')
    },

    // 上传成功处理
    handleUploadSuccess(response, file, fileList) {
      console.log('上传成功:', response, file, fileList)
    },

    // 上传失败处理
    handleUploadError(error, file, fileList) {
      console.log('上传失败:', error, file, fileList)
      this.$message.error('文件上传失败')
    },

    // 文件数量超出限制
    handleExceed(files, fileList) {
      this.$message.warning(`最多只能选择1个文件，当前选择了${files.length}个文件，共${files.length + fileList.length}个文件`)
    },

    // 触发原生文件选择器
    triggerFileSelect() {
      console.log('触发原生文件选择器')
      this.$refs.fileInput.click()
    },

    // 处理原生文件选择
    handleNativeFileChange(event) {
      console.log('=== 原生文件选择事件 ===')
      const file = event.target.files[0]
      console.log('选择的文件:', file)
      
      if (!file) {
        console.log('没有选择文件')
        return
      }
      
      // 验证文件格式
      if (!file.name.toLowerCase().endsWith('.bin')) {
        this.$message.error('只能选择.bin格式的固件文件')
        event.target.value = '' // 清空选择
        return
      }
      
      // 验证文件大小
      const isLt50M = file.size / 1024 / 1024 < 50
      if (!isLt50M) {
        this.$message.error('固件文件大小不能超过 50MB!')
        event.target.value = '' // 清空选择
        return
      }
      
      // 设置文件到表单
      this.uploadForm.file = file
      console.log('文件已设置到uploadForm.file:', this.uploadForm.file)
      
      // 更新文件列表显示
      this.uploadFileList = [{
        name: file.name,
        size: file.size,
        status: 'ready',
        raw: file
      }]
      
      this.$message.success('文件选择成功: ' + file.name)
    },

    // 处理拖拽文件
    handleDrop(event) {
      console.log('=== 拖拽文件事件 ===')
      const files = event.dataTransfer.files
      console.log('拖拽的文件:', files)
      
      if (files.length === 0) {
        console.log('没有拖拽文件')
        return
      }
      
      const file = files[0]
      console.log('选择的文件:', file)
      
      // 验证文件格式
      if (!file.name.toLowerCase().endsWith('.bin')) {
        this.$message.error('只能拖拽.bin格式的固件文件')
        return
      }
      
      // 验证文件大小
      const isLt50M = file.size / 1024 / 1024 < 50
      if (!isLt50M) {
        this.$message.error('固件文件大小不能超过 50MB!')
        return
      }
      
      // 设置文件到表单
      this.uploadForm.file = file
      console.log('文件已设置到uploadForm.file:', this.uploadForm.file)
      
      // 更新文件列表显示
      this.uploadFileList = [{
        name: file.name,
        size: file.size,
        status: 'ready',
        raw: file
      }]
      
      this.$message.success('文件拖拽成功: ' + file.name)
    },

    // 上传前验证
    beforeUpload(file) {
      console.log('=== 上传前验证 ===')
      console.log('验证文件:', file)
      console.log('文件名称:', file.name)
      console.log('文件大小:', file.size)
      
      const isBin = file.name.toLowerCase().endsWith('.bin')
      if (!isBin) {
        console.log('文件格式验证失败:', file.name)
        this.$message.error('只能上传.bin格式的固件文件!')
        return false
      }
      
      const isLt50M = file.size / 1024 / 1024 < 50
      if (!isLt50M) {
        console.log('文件大小验证失败:', file.size)
        this.$message.error('固件文件大小不能超过 50MB!')
        return false
      }
      
      console.log('文件验证通过')
      return true
    },

    // 提交上传
    submitUpload() {
      // 手动验证文件是否选择
      if (!this.uploadForm.file) {
        this.$message.error('请选择固件文件')
        return
      }

      // 验证表单（安全方式）
      if (this.$refs.uploadForm && this.$refs.uploadForm.validate) {
        this.$refs.uploadForm.validate((valid) => {
          if (!valid) return
          this.performUpload()
        })
      } else {
        // 如果表单验证不可用，直接执行上传
        this.performUpload()
      }
    },

    // 执行上传操作
    performUpload() {
      this.uploading = true
      const formData = new FormData()
      formData.append('file', this.uploadForm.file)
      formData.append('version', this.uploadForm.version)
      formData.append('description', this.uploadForm.description)

      // 使用专门的文件上传方法
      axios.upload({
        url: api.firmware.upload,
        data: formData
      }).then(res => {
          this.uploading = false
          console.log('上传响应:', res)
          console.log('响应数据:', res.data)
          console.log('响应代码:', res.code)
          
          // 检查响应格式，可能是res.data.code或res.code
          const responseCode = (res.data && res.data.code) ? res.data.code : res.code;
          const responseMessage = (res.data && res.data.message) ? res.data.message : res.message;
          
          if (responseCode === 200) {
            this.$message.success('固件上传成功!')
            this.resetUpload()
            this.loadFileList()
          } else {
            this.$message.error('上传失败: ' + responseMessage)
          }
        }).catch((error) => {
          this.uploading = false
          console.error('上传错误:', error)
          this.$message.error('上传失败: ' + (error.response ? error.response.data.message : error.message))
        })
    },

    // 重置上传表单
    resetUpload() {
      this.uploadForm = {
        file: null,
        version: '',
        description: ''
      }
      // 安全地重置表单
      if (this.$refs.uploadForm && this.$refs.uploadForm.resetFields) {
        this.$refs.uploadForm.resetFields()
      }
      // 清空上传组件的文件列表
      if (this.$refs.upload && this.$refs.upload.clearFiles) {
        this.$refs.upload.clearFiles()
      }
      // 手动清空上传文件列表
      this.uploadFileList = []
    },

    // 下载文件
    downloadFile(file) {
      const link = document.createElement('a')
      link.href = file.downloadUrl
      link.download = file.fileName
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    },

    // 删除文件
    deleteFile(file) {
      // 使用原生confirm作为备选方案
      if (confirm('确定要删除这个固件文件吗？')) {
        this.$set(file, 'deleting', true)
        console.log('开始删除文件:', file.fileName)
        
        const deleteUrl = api.firmware.delete + '/' + file.fileName
        console.log('删除URL:', deleteUrl)
        
        axios
          .delete({
            url: deleteUrl
          })
          .then(res => {
            this.$set(file, 'deleting', false)
            console.log('删除响应:', res)
            console.log('删除响应数据:', res.data)
            console.log('删除响应代码:', res.code)
            
            // 检查响应格式
            const responseCode = (res.data && res.data.code) ? res.data.code : res.code;
            const responseMessage = (res.data && res.data.message) ? res.data.message : res.message;
            
            if (responseCode === 200) {
              this.$message.success('文件删除成功!')
              this.loadFileList()
            } else {
              this.$message.error('删除失败: ' + responseMessage)
            }
          }).catch((error) => {
            this.$set(file, 'deleting', false)
            console.error('删除失败:', error)
            console.error('删除错误详情:', error.response)
            this.$message.error('删除失败: ' + (error.response ? error.response.data.message : error.message))
          })
      }
    },

    // 格式化文件大小
    formatFileSize(bytes) {
      if (bytes === 0) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    },

    // 格式化日期
    formatDate(date) {
      if (!date) return ''
      const d = new Date(date)
      return d.toLocaleString('zh-CN')
    }
  }
}
</script>

<style scoped>
.firmware-management {
  padding: 24px;
  background-color: #f5f5f5;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 24px;
  border-radius: 12px;
  color: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 600;
}

.page-header p {
  margin: 0;
  font-size: 16px;
  opacity: 0.9;
}

.upload-section {
  margin-bottom: 24px;
}

.upload-card {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: none;
}

.upload-card .el-card__header {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-radius: 12px 12px 0 0;
  padding: 16px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.upload-card .el-card__body {
  padding: 24px;
}

.file-list-section {
  margin-top: 24px;
}

.file-list-section .el-card {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: none;
}

.file-list-section .el-card__header {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
  border-radius: 12px 12px 0 0;
  padding: 16px 20px;
}

.empty-state {
  text-align: center;
  padding: 60px 40px;
  color: #909399;
  background: #fafafa;
  border-radius: 8px;
  margin: 20px 0;
}

.empty-state i {
  font-size: 64px;
  margin-bottom: 16px;
  display: block;
  color: #d3d4d6;
}

.empty-state p {
  margin: 0;
  font-size: 16px;
  color: #909399;
}

.el-upload__tip {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}

.el-form-item {
  margin-bottom: 24px;
}

.el-form-item__label {
  font-weight: 600;
  color: #303133;
}

.el-input__inner {
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  transition: all 0.3s;
}

.el-input__inner:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.el-textarea__inner {
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  transition: all 0.3s;
}

.el-textarea__inner:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.el-button {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s;
  padding: 12px 24px;
}

.el-button--primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.6);
}

.el-button--danger {
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a52 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.4);
}

.el-button--danger:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 107, 107, 0.6);
}

.el-button--default {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  color: #495057;
}

.el-button--default:hover {
  background: #e9ecef;
  border-color: #adb5bd;
  transform: translateY(-1px);
}

.el-upload {
  border: 2px dashed #d9d9d9;
  border-radius: 12px;
  background: #fafafa;
  transition: all 0.3s;
}

.el-upload:hover {
  border-color: #409eff;
  background: #f0f9ff;
}

.el-upload-dragger {
  border: none;
  border-radius: 12px;
  background: transparent;
  padding: 40px;
}

.el-upload-dragger:hover {
  background: rgba(64, 158, 255, 0.05);
}

.el-table {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.el-table th {
  background: #f8f9fa;
  color: #495057;
  font-weight: 600;
}

.el-table td {
  border-bottom: 1px solid #f0f0f0;
}

.el-table .el-button--mini {
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 6px;
}

.el-upload__text {
  color: #606266;
  font-size: 14px;
}

.el-upload__text em {
  color: #409eff;
  font-style: normal;
  font-weight: 500;
}

.file-upload-container {
  position: relative;
}

.upload-fallback {
  text-align: center;
  padding: 20px;
  background: #f8f9fa;
  border: 2px dashed #dee2e6;
  border-radius: 8px;
  margin-top: 10px;
}

.upload-fallback .el-button {
  margin-bottom: 10px;
}

.upload-tip {
  margin: 0;
  color: #6c757d;
  font-size: 14px;
}
</style>
