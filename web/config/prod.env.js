'use strict'
module.exports = {
  NODE_ENV: '"production"',
  BASE_API: '""',  // 保持为空字符串，使用相对路径
  BASE_URL: '"https://8.130.214.215"', // 您的生产环境前端URL
  BACKEND_URL: '"https://8.130.214.215"' // 生产环境下为空，使用相对路径由Nginx代理
}
