# 设备端 MCP 控制说明（水泵 + 灯带）

## 通信方式

设备通过 MQTT 或 WebSocket 与服务器连接。服务器在该连接上向设备下发 JSON。控制指令使用 MCP：消息根节点带 `"type": "mcp"`，payload 为 JSON-RPC 2.0 的请求体（对象）。

## 消息格式

```json
{
  "type": "mcp",
  "payload": {
    "jsonrpc": "2.0",
    "id": <数字，用于匹配响应>,
    "method": "tools/call",
    "params": {
      "name": "<工具名>",
      "arguments": { <工具参数，见下表> }
    }
  }
}
```

payload 在实现时可能是对象或已序列化的 JSON 字符串，需与设备端/协议约定一致。设备会按 MCP 协议用相同 id 回传执行结果。

## 水泵工具（name / arguments）

| 工具名 | 说明 | arguments |
|--------|------|-----------|
| self.pump.turn_on | 启动水泵（全速） | {} |
| self.pump.turn_off | 关闭水泵 | {} |
| self.pump.set_duty | 设置占空比 0–100% | {"duty": 80} |
| self.pump.get_status | 查询状态 | {} |

## 灯带工具（name / arguments）

| 工具名 | 说明 | arguments |
|--------|------|-----------|
| self.led_strip.set_brightness | 设置亮度等级 | {"level": 1} 到 8（0=关） |
| self.led_strip.get_brightness | 获取亮度 | {} |
| self.led_strip.set_all_color | 全部 LED 同色 | {"red":255,"green":255,"blue":255}（0–255） |
| self.led_strip.set_single_color | 单个 LED 颜色 | {"index":0,"red":255,"green":0,"blue":0}（index 0–7） |
| self.led_strip.blink | 闪烁 | {"red":255,"green":0,"blue":0,"interval":500}（interval 100–5000 ms） |
| self.led_strip.scroll | 跑马灯 | {"red":255,"green":0,"blue":0,"length":2,"interval":200}（length 1–8，interval 50–2000） |
| self.led_strip.breathe | 呼吸灯 | {"red":255,"green":0,"blue":0,"interval":100}（interval 10–500） |

## 示例：下发一条 MCP 指令

**启动水泵：**

```json
{
  "type": "mcp",
  "payload": {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "self.pump.turn_on",
      "arguments": {}
    }
  }
}
```

- **关闭水泵**：将 name 改为 `self.pump.turn_off`，arguments 仍为 `{}`。
- **灯带设为白光并亮度 5**：先发 `self.led_strip.set_brightness`、arguments: `{"level": 5}`，再发 `self.led_strip.set_all_color`、arguments: `{"red":255,"green":255,"blue":255}`。

## 服务器侧实现要点

- 在已有设备连接（MQTT 或 WebSocket）上发送上述 JSON，不要改 type 和 payload 的结构。
- 每次请求使用不同或递增的 id，便于和设备返回的 MCP 响应对应。
- 若设备端期望 payload 为字符串，则先把 payload 内的对象用 JSON.stringify 再赋给 payload 字段。
