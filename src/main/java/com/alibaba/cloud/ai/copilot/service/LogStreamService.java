package com.alibaba.cloud.ai.copilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE日志推送服务
 * 负责将AOP日志实时推送到前端
 */
@Service
public class LogStreamService {

    private static final Logger logger = LoggerFactory.getLogger(LogStreamService.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 活跃的SSE连接 taskId -> SseEmitter
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();

    // JSON序列化器
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 建立SSE连接
     */
    public SseEmitter createConnection(String taskId) {
        logger.info("🔗 建立SSE连接: taskId={}", taskId);

        SseEmitter emitter = new SseEmitter(0L); // 无超时

        // 设置连接事件处理
        emitter.onCompletion(() -> {
            logger.info("✅ SSE连接完成: taskId={}", taskId);
            activeConnections.remove(taskId);
        });

        emitter.onTimeout(() -> {
            logger.warn("⏰ SSE连接超时: taskId={}", taskId);
            activeConnections.remove(taskId);
        });

        emitter.onError((ex) -> {
            logger.error("❌ SSE连接错误: taskId={}, error={}", taskId, ex.getMessage());
            activeConnections.remove(taskId);
        });

        // 保存连接
        activeConnections.put(taskId, emitter);

        // 发送连接成功消息
        sendLogEvent(taskId, LogEvent.createConnectionEvent(taskId));

        return emitter;
    }

    /**
     * 关闭SSE连接
     */
    public void closeConnection(String taskId) {
        SseEmitter emitter = activeConnections.remove(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
                logger.info("🔚 关闭SSE连接: taskId={}", taskId);
            } catch (Exception e) {
                logger.error("关闭SSE连接失败: taskId={}, error={}", taskId, e.getMessage());
            }
        }
    }

    /**
     * 推送工具执行概要事件
     */
    public void pushToolExecutionSummary(String taskId, String toolName, String filePath, String summary, String reason) {
        AnalysisEvent event = new AnalysisEvent();
        event.setType("TOOL_EXECUTION_SUMMARY");
        event.setTaskId(taskId);
        event.setStepName("工具执行概要");
        event.setDescription(summary);
        event.setDetails(reason);
        event.setStatus("PLANNING");
        event.setMessage("准备执行工具: " + toolName);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon(getToolIcon(toolName));

        sendLogEvent(taskId, event);
    }

    /**
     * 推送工具开始执行事件
     */
    public void pushToolStart(String taskId, String toolName, String filePath, String message) {
        ToolLogEvent event = new ToolLogEvent();
        event.setType("TOOL_START");
        event.setTaskId(taskId);
        event.setToolName(toolName);
        event.setFilePath(filePath);
        event.setMessage(message);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon(getToolIcon(toolName));
        event.setStatus("RUNNING");

        sendLogEvent(taskId, event);
    }

    /**
     * 推送工具执行成功事件
     */
    public void pushToolSuccess(String taskId, String toolName, String filePath, String message, long executionTime) {
        ToolLogEvent event = new ToolLogEvent();
        event.setType("TOOL_SUCCESS");
        event.setTaskId(taskId);
        event.setToolName(toolName);
        event.setFilePath(filePath);
        event.setMessage(message);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon(getToolIcon(toolName));
        event.setStatus("SUCCESS");
        event.setExecutionTime(executionTime);

        sendLogEvent(taskId, event);
    }

    /**
     * 推送工具执行失败事件
     */
    public void pushToolError(String taskId, String toolName, String filePath, String message, long executionTime) {
        ToolLogEvent event = new ToolLogEvent();
        event.setType("TOOL_ERROR");
        event.setTaskId(taskId);
        event.setToolName(toolName);
        event.setFilePath(filePath);
        event.setMessage(message);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("❌");
        event.setStatus("ERROR");
        event.setExecutionTime(executionTime);

        sendLogEvent(taskId, event);
    }

    /**
     * 推送AI分析过程事件
     */
    public void pushAnalysisStep(String taskId, String stepName, String description, String status) {
        AnalysisEvent event = new AnalysisEvent();
        event.setType("ANALYSIS_STEP");
        event.setTaskId(taskId);
        event.setStepName(stepName);
        event.setDescription(description);
        event.setStatus(status);
        event.setMessage(description);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon(getAnalysisIcon(stepName));

        sendLogEvent(taskId, event);
    }

    /**
     * 推送任务开始分析事件
     */
    public void pushTaskAnalysisStart(String taskId, String userMessage) {
        AnalysisEvent event = new AnalysisEvent();
        event.setType("TASK_ANALYSIS_START");
        event.setTaskId(taskId);
        event.setStepName("任务分析");
        event.setDescription("开始分析用户需求: " + (userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage));
        event.setStatus("ANALYZING");
        event.setMessage("AI正在分析您的需求...");
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("🧠");

        sendLogEvent(taskId, event);
    }

    /**
     * 推送执行计划生成事件
     */
    public void pushExecutionPlanGenerated(String taskId, String planSummary) {
        AnalysisEvent event = new AnalysisEvent();
        event.setType("EXECUTION_PLAN");
        event.setTaskId(taskId);
        event.setStepName("执行计划");
        event.setDescription(planSummary);
        event.setStatus("COMPLETED");
        event.setMessage("执行计划已生成");
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("📋");

        sendLogEvent(taskId, event);
    }

