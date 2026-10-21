package com.kuoge.agentos.orchestration;

import java.util.*;

/**
 * 有向无环图（DAG）执行图。
 */
public class ExecutionGraph {

    private final Map<String, AgentTask> tasks = new LinkedHashMap<>();
    private final Map<String, Set<String>> downstream = new HashMap<>();

    public ExecutionGraph addTask(AgentTask task) {
        tasks.put(task.taskId(), task);
        downstream.putIfAbsent(task.taskId(), new HashSet<>());
        for (String dep : task.dependsOn()) {
            downstream.computeIfAbsent(dep, k -> new HashSet<>()).add(task.taskId());
        }
        return this;
    }

    public List<AgentTask> getReadyTasks(Set<String> completed) {
        List<AgentTask> ready = new ArrayList<>();
        for (AgentTask task : tasks.values()) {
            if (task.status() == TaskStatus.PENDING && completed.containsAll(task.dependsOn())) {
                ready.add(task);
            }
        }
        return ready;
    }

    public AgentTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public Collection<AgentTask> allTasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    public Set<String> getDownstream(String taskId) {
        return downstream.getOrDefault(taskId, Collections.emptySet());
    }

    public boolean isCompleted(Set<String> completed) {
        return completed.containsAll(tasks.keySet());
    }
}
