<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item label="平台">
                  <a-select v-model="query.provider" @change="getData()">
                    <a-select-option v-for="item in providerOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :xl="8" :lg="12" :xs="24">
                <a-form-item label="智能体名称">
                  <a-input-search v-model="query.agentName" placeholder="请输入" allow-clear @search="getData()" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>

        <!-- 表格数据 -->
        <a-card title="智能体管理" :bodyStyle="{ padding: 0 }" :bordered="false">
          <template slot="extra">
            <a-button type="primary" @click="handleConfigPlatform" style="margin-right: 8px">
              <a-icon type="setting" />平台配置
            </a-button>
          </template>
          <a-table rowKey="configId" :columns="getTableColumns" :data-source="agentList" :loading="loading"
            :pagination="pagination" @change="handleTableChange" size="middle" :scroll="{ x: 1000 }">
            <!-- Icon -->
            <template slot="iconUrl" slot-scope="text, record">
              <a-avatar :src="record.iconUrl" shape="square" size="large" />
            </template>
            <!-- 智能体名称 -->
            <template slot="agentName" slot-scope="text, record">
              <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                <span v-if="text">{{ text }}</span>
                <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
              </a-tooltip>
            </template>
            <!-- 平台 -->
            <template slot="provider" slot-scope="text">
              <a-tag color="blue">{{ text }}</a-tag>
            </template>
            <!-- 描述 -->
            <template slot="agentDesc" slot-scope="text">
              <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                <span v-if="text">{{ text }}</span>
                <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
              </a-tooltip>
            </template>
            <!-- 默认状态 -->
            <template slot="isDefault" slot-scope="text">
              <a-tag v-if="text == 1" color="green">默认</a-tag>
              <span v-else>-</span>
            </template>
            <!-- 操作 -->
            <template slot="operation" slot-scope="text, record">
              <a-space>
                <!-- 添加设为默认按钮 -->
                <a v-if="record.isDefault != 1" href="javascript:" :disabled="record.isDefault == 1" @click="setAsDefault(record)">设为默认</a>
                <a-popconfirm title="确定要删除此智能体吗？" @confirm="handleDelete(record)">
                  <a v-if="record.isDefault != 1" href="javascript:" style="color: #ff4d4f">删除</a>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </div>
    </a-layout-content>
    <a-back-top />

    <!-- 平台配置对话框 -->
    <a-modal :title="'平台配置 - ' + (query.provider === 'coze' ? 'Coze' : query.provider === 'dify' ? 'Dify' : query.provider)" 
      :visible="platformModalVisible" :confirm-loading="platformModalLoading"
      @ok="handlePlatformModalOk" @cancel="handlePlatformModalCancel">
      <a-form-model ref="platformForm" :model="platformForm" :rules="platformRules" :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }">
        <a-form-model-item v-for="item in getFormItems" :key="item.field" :label="item.label" :prop="item.field">
          <a-input v-model="platformForm[item.field]" :placeholder="item.placeholder">
            <template v-if="item.suffix" slot="suffix">
              <span style="color: #999">{{ item.suffix }}</span>
            </template>
          </a-input>
        </a-form-model-item>
      </a-form-model>
    </a-modal>
  </a-layout>
</template>

<script>
import axios from "@/services/axios";
import api from "@/services/api";
import mixin from "@/mixins/index";