    /**
     * 推送任务完成事件
     */
    public void pushTaskComplete(String taskId) {
        LogEvent event = new LogEvent();
        event.setType("TASK_COMPLETE");
        event.setTaskId(taskId);
        event.setMessage("任务执行完成");
        event.setTimestamp(LocalDateTime.now().format(formatter));

        sendLogEvent(taskId, event);

        // 延迟关闭连接
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 等待2秒让前端处理完成事件
                closeConnection(taskId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 推送文件创建事件
     */
    public void pushFileCreated(String taskId, String filePath, String message) {
        FileStreamEvent event = new FileStreamEvent();
        event.setType("FILE_CREATED");
        event.setTaskId(taskId);
        event.setFilePath(filePath);
        event.setMessage(message);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("📄");
        event.setStatus("CREATED");

        sendLogEvent(taskId, event);
    }

    /**
     * 推送文件内容块写入事件
     */
    public void pushFileContentChunk(String taskId, String filePath, String chunk, int chunkIndex, long totalBytes, long writtenBytes) {
        FileStreamEvent event = new FileStreamEvent();
        event.setType("FILE_CONTENT_CHUNK");
        event.setTaskId(taskId);
        event.setFilePath(filePath);
        event.setMessage(String.format("写入内容块 %d (%d/%d bytes)", chunkIndex, writtenBytes, totalBytes));
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("✏️");
        event.setStatus("WRITING");
        event.setChunkIndex(chunkIndex);
        event.setTotalBytes(totalBytes);
        event.setWrittenBytes(writtenBytes);
        event.setContentChunk(chunk);

        sendLogEvent(taskId, event);
    }

    /**
     * 推送文件写入进度事件
     */
    public void pushFileWriteProgress(String taskId, String filePath, long totalBytes, long writtenBytes, double progressPercent) {
        FileStreamEvent event = new FileStreamEvent();
        event.setType("FILE_WRITE_PROGRESS");
        event.setTaskId(taskId);
        event.setFilePath(filePath);
        event.setMessage(String.format("写入进度: %.1f%% (%d/%d bytes)", progressPercent, writtenBytes, totalBytes));
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("📊");
        event.setStatus("WRITING");
        event.setTotalBytes(totalBytes);
        event.setWrittenBytes(writtenBytes);
        event.setProgressPercent(progressPercent);

        sendLogEvent(taskId, event);
    }

    /**
     * 推送文件写入完成事件
     */
    public void pushFileWriteComplete(String taskId, String filePath, long totalBytes, long executionTime) {
        FileStreamEvent event = new FileStreamEvent();
        event.setType("FILE_WRITE_COMPLETE");
        event.setTaskId(taskId);
        event.setFilePath(filePath);
        event.setMessage(String.format("文件写入完成 (%d bytes, %dms)", totalBytes, executionTime));
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("✅");
        event.setStatus("COMPLETE");
        event.setTotalBytes(totalBytes);
        event.setWrittenBytes(totalBytes);
        event.setProgressPercent(100.0);
        event.setExecutionTime(executionTime);

        sendLogEvent(taskId, event);
    }

    /**
     * 推送文件写入错误事件
     */
    public void pushFileWriteError(String taskId, String filePath, String errorMessage, long executionTime) {
        FileStreamEvent event = new FileStreamEvent();
        event.setType("FILE_WRITE_ERROR");
        event.setTaskId(taskId);
        event.setFilePath(filePath);
        event.setMessage("文件写入失败: " + errorMessage);
        event.setTimestamp(LocalDateTime.now().format(formatter));
        event.setIcon("❌");
        event.setStatus("ERROR");
        event.setExecutionTime(executionTime);

        sendLogEvent(taskId, event);
    }

    /**
     * 发送日志事件到前端
     */
    private void sendLogEvent(String taskId, Object event) {
        SseEmitter emitter = activeConnections.get(taskId);
        if (emitter != null) {
            try {
                String jsonData = objectMapper.writeValueAsString(event);
                logger.info("📤 准备推送日志事件: taskId={}, type={}, data={}", taskId,
                    event instanceof LogEvent ? ((LogEvent) event).getType() : "unknown", jsonData);

                emitter.send(SseEmitter.event()
                    .name("log")
                    .data(jsonData));

                logger.info("✅ 日志事件推送成功: taskId={}", taskId);
            } catch (IOException e) {
                logger.error("推送日志事件失败: taskId={}, error={}", taskId, e.getMessage());
                activeConnections.remove(taskId);
            }
        } else {
            logger.warn("⚠️ 未找到SSE连接: taskId={}, 无法推送事件", taskId);
        }
    }

    /**
     * 获取工具图标
     */
    private String getToolIcon(String toolName) {
        switch (toolName) {
            case "readFile": return "📖";
            case "writeFile": return "✏️";
            case "editFile": return "📝";
            case "listDirectory": return "📁";
            case "analyzeProject": return "🔍";
            case "scaffoldProject": return "🏗️";
            case "smartEdit": return "🧠";
            default: return "⚙️";
        }
    }

    /**
     * 获取分析步骤图标
     */
    private String getAnalysisIcon(String stepName) {
        switch (stepName) {
            case "任务分析": return "🧠";
            case "需求理解": return "💡";
            case "执行计划": return "📋";
            case "技术选型": return "🔧";
            case "架构设计": return "🏗️";
            case "文件规划": return "📁";
            case "代码生成": return "💻";
            case "测试验证": return "✅";
            default: return "🔍";
        }
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
}
