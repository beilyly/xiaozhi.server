# 小智ESP32服务器CentOS部署文档

## 系统要求

- CentOS 7/8（推荐CentOS 8）
- 最小化安装 + 开发工具（gcc, make等）
- 至少2GB内存（推荐4GB）
- 至少10GB磁盘空间

## 1. 环境准备

### 1.1 安装基础工具

```bash
sudo yum install -y epel-release
sudo yum install -y wget curl git vim unzip
```

### 1.2 配置防火墙

```bash
sudo firewall-cmd --permanent --add-port=8084/tcp
sudo firewall-cmd --permanent --add-port=8091/tcp
sudo firewall-cmd --permanent --add-port=3306/tcp
sudo firewall-cmd --reload
```

## 2. 安装Java JDK 21

```bash
sudo yum install -y java-21-openjdk java-21-openjdk-devel
```

验证安装：

```bash
java -version
```

## 3. 安装MySQL 8.0

```bash
# 安装MySQL 8.0存储库
sudo yum localinstall -y https://dev.mysql.com/get/mysql80-community-release-el7-7.noarch.rpm
sudo yum install -y mysql-community-server
```

启动MySQL服务：

```bash
sudo systemctl start mysqld
sudo systemctl enable mysqld
```

获取临时密码：

```bash
sudo grep 'temporary password' /var/log/mysqld.log
```

安全设置：

```bash
sudo mysql_secure_installation
```

## 4. 安装Maven

```bash
sudo yum install -y maven
```

验证安装：

```bash
mvn -v
```

## 5. 安装Node.js 16

```bash
curl -sL https://rpm.nodesource.com/setup_16.x | sudo bash -
sudo yum install -y nodejs
```

验证安装：

```bash
node -v
npm -v
```

## 6. 安装FFmpeg

```bash
sudo yum install -y ffmpeg ffmpeg-devel
```

验证安装：

```bash
ffmpeg -version
```

## 7. 数据库配置

创建数据库和用户：

```sql
mysql -u root -p
CREATE DATABASE xiaozhi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'xiaozhi'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';
FLUSH PRIVILEGES;
exit
```

导入初始化脚本：

```bash
mysql -u root -p xiaozhi < db/init.sql
```

## 8. 下载Vosk语音识别模型

```bash
wget https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip
unzip vosk-model-cn-0.22.zip
mkdir -p models
mv vosk-model-cn-0.22 models/vosk-model
```

## 9. 项目部署

### 9.1 克隆项目

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java
cd xiaozhi-esp32-server-java
```

### 9.2 后端部署

```bash
mvn clean package -DskipTests
java -jar target/xiaozhi.server-*.jar &（版本号可能不同）
```

### 9.3 前端部署

```bash
cd web
npm install
npm run build
```

## 10. 配置系统服务（可选）

### 10.1 创建后端服务

编辑服务文件：

```bash
sudo vim /etc/systemd/system/xiaozhi.service
```

添加内容：

```
[Unit]
Description=Xiaozhi ESP32 Server
After=syslog.target network.target

[Service]
User=root
WorkingDirectory=/path/to/xiaozhi-esp32-server-java
ExecStart=/usr/bin/java -jar target/xiaozhi.server-*.jar（版本号可能不同）
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl start xiaozhi
sudo systemctl enable xiaozhi
```

### 10.2 配置Nginx（可选）

```bash
sudo yum install -y nginx
sudo vim /etc/nginx/conf.d/xiaozhi.conf
```

添加配置：

```
server {
    listen 80;
    server_name your_domain_or_ip;

    location / {
        root /path/to/xiaozhi-esp32-server-java/web/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8091;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

启动Nginx：

```bash
sudo systemctl start nginx
sudo systemctl enable nginx
```

## 11. 访问系统

- 直接访问：`http://your_server_ip:8084`
- 如果配置了Nginx：`http://your_domain_or_ip`
- 默认管理员账号：admin/123456

## 常见问题解决

1. **MySQL初始化失败**

   ```bash
   sudo systemctl restart mysqld
   mysql_upgrade -u root -p
   ```

2. **端口冲突**

   ```bash
   netstat -tulnp | grep 8084
   kill -9 <PID>
   ```

3. **Node.js版本问题**

   ```bash
   sudo yum remove -y nodejs npm
   curl -sL https://deb.nodesource.com/setup_16.x | sudo -E bash -
   sudo yum install -y nodejs
   ```

4. **内存不足**

   增加swap空间：

   ```bash
   sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
   ```

5. **Vosk模型加载失败**

   ```bash
   chmod -R 755 models
   ```

## 维护命令

- 查看后端日志：

  ```bash
  journalctl -u xiaozhi -f
  ```

- 更新代码：

  ```bash
  git pull origin master
  mvn clean package -DskipTests
  sudo systemctl restart xiaozhi
  ```

- 数据库备份：

  ```bash
  mysqldump -u root -p xiaozhi > xiaozhi_backup_$(date +%Y%m%d).sql
  ```

## 注意事项

1. **生产环境建议：**
   - 修改默认密码
   - 配置HTTPS
   - 定期备份数据库

2. **性能优化：**

   增加JVM内存：

   ```bash
   java -Xms512m -Xmx1024m -jar target/xiaozhi.server-*.jar（版本号可能不同）
   ```
