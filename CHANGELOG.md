# 变更日志
## [2.8.17] - 2025-07-16
### 新增
- feat: 新增 Swagger
- update: 模型增加辨识度标签
- update: 删除全局聊天多余缩小按钮
- update: 优化展示样式，可以切换浏览器标签页样式
- update: 实体采用 Lombok 方法
### 修复
- fix: 修复地址错误问题
- fix: 修复添加设备时验证码未生效问题
- fix: 修复 init SQL 脚本初始化缺少字段问题
- fix: 修复 issues #119 #120
### 样式优化
- style: 更新全局聊天缩放动画，更接近苹果效果
- 优化: 聊天样式
### 删除
- delete: 删除无用 log
### 重构
- refactor(stt): 优化 VoskSttService 类的代码结构
- refactor: 去掉多余 log

# 变更日志
## [2.8.16] - 2025-07-02
### 其他变更
- refactor:vad重构，去除agc
- refactor:重构音频发送逻辑，按照实际帧位置发送

# 变更日志
## [2.8.15] - 2025-07-01

### 修复
- fix:修复tag更新错误问题
- fix:修复设备在聆听时，修改角色配置导致缓存更新时多次查询数据库的问题
- fix:修复init初始化确实头像字段

### 其他变更
- refactor:优化token缓存，减少冗余代码
- update:阿里巴巴sdk日志级别改为warn

## [2.8.0] - 2025-06-15

### 新功能
- feat:增加logback输入 close #37
- feat:新增橘色设备量展示

### 修复
- fix(stt.aliyun): do not reuse recognizer
- fix(stt.aliyun): support long speech recognition
- fix: memory leak. Should clean up dialogue info after session closed

### 其他变更
- chore: update version to 2.8.0 [skip ci]
- update:角色返回增加modelName
- docs: update changelog for v2.7.68 [skip ci]
- chore: update version to 2.7.68 [skip ci]
- docs: update changelog for v2.7.67 [skip ci]
- chore: update version to 2.7.67 [skip ci]
- docs: update changelog for v2.7.66 [skip ci]
- chore: update version to 2.7.66 [skip ci]
- refactor(stt): simplify SttServiceFactory

## [2.7.68] - 2025-06-14

### 修复
- fix(stt.aliyun): do not reuse recognizer
- fix(stt.aliyun): support long speech recognition
- fix: memory leak. Should clean up dialogue info after session closed

### 其他变更
- chore: update version to 2.7.68 [skip ci]
- docs: update changelog for v2.7.67 [skip ci]
- chore: update version to 2.7.67 [skip ci]
- docs: update changelog for v2.7.66 [skip ci]
- chore: update version to 2.7.66 [skip ci]
- refactor(stt): simplify SttServiceFactory

## [2.7.67] - 2025-06-14

### 修复
- fix: memory leak. Should clean up dialogue info after session closed

### 其他变更
- chore: update version to 2.7.67 [skip ci]
- docs: update changelog for v2.7.66 [skip ci]
- chore: update version to 2.7.66 [skip ci]

## [2.7.64] - 2025-06-12

### 修复
- Merge pull request #98 from vritser/main
- fix(audio): merge audio files

### 其他变更
- chore: update version to 2.7.64 [skip ci]
- docs: update changelog for v2.7.63 [skip ci]
- chore: update version to 2.7.63 [skip ci]

## [2.7.60] - 2025-06-11

### 新功能
- Merge pull request #96 from vritser/main
- feat(tts): support minimax t2a

### 修复
- fix:修复阿里语音合成多余参数，删除
- fix(tts): tts service factory

### 其他变更
- chore: update version to 2.7.60 [skip ci]
- docs: update changelog for v2.7.59 [skip ci]
- chore: update version to 2.7.59 [skip ci]
- refactor(tts): add default implements
- docs: update changelog for v2.7.58 [skip ci]
- chore: update version to 2.7.58 [skip ci]

## [2.7.59] - 2025-06-11

### 新功能
- Merge pull request #96 from vritser/main
- feat(tts): support minimax t2a

### 修复
- fix(tts): tts service factory

### 其他变更
- chore: update version to 2.7.59 [skip ci]
- refactor(tts): add default implements
- docs: update changelog for v2.7.58 [skip ci]
- chore: update version to 2.7.58 [skip ci]

