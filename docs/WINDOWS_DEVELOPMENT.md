# Windows 部署小智ESP32服务器的详细步骤

## 系统要求确认
- 确保您的Windows系统满足以下要求：
  - Windows 10或更高版本（建议使用最新版本）
  - 管理员权限

## 1. 安装Java JDK 21
1. 访问Oracle官网下载JDK 21：[Oracle JDK 21下载](https://www.oracle.com/java/technologies/downloads/#java21)  
2. 运行安装程序，按向导完成安装
3. 配置环境变量：
   - 右键"此电脑" → 属性 → 高级系统设置 → 环境变量
   - 在"系统变量"中新建：
     - 变量名：`JAVA_HOME`
     - 变量值：`C:\Program Files\Java\jdk-21.0.7`（具体路径取决于您的安装版本）
   - 编辑"Path"变量，添加：`%JAVA_HOME%\bin`
4. 验证安装：
   - 打开命令提示符（Win+R，输入`cmd`）
   - 输入：`java -version`
   - 应显示类似：`openjdk version "21.0.7"`

## 2. 安装MySQL数据库
1. 下载MySQL社区版：[MySQL下载](https://dev.mysql.com/downloads/installer/)
2. 运行安装程序，选择"Custom"安装
3. 选择安装：
   - MySQL Server
   - MySQL Workbench（可选，图形界面工具）
4. 在配置步骤：
   - 设置root密码（建议使用复杂密码）
   - 记住您设置的密码
5. 完成安装后，启动MySQL服务
6. 配置变量
   - 找到MySQL的安装路径，默认路径通常是：`C:\Program Files\MySQL\MySQL Server 8\bin`
   - 如果不确定，可以在文件资源管理器中搜索 `mysql.exe` 的路径。
7. 添加到环境变量
   - 右键 此电脑 → 属性 → 高级系统设置 → 环境变量。
   - 在 系统变量 中找到 Path，点击 编辑 → 新建，粘贴MySQL的bin路径（如上述路径）。
   - 保存后关闭所有窗口。
8. 验证是否生效
   - 重新打开命令提示符（CMD），输入`mysql --version`
   - 如果显示版本信息（如 `mysql  Ver 8.0.42`），则配置成功。

## 3. 安装Maven
1. 下载Maven：[Maven下载](https://maven.apache.org/download.cgi)
   - 选择"Binary zip archive"下载
2. 解压到`C:\Program Files\apache-maven-3.9.4`（版本号可能不同）
3. 配置环境变量：
   - 新建系统变量：
     - 变量名：`MAVEN_HOME`
     - 变量值：`C:\Program Files\apache-maven-3.9.4`
   - 编辑"Path"变量，添加：`%MAVEN_HOME%\bin`
4. 验证安装：
   - 命令提示符输入：`mvn -v`
   - 应显示Maven版本信息

## 4. 安装Node.js和npm
1. 下载Node.js LTS版本：[Node.js下载](https://nodejs.org/)
2. 运行安装程序，按默认选项安装
3. 安装完成后验证：
   - 命令提示符输入：
     - `node -v`
     - `npm -v`
   - 应显示版本信息

## 5. 安装FFmpeg（必需）
1. 访问FFmpeg官网：[FFmpeg下载](https://ffmpeg.org/download.html)
2. 选择"Windows builds from gyan.dev"链接
3. 下载最新完整版（如`ffmpeg-git-full.7z`）
4. 解压到`C:\Program Files\ffmpeg`（可以自定义路径）
5. 配置环境变量：
   - 编辑"Path"变量，添加：`C:\Program Files\ffmpeg\bin`
6. 验证安装：
   - 命令提示符输入：`ffmpeg -version`
   - 应显示FFmpeg版本信息

## 6. 数据库配置（详细Windows步骤）
1. 打开命令提示符
2. 登录MySQL（使用安装时设置的root密码）：
   ```bash
   mysql -u root -p
   ```
3. 创建数据库：
   ```sql
   CREATE DATABASE xiaozhi;
   ```
4. 创建用户并授权：
   ```sql
   CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
   GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
   FLUSH PRIVILEGES;
   ```
5. 初始化数据库：
   - 确保您已经克隆了项目代码
   - 在命令提示符中导航到项目目录下的`db`文件夹
   - 执行：
     ```bash
     mysql -u root -p xiaozhi < init.sql
     ```

## 7. Vosk语音识别模型安装（Windows）
1. 下载中文模型：[Vosk模型](https://alphacephei.com/vosk/models)
   - 选择`vosk-model-cn-0.22`（或最新中文模型）
2. 解压下载的zip文件
3. 在项目根目录创建`models`文件夹（如果不存在）
4. 将解压后的模型文件夹重命名为`vosk-model`并放入`models`目录
5. 完整路径应为：`项目目录\models\vosk-model`

## 后端部署（Windows）
1. 克隆项目（如果尚未克隆）：
   ```bash
   git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java
   ```
2. 进入项目目录：
   ```bash
   cd xiaozhi-esp32-server-java
   ```
3. 使用Maven构建：
   ```bash
   mvn clean package -DskipTests
   ```
4. 运行后端服务：
   ```bash
   java -jar target\xiaozhi.server-*.jar（版本号可能不同）
   ```

## 前端部署（Windows）
1. 打开新的命令提示符窗口
2. 导航到前端目录：
   ```bash
   cd xiaozhi-esp32-server-java\web
   ```
3. 安装依赖：
   ```bash
   npm install
   ```
4. 运行开发服务器：
   ```bash
   npm run dev
   ```

## 访问系统
1. 确保后端服务正在运行
2. 确保前端开发服务器正在运行
3. 打开浏览器访问：[http://localhost:8084](http://localhost:8084)
4. 使用默认凭据登录：
   - 用户名：`admin`
   - 密码：`123456`

## Windows常见问题解决
1. 端口冲突：
   - 如果8084端口被占用，可以：
     - 编辑`src\main\resources\application.properties`，修改`server.port`
     - 编辑前端`web`目录下的配置文件相应修改API地址
2. FFmpeg找不到：
   - 确保已正确添加FFmpeg到PATH
   - 重启命令提示符窗口后重试
3. MySQL连接问题：
   - 确保MySQL服务已启动（可在服务管理器中检查）
   - 检查`application.properties`中的数据库配置
4. 缺少依赖：
   - 如果构建失败，尝试：
     ```bash
     mvn clean install
     ```
   - 确保网络连接正常，能访问Maven中央仓库
