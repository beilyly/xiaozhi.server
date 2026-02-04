package com.xiaozhi.dialogue.llm.tool.mcp.device;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.domain.DeviceMcpMessage;
import com.xiaozhi.communication.domain.mcp.device.DeviceMcpPayload;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpClientInfo;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpInitialize;
import com.xiaozhi.communication.domain.mcp.device.initialize.DeviceMcpVision;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceMcpService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceMcpService.class);

    @Resource
    private Environment environment;

    @Resource
    private CmsUtils cmsUtils;

    @Value("${xiaozhi.mcp:device:max.tools.count:32}")
    private static int maxToolsCount = 32; // 最大工具数量限制

    /**
     * 初始化设备端MCP工具列表
     *
     * @param chatSession
     */
    public void initialize(ChatSession chatSession) {
        //1、调用始化命令
        DeviceMcpMessage initResult = sendInitialize(chatSession);
        //根据调用结果进行处理
        if (initResult != null) {
            chatSession.getDeviceMcpHolder().setMcpInitialized(true);
        }
        if (chatSession.getDeviceMcpHolder().isMcpInitialized()) {
            //2、获取工具列表
            sendToolsList(chatSession);
            //3、注册水泵与灯带固定工具（与设备端 MCP 约定一致，见 docs/device-mcp-control.md）
            registerPumpAndLedStripTools(chatSession);
        }
    }

    /**
     * 注册水泵与灯带 MCP 工具，用于下发 self.pump.* / self.led_strip.* 的 tools/call 指令。
     */
    private void registerPumpAndLedStripTools(ChatSession chatSession) {
        int existingCount = chatSession.getToolCallbacks().size();
        if (existingCount + 11 > maxToolsCount) {
            logger.debug("SessionId: {}, skip pump/led_strip tools, tool count limit reached", chatSession.getSessionId());
            return;
        }
        List<ToolDef> tools = List.of(
            new ToolDef("self.pump.turn_on", "启动水泵（全速）", "{}"),
            new ToolDef("self.pump.turn_off", "关闭水泵", "{}"),
            new ToolDef("self.pump.set_duty", "设置水泵占空比0-100%", "{\"type\":\"object\",\"properties\":{\"duty\":{\"type\":\"integer\",\"description\":\"占空比0-100\"}}}"),
            new ToolDef("self.pump.get_status", "查询水泵状态", "{}"),
            new ToolDef("self.led_strip.set_brightness", "设置灯带亮度等级0-8（0=关）", "{\"type\":\"object\",\"properties\":{\"level\":{\"type\":\"integer\",\"description\":\"亮度0-8\"}}}"),
            new ToolDef("self.led_strip.get_brightness", "获取灯带亮度", "{}"),
            new ToolDef("self.led_strip.set_all_color", "全部LED同色，RGB 0-255", "{\"type\":\"object\",\"properties\":{\"red\":{\"type\":\"integer\"},\"green\":{\"type\":\"integer\"},\"blue\":{\"type\":\"integer\"}}}"),
            new ToolDef("self.led_strip.set_single_color", "单个LED颜色，index 0-7", "{\"type\":\"object\",\"properties\":{\"index\":{\"type\":\"integer\"},\"red\":{\"type\":\"integer\"},\"green\":{\"type\":\"integer\"},\"blue\":{\"type\":\"integer\"}}}"),
            new ToolDef("self.led_strip.blink", "灯带闪烁，interval 100-5000ms", "{\"type\":\"object\",\"properties\":{\"red\":{\"type\":\"integer\"},\"green\":{\"type\":\"integer\"},\"blue\":{\"type\":\"integer\"},\"interval\":{\"type\":\"integer\"}}}"),
            new ToolDef("self.led_strip.scroll", "跑马灯，length 1-8，interval 50-2000ms", "{\"type\":\"object\",\"properties\":{\"red\":{\"type\":\"integer\"},\"green\":{\"type\":\"integer\"},\"blue\":{\"type\":\"integer\"},\"length\":{\"type\":\"integer\"},\"interval\":{\"type\":\"integer\"}}}"),
            new ToolDef("self.led_strip.breathe", "呼吸灯，interval 10-500ms", "{\"type\":\"object\",\"properties\":{\"red\":{\"type\":\"integer\"},\"green\":{\"type\":\"integer\"},\"blue\":{\"type\":\"integer\"},\"interval\":{\"type\":\"integer\"}}}")
        );
        for (ToolDef def : tools) {
            ToolCallback toolCallback = buildMcpToolCallback(chatSession, def.name, def.description, def.inputSchema);
            String funcName = "mcp_" + def.name.replace(".", "_");
            chatSession.getToolsSessionHolder().registerFunction(funcName, toolCallback);
        }
        logger.debug("SessionId: {}, pump and led_strip MCP tools registered", chatSession.getSessionId());
    }

    private ToolCallback buildMcpToolCallback(ChatSession chatSession, String name, String description, String inputSchema) {
        return FunctionToolCallback
            .builder("mcp_" + name.replace(".", "_"), (Map<String, Object> params, ToolContext toolContext) -> {
                DeviceMcpMessage request = new DeviceMcpMessage();
                request.setSessionId(chatSession.getSessionId());
                DeviceMcpPayload requestPayload = new DeviceMcpPayload();
                requestPayload.setMethod("tools/call");
                requestPayload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
                requestPayload.setParams(Map.of(
                    "name", name,
                    "arguments", params != null ? params : Map.of()
                ));
                request.setPayload(requestPayload);
                DeviceMcpMessage response = sendMcpRequest(chatSession, request);
                if (response != null) {
                    logger.info("SessionId: {}, MCP tools/call response: {}", chatSession.getSessionId(), response);
                    if (response.getPayload().getResult() == null) {
                        return response.getPayload().getError() != null ? response.getPayload().getError().get("message") : "未知错误";
                    }
                    if ("false".equals(String.valueOf(response.getPayload().getResult().get("isError")))) {
                        return response.getPayload().getResult().get("content");
                    }
                    return response.getPayload().getError();
                }
                return "操作失败";
            })
            .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
            .description(description)
            .inputSchema(inputSchema)
            .inputType(Map.class)
            .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
            .build();
    }

    private record ToolDef(String name, String description, String inputSchema) {}

    /**
     * 发送初始化命令
     *
     * @param chatSession
     * @return
     */
    protected DeviceMcpMessage sendInitialize(ChatSession chatSession) {
        DeviceMcpMessage message = new DeviceMcpMessage();
        message.setSessionId(chatSession.getSessionId());
        DeviceMcpPayload payload = new DeviceMcpPayload();
        payload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
        payload.setMethod("initialize");

        DeviceMcpInitialize initialize = deviceMcpInitialize(chatSession);

        payload.setParams(initialize);
        message.setPayload(payload);

        DeviceMcpMessage result = sendMcpRequest(chatSession, message);
        if (result != null) {
            logger.debug("SessionId: {}, MCP initialized successfully", chatSession.getSessionId());
            return result;
        }
        return null;
    }

    /**
     * 摄像头视觉相关, 根据实际需要设置vision的属性
     */
    @NotNull
    private DeviceMcpInitialize deviceMcpInitialize(ChatSession chatSession) {
        // MCP初始化参数
        DeviceMcpInitialize initialize = new DeviceMcpInitialize();
        initialize.setClientInfo(new DeviceMcpClientInfo());

        DeviceMcpVision vision = new DeviceMcpVision();

        //VLChatController
        String url = "http://" + cmsUtils.getServerIp() + ":8091/api/vl/chat";
        vision.setUrl(url);
        vision.setToken(chatSession.getSessionId());

        initialize.setCapabilities(Map.of(
                "vision", vision
        ));
        return initialize;
    }

    /**
     * 发送工具列表请求
     *
     * @param chatSession
     */
    private void sendToolsList(ChatSession chatSession) {
        DeviceMcpMessage message = new DeviceMcpMessage();
        message.setSessionId(chatSession.getSessionId());
        DeviceMcpPayload payload = new DeviceMcpPayload();
        payload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
        payload.setMethod("tools/list");
        if (chatSession.getDeviceMcpHolder().getMcpCursor() != null) {
            payload.setParams(Map.of(
                    "cursor", chatSession.getDeviceMcpHolder().getMcpCursor()));
        } else {
            payload.setParams(Map.of(
                    "cursor", "")); // 初始请求时使用空字符串
        }
        message.setPayload(payload);

        DeviceMcpMessage result = sendMcpRequest(chatSession, message);
        if (result != null) {
            //处理工具的注册
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.getPayload().getResult().get("tools");
            Object nextCursor = result.getPayload().getResult().get("nextCursor");
            int toolsCount = chatSession.getToolCallbacks().size();
            if (tools.isEmpty() || (toolsCount + tools.size()) > maxToolsCount) {//工具数量超过限制，不再添加
                return;
            } else {
                for (Map<String, Object> tool : tools) {
                    //开始注册工具
                    String name = (String) tool.get("name");
                    String funcName = "mcp_" + name.replace(".", "_");
                    String funcDescription = (String) tool.get("description");
                    Object inputSchema = tool.get("inputSchema");

                    ToolCallback toolCallback = FunctionToolCallback
                            .builder(funcName, (Map<String, Object> params, ToolContext toolContext) -> {
                                DeviceMcpMessage request = new DeviceMcpMessage();
                                request.setSessionId(chatSession.getSessionId());

                                DeviceMcpPayload requestPayload = new DeviceMcpPayload();
                                requestPayload.setMethod("tools/call");
                                requestPayload.setId(chatSession.getDeviceMcpHolder().getMcpRequestId());
                                requestPayload.setParams(Map.of(
                                        "name", name,
                                        "arguments", params
                                ));

                                request.setPayload(requestPayload);
                                DeviceMcpMessage response = sendMcpRequest(chatSession, request);
                                if (response != null) {
                                    logger.info("SessionId: {},  MCP function call response: {}", chatSession.getSessionId(), response);
                                    //空指针
                                    if (response.getPayload().getResult() == null) {
                                        return response.getPayload().getError().get("message");//返回结果
                                    }
                                    if ("false".equals(String.valueOf(response.getPayload().getResult().get("isError")))) {
                                        return response.getPayload().getResult().get("content");//返回结果
                                    } else {
                                        return response.getPayload().getError();
                                    }
                                } else {
                                    return "操作失败";
                                }
                            })
                            .toolMetadata(ToolMetadata.builder().returnDirect(false).build())// 设置返回值需要ai再处理
                            .description(funcDescription)
                            .inputSchema(JsonUtil.toJson(inputSchema))
                            .inputType(Map.class)
                            .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                            .build();
                    // 注册到当前会话的函数持有者
                    chatSession.getToolsSessionHolder().registerFunction(funcName, toolCallback);
                }
            }
            // 如果cursor不为空，则迭代调用
            if (nextCursor != null && !nextCursor.toString().isEmpty()) {
                // 如果有下一页游标，继续请求下一页
                chatSession.getDeviceMcpHolder().setMcpCursor(nextCursor.toString());
                sendToolsList(chatSession);
            } else {
                // 所有工具加载完成
                chatSession.getDeviceMcpHolder().setMcpCursor(null);
                logger.debug("SessionId: {}, mcp tools loaded successfully", chatSession.getSessionId());
            }
        }
    }

    public DeviceMcpMessage sendMcpRequest(ChatSession chatSession, DeviceMcpMessage mcpMessage) {
        Long id = mcpMessage.getPayload().getId();
        CompletableFuture<DeviceMcpMessage> future = new CompletableFuture<>();
        chatSession.sendTextMessage(JsonUtil.toJson(mcpMessage));
        chatSession.getDeviceMcpHolder().getMcpPendingRequests().put(id, future);

        DeviceMcpMessage response = null;
        try {
            // 阻塞并等待异步操作完成
            response = future.get(30, TimeUnit.SECONDS);//等待2秒，没反应则退出
        } catch (Exception e) {
            logger.error("SessionId: {}, Error sending MCP request", chatSession.getSessionId(), e);
            chatSession.getDeviceMcpHolder().getMcpPendingRequests().remove(id);
        }
        return response;
    }

}