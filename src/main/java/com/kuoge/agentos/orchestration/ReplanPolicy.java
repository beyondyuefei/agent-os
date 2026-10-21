package com.kuoge.agentos.orchestration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 重计划策略 —— 支持失败后的局部重试、跳过或停止。
 *
 * <p>借鉴 Hermes Agent 的 resilient workflows：当子 Agent 输出未通过检查点或抛出异常时，
 * 编排器可根据策略决定下一步动作，而不是直接让整个工作流失败。
 */
public interface ReplanPolicy {

    /**
     * 旧版兼容接口：直接返回新图。
     */
    ExecutionGraph replan(ExecutionGraph currentGraph, AgentTask failedTask);

    /**
     * 新版接口：携带重试上下文。
     */
    ReplanDecision replan(ExecutionGraph currentGraph, AgentTask failedTask, ReplanContext context);

    static ReplanPolicy stopOnFailure() {
        return new ReplanPolicy() {
            @Override
            public ExecutionGraph replan(ExecutionGraph currentGraph, AgentTask failedTask) {
                return currentGraph;
            }

            @Override
            public ReplanDecision replan(ExecutionGraph currentGraph, AgentTask failedTask, ReplanContext context) {
                return ReplanDecision.stop("Stop on failure: " + failedTask.errorMessage());
            }
        };
    }

    static ReplanPolicy retryOnce(BiFunction<AgentTask, Integer, AgentTask> taskBuilder) {
        return new ReplanPolicy() {
            @Override
            public ExecutionGraph replan(ExecutionGraph currentGraph, AgentTask failedTask) {
                String retryId = failedTask.taskId() + "_retry";
                AgentTask retryTask = taskBuilder.apply(failedTask, 1);
                return new ExecutionGraph().addTask(retryTask);
            }

            @Override
            public ReplanDecision replan(ExecutionGraph currentGraph, AgentTask failedTask, ReplanContext context) {
                int count = context.getRetryCount(failedTask.taskId());
                if (count >= 1) {
                    return ReplanDecision.stop("Max retries exceeded for " + failedTask.taskId());
                }
                String retryId = failedTask.taskId() + "_retry_" + (count + 1);
                AgentTask retryTask = taskBuilder.apply(failedTask, count + 1)
                        .withStatus(TaskStatus.PENDING);
                context.recordRetry(failedTask.taskId());
                return ReplanDecision.retry(retryTask, "Retry task " + failedTask.taskId());
            }
        };
    }

    static ReplanPolicy skipOnFailure() {
        return new ReplanPolicy() {
            @Override
            public ExecutionGraph replan(ExecutionGraph currentGraph, AgentTask failedTask) {
                return currentGraph;
            }

            @Override
            public ReplanDecision replan(ExecutionGraph currentGraph, AgentTask failedTask, ReplanContext context) {
                return ReplanDecision.skip("Skip failed task " + failedTask.taskId());
            }
        };
    }

    /**
     * 重计划上下文，记录每个任务的失败与重试次数。
     */
    class ReplanContext {
        private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();
        private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

        public void recordRetry(String taskId) {
            retryCounts.merge(taskId, 1, Integer::sum);
        }

        public void recordFailure(String taskId) {
            failureCounts.merge(taskId, 1, Integer::sum);
        }

        public int getRetryCount(String taskId) {
            return retryCounts.getOrDefault(taskId, 0);
        }

        public int getFailureCount(String taskId) {
            return failureCounts.getOrDefault(taskId, 0);
        }
    }

    /**
     * 重计划决策。
     */
    record ReplanDecision(Action action, AgentTask newTask, String reason) {
        enum Action {
            STOP,
            RETRY,
            SKIP
        }

        static ReplanDecision stop(String reason) {
            return new ReplanDecision(Action.STOP, null, reason);
        }

        static ReplanDecision retry(AgentTask newTask, String reason) {
            return new ReplanDecision(Action.RETRY, newTask, reason);
        }

        static ReplanDecision skip(String reason) {
            return new ReplanDecision(Action.SKIP, null, reason);
        }
    }
}