export default {
  name: 'Agent',
  mixins: [mixin],
  data() {
    return {
      // 查询参数
      query: {
        agentName: '',
        provider: 'coze'
      },
      // 平台选项
      providerOptions: [
        { label: 'Coze', value: 'coze' },
        { label: 'Dify', value: 'dify' }
      ],
      // 表格列定义
      tableColumns: [
        { title: '头像', dataIndex: 'iconUrl', width: 80, align: 'center', scopedSlots: { customRender: 'iconUrl' }, fixed: 'left' },
        { title: '智能体名称', dataIndex: 'agentName', scopedSlots: { customRender: 'agentName' }, width: 150, align: 'center', fixed: 'left', ellipsis: true },
        { title: '平台', dataIndex: 'provider', scopedSlots: { customRender: 'provider' }, width: 80, align: 'center' },
        { title: '智能体描述', dataIndex: 'agentDesc', align: 'center', scopedSlots: { customRender: 'agentDesc' }, ellipsis: true },
        // 添加默认状态列
        { title: '默认', dataIndex: 'isDefault', width: 80, align: 'center', scopedSlots: { customRender: 'isDefault' } },
        { title: '发布时间', dataIndex: 'publishTime', width: 180, align: 'center' },
        { title: '操作', dataIndex: 'operation', scopedSlots: { customRender: 'operation' }, width: 150, align: 'center', fixed: 'right' }
      ],
      // 表格数据
      agentList: [],
      // 平台配置模态框
      platformModalVisible: false,
      platformModalLoading: false,
      // 是否为编辑模式
      isEdit: false,
      // 当前编辑的配置ID
      currentConfigId: null,

      // 平台表单对象
      platformForm: {
        configType: 'agent',
        provider: 'coze',
        configName: '',
        configDesc: '',
        appId: '',
        apiKey: '',
        apiUrl: '',
        ak: '',
        sk: ''
      },

      // 表单项配置
      formItems: {
        coze: [
          {
            field: 'appId',
            label: 'App ID',
            placeholder: '请输入Coze App ID'
          },
          {
            field: 'apiSecret',
            label: 'Space ID',
            placeholder: '请输入Coze Space ID'
          },
          {
            field: 'ak',
            label: '公钥',
            placeholder: '请输入公钥'
          },
          {
            field: 'sk',
            label: '私钥',
            placeholder: '请输入私钥'
          }
        ],
        dify: [
          {
            field: 'apiUrl',
            label: 'API URL',
            placeholder: '请输入API URL',
            suffix: '/chat_message'
          },
          {
            field: 'apiKey',
            label: 'API Key',
            placeholder: '请输入API Key'
          }
        ]
      },

      // 平台表单验证规则
      platformRules: {
        appId: [{ required: true, message: '请输入App ID', trigger: 'blur' }],
        apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
        apiSecret: [{ required: true, message: '请输入Space Id', trigger: 'blur' }],
        ak: [{ required: true, message: '请输入公钥', trigger: 'blur' }],
        sk: [{ required: true, message: '请输入私钥', trigger: 'blur' }],
        apiUrl: [{ required: true, message: '请输入URL', trigger: 'blur' }]
      }
    }
  },
  computed: {
    // 根据当前选择的平台动态生成表格列
    getTableColumns() {
      // 创建列数组的副本，以免修改原始数据
      const columns = [...this.tableColumns];
      
      // 如果当前选择的是Coze平台，则插入智能体ID列
      if (this.query.provider === 'coze') {
        const botIdColumn = { 
          title: '智能体ID', 
          dataIndex: 'botId', 
          width: 180, 
          align: 'center', 
          scopedSlots: { customRender: 'botId' } 
        }
        // 在第二列后插入智能体ID列
        columns.splice(2, 0, botIdColumn);
      }
      
      return columns;
    },
    
    // 根据当前选择的平台获取对应的表单项
    getFormItems() {
      return this.formItems[this.query.provider] || [];
    }
  },
  created() {
    this.getData()
  },
  methods: {
    // 获取智能体列表
    getData() {
      this.loading = true;

      // 调用后端API获取智能体列表
      axios.get({
        url: api.agent.query,
        data: {
          provider: this.query.provider,
          agentName: this.query.agentName,
          configType: 'agent',
          start: this.pagination.page,
          limit: this.pagination.pageSize
        }
      })
        .then(res => {
          if (res.code === 200) {
            this.agentList = res.data.list;
            this.pagination.total = res.data.total;
          } else {
            this.$message.error(res.msg);
          }
        })
        .catch(error => {
          console.error('Error fetching agents:', error);
          this.$message.error('获取智能体列表失败');
        })
        .finally(() => {
          this.loading = false;
        });
    },

    // 平台配置按钮点击
    handleConfigPlatform() {
      // 查询当前平台的配置
      this.platformModalLoading = true;
      
      axios.get({
        url: api.config.query,
        data: {
          configType: 'agent',
          provider: this.query.provider
        }
      })
        .then(res => {
          if (res.code === 200) {
            const configs = res.data.list || [];
            
            // 重置表单
            this.platformForm = {
              configType: 'agent',
              provider: this.query.provider,
              configName: '',
              configDesc: '',
              appId: '',
              apiKey: '',
              apiSecret: '',
              apiUrl: '',
              ak: '',
              sk: ''
            };
            
            // 如果存在配置，则填充表单
            if (configs.length > 0) {
              const config = configs[0];
              this.isEdit = true;
              this.currentConfigId = config.configId;
              
              // 填充表单数据
              this.platformForm = {
                configType: config.configType || 'agent',
                provider: config.provider,
                configName: config.configName || '',
                configDesc: config.configDesc || '',
                appId: config.appId || '',
                apiSecret: config.apiSecret || '',
                apiKey: config.apiKey || '',
                apiUrl: config.apiUrl || '',
                ak: config.ak || '',
                sk: config.sk || ''
              };
            } else {
              // 不存在配置，则为添加模式
              this.isEdit = false;
              this.currentConfigId = null;
              
              // 如果是Dify平台，设置默认的apiUrl
              if (this.query.provider === 'dify') {
                this.platformForm.apiUrl = 'https://api.dify.ai/v1';
              }
            }
            
            this.platformModalVisible = true;
          } else {
            this.$message.error(res.msg || '获取平台配置失败');
          }
        })
        .catch(error => {
          console.error('Error fetching platform config:', error);
          this.$message.error('获取平台配置失败');
        })
        .finally(() => {
          this.platformModalLoading = false;
        });
    },

    // 平台配置模态框确认
    handlePlatformModalOk() {
      this.$refs.platformForm.validate(valid => {
        if (valid) {
          this.platformModalLoading = true;

          // 如果是Dify平台，确保apiUrl有正确的后缀
          if (this.platformForm.provider === 'dify' && this.platformForm.apiUrl) {
            // 确保URL末尾没有斜杠
            let baseUrl = this.platformForm.apiUrl;
            if (baseUrl.endsWith('/')) {
              baseUrl = baseUrl.slice(0, -1);
            }
            this.platformForm.apiUrl = baseUrl;
          }

          // 根据是否为编辑模式选择不同的API
          const apiEndpoint = this.isEdit ? api.config.update : api.config.add;
          
          // 如果是编辑模式，添加configId
          if (this.isEdit) {
            this.platformForm.configId = this.currentConfigId;
          }

          // 调用后端API添加或更新配置
          axios.post({
            url: apiEndpoint,
            data: {...this.platformForm}
          })
            .then(res => {
              if (res.code === 200) {
                this.$message.success(this.isEdit ? '更新平台配置成功' : '添加平台配置成功');
                this.platformModalVisible = false;
                
                // 刷新智能体列表
                this.getData();
              } else {
                this.$message.error(res.msg || (this.isEdit ? '更新平台配置失败' : '添加平台配置失败'));
              }
            })
            .catch(error => {
              console.error('Error with platform config:', error);
              this.$message.error(this.isEdit ? '更新平台配置失败' : '添加平台配置失败');
            })
            .finally(() => {
              this.platformModalLoading = false;
            });
        }
      });
    },

    // 平台配置模态框取消
    handlePlatformModalCancel() {
      this.platformModalVisible = false;
    },

    // 设置为默认智能体
    setAsDefault(record) {
      this.$confirm({
        title: '确定要将此智能体设为默认吗？',
        content: '设为默认后，系统将优先使用此智能体，原默认智能体将被取消默认状态。',
        okText: '确定',
        cancelText: '取消',
        onOk: () => {
          this.loading = true;
          
          // 调用后端API更新配置为默认
          axios.post({
            url: api.config.update,
            data: {
              configId: record.configId,
              configType: 'llm',
              isDefault: 1
            }
          })
            .then(res => {
              if (res.code === 200) {
                this.$message.success(`已将"${record.agentName}"设为默认智能体`);
                this.getData();
              } else {
                this.showError(res.message);
              }
            })
            .catch(error => {
              this.$message.error('设置默认智能体失败');
            })
            .finally(() => {
              this.loading = false;
            });
        }
      });
    },

    // 删除智能体
    handleDelete(record) {
      axios.post({
        url: api.agent.delete,
        data: { bot_id: record.bot_id }
      })
        .then(res => {
          if (res.code === 200) {
            this.$message.success('删除成功');
            this.getData();
          } else {
            this.$message.error(res.msg || '删除失败');
          }
        })
        .catch(() => {
          this.$message.error('服务器错误，请稍后再试');
        });
    },
  }
}
</script>