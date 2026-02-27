package com.alibaba.cloud.ai.copilot.hook;

import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeService;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识上下文 Hook
 * 在模型调用前,自动从知识库检索相关内容并注入到上下文中
 *
 * @author RobustH
 */
@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
@RequiredArgsConstructor
public class KnowledgeContextHook extends MessagesModelHook {

    private final KnowledgeService knowledgeService;
    private final KnowledgeAvailabilityChecker availabilityChecker;

    private static final int MAX_RESULTS = 3;  // 最多注入 3 条知识
    private static final int MIN_QUERY_LENGTH = 5;  // 最小查询长度

    @Override
    public String getName() {
        return "knowledge_context_hook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        try {
            // Milvus 不可用时直接跳过，不干预消息流
            if (!availabilityChecker.isAvailable()) {
                log.debug("向量数据库不可用，跳过知识上下文注入");
                return new AgentCommand(previousMessages);
            }

            // 只跳过 ReactAgent 工具调用内部循环（最后一条消息是工具响应时）
            // 允许多轮对话也能注入知识（不能因为有历史 AssistantMessage 就跳过）
            boolean isToolCallLoop = !previousMessages.isEmpty() &&
                    previousMessages.get(previousMessages.size() - 1) instanceof ToolResponseMessage;

            if (isToolCallLoop) {
                log.debug("ReactAgent 工具调用内部循环,跳过知识上下文注入");
                return new AgentCommand(previousMessages);
            }

            // 获取 userId
            String userId = getUserId(config);
            if (userId == null) {
                log.warn("未找到 userId，跳过知识上下文注入");
                return new AgentCommand(previousMessages);
            }

            // 提取用户查询
            String userQuery = extractUserQuery(previousMessages);
            if (userQuery == null || userQuery.length() < MIN_QUERY_LENGTH) {
                log.debug("用户查询为空或太短,跳过知识上下文注入");
                return new AgentCommand(previousMessages);
            }

            log.info("开始知识库搜索: userId={}, query={}", userId, userQuery);
            // 搜索相关知识
            List<Document> knowledgeDocs = knowledgeService.search(userId, userQuery, MAX_RESULTS);
            log.info("知识库搜索结果: userId={}, 结果数={}", userId, knowledgeDocs.size());
            if (knowledgeDocs.isEmpty()) {
                log.debug("未找到相关知识,跳过上下文注入: query={}", userQuery);
                return new AgentCommand(previousMessages);
            }

            // 格式化知识上下文
            String knowledgeContext = knowledgeService.formatAsContext(knowledgeDocs);
            if (knowledgeContext == null || knowledgeContext.trim().isEmpty()) {
                return new AgentCommand(previousMessages);
            }

            // 构建上下文消息
            SystemMessage contextMessage = new SystemMessage(
                    "## 用户项目上下文\n\n" +
                    "以下是从用户知识库中检索到的相关内容,可以帮助你更好地理解用户的项目:\n\n" +
                    knowledgeContext + "\n\n" +
                    "请基于这些上下文信息回答用户的问题。"
            );

            // 在消息列表开头注入上下文 (在 SystemMessage 之后,UserMessage 之前)
            List<Message> updatedMessages = injectContext(previousMessages, contextMessage);

            log.info("已注入知识上下文: userId={}, 知识块数={}, 查询={}", 
                    userId, knowledgeDocs.size(), userQuery);

            return new AgentCommand(updatedMessages, UpdatePolicy.REPLACE);

        } catch (Exception e) {
            log.error("知识上下文注入失败", e);
            return new AgentCommand(previousMessages);
        }
    }

    /**
     * 从配置中获取用户 ID
     */
    private String getUserId(RunnableConfig config) {
        // 优先从 RunnableConfig.metadata 读取（官方文档方式）
        // config.metadata("key") 返回 Optional<?>，直接 orElse 取值
        if (config != null) {
            try {
                Object userIdObj = config.metadata("userId").orElse(null);
                if (userIdObj != null) {
                    log.info("从 RunnableConfig.metadata 获取到 userId: {}", userIdObj);
                    return userIdObj.toString();
                }
            } catch (Exception e) {
                log.warn("从 RunnableConfig 获取 userId 失败", e);
            }
        }

        // 降级：从 Sa-Token 当前请求线程获取
        try {
            if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
                String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
                log.info("从 Sa-Token 降级获取到 userId: {}", userId);
                return userId;
            }
        } catch (Exception e) {
            log.warn("无法从 Sa-Token 获取 userId", e);
        }

        log.warn("无法获取 userId，知识库上下文注入将被跳过");
        return null;
    }

    /**
     * 提取用户查询
     */
    private String extractUserQuery(List<Message> messages) {
        // 从后往前找第一个 UserMessage
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage userMsg) {
                return userMsg.getText();
            }
        }
        return null;
    }

    /**
     * 注入上下文消息
     * 策略: 在第一个 SystemMessage 之后插入
     */
    private List<Message> injectContext(List<Message> messages, SystemMessage contextMessage) {
        List<Message> result = new ArrayList<>();
        
        boolean contextInjected = false;
        for (Message msg : messages) {
            result.add(msg);
            
            // 在第一个 SystemMessage 之后插入上下文
            if (!contextInjected && msg instanceof SystemMessage) {
                result.add(contextMessage);
                contextInjected = true;
            }
        }

        // 如果没有 SystemMessage,在开头插入
        if (!contextInjected) {
            result.add(0, contextMessage);
        }

        return result;
    }
}
